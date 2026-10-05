/******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import org.jspecify.annotations.Nullable;

import org.eclipse.openvsx.json.UserJson;

@Entity
public class UserData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Role {
        ADMIN, PRIVILEGED;

        public static Role valueOfIgnoreCase(String value) {
            if (value == null) {
                return null;
            }
            return Role.valueOf(value.trim().toUpperCase());
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    @Id
    @GeneratedValue(generator = "userDataSeq")
    @SequenceGenerator(name = "userDataSeq", sequenceName = "user_data_seq")
    private long id;

    @Column(length = 32)
    @Convert(converter = UserRoleConverter.class)
    private Role role;

    // EAGER, unlike tokens/memberships below: UserService#findLoggedInUser loads this entity with a
    // bare entityManager.find() outside any transaction, and every real deployment config sets
    // spring.jpa.open-in-view: false, so there is no session left afterward for a LAZY collection to
    // initialize from - exactly the LazyInitializationException role never has, being a plain column
    // the find() call itself already materializes.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_data_permission", joinColumns = @JoinColumn(name = "user_data_id"))
    @Column(name = "permission", length = 32)
    @Convert(converter = PermissionConverter.class)
    private Set<Permission> permissions = new HashSet<>();

    private String loginName;

    private String fullName;

    private String email;

    private String avatarUrl;

    @Column(length = 32)
    private String provider;

    private String authId;

    private String providerUrl;

    @OneToMany(mappedBy = "user")
    private List<PersonalAccessToken> tokens;

    @OneToMany(mappedBy = "user")
    private List<NamespaceMembership> memberships;

    private String eclipsePersonId;

    @Column(length = 4096)
    @Convert(converter = AuthTokenConverter.class)
    private AuthToken eclipseToken;

    /**
     * Convert to a JSON object.
     */
    public UserJson toUserJson() {
        var json = new UserJson();
        json.setLoginName(this.getLoginName());
        json.setFullName(this.getFullName());
        json.setAvatarUrl(this.getAvatarUrl());
        json.setHomepage(this.getProviderUrl());
        json.setProvider(this.getProvider());
        return json;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    /**
     * Whether this user bypasses per-namespace verification entirely, e.g. in
     * {@link org.eclipse.openvsx.repositories.RepositoryService#isVerifiedPublisher}.
     */
    public boolean isPrivileged() {
        return Role.PRIVILEGED.equals(role);
    }

    public @Nullable String getRoleAsString() {
        return Optional.ofNullable(this.getRole()).map(Role::toString).orElse(null);
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<String> getPermissionsAsStrings() {
        return permissions.stream().map(Permission::toString).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether this user is allowed to perform an admin action requiring {@code permission}.
     * {@link Role#ADMIN} always has every permission, including ones added after the role was
     * granted, so it never needs to be reflected in {@link #permissions} itself.
     */
    public boolean hasPermission(Permission permission) {
        return Role.ADMIN.equals(role) || permissions.contains(permission);
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getAuthId() {
        return authId;
    }

    public void setAuthId(String authId) {
        this.authId = authId;
    }

    public String getProviderUrl() {
        return providerUrl;
    }

    public void setProviderUrl(String providerUrl) {
        this.providerUrl = providerUrl;
    }

    public String getEclipsePersonId() {
        return eclipsePersonId;
    }

    public void setEclipsePersonId(String eclipsePersonId) {
        this.eclipsePersonId = eclipsePersonId;
    }

    public AuthToken getEclipseToken() {
        return eclipseToken;
    }

    public void setEclipseToken(AuthToken eclipseToken) {
        this.eclipseToken = eclipseToken;
    }

    // tokens and memberships are deliberately excluded below: each of their elements holds this
    // user back (PersonalAccessToken#user, NamespaceMembership#user), so hashing them here would
    // recurse into this user's hashCode again, unconditionally. permissions has no such back-reference
    // and is EAGER, so it carries no risk of either problem.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserData userData = (UserData) o;
        return id == userData.id
                && Objects.equals(role, userData.role)
                && Objects.equals(permissions, userData.permissions)
                && Objects.equals(loginName, userData.loginName)
                && Objects.equals(fullName, userData.fullName)
                && Objects.equals(email, userData.email)
                && Objects.equals(avatarUrl, userData.avatarUrl)
                && Objects.equals(provider, userData.provider)
                && Objects.equals(authId, userData.authId)
                && Objects.equals(providerUrl, userData.providerUrl)
                && Objects.equals(eclipsePersonId, userData.eclipsePersonId)
                && Objects.equals(eclipseToken, userData.eclipseToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                role,
                permissions,
                loginName,
                fullName,
                email,
                avatarUrl,
                provider,
                authId,
                providerUrl,
                eclipsePersonId,
                eclipseToken);
    }
}
