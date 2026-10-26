/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/
package org.eclipse.openvsx.publisheragreement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.PublisherAgreementEvent;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.json.PublisherAgreementEntryJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;

/**
 * Everything about publisher agreements that does not depend on where the agreement is kept, written once so that
 * a {@link PublisherAgreementProvider} does not have to repeat it: who may publish, the block that follows a
 * revocation, the history, the JSON served to clients, the HTTP status of a refusal and the reactivation of
 * extensions after signing. The provider only says what a user has signed; whether that version still counts is
 * decided here, by {@link PublisherAgreementVersions}.
 * <p>
 * An outdated agreement, one signed for a version that no longer counts, keeps the user's extensions published but
 * stops them publishing anything new until they sign the current one. A revocation, or having signed none,
 * also takes the extensions offline.
 * <p>
 * The steps that change a user's agreement ({@link #sign}, {@link #revoke}, {@link #reinstate}) take the lock of
 * the user row, held until the surrounding transaction ends, so that they cannot interleave: a revocation that
 * commits while the user is signing is seen by the signing instead of being undone by it.
 */
@Service
public class PublisherAgreementService {

    private static final Logger logger = LoggerFactory.getLogger(PublisherAgreementService.class);

    private static final List<PublisherAgreementEvent.Type> BLOCK_STEPS = List.of(
            PublisherAgreementEvent.Type.REVOKED,
            PublisherAgreementEvent.Type.REINSTATED);
    private static final List<PublisherAgreementEvent.Type> SIGNING_STEPS = List.of(
            PublisherAgreementEvent.Type.SIGNED,
            PublisherAgreementEvent.Type.REVOKED);

    /** The provider's states, and a revocation that blocks the user whatever the provider says. */
    private enum State {
        NONE, SIGNED, OUTDATED, REVOKED, UNKNOWN
    }

    private record Assessment(State state, AgreementStatus status) {}

    private final PublisherAgreementProvider provider;
    private final RepositoryService repositories;
    private final ExtensionService extensions;
    private final TransactionTemplate transactions;
    private final PublisherAgreementVersions versions;

    public PublisherAgreementService(
            PublisherAgreementProvider provider,
            RepositoryService repositories,
            ExtensionService extensions,
            TransactionTemplate transactions,
            PublisherAgreementVersions versions
    ) {
        this.versions = versions;
        this.provider = provider;
        this.repositories = repositories;
        this.extensions = extensions;
        this.transactions = transactions;
    }

    /**
     * Whether this deployment requires an agreement at all.
     */
    public boolean enabled() {
        return provider.enabled();
    }

    /**
     * Whether the user may publish, which takes a current agreement.
     */
    public boolean mayPublish(UserData user) {
        return obstacle(user) == null;
    }

    /**
     * For publishing an extension version, or the namespace for one: the agreement has to be current.
     *
     * @throws ErrorResultException if the user may not publish for lack of a current agreement
     */
    public void requireAgreement(UserData user) {
        var obstacle = obstacle(user);
        if (obstacle != null) {
            throw refusal(obstacle);
        }
    }

    /**
     * What keeps the user from publishing, or {@code null} if nothing does.
     */
    private @Nullable Assessment obstacle(UserData user) {
        if (!enabled()) {
            return null;
        }
        if (isBlocked(user)) {
            return new Assessment(State.REVOKED, AgreementStatus.of(AgreementStatus.State.NONE));
        }
        // A user created directly in the database has no login to sign with, so only a revocation holds.
        if (user.getProvider() == null) {
            return null;
        }
        var assessment = providerAssessment(user, false);
        return switch (assessment.state()) {
            case SIGNED -> null;
            default -> assessment;
        };
    }

