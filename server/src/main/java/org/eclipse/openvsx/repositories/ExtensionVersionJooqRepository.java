/** ******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.repositories;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JoinType;
import org.jooq.Record;
import org.jooq.Row1;
import org.jooq.Row2;
import org.jooq.SelectQuery;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.ListOfStringConverter;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.PersonalAccessTokenType;
import org.eclipse.openvsx.entities.SignatureKeyPair;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.ChangeEntryJson;
import org.eclipse.openvsx.json.QueryRequest;
import org.eclipse.openvsx.json.TargetPlatformActiveJson;
import org.eclipse.openvsx.json.VersionTargetPlatformsJson;
import org.eclipse.openvsx.util.ChangesCursor;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.TargetPlatformVersion;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.VersionAlias;

import static org.eclipse.openvsx.jooq.Tables.*;
import static org.jooq.impl.DSL.*;

@Component
public class ExtensionVersionJooqRepository {

    private final DSLContext dsl;

    public ExtensionVersionJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * An extension's stable release history stays bounded on its own, but a pre-release channel
     * can publish one build per commit and accumulate thousands of entries - {@code
     * maxPreReleaseVersions} keeps only that many of the most recent ones per extension (across all
     * of its target platforms combined), ranked by the same semver-based ordering {@code
     * ExtensionVersion.SORT_COMPARATOR} uses for "latest", so the true latest pre-release - rank #1 -
     * is never dropped by the cap. The caller decides the limit rather than it being fixed here; a
     * negative value (e.g. {@code -1}) disables the cap entirely, matching the "negative means
     * unlimited" convention already used for {@code ovsx.data.mirror.requests-per-second}.
     */
    public List<ExtensionVersion> findAllActiveByExtensionIdAndTargetPlatform(
            Collection<Long> extensionIds,
            String targetPlatform,
            int maxPreReleaseVersions
    ) {
        if (maxPreReleaseVersions < 0) {
            // No ranking is needed at all when the cap is disabled (the default), so this selects the
            // plain, unaliased columns and maps them the same way every other unranked query in this
            // class does (see toExtensionVersion), rather than the aliasing/remapping machinery below,
            // which exists only to make the capped path's CTE possible.
            var query = dsl.select(
                    NAMESPACE.ID,
                    NAMESPACE.NAME,
                    EXTENSION.ID,
                    EXTENSION.NAME,
                    EXTENSION_VERSION.ID,
                    EXTENSION_VERSION.VERSION,
                    EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                    EXTENSION_VERSION.REMOVED,
                    EXTENSION_VERSION.TARGET_PLATFORM,
                    EXTENSION_VERSION.PREVIEW,
                    EXTENSION_VERSION.PRE_RELEASE,
                    EXTENSION_VERSION.TIMESTAMP,
                    EXTENSION_VERSION.DISPLAY_NAME,
                    EXTENSION_VERSION.DESCRIPTION,
                    EXTENSION_VERSION.ENGINES,
                    EXTENSION_VERSION.CATEGORIES,
                    EXTENSION_VERSION.TAGS,
                    EXTENSION_VERSION.EXTENSION_KIND,
                    EXTENSION_VERSION.REPOSITORY,
                    EXTENSION_VERSION.SPONSOR_LINK,
                    EXTENSION_VERSION.GALLERY_COLOR,
                    EXTENSION_VERSION.GALLERY_THEME,
                    EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                    EXTENSION_VERSION.DEPENDENCIES,
                    EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                    SIGNATURE_KEY_PAIR.PUBLIC_ID)
                    .from(EXTENSION_VERSION)
                    .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                    .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                    .leftJoin(SIGNATURE_KEY_PAIR)
                    .on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                    .where(EXTENSION_VERSION.ACTIVE.eq(true))
                    .and(EXTENSION_VERSION.EXTENSION_ID.in(extensionIds));

            if (targetPlatform != null) {
                query = query.and(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
            }

            return query.fetch().map(this::toExtensionVersion);
        }

        // Capped: rank pre-releases with a single denseRank() window function (over all target
        // platforms combined, per this method's contract) and keep only the top maxPreReleaseVersions
        // of them, plus every stable release. The ranked query is wrapped in a CTE below so the "rank"
        // window-function alias computed here can be referenced in a WHERE clause - a plain SELECT
        // can't reference its own select-list alias from its own WHERE - which is also why every
        // column needs a unique name via .as(...): NAMESPACE.ID/EXTENSION.ID/EXTENSION_VERSION.ID
        // would otherwise all render as "id", making a by-name reference from the CTE ambiguous.
        var baseQuery = dsl.select(
                NAMESPACE.ID.as("namespace_id"),
                NAMESPACE.NAME.as("namespace_name"),
                EXTENSION.ID.as("extension_id"),
                EXTENSION.NAME.as("extension_name"),
                EXTENSION_VERSION.ID.as("extension_version_id"),
                EXTENSION_VERSION.VERSION.as("extension_version_version"),
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS.as("extension_version_potentially_malicious"),
                EXTENSION_VERSION.REMOVED.as("extension_version_removed"),
                EXTENSION_VERSION.TARGET_PLATFORM.as("extension_version_target_platform"),
                EXTENSION_VERSION.PREVIEW.as("extension_version_preview"),
                EXTENSION_VERSION.PRE_RELEASE.as("extension_version_pre_release"),
                EXTENSION_VERSION.TIMESTAMP.as("extension_version_timestamp"),
                EXTENSION_VERSION.DISPLAY_NAME.as("extension_version_display_name"),
                EXTENSION_VERSION.DESCRIPTION.as("extension_version_description"),
                EXTENSION_VERSION.ENGINES.as("extension_version_engines"),
                EXTENSION_VERSION.CATEGORIES.as("extension_version_categories"),
                EXTENSION_VERSION.TAGS.as("extension_version_tags"),
                EXTENSION_VERSION.EXTENSION_KIND.as("extension_version_extension_kind"),
                EXTENSION_VERSION.REPOSITORY.as("extension_version_repository"),
                EXTENSION_VERSION.SPONSOR_LINK.as("extension_version_sponsor_link"),
                EXTENSION_VERSION.GALLERY_COLOR.as("extension_version_gallery_color"),
                EXTENSION_VERSION.GALLERY_THEME.as("extension_version_gallery_theme"),
                EXTENSION_VERSION.LOCALIZED_LANGUAGES.as("extension_version_localized_languages"),
                EXTENSION_VERSION.DEPENDENCIES.as("extension_version_dependencies"),
                EXTENSION_VERSION.BUNDLED_EXTENSIONS.as("extension_version_bundled_extensions"),
                SIGNATURE_KEY_PAIR.PUBLIC_ID.as("signature_key_pair_public_id"),
                denseRank().over(
                        // Rows with no parsed semver (legacy/unparseable version strings) must sort
                        // behind every real semver version rather than in front of it - Postgres
                        // defaults DESC to NULLS FIRST, so nullsLast() is required here to keep the
                        // "rank #1 is the true latest pre-release" guarantee this method documents.
                        partitionBy(EXTENSION_VERSION.EXTENSION_ID, EXTENSION_VERSION.PRE_RELEASE).orderBy(
                                EXTENSION_VERSION.SEMVER_MAJOR.desc().nullsLast(),
                                EXTENSION_VERSION.SEMVER_MINOR.desc().nullsLast(),
                                EXTENSION_VERSION.SEMVER_PATCH.desc().nullsLast(),
                                EXTENSION_VERSION.TIMESTAMP.desc(),
                                EXTENSION_VERSION.ID.desc()))
                        .as("rank"))
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(EXTENSION_VERSION.EXTENSION_ID.in(extensionIds));

        var rankedExtensionVersion = name("ranked_extension_version").as(baseQuery);
        var query = dsl.with(rankedExtensionVersion)
                .select()
                .from(rankedExtensionVersion)
                .where(
                        rankedExtensionVersion.field("extension_version_pre_release", Boolean.class).eq(false)
                                .or(
                                        rankedExtensionVersion.field("rank", Integer.class)
                                                .lessOrEqual(maxPreReleaseVersions)));

        if (targetPlatform != null) {
            query = query.and(
                    rankedExtensionVersion.field("extension_version_target_platform", String.class)
                            .eq(targetPlatform));
        }

        return query.fetch().map(this::toRankedExtensionVersion);
    }

    public Page<String> findActiveVersionStringsSorted(
            String namespace,
            String extension,
            String targetPlatform,
            Pageable page
    ) {
        var count = DSL.countDistinct(EXTENSION_VERSION.VERSION);
        var totalQuery = dsl.select(count)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(EXTENSION_VERSION.ACTIVE.eq(true))
                .and(NAMESPACE.NAME.equalIgnoreCase(namespace))
                .and(EXTENSION.NAME.equalIgnoreCase(extension));

        if (targetPlatform != null) {
            totalQuery = totalQuery.and(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        conditions.add(NAMESPACE.NAME.equalIgnoreCase(namespace));
        conditions.add(EXTENSION.NAME.equalIgnoreCase(extension));
        if (targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        var versions = findVersionStringsSorted(conditions, page);
        var total = totalQuery.fetchOne(count);
        return new PageImpl<>(versions, page, total);
    }

    public Map<Long, List<String>> findActiveVersionStringsSorted(
            Collection<Long> extensionIds,
            String targetPlatform,
            int numberOfRows
    ) {
        if (extensionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        var ids = DSL.values(extensionIds.stream().map(DSL::row).toArray(Row1[]::new)).as("ids", "id");
        var topQuery = dsl.selectQuery();
        topQuery.addSelect(
                EXTENSION_VERSION.EXTENSION_ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE);
        topQuery.setDistinct(true);
        topQuery.addFrom(EXTENSION_VERSION);
        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.EXTENSION_ID.eq(ids.field("id", Long.class)));
        conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        if (targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        topQuery.addConditions(conditions);
        topQuery.addLimit(numberOfRows);
        topQuery.addOrderBy(
                EXTENSION_VERSION.EXTENSION_ID.asc(),
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.VERSION.asc());

        var top = topQuery.asTable("ev_top");
        return dsl.select(
                top.field(EXTENSION_VERSION.EXTENSION_ID),
                top.field(EXTENSION_VERSION.VERSION))
                .from(ids, DSL.lateral(top))
                .stream()
                .map(row -> {
                    var extensionId = row.get(top.field(EXTENSION_VERSION.EXTENSION_ID));
                    var version = row.get(top.field(EXTENSION_VERSION.VERSION));
                    return Map.entry(extensionId, version);
                })
                .collect(
                        Collectors.groupingBy(
                                Map.Entry::getKey,
                                Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    public List<ExtensionVersion> findActiveVersionReferencesSorted(Collection<Long> extensionIds, int numberOfRows) {
        if (extensionIds.isEmpty()) {
            return Collections.emptyList();
        }

        var ids = DSL.values(extensionIds.stream().map(DSL::row).toArray(Row1[]::new)).as("ids", "id");
        var namespaceCol = NAMESPACE.NAME.as("namespace");
        var extensionCol = EXTENSION.NAME.as("extension");
        var top = dsl.select(
                namespaceCol,
                extensionCol,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.EXTENSION_ID,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.ENGINES,
                SIGNATURE_KEY_PAIR.PUBLIC_ID)
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .leftJoin(SIGNATURE_KEY_PAIR).on(SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID))
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(ids.field("id", Long.class)))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .orderBy(
                        EXTENSION_VERSION.EXTENSION_ID.asc(),
                        EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                        EXTENSION_VERSION.SEMVER_MINOR.desc(),
                        EXTENSION_VERSION.SEMVER_PATCH.desc(),
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                        EXTENSION_VERSION.TIMESTAMP.desc())
                .limit(numberOfRows)
                .asTable("ev_top");

        var converter = new ListOfStringConverter();
        return dsl.select(
                top.field(namespaceCol),
                top.field(extensionCol),
                top.field(EXTENSION_VERSION.ID),
                top.field(EXTENSION_VERSION.EXTENSION_ID),
                top.field(EXTENSION_VERSION.TARGET_PLATFORM),
                top.field(EXTENSION_VERSION.VERSION),
                top.field(EXTENSION_VERSION.ENGINES),
                top.field(SIGNATURE_KEY_PAIR.PUBLIC_ID))
                .from(ids, DSL.lateral(top))
                .stream()
                .map(row -> {
                    var namespace = new Namespace();
                    namespace.setName(row.get(top.field(namespaceCol)));

                    var extension = new Extension();
                    extension.setId(row.get(top.field(EXTENSION_VERSION.EXTENSION_ID)));
                    extension.setName(row.get(top.field(extensionCol)));
                    extension.setNamespace(namespace);

                    var signatureKeyPair = new SignatureKeyPair();
                    signatureKeyPair.setPublicId(row.get(top.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)));

                    var extVersion = new ExtensionVersion();
                    extVersion.setId(row.get(top.field(EXTENSION_VERSION.ID)));
                    extVersion.setTargetPlatform(row.get(top.field(EXTENSION_VERSION.TARGET_PLATFORM)));
                    extVersion.setVersion(row.get(top.field(EXTENSION_VERSION.VERSION)));
                    extVersion.setEngines(toList(row.get(top.field(EXTENSION_VERSION.ENGINES)), converter));
                    extVersion.setExtension(extension);
                    extVersion.setSignatureKeyPair(signatureKeyPair);
                    return extVersion;
                })
                .collect(Collectors.toList());
    }

    public List<String> findVersionStringsSorted(
            Long extensionId,
            String targetPlatform,
            boolean onlyActive,
            int numberOfRows
    ) {
        var conditions = new ArrayList<Condition>();
        conditions.add(EXTENSION_VERSION.EXTENSION_ID.eq(extensionId));
        if (targetPlatform != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if (onlyActive) {
            conditions.add(EXTENSION_VERSION.ACTIVE.eq(true));
        }

        return findVersionStringsSorted(conditions, Pageable.ofSize(numberOfRows));
    }

    private List<String> findVersionStringsSorted(List<Condition> conditions, Pageable page) {
        var versionsQuery = dsl.selectQuery();
        versionsQuery.setDistinct(true);
        versionsQuery.addSelect(
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE);

        versionsQuery.addFrom(EXTENSION_VERSION);
        versionsQuery.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        versionsQuery.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        versionsQuery.addConditions(conditions);

        versionsQuery.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.VERSION.asc());

        versionsQuery.addLimit(page.getPageSize());
        versionsQuery.addOffset(page.getOffset());
        return versionsQuery.fetch(row -> row.get(EXTENSION_VERSION.VERSION));
    }

    public Page<ExtensionVersion> findActiveVersions(QueryRequest request) {
        var conditions = new ArrayList<Condition>();
        if (!StringUtils.isEmpty(request.namespaceUuid())) {
            conditions.add(NAMESPACE.PUBLIC_ID.eq(request.namespaceUuid()));
        }
        if (!StringUtils.isEmpty(request.namespaceName())) {
            conditions.add(NAMESPACE.NAME.equalIgnoreCase(request.namespaceName()));
        }
        if (!StringUtils.isEmpty(request.extensionUuid())) {
            conditions.add(EXTENSION.PUBLIC_ID.eq(request.extensionUuid()));
        }
        if (!StringUtils.isEmpty(request.extensionName())) {
            conditions.add(EXTENSION.NAME.equalIgnoreCase(request.extensionName()));
        }
        if (request.targetPlatform() != null) {
            conditions.add(EXTENSION_VERSION.TARGET_PLATFORM.eq(request.targetPlatform()));
        }
        if (!StringUtils.isEmpty(request.extensionVersion())) {
            conditions.add(EXTENSION_VERSION.VERSION.eq(request.extensionVersion()));
        }

        var totalCol = "total";
        var totalQuery = dsl.selectQuery();
        totalQuery.addFrom(EXTENSION_VERSION);
        totalQuery.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        totalQuery.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        totalQuery.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));

        var query = findAllActive();
        if (!request.includeAllVersions()) {
            var distinctOn = new Field[] {
                EXTENSION_VERSION.EXTENSION_ID,
                EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM,
                EXTENSION_VERSION.TARGET_PLATFORM
            };

            totalQuery.addSelect(DSL.countDistinct(distinctOn).as(totalCol));
            query.addDistinctOn(distinctOn);
            query.addOrderBy(
                    EXTENSION_VERSION.EXTENSION_ID.asc(),
                    EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                    EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                    EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                    EXTENSION_VERSION.SEMVER_MINOR.desc(),
                    EXTENSION_VERSION.SEMVER_PATCH.desc(),
                    EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                    EXTENSION_VERSION.TIMESTAMP.desc());
        } else {
            totalQuery.addSelect(DSL.count().as(totalCol));
            query.addOrderBy(
                    EXTENSION_VERSION.EXTENSION_ID.asc(),
                    EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                    EXTENSION_VERSION.SEMVER_MINOR.desc(),
                    EXTENSION_VERSION.SEMVER_PATCH.desc(),
                    EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                    EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                    EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                    EXTENSION_VERSION.TIMESTAMP.desc());
        }

        totalQuery.addConditions(conditions);
        query.addSelect(EXTENSION.DEPRECATED, EXTENSION.DOWNLOADABLE, EXTENSION.REPLACEMENT_ID);
        query.addConditions(conditions);
        query.addOffset(request.offset());
        query.addLimit(request.size());

        var content = query.fetch().map(row -> {
            var extVersion = toExtensionVersionFull(row);
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            extVersion.getExtension().setDownloadable(row.get(EXTENSION.DOWNLOADABLE));

            var replacementId = row.get(EXTENSION.REPLACEMENT_ID);
            if (replacementId != null) {
                var replacement = new Extension();
                replacement.setId(replacementId);
                extVersion.getExtension().setReplacement(replacement);
            }
            return extVersion;
        });
        var total = totalQuery.fetchOne(totalCol, Integer.class);
        return new PageImpl<>(
                content,
                PageRequest.of(request.offset() / request.size(), request.size()),
                total != null ? total : 0);
    }

    private SelectQuery<Record> findAllActive() {
        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);
        query.addFrom(EXTENSION_VERSION);
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(EXTENSION_VERSION.PUBLISHED_BY_ID));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));
        return query;
    }

    private List<ExtensionVersion> fetch(SelectQuery<Record> query) {
        return query.fetch().map(this::toExtensionVersionFull);
    }

    private ExtensionVersion toExtensionVersionFull(Record row) {
        return toExtensionVersionFull(row, null, null);
    }

    private ExtensionVersion toExtensionVersionFull(
            Record row,
            Extension extension,
            FieldMapper extensionVersionMapper
    ) {
        if (extensionVersionMapper == null) {
            extensionVersionMapper = new IdentityFieldMapper();
        }

        var extVersion = toExtensionVersionCommon(row, extension, extensionVersionMapper);
        extVersion.setLicense(row.get(extensionVersionMapper.map(EXTENSION_VERSION.LICENSE)));
        extVersion.setHomepage(row.get(extensionVersionMapper.map(EXTENSION_VERSION.HOMEPAGE)));
        extVersion.setBugs(row.get(extensionVersionMapper.map(EXTENSION_VERSION.BUGS)));
        extVersion.setMarkdown(row.get(extensionVersionMapper.map(EXTENSION_VERSION.MARKDOWN)));
        extVersion.setQna(row.get(extensionVersionMapper.map(EXTENSION_VERSION.QNA)));

        if (extension == null) {
            var newExtension = extVersion.getExtension();
            newExtension.setPublicId(row.get(EXTENSION.PUBLIC_ID));
            newExtension.setAverageRating(row.get(EXTENSION.AVERAGE_RATING));
            newExtension.setReviewCount(row.get(EXTENSION.REVIEW_COUNT));
            newExtension.setDownloadCount(row.get(EXTENSION.DOWNLOAD_COUNT));
            newExtension.setPublishedDate(row.get(EXTENSION.PUBLISHED_DATE));
            newExtension.setLastUpdatedDate(row.get(EXTENSION.LAST_UPDATED_DATE));

            var newNamespace = newExtension.getNamespace();
            newNamespace.setPublicId(row.get(NAMESPACE.PUBLIC_ID));
        }

        var user = new UserData();
        user.setId(row.get(USER_DATA.ID));
        user.setRole(UserData.Role.valueOfIgnoreCase(row.get(USER_DATA.ROLE)));
        user.setLoginName(row.get(USER_DATA.LOGIN_NAME));
        user.setFullName(row.get(USER_DATA.FULL_NAME));
        user.setAvatarUrl(row.get(USER_DATA.AVATAR_URL));
        user.setProviderUrl(row.get(USER_DATA.PROVIDER_URL));
        user.setProvider(row.get(USER_DATA.PROVIDER));

        extVersion.setPublishedBy(user);
        var publishedWithTt = row.get(extensionVersionMapper.map(EXTENSION_VERSION.PUBLISHED_WITH_TT));
        extVersion
                .setPublishedWithTt(publishedWithTt != null ? PersonalAccessTokenType.valueOf(publishedWithTt) : null);
        extVersion.setType(ExtensionVersion.Type.REGULAR);
        return extVersion;
    }

    /**
     * Package-private so a performance test can map rows of a query shape that no longer exists in
     * production (the pre-#2062 query, with unaliased columns matching {@link IdentityFieldMapper})
     * with the same per-row cost {@link #toRankedExtensionVersion} pays, for a fair comparison.
     */
    ExtensionVersion toExtensionVersion(Record row) {
        var extVersion = toExtensionVersionCommon(row, null, new IdentityFieldMapper());
        extVersion.setType(ExtensionVersion.Type.MINIMAL);
        return extVersion;
    }

    private ExtensionVersion toRankedExtensionVersion(Record row) {
        var extVersion = toExtensionVersionCommon(row, null, new RankedFieldMapper(row));
        extVersion.setType(ExtensionVersion.Type.MINIMAL);
        return extVersion;
    }

    private ExtensionVersion toExtensionVersionCommon(
            Record row,
            Extension extension,
            FieldMapper extensionVersionMapper
    ) {
        var converter = new ListOfStringConverter();

        var extVersion = new ExtensionVersion();
        extVersion.setId(row.get(extensionVersionMapper.map(EXTENSION_VERSION.ID)));
        extVersion.setVersion(row.get(extensionVersionMapper.map(EXTENSION_VERSION.VERSION)));
        extVersion.setTargetPlatform(row.get(extensionVersionMapper.map(EXTENSION_VERSION.TARGET_PLATFORM)));
        extVersion.setPreview(row.get(extensionVersionMapper.map(EXTENSION_VERSION.PREVIEW)));
        extVersion.setPreRelease(row.get(extensionVersionMapper.map(EXTENSION_VERSION.PRE_RELEASE)));
        extVersion.setTimestamp(row.get(extensionVersionMapper.map(EXTENSION_VERSION.TIMESTAMP)));
        extVersion.setDisplayName(row.get(extensionVersionMapper.map(EXTENSION_VERSION.DISPLAY_NAME)));
        extVersion.setDescription(row.get(extensionVersionMapper.map(EXTENSION_VERSION.DESCRIPTION)));
        extVersion.setEngines(toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.ENGINES)), converter));
        extVersion.setCategories(toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.CATEGORIES)), converter));
        extVersion.setTags(toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.TAGS)), converter));
        extVersion.setExtensionKind(
                toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.EXTENSION_KIND)), converter));
        extVersion.setRepository(row.get(extensionVersionMapper.map(EXTENSION_VERSION.REPOSITORY)));
        extVersion.setGalleryColor(row.get(extensionVersionMapper.map(EXTENSION_VERSION.GALLERY_COLOR)));
        extVersion.setGalleryTheme(row.get(extensionVersionMapper.map(EXTENSION_VERSION.GALLERY_THEME)));
        extVersion.setLocalizedLanguages(
                toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.LOCALIZED_LANGUAGES)), converter));
        extVersion.setDependencies(
                toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.DEPENDENCIES)), converter));
        extVersion.setBundledExtensions(
                toList(row.get(extensionVersionMapper.map(EXTENSION_VERSION.BUNDLED_EXTENSIONS)), converter));
        extVersion.setSponsorLink(row.get(extensionVersionMapper.map(EXTENSION_VERSION.SPONSOR_LINK)));
        extVersion.setPotentiallyMalicious(
                Boolean.TRUE.equals(row.get(extensionVersionMapper.map(EXTENSION_VERSION.POTENTIALLY_MALICIOUS))));

        // The `removed` column is only selected by queries that may include non-active versions; when it
        // is present, carry it through so callers observe the correct tombstone state. (Active versions
        // are never removed, so leaving it false for active-only queries is correct.)
        var removedField = extensionVersionMapper.map(EXTENSION_VERSION.REMOVED);
        if (row.field(removedField) != null) {
            extVersion.setRemoved(row.get(removedField));
        }

        if (extension == null) {
            var namespace = new Namespace();
            namespace.setId(row.get(extensionVersionMapper.map(NAMESPACE.ID)));
            namespace.setName(row.get(extensionVersionMapper.map(NAMESPACE.NAME)));

            extension = new Extension();
            extension.setId(row.get(extensionVersionMapper.map(EXTENSION.ID)));
            extension.setName(row.get(extensionVersionMapper.map(EXTENSION.NAME)));
            extension.setNamespace(namespace);
        }

        extVersion.setExtension(extension);

        var keyPair = new SignatureKeyPair();
        keyPair.setPublicId(row.get(extensionVersionMapper.map(SIGNATURE_KEY_PAIR.PUBLIC_ID)));
        extVersion.setSignatureKeyPair(keyPair);
        return extVersion;
    }

    private List<String> toList(String raw, ListOfStringConverter converter) {
        return converter.convertToEntityAttribute(raw);
    }

    public List<VersionTargetPlatformsJson> findTargetPlatformsGroupedByVersion(Extension extension) {
        var targetPlatforms = DSL.arrayAgg(EXTENSION_VERSION.TARGET_PLATFORM)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());
        var targetPlatformsActive = DSL.arrayAgg(EXTENSION_VERSION.ACTIVE)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());
        var targetPlatformsRemoved = DSL.arrayAgg(EXTENSION_VERSION.REMOVED)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());

        return dsl.select(
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                EXTENSION_VERSION.VERSION,
                targetPlatforms,
                targetPlatformsActive,
                targetPlatformsRemoved)
                .from(EXTENSION_VERSION)
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()))
                .groupBy(
                        EXTENSION_VERSION.SEMVER_MAJOR,
                        EXTENSION_VERSION.SEMVER_MINOR,
                        EXTENSION_VERSION.SEMVER_PATCH,
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                        EXTENSION_VERSION.VERSION)
                .orderBy(
                        EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                        EXTENSION_VERSION.SEMVER_MINOR.desc(),
                        EXTENSION_VERSION.SEMVER_PATCH.desc(),
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                        EXTENSION_VERSION.VERSION.asc())
                .fetch()
                .map(
                        row -> toVersionTargetPlatformsJson(
                                row.get(EXTENSION_VERSION.VERSION),
                                row.get(targetPlatforms),
                                row.get(targetPlatformsActive),
                                row.get(targetPlatformsRemoved)));
    }

    public List<VersionTargetPlatformsJson> findTargetPlatformsGroupedByVersion(Extension extension, UserData user) {
        var targetPlatforms = DSL.arrayAgg(EXTENSION_VERSION.TARGET_PLATFORM)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());
        var targetPlatformsActive = DSL.arrayAgg(EXTENSION_VERSION.ACTIVE)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());
        var targetPlatformsRemoved = DSL.arrayAgg(EXTENSION_VERSION.REMOVED)
                .orderBy(
                        EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                        EXTENSION_VERSION.TARGET_PLATFORM.asc());

        return dsl.select(
                EXTENSION_VERSION.SEMVER_MAJOR,
                EXTENSION_VERSION.SEMVER_MINOR,
                EXTENSION_VERSION.SEMVER_PATCH,
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                EXTENSION_VERSION.VERSION,
                targetPlatforms,
                targetPlatformsActive,
                targetPlatformsRemoved)
                .from(EXTENSION_VERSION)
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()))
                .and(EXTENSION_VERSION.PUBLISHED_BY_ID.eq(user.getId()))
                .groupBy(
                        EXTENSION_VERSION.SEMVER_MAJOR,
                        EXTENSION_VERSION.SEMVER_MINOR,
                        EXTENSION_VERSION.SEMVER_PATCH,
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE,
                        EXTENSION_VERSION.VERSION)
                .orderBy(
                        EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                        EXTENSION_VERSION.SEMVER_MINOR.desc(),
                        EXTENSION_VERSION.SEMVER_PATCH.desc(),
                        EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                        EXTENSION_VERSION.VERSION.asc())
                .fetch()
                .map(
                        row -> toVersionTargetPlatformsJson(
                                row.get(EXTENSION_VERSION.VERSION),
                                row.get(targetPlatforms),
                                row.get(targetPlatformsActive),
                                row.get(targetPlatformsRemoved)));
    }

    private VersionTargetPlatformsJson toVersionTargetPlatformsJson(
            String version,
            String[] targetPlatforms,
            Boolean[] active,
            Boolean[] removed
    ) {
        var platforms = new ArrayList<TargetPlatformActiveJson>(targetPlatforms.length);
        for (int i = 0; i < targetPlatforms.length; i++) {
            platforms.add(
                    new TargetPlatformActiveJson(
                            targetPlatforms[i],
                            Boolean.TRUE.equals(active[i]),
                            Boolean.TRUE.equals(removed[i])));
        }

        return new VersionTargetPlatformsJson(version, platforms);
    }

    public List<ExtensionVersion> findVersionsForUrls(Extension extension, String targetPlatform, String version) {
        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM);
        query.addFrom(EXTENSION_VERSION);
        query.addConditions(
                EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()),
                EXTENSION_VERSION.VERSION.eq(version),
                // Only active versions may be offered for download. A soft-deleted (removed) version is always
                // inactive and has had its files stripped from storage, so it must never appear in the public
                // download URL map (this also avoids spurious "Could not find download" warnings for tombstones).
                EXTENSION_VERSION.ACTIVE.eq(true));
        if (targetPlatform != null) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }

        return query.fetch()
                .map(row -> {
                    var extVersion = new ExtensionVersion();
                    extVersion.setId(row.get(EXTENSION_VERSION.ID));
                    extVersion.setVersion(row.get(EXTENSION_VERSION.VERSION));
                    extVersion.setTargetPlatform(row.get(EXTENSION_VERSION.TARGET_PLATFORM));
                    extVersion.setExtension(extension);
                    return extVersion;
                });
    }

    public ExtensionVersion findLatestReplacement(
            long extensionId,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                EXTENSION.NAME,
                EXTENSION.ACTIVE,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.DISPLAY_NAME);
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extensionId));
        return query.fetchOne(row -> {
            var namespace = new Namespace();
            namespace.setId(row.get(NAMESPACE.ID));
            namespace.setName(row.get(NAMESPACE.NAME));

            var extension = new Extension();
            extension.setId(extensionId);
            extension.setName(row.get(EXTENSION.NAME));
            extension.setActive(row.get(EXTENSION.ACTIVE));
            extension.setNamespace(namespace);

            var extVersion = new ExtensionVersion();
            extVersion.setId(row.get(EXTENSION_VERSION.ID));
            extVersion.setDisplayName(row.get(EXTENSION_VERSION.DISPLAY_NAME));
            extVersion.setExtension(extension);
            return extVersion;
        });
    }

    public ExtensionVersion findLatest(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addSelect(
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);
        query.addJoin(USER_DATA, USER_DATA.ID.eq(EXTENSION_VERSION.PUBLISHED_BY_ID));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()));
        return query.fetchOne(row -> toExtensionVersionFull(row, extension, null));
    }

    public ExtensionVersion findLatest(
            String namespaceName,
            String extensionName,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.PUBLIC_ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                EXTENSION.ID,
                EXTENSION.PUBLIC_ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.ACTIVE,
                EXTENSION.DEPRECATED,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);
        query.addJoin(USER_DATA, USER_DATA.ID.eq(EXTENSION_VERSION.PUBLISHED_BY_ID));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(
                NAMESPACE.NAME.equalIgnoreCase(namespaceName),
                EXTENSION.NAME.equalIgnoreCase(extensionName));
        return query.fetchOne(row -> {
            var extVersion = toExtensionVersionFull(row);
            extVersion.getExtension().setActive(row.get(EXTENSION.ACTIVE));
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            extVersion.getExtension().getNamespace().setDisplayName(row.get(NAMESPACE.DISPLAY_NAME));
            return extVersion;
        });
    }

    public Map<Long, Boolean> findLatestIsPreview(Collection<Long> extensionIds) {
        var latestQuery = findLatestQuery(null, false, true);
        latestQuery.addSelect(EXTENSION_VERSION.PREVIEW);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                latest.field(EXTENSION_VERSION.PREVIEW));
        query.addFrom(EXTENSION, DSL.lateral(latest));
        query.addConditions(EXTENSION.ID.in(extensionIds));

        return query.fetch(row -> {
            var id = row.get(EXTENSION.ID);
            var preview = row.get(latest.field(EXTENSION_VERSION.PREVIEW));
            return Map.entry(id, preview);
        })
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<ExtensionVersion> findLatest(Collection<Long> extensionIds) {
        return findLatest(extensionIds, null);
    }

    public List<ExtensionVersion> findLatest(Collection<Long> extensionIds, String targetPlatform) {
        var latestQuery = findLatestQuery(targetPlatform, false, true);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID,
                EXTENSION_VERSION.PUBLISHED_BY_ID);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.DEPRECATED,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.POTENTIALLY_MALICIOUS),
                latest.field(EXTENSION_VERSION.REMOVED),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.PREVIEW),
                latest.field(EXTENSION_VERSION.PRE_RELEASE),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(EXTENSION_VERSION.ENGINES),
                latest.field(EXTENSION_VERSION.CATEGORIES),
                latest.field(EXTENSION_VERSION.TAGS),
                latest.field(EXTENSION_VERSION.EXTENSION_KIND),
                latest.field(EXTENSION_VERSION.LICENSE),
                latest.field(EXTENSION_VERSION.HOMEPAGE),
                latest.field(EXTENSION_VERSION.REPOSITORY),
                latest.field(EXTENSION_VERSION.SPONSOR_LINK),
                latest.field(EXTENSION_VERSION.BUGS),
                latest.field(EXTENSION_VERSION.MARKDOWN),
                latest.field(EXTENSION_VERSION.GALLERY_COLOR),
                latest.field(EXTENSION_VERSION.GALLERY_THEME),
                latest.field(EXTENSION_VERSION.LOCALIZED_LANGUAGES),
                latest.field(EXTENSION_VERSION.QNA),
                latest.field(EXTENSION_VERSION.DEPENDENCIES),
                latest.field(EXTENSION_VERSION.BUNDLED_EXTENSIONS),
                latest.field(EXTENSION_VERSION.PUBLISHED_WITH_TT),
                SIGNATURE_KEY_PAIR.PUBLIC_ID,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER);
        query.addFrom(NAMESPACE);
        query.addJoin(EXTENSION, EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID));
        query.addJoin(latest, JoinType.CROSS_APPLY, DSL.condition(true));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(latest.field(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID)));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(latest.field(EXTENSION_VERSION.PUBLISHED_BY_ID)));
        query.addConditions(EXTENSION.ID.in(extensionIds));
        return query.fetch(row -> {
            var extVersion = toExtensionVersionFull(row, null, new TableFieldMapper(latest));
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            return extVersion;
        });
    }

    public List<ExtensionVersion> findLatest(Namespace namespace) {
        var latestQuery = findLatestQuery(null, false, true);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        latestQuery.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.DEPRECATED,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(SIGNATURE_KEY_PAIR.PUBLIC_ID));
        query.addFrom(EXTENSION, DSL.lateral(latest));
        query.addConditions(
                EXTENSION.NAMESPACE_ID.eq(namespace.getId()),
                EXTENSION.ACTIVE.eq(true));
        query.addOrderBy(EXTENSION.DOWNLOAD_COUNT.desc());

        return query.fetch(row -> {
            var extension = new Extension();
            extension.setId(row.get(EXTENSION.ID));
            extension.setName(row.get(EXTENSION.NAME));
            extension.setAverageRating(row.get(EXTENSION.AVERAGE_RATING));
            extension.setReviewCount(row.get(EXTENSION.REVIEW_COUNT));
            extension.setDownloadCount(row.get(EXTENSION.DOWNLOAD_COUNT));
            extension.setDeprecated(row.get(EXTENSION.DEPRECATED));
            extension.setNamespace(namespace);

            var extVersion = new ExtensionVersion();
            extVersion.setId(row.get(latest.field(EXTENSION_VERSION.ID)));
            extVersion.setVersion(row.get(latest.field(EXTENSION_VERSION.VERSION)));
            extVersion.setTargetPlatform(row.get(latest.field(EXTENSION_VERSION.TARGET_PLATFORM)));
            extVersion.setTimestamp(row.get(latest.field(EXTENSION_VERSION.TIMESTAMP)));
            extVersion.setDisplayName(row.get(latest.field(EXTENSION_VERSION.DISPLAY_NAME)));
            extVersion.setDescription(row.get(latest.field(EXTENSION_VERSION.DESCRIPTION)));
            extVersion.setExtension(extension);

            var keyPair = new SignatureKeyPair();
            keyPair.setPublicId(row.get(latest.field(SIGNATURE_KEY_PAIR.PUBLIC_ID)));
            extVersion.setSignatureKeyPair(keyPair);

            return extVersion;
        });
    }

    public List<ExtensionVersion> findLatest(UserData user) {
        var latestQuery = findLatestQuery(null, false, false);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID,
                EXTENSION_VERSION.PUBLISHED_BY_ID);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.ACTIVE,
                EXTENSION.DEPRECATED,
                EXTENSION.DOWNLOADABLE,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.POTENTIALLY_MALICIOUS),
                latest.field(EXTENSION_VERSION.REMOVED),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.PREVIEW),
                latest.field(EXTENSION_VERSION.PRE_RELEASE),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(EXTENSION_VERSION.ENGINES),
                latest.field(EXTENSION_VERSION.CATEGORIES),
                latest.field(EXTENSION_VERSION.TAGS),
                latest.field(EXTENSION_VERSION.EXTENSION_KIND),
                latest.field(EXTENSION_VERSION.LICENSE),
                latest.field(EXTENSION_VERSION.HOMEPAGE),
                latest.field(EXTENSION_VERSION.REPOSITORY),
                latest.field(EXTENSION_VERSION.SPONSOR_LINK),
                latest.field(EXTENSION_VERSION.BUGS),
                latest.field(EXTENSION_VERSION.MARKDOWN),
                latest.field(EXTENSION_VERSION.GALLERY_COLOR),
                latest.field(EXTENSION_VERSION.GALLERY_THEME),
                latest.field(EXTENSION_VERSION.LOCALIZED_LANGUAGES),
                latest.field(EXTENSION_VERSION.QNA),
                latest.field(EXTENSION_VERSION.DEPENDENCIES),
                latest.field(EXTENSION_VERSION.BUNDLED_EXTENSIONS),
                latest.field(EXTENSION_VERSION.PUBLISHED_WITH_TT),
                SIGNATURE_KEY_PAIR.PUBLIC_ID,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER);
        query.addFrom(NAMESPACE);
        query.addJoin(EXTENSION, EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID));
        query.addJoin(latest, JoinType.CROSS_APPLY, DSL.condition(true));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(latest.field(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID)));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(latest.field(EXTENSION_VERSION.PUBLISHED_BY_ID)));
        query.addConditions(USER_DATA.ID.eq(user.getId()));
        return query.fetch(row -> {
            var extVersion = toExtensionVersionFull(row, null, new TableFieldMapper(latest));
            extVersion.getExtension().getNamespace().setDisplayName(row.get(NAMESPACE.DISPLAY_NAME));
            extVersion.getExtension().setActive(row.get(EXTENSION.ACTIVE));
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            extVersion.getExtension().setDownloadable(row.get(EXTENSION.DOWNLOADABLE));
            return extVersion;
        });
    }

    public ExtensionVersion findLatest(UserData user, String namespace, String extension) {
        var latestQuery = findLatestQuery(null, false, false);
        latestQuery.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID,
                EXTENSION_VERSION.PUBLISHED_BY_ID);
        latestQuery.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(EXTENSION.ID));
        var latest = latestQuery.asTable();

        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.ACTIVE,
                EXTENSION.DEPRECATED,
                EXTENSION.DOWNLOADABLE,
                latest.field(EXTENSION_VERSION.ID),
                latest.field(EXTENSION_VERSION.POTENTIALLY_MALICIOUS),
                latest.field(EXTENSION_VERSION.REMOVED),
                latest.field(EXTENSION_VERSION.VERSION),
                latest.field(EXTENSION_VERSION.TARGET_PLATFORM),
                latest.field(EXTENSION_VERSION.PREVIEW),
                latest.field(EXTENSION_VERSION.PRE_RELEASE),
                latest.field(EXTENSION_VERSION.TIMESTAMP),
                latest.field(EXTENSION_VERSION.DISPLAY_NAME),
                latest.field(EXTENSION_VERSION.DESCRIPTION),
                latest.field(EXTENSION_VERSION.ENGINES),
                latest.field(EXTENSION_VERSION.CATEGORIES),
                latest.field(EXTENSION_VERSION.TAGS),
                latest.field(EXTENSION_VERSION.EXTENSION_KIND),
                latest.field(EXTENSION_VERSION.LICENSE),
                latest.field(EXTENSION_VERSION.HOMEPAGE),
                latest.field(EXTENSION_VERSION.REPOSITORY),
                latest.field(EXTENSION_VERSION.SPONSOR_LINK),
                latest.field(EXTENSION_VERSION.BUGS),
                latest.field(EXTENSION_VERSION.MARKDOWN),
                latest.field(EXTENSION_VERSION.GALLERY_COLOR),
                latest.field(EXTENSION_VERSION.GALLERY_THEME),
                latest.field(EXTENSION_VERSION.LOCALIZED_LANGUAGES),
                latest.field(EXTENSION_VERSION.QNA),
                latest.field(EXTENSION_VERSION.DEPENDENCIES),
                latest.field(EXTENSION_VERSION.BUNDLED_EXTENSIONS),
                latest.field(EXTENSION_VERSION.PUBLISHED_WITH_TT),
                SIGNATURE_KEY_PAIR.PUBLIC_ID,
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER);
        query.addFrom(NAMESPACE);
        query.addJoin(EXTENSION, EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID));
        query.addJoin(latest, JoinType.CROSS_APPLY, DSL.condition(true));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(latest.field(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID)));
        query.addJoin(USER_DATA, USER_DATA.ID.eq(latest.field(EXTENSION_VERSION.PUBLISHED_BY_ID)));
        query.addConditions(
                USER_DATA.ID.eq(user.getId()),
                NAMESPACE.NAME.equalIgnoreCase(namespace),
                EXTENSION.NAME.equalIgnoreCase(extension));
        return query.fetchOne(row -> {
            var extVersion = toExtensionVersionFull(row, null, new TableFieldMapper(latest));
            extVersion.getExtension().getNamespace().setDisplayName(row.get(NAMESPACE.DISPLAY_NAME));
            extVersion.getExtension().setActive(row.get(EXTENSION.ACTIVE));
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            extVersion.getExtension().setDownloadable(row.get(EXTENSION.DOWNLOADABLE));
            return extVersion;
        });
    }

    public List<ExtensionVersion> findLatestVersionByTargetPlatform(
            Extension extension,
            boolean preReleases,
            boolean onlyActive
    ) {
        var query = dsl.selectQuery();
        query.addDistinctOn(EXTENSION_VERSION.TARGET_PLATFORM);
        query.addFrom(EXTENSION_VERSION);

        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));

        query.addConditions(EXTENSION_VERSION.PRE_RELEASE.eq(preReleases));

        if (onlyActive) {
            query.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));
        }

        query.addOrderBy(
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.TIMESTAMP.desc());

        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()));

        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);

        return query.fetch().map(this::toExtensionVersion);
    }

    public ExtensionVersion findLatestForAllUrls(
            Extension extension,
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = findLatestQuery(targetPlatform, onlyPreRelease, onlyActive);
        query.addConditions(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()));
        query.addSelect(
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.PREVIEW);
        return query.fetchOne(row -> {
            if (row == null) {
                return null;
            }

            var extVersion = new ExtensionVersion();
            extVersion.setId(row.get(EXTENSION_VERSION.ID));
            extVersion.setVersion(row.get(EXTENSION_VERSION.VERSION));
            extVersion.setPreview(row.get(EXTENSION_VERSION.PREVIEW));
            extVersion.setExtension(extension);
            return extVersion;
        });
    }

    SelectQuery<Record> findLatestQuery(
            String targetPlatform,
            boolean onlyPreRelease,
            boolean onlyActive
    ) {
        var query = dsl.selectQuery();
        query.addFrom(EXTENSION_VERSION);
        if (TargetPlatform.isValid(targetPlatform)) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if (onlyPreRelease) {
            query.addConditions(EXTENSION_VERSION.PRE_RELEASE.eq(true));
        }
        if (onlyActive) {
            query.addConditions(EXTENSION_VERSION.ACTIVE.eq(true));
        }

        query.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                EXTENSION_VERSION.TIMESTAMP.desc());
        query.addLimit(1);
        return query;
    }

    public ExtensionVersion find(String namespaceName, String extensionName, String targetPlatform, String version) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        var query = findLatestQuery(targetPlatform, onlyPreRelease, true);
        return findInternal(query, namespaceName, extensionName, version);
    }

    /**
     * Find an extension version regardless of active status.
     * Use this for admin operations on quarantined/inactive extensions.
     */
    public ExtensionVersion findIncludingInactive(
            String namespaceName,
            String extensionName,
            String targetPlatform,
            String version
    ) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        // Pass false for onlyActive to include inactive (quarantined) extensions
        var query = findLatestQuery(targetPlatform, onlyPreRelease, false);
        return findInternal(query, namespaceName, extensionName, version);
    }

    private ExtensionVersion findInternal(
            SelectQuery<Record> query,
            String namespaceName,
            String extensionName,
            String version
    ) {
        query.addSelect(
                USER_DATA.ID,
                USER_DATA.ROLE,
                USER_DATA.LOGIN_NAME,
                USER_DATA.FULL_NAME,
                USER_DATA.AVATAR_URL,
                USER_DATA.PROVIDER_URL,
                USER_DATA.PROVIDER,
                NAMESPACE.ID,
                NAMESPACE.NAME,
                NAMESPACE.DISPLAY_NAME,
                NAMESPACE.PUBLIC_ID,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION.PUBLIC_ID,
                EXTENSION.AVERAGE_RATING,
                EXTENSION.REVIEW_COUNT,
                EXTENSION.DOWNLOAD_COUNT,
                EXTENSION.PUBLISHED_DATE,
                EXTENSION.LAST_UPDATED_DATE,
                EXTENSION.DEPRECATED,
                EXTENSION.DOWNLOADABLE,
                EXTENSION.REPLACEMENT_ID,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.VERSION,
                EXTENSION_VERSION.POTENTIALLY_MALICIOUS,
                EXTENSION_VERSION.REMOVED,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.PREVIEW,
                EXTENSION_VERSION.PRE_RELEASE,
                EXTENSION_VERSION.TIMESTAMP,
                EXTENSION_VERSION.DISPLAY_NAME,
                EXTENSION_VERSION.DESCRIPTION,
                EXTENSION_VERSION.ENGINES,
                EXTENSION_VERSION.CATEGORIES,
                EXTENSION_VERSION.TAGS,
                EXTENSION_VERSION.EXTENSION_KIND,
                EXTENSION_VERSION.LICENSE,
                EXTENSION_VERSION.HOMEPAGE,
                EXTENSION_VERSION.REPOSITORY,
                EXTENSION_VERSION.SPONSOR_LINK,
                EXTENSION_VERSION.BUGS,
                EXTENSION_VERSION.MARKDOWN,
                EXTENSION_VERSION.GALLERY_COLOR,
                EXTENSION_VERSION.GALLERY_THEME,
                EXTENSION_VERSION.LOCALIZED_LANGUAGES,
                EXTENSION_VERSION.QNA,
                EXTENSION_VERSION.DEPENDENCIES,
                EXTENSION_VERSION.BUNDLED_EXTENSIONS,
                EXTENSION_VERSION.PUBLISHED_WITH_TT,
                SIGNATURE_KEY_PAIR.PUBLIC_ID);
        query.addJoin(USER_DATA, USER_DATA.ID.eq(EXTENSION_VERSION.PUBLISHED_BY_ID));
        query.addJoin(
                SIGNATURE_KEY_PAIR,
                JoinType.LEFT_OUTER_JOIN,
                SIGNATURE_KEY_PAIR.ID.eq(EXTENSION_VERSION.SIGNATURE_KEY_PAIR_ID));
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(
                EXTENSION.NAME.equalIgnoreCase(extensionName),
                NAMESPACE.NAME.equalIgnoreCase(namespaceName));
        if (!VersionAlias.LATEST.equals(version) && !VersionAlias.PRE_RELEASE.equals(version)) {
            query.addConditions(EXTENSION_VERSION.VERSION.eq(version));
        }

        return query.fetchOne(row -> {
            var extVersion = toExtensionVersionFull(row);
            extVersion.getExtension().setDeprecated(row.get(EXTENSION.DEPRECATED));
            extVersion.getExtension().setDownloadable(row.get(EXTENSION.DOWNLOADABLE));
            extVersion.getExtension().getNamespace().setDisplayName(row.get(NAMESPACE.DISPLAY_NAME));

            var replacementId = row.get(EXTENSION.REPLACEMENT_ID);
            if (replacementId != null) {
                var replacement = new Extension();
                replacement.setId(replacementId);
                extVersion.getExtension().setReplacement(replacement);
            }
            return extVersion;
        });
    }

    public List<String> findDistinctTargetPlatforms(Extension extension) {
        return dsl.selectDistinct(EXTENSION_VERSION.TARGET_PLATFORM)
                .from(EXTENSION_VERSION)
                .where(EXTENSION_VERSION.EXTENSION_ID.eq(extension.getId()))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .fetch(EXTENSION_VERSION.TARGET_PLATFORM);
    }

    public boolean hasSameVersion(ExtensionVersion extVersion) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(EXTENSION_VERSION)
                        .where(EXTENSION_VERSION.EXTENSION_ID.eq(extVersion.getExtension().getId()))
                        .and(EXTENSION_VERSION.VERSION.eq(extVersion.getVersion()))
                        .and(EXTENSION_VERSION.PRE_RELEASE.eq(!extVersion.isPreRelease())));
    }

    public Integer countVersions(String namespaceName, String extensionName) {
        return dsl.select(DSL.count().as("count"))
                .from(EXTENSION_VERSION)
                .join(EXTENSION)
                .on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE)
                .on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(NAMESPACE.NAME.equalIgnoreCase(namespaceName))
                .and(EXTENSION.NAME.equalIgnoreCase(extensionName))
                .fetchOne("count", Integer.class);
    }

    public boolean isDeleteAllActiveVersions(
            String namespaceName,
            String extensionName,
            TargetPlatformVersion... targetVersions
    ) {
        if (targetVersions.length == 0) {
            return false;
        }

        var all = dsl.select(DSL.count(EXTENSION_VERSION.ID).as("all"))
                .from(EXTENSION_VERSION)
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID))
                .where(NAMESPACE.NAME.equalIgnoreCase(namespaceName))
                .and(EXTENSION.NAME.equalIgnoreCase(extensionName))
                .and(EXTENSION_VERSION.ACTIVE.eq(true))
                .fetchOne("all", Integer.class);

        var rows = Arrays.stream(targetVersions).map((tv) -> DSL.row(tv.version(), tv.targetPlatform()))
                .toArray(Row2[]::new);
        var versions = DSL.values(rows).as("v", "version", "target");
        var VERSION = versions.field("version", String.class);
        var TARGET = versions.field("target", String.class);
        var actualSelect = dsl.select(DSL.count(EXTENSION_VERSION.ID).as("actual"))
                .from(versions)
                .join(EXTENSION_VERSION)
                .on(EXTENSION_VERSION.VERSION.eq(VERSION).and(EXTENSION_VERSION.TARGET_PLATFORM.eq(TARGET)))
                .join(EXTENSION).on(EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID))
                .join(NAMESPACE).on(NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));

        var condition = actualSelect
                .where(NAMESPACE.NAME.equalIgnoreCase(namespaceName))
                .and(EXTENSION.NAME.equalIgnoreCase(extensionName))
                .and(EXTENSION_VERSION.ACTIVE.eq(true));

        var actual = condition.fetchOne("actual", Integer.class);

        return Objects.equals(actual, all);
    }

    /**
     * Feed of the publicly visible transitions of extension versions, oldest first.
     * <p>
     * Reads the append-only {@code extension_version_change} log rather than deriving the transitions
     * from the version rows, so that every transition a version went through is reported, and entries
     * are never reordered once written. A version that is still being published has no log entry yet
     * and so stays out of the feed.
     * <p>
     * Everything is read from the log itself, without joining {@code extension_version}: an entry has to
     * stay readable after the version was purged, and a join would drop exactly the entries that report
     * the purge. That the log carries its own copy of the coordinates is what makes this possible.
     * <p>
     * The ordering is the one the {@code extension_version_change_feed_idx} index is built on, do not
     * change one without the other.
     */
    public ChangesPage findChanges(LocalDateTime since, LocalDateTime until, ChangesCursor after, int size) {
        var conditions = new ArrayList<Condition>();
        if (since != null) {
            conditions.add(EXTENSION_VERSION_CHANGE.CHANGED_AT.greaterOrEqual(since));
        }
        if (until != null) {
            conditions.add(EXTENSION_VERSION_CHANGE.CHANGED_AT.lessThan(until));
        }
        if (after != null) {
            // Compares the whole sort key rather than just the instant, so that the entries sharing an
            // instant with the one a consumer stopped at are resumed within: comparing instants alone
            // would either skip the rest of them or report the ones already processed a second time.
            conditions.add(
                    DSL.row(EXTENSION_VERSION_CHANGE.CHANGED_AT, EXTENSION_VERSION_CHANGE.ID)
                            .gt(after.changedAt(), after.id()));
        }

        // One row past the page, to answer whether there are more entries. Cheaper than counting the
        // matching ones, and unlike a count it stays cheap however long the append-only log grows.
        var rows = dsl.select(
                EXTENSION_VERSION_CHANGE.ID,
                EXTENSION_VERSION_CHANGE.NAMESPACE,
                EXTENSION_VERSION_CHANGE.EXTENSION,
                EXTENSION_VERSION_CHANGE.VERSION,
                EXTENSION_VERSION_CHANGE.TARGET_PLATFORM,
                EXTENSION_VERSION_CHANGE.STATE,
                EXTENSION_VERSION_CHANGE.TIMESTAMP,
                EXTENSION_VERSION_CHANGE.CHANGED_AT)
                .from(EXTENSION_VERSION_CHANGE)
                .where(conditions)
                // the id breaks ties between transitions that share an instant, so that paging through
                // the feed can neither skip nor repeat an entry
                .orderBy(EXTENSION_VERSION_CHANGE.CHANGED_AT.asc(), EXTENSION_VERSION_CHANGE.ID.asc())
                .limit(size + 1)
                .fetch();

        var hasMore = rows.size() > size;
        var pageRows = hasMore ? rows.subList(0, size) : rows;

        var changes = pageRows.stream().map(row -> {
            var entry = new ChangeEntryJson();
            entry.setNamespace(row.get(EXTENSION_VERSION_CHANGE.NAMESPACE));
            entry.setName(row.get(EXTENSION_VERSION_CHANGE.EXTENSION));
            entry.setVersion(row.get(EXTENSION_VERSION_CHANGE.VERSION));
            entry.setTargetPlatform(row.get(EXTENSION_VERSION_CHANGE.TARGET_PLATFORM));
            entry.setState(row.get(EXTENSION_VERSION_CHANGE.STATE));
            // A version carries no publication timestamp of its own if it was published before the
            // registry recorded one, so the entry reports none either and the field is left out of the
            // response. 'changedAt' is always there, the log cannot be ordered without it and the column
            // is NOT NULL.
            var timestamp = row.get(EXTENSION_VERSION_CHANGE.TIMESTAMP);
            if (timestamp != null) {
                entry.setTimestamp(TimeUtil.toUTCString(timestamp));
            }
            entry.setLastUpdated(TimeUtil.toUTCString(row.get(EXTENSION_VERSION_CHANGE.CHANGED_AT)));
            return entry;
        }).toList();

        // An empty page resumes from where it was requested, so that a consumer polling an idle registry
        // keeps a usable cursor instead of having to fall back to a timestamp.
        var nextCursor = after;
        if (!pageRows.isEmpty()) {
            var last = pageRows.get(pageRows.size() - 1);
            nextCursor = new ChangesCursor(
                    last.get(EXTENSION_VERSION_CHANGE.CHANGED_AT),
                    last.get(EXTENSION_VERSION_CHANGE.ID));
        }

        return new ChangesPage(changes, nextCursor, hasMore);
    }

    private interface FieldMapper {
        <T> Field<T> map(Field<T> field);
    }

    /**
     * Maps a field of {@link Tables#EXTENSION_VERSION} onto the equivalent field of the given derived
     * table - e.g. remaps {@code EXTENSION_VERSION.ID} onto {@code latest.field(EXTENSION_VERSION.ID)} -
     * so a caller can read a row of {@code latest} using the familiar static-table field constants.
     * <p>
     * {@code toExtensionVersionCommon} runs every field it reads through the same mapper, including
     * fields of other tables that were selected as-is rather than through {@code table} (e.g.
     * {@code NAMESPACE.ID}, {@code EXTENSION.ID}). Those must be left alone, which is why only fields
     * of the derived table's own source table are remapped: {@link Table#field(Field)} resolves by
     * name, not by lineage, so asking it for {@code NAMESPACE.ID} hands back the derived table's own
     * {@code id} - the version's - and the caller reads a version id where it wanted a namespace id.
     * That is silent: the value has the right type, and only its meaning is wrong.
     * <p>
     * A field that already belongs to {@code table}, or isn't a plain table field at all (e.g. an
     * already-computed expression), is returned unchanged - remapping it would be a no-op.
     */
    private record TableFieldMapper(Table<Record> table) implements FieldMapper {
        @Override
        public <T> Field<T> map(Field<T> field) {
            if (field instanceof TableField<?, ?> tableField
                    && tableField.getTable() == EXTENSION_VERSION
                    && tableField.getTable() != table) {
                var remapped = table.field(field);
                return remapped != null ? remapped : field;
            }
            return field;
        }
    }

    /**
     * The identity field mapper: maps field onto itself.
     */
    private static class IdentityFieldMapper implements FieldMapper {
        @Override
        public <T> Field<T> map(Field<T> field) {
            return field;
        }
    }

    /**
     * Maps a static field constant (e.g. {@code EXTENSION_VERSION.ID}) onto the actual field of the
     * given {@code row} named {@code "table_field"} (e.g. {@code "extension_version_id"}), matching
     * the {@code .as("table_field")} aliases the ranked query builds its select list with.
     * <p>
     * Resolving the field through {@code row.field(String)} - the row's own name-indexed lookup -
     * rather than constructing a brand new, unrelated {@code Field} object and handing that to
     * {@code row.get(...)}, matters: jOOQ resolves an unrecognized {@code Field} instance passed to
     * {@code Record.get(...)}/{@code Row.field(...)} by falling back to a linear scan, while
     * {@code row.field(String)} is the row's native, indexed lookup. Measured impact: for a query
     * returning ~3000 rows with ~20 mapped columns each, going through the row's own lookup rather
     * than a foreign {@code Field} object cut mapping time roughly in half (from a wall-clock ~90ms
     * for this method's uncapped path down to being indistinguishable from the equivalent unmapped
     * fetch). The name derivation itself (the only part that is a pure function of the static field,
     * not of {@code row}) is cached, since {@code toExtensionVersionCommon} calls {@code map(...)}
     * for every mapped column of every row.
     */
    private record RankedFieldMapper(Record row) implements FieldMapper {
        private static final Map<Field<?>, String> NAME_CACHE = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> Field<T> map(Field<T> field) {
            var name = NAME_CACHE.computeIfAbsent(
                    field,
                    f -> f.getQualifiedName().toString().replace("\"", "").replace(".", "_"));
            return (Field<T>) row.field(name);
        }
    }
}
