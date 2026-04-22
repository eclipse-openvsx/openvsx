/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import jakarta.persistence.*;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.json.NamespaceDetailsJson;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Entity
@Table(name = "namespace")
public class Namespace implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final String SL_LINKEDIN = "linkedin";
	private static final String SL_GITHUB = "github";
	private static final String SL_TWITTER = "twitter";

    @Id
	@GeneratedValue(generator = "namespaceSeq")
	@SequenceGenerator(name = "namespaceSeq", sequenceName = "namespace_seq")
	private long id;

    @Column(length = 128)
	private String publicId;

	private String name;

	@Column(length = 32)
	private String displayName;

	private String description;

	private String website;

	private String supportLink;

	private String logoName;
	
	@Column(length = 32)
	private String logoStorageType;

	@ElementCollection
	@MapKeyColumn(name = "provider")
	@Column(name = "social_link")
	private Map<String, String> socialLinks;

    @OneToMany(mappedBy = "namespace")
	private List<Extension> extensions;

    @OneToMany(mappedBy = "namespace")
	private List<NamespaceMembership> memberships;


	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getPublicId() {
		return publicId;
	}

	public void setPublicId(String publicId) {
		this.publicId = publicId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLogoName() {
		return logoName;
	}

	public void setLogoName(String logoName) {
		this.logoName = logoName;
	}

	public String getLogoStorageType() {
		return logoStorageType;
	}

	public void setLogoStorageType(String logoStorageType) {
		this.logoStorageType = logoStorageType;
	}

	public String getWebsite() {
		return website;
	}

	public void setWebsite(String website) {
		this.website = website;
	}

	public String getSupportLink() {
		return supportLink;
	}

	public void setSupportLink(String supportLink) {
		this.supportLink = supportLink;
	}

	public Map<String, String> getSocialLinks() {
		return socialLinks;
	}

	public void setSocialLinks(Map<String, String> socialLinks) {
		this.socialLinks = socialLinks.entrySet().stream()
				.filter(e -> !StringUtils.isEmpty(e.getValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public List<Extension> getExtensions() {
		return extensions;
	}

	public void setExtensions(List<Extension> extensions) {
		this.extensions = extensions;
	}

	public List<NamespaceMembership> getMemberships() {
		return memberships;
	}

	public void setMemberships(List<NamespaceMembership> memberships) {
		this.memberships = memberships;
	}

	public NamespaceDetailsJson toNamespaceDetailsJson() {
		var details = new NamespaceDetailsJson();
		details.setName(name);
		details.setDisplayName(StringUtils.isNotEmpty(displayName) ? displayName : name);
		details.setDescription(description);
		details.setWebsite(website);
		details.setSupportLink(supportLink);
		details.setSocialLinks(Map.of(
				SL_LINKEDIN, socialLinks.getOrDefault(SL_LINKEDIN, ""),
				SL_GITHUB, socialLinks.getOrDefault(SL_GITHUB, ""),
				SL_TWITTER, socialLinks.getOrDefault(SL_TWITTER, "")
		));

		return details;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Namespace namespace = (Namespace) o;
		return id == namespace.id
				&& Objects.equals(publicId, namespace.publicId)
				&& Objects.equals(name, namespace.name)
				&& Objects.equals(displayName, namespace.displayName)
				&& Objects.equals(description, namespace.description)
				&& Objects.equals(website, namespace.website)
				&& Objects.equals(supportLink, namespace.supportLink)
				&& Objects.equals(logoName, namespace.logoName)
				&& Objects.equals(logoStorageType, namespace.logoStorageType)
				&& Objects.equals(socialLinks, namespace.socialLinks)
				&& Objects.equals(extensions, namespace.extensions)
				&& Objects.equals(memberships, namespace.memberships);
	}

	@Override
	public int hashCode() {
        return Objects.hash(id, publicId, name, displayName, description, website, supportLink, logoName,
				logoStorageType, socialLinks, extensions, memberships);
	}
}