    private ErrorResultException refusal(Assessment obstacle) {
        return switch (obstacle.state()) {
            case REVOKED -> new ErrorResultException(
                    "Your publisher agreement was revoked; contact an administrator.",
                    HttpStatus.FORBIDDEN);
            case UNKNOWN -> new ErrorResultException(
                    "Your publisher agreement could not be verified at the moment, please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE);
            case OUTDATED -> new ErrorResultException(
                    "Your publisher agreement is outdated"
                            + (obstacle.status().version() == null
                                    ? ""
                                    : " (version " + obstacle.status().version() + ")")
                            + ". Please sign the current publisher agreement before publishing.",
                    HttpStatus.FORBIDDEN);
            default -> {
                var login = provider.requiredLogin();
                yield new ErrorResultException(
                        login != null && !obstacle.status().loginLinked()
                                ? "You must log in with " + login.displayName()
                                        + " and sign the publisher agreement before publishing any extension."
                                : "You must sign the publisher agreement before publishing any extension.",
                        HttpStatus.FORBIDDEN);
            }
        };
    }

    /**
     * Adds the user's agreement to the JSON served to that user.
     */
    public void enrichUserJson(UserJson json, UserData user) {
        enrich(json, user, false, null);
    }

    /**
     * Adds the user's agreement to the JSON served to administrators, which stays unset where the agreement
     * could not be determined: it must not be mistaken for none.
     */
    public void enrichUserJsonForAdmin(UserJson json, UserData user) {
        enrich(json, user, true, null);
    }

    private void enrich(UserJson json, UserData user, boolean admin, @Nullable AgreementStatus known) {
        if (!enabled()) {
            return;
        }
        var assessment = known != null ? new Assessment(stateOf(known), known) : assess(user, admin);
        if (admin && assessment.state() == State.UNKNOWN) {
            return;
        }

        var agreement = new UserJson.PublisherAgreement();
        agreement.setStatus(
                (assessment.state() == State.UNKNOWN ? State.NONE : assessment.state()).name()
                        .toLowerCase(Locale.ROOT));
        var login = provider.requiredLogin();
        if (login != null) {
            // The web UI sends the user through this login before signing or revoking.
            agreement.setLoginProvider(login.registrationId());
            agreement.setLoginProviderName(login.displayName());
        }
        if (assessment.state() == State.SIGNED || assessment.state() == State.OUTDATED) {
            agreement.setVersion(assessment.status().version());
            if (assessment.status().timestamp() != null) {
                agreement.setTimestamp(TimeUtil.toUTCString(assessment.status().timestamp()));
            }
        }
        json.setPublisherAgreement(agreement);

        if (!admin && login != null && assessment.status().loginLinked()) {
            var linked = new UserJson();
            linked.setProvider(login.registrationId());
            if (json.getAdditionalLogins() == null) {
                json.setAdditionalLogins(new ArrayList<>(List.of(linked)));
            } else {
                json.getAdditionalLogins().add(linked);
            }
        }
    }

    /**
     * Signs the agreement on behalf of the user, adds the resulting status to the JSON, and makes the extension
     * versions hidden while the agreement was missing visible again. Signing what is already signed signs
     * nothing again, so a repeated call, as after a double click, is harmless; it still makes the versions
     * visible, so that a call that failed after the signing was recorded can be repeated.
     *
     * @return whether the user signed now; false if the current agreement was already signed
     * @throws ErrorResultException if the user may not sign, e.g. after a revocation
     */
    public boolean sign(UserData user, UserJson json) {
        if (!enabled()) {
            throw new ErrorResultException("Publisher agreements are not supported.", HttpStatus.NOT_FOUND);
        }
        if (isBlocked(user)) {
            throw refusal(new Assessment(State.REVOKED, AgreementStatus.of(AgreementStatus.State.NONE)));
        }
        var before = providerStatus(user, false);
        if (before.state() == AgreementStatus.State.UNKNOWN) {
            throw refusal(new Assessment(State.UNKNOWN, before));
        }

        var signedNow = before.state() != AgreementStatus.State.SIGNED;
        var current = signedNow ? signWithProvider(user) : before;
        // The provider may take a while, remote as it can be, so the block is judged again under the lock: an
        // admin who revoked meanwhile must not have the signing recorded and the extensions reactivated.
        transactions.executeWithoutResult(status -> {
            repositories.lockUser(user);
            if (isBlocked(user)) {
                throw refusal(new Assessment(State.REVOKED, AgreementStatus.of(AgreementStatus.State.NONE)));
            }
            if (signedNow) {
                recordSigning(user, current.version());
            }
            extensions.reactivateExtensions(user);
        });
        // From what the provider just answered, not asked again: it may still show the state before signing.
        enrich(json, user, false, current);
        return signedNow;
    }

    /**
     * Signs at the provider, which has to say that the user signed: anything else is not a signing, and is
     * neither recorded nor allowed to reactivate extensions.
     */
    private AgreementStatus signWithProvider(UserData user) {
        AgreementStatus reported;
        try {
            reported = provider.sign(user);
        } catch (PublisherAgreementException exc) {
            throw refusal(exc);
        }
        var signed = judged(reported);
        if (reported != null
                && reported.state() == AgreementStatus.State.SIGNED
                && signed.state() == AgreementStatus.State.OUTDATED) {
            // Retrying cannot help: the same version would be rejected again, so say what is wrong.
            logger.error(
                    "The publisher agreement provider signed version '{}' for {}, which ovsx.publisher-agreement.version"
                            + " and allowed-versions do not accept",
                    reported.version(),
                    user.getLoginName());
            throw new ErrorResultException(
                    "The publisher agreement was signed with version " + reported.version()
                            + ", which this registry does not accept; contact an administrator.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (signed == null || signed.state() != AgreementStatus.State.SIGNED) {
            logger.warn(
                    "The publisher agreement provider did not sign for {}, it reported {}",
                    user.getLoginName(),
                    signed == null ? "nothing" : signed.state());
            throw new ErrorResultException(
                    "The publisher agreement could not be signed at the moment, please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return signed;
    }

    private void recordSigning(UserData user, @Nullable String version) {
        var last = repositories.findLatestPublisherAgreementEvent(user.getId(), SIGNING_STEPS);
        if (last != null
                && last.getType() == PublisherAgreementEvent.Type.SIGNED
                && Objects.equals(last.getVersion(), version)) {
            return;
        }
        record(user, PublisherAgreementEvent.Type.SIGNED, version, user);
    }

    /**
     * Revokes the user's agreement: records the block, which holds even where withdrawing at the provider
     * fails, and then withdraws it there.
     *
     * @throws ErrorResultException if the provider failed to withdraw, or could not say whether there is an
     *         agreement to withdraw; the block is in place by then
     */
    public void revoke(UserData user, UserData admin) {
        // Recorded even while agreements are disabled, so that enabling them later does not forget it.
        // Inside the surrounding transaction, if there is one, the lock is held until it ends: a user who signs
        // meanwhile waits for everything the caller revokes along with the agreement.
        transactions.executeWithoutResult(status -> {
            repositories.lockUser(user);
            if (!isBlocked(user)) {
                record(user, PublisherAgreementEvent.Type.REVOKED, null, admin);
            }
        });
        if (!enabled()) {
            return;
        }
        // Only what is reliably known to exist is withdrawn: for none or an undetermined status there is
        // nothing confirmed to withdraw, and asking the provider would be a guess.
        var status = providerStatus(user, true);
        if (status.state() == AgreementStatus.State.SIGNED || status.state() == AgreementStatus.State.OUTDATED) {
            try {
                provider.withdraw(user, admin);
            } catch (PublisherAgreementException exc) {
                throw refusal(exc);
            }
        } else if (status.state() == AgreementStatus.State.UNKNOWN) {
            throw new ErrorResultException(
                    "The user is blocked from signing, but the provider could not say whether an agreement "
                            + "needs withdrawing there.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Lifts the block of a revocation, so the user may sign again, and makes the extension versions the
     * revocation hid visible again if the provider still has the user's agreement: such a user cannot sign again,
     * and would never get them back. Does nothing if the user is not blocked.
     */
    public void reinstate(UserData user, UserData admin) {
        if (!isBlocked(user)) {
            return;
        }
        var status = providerStatus(user, true);
        transactions.executeWithoutResult(transaction -> {
            repositories.lockUser(user);
            if (isBlocked(user)) {
                record(user, PublisherAgreementEvent.Type.REINSTATED, null, admin);
                if (status.state() == AgreementStatus.State.SIGNED) {
                    extensions.reactivateExtensions(user);
                }
            }
        });
    }

    /**
     * Whether the user's extensions may stay published, for the check on startup. The user is compliant while
     * they have an agreement, an outdated one included: it only keeps them from publishing. Having
     * none, or a revoked one, is not compliant; one the provider cannot tell about is neither.
     */
    public PublisherCompliance compliance(UserData user) {
        if (!enabled()) {
            return PublisherCompliance.COMPLIANT;
        }
        if (isBlocked(user)) {
            return PublisherCompliance.NON_COMPLIANT;
        }
        if (user.getProvider() == null) {
            return PublisherCompliance.COMPLIANT;
        }
        return switch (providerStatus(user, true).state()) {
            case SIGNED, OUTDATED -> PublisherCompliance.COMPLIANT;
            case UNKNOWN -> PublisherCompliance.UNKNOWN;
            case NONE -> PublisherCompliance.NON_COMPLIANT;
        };
    }

    /**
     * What happened to the user's agreement, the latest first.
     */
    public List<PublisherAgreementEntryJson> history(UserData user) {
        return repositories.findPublisherAgreementEvents(user.getId()).stream()
                .map(
                        event -> new PublisherAgreementEntryJson(
                                event.getType().name().toLowerCase(Locale.ROOT),
                                event.getVersion(),
                                TimeUtil.toUTCString(event.getTimestamp()),
                                event.getActorId()))
                .toList();
    }

    private Assessment assess(UserData user, boolean admin) {
        if (isBlocked(user)) {
            return new Assessment(State.REVOKED, AgreementStatus.of(AgreementStatus.State.NONE));
        }
        return providerAssessment(user, admin);
    }

    private Assessment providerAssessment(UserData user, boolean admin) {
        var status = providerStatus(user, admin);
        return new Assessment(stateOf(status), status);
    }

    private static State stateOf(AgreementStatus status) {
        return switch (status.state()) {
            case NONE -> State.NONE;
            case SIGNED -> State.SIGNED;
            case OUTDATED -> State.OUTDATED;
            case UNKNOWN -> State.UNKNOWN;
        };
    }

    /**
     * What the provider says, with a signature of a version that no longer counts made outdated, or
     * {@link AgreementStatus.State#UNKNOWN} if it fails to say.
     */
    private AgreementStatus providerStatus(UserData user, boolean admin) {
        try {
            return judged(admin ? provider.statusForAdmin(user) : provider.status(user));
        } catch (RuntimeException exc) {
            logger.warn("Failed to determine the publisher agreement of {}", user.getLoginName(), exc);
            return AgreementStatus.of(AgreementStatus.State.UNKNOWN);
        }
    }

    /**
     * A provider reports what was signed; whether that version still counts is decided here, once for all. A
     * signature without a version counts, as the provider cannot tell it.
     */
    private @Nullable AgreementStatus judged(@Nullable AgreementStatus status) {
        if (status == null
                || status.state() != AgreementStatus.State.SIGNED
                || status.version() == null
                || versions.accepts(status.version())) {
            return status;
        }
        return new AgreementStatus(
                AgreementStatus.State.OUTDATED,
                status.version(),
                status.timestamp(),
                status.loginLinked());
    }

    private boolean isBlocked(UserData user) {
        var step = repositories.findLatestPublisherAgreementEvent(user.getId(), BLOCK_STEPS);
        return step != null && step.getType() == PublisherAgreementEvent.Type.REVOKED;
    }

    private void record(
            UserData user,
            PublisherAgreementEvent.Type type,
            @Nullable String version,
            UserData actor
    ) {
        var event = new PublisherAgreementEvent();
        event.setUserDataId(user.getId());
        event.setType(type);
        event.setVersion(version);
        event.setTimestamp(TimeUtil.getCurrentUTC());
        event.setActorId(actor.getId());
        repositories.savePublisherAgreementEvent(event);
    }

    private ErrorResultException refusal(PublisherAgreementException exc) {
        var status = switch (exc.getReason()) {
            case LOGIN_REQUIRED -> HttpStatus.FORBIDDEN;
            case REJECTED -> HttpStatus.BAD_REQUEST;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNSUPPORTED -> HttpStatus.NOT_FOUND;
        };
        return new ErrorResultException(exc.getMessage(), status);
    }
}
