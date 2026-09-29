/*******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

import jakarta.persistence.*;

@Entity
public class SignatureKeyPair implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String KEYPAIR_MODE_CREATE = "create";
    public static final String KEYPAIR_MODE_RENEW = "renew";
    public static final String KEYPAIR_MODE_DELETE = "delete";

    @Id
    @GeneratedValue(generator = "signatureKeyPairSeq")
    @SequenceGenerator(name = "signatureKeyPairSeq", sequenceName = "signature_key_pair_seq")
    private long id;

    @Column(length = 128)
    private String publicId;

    @Column(length = 32)
    private byte[] privateKey;

    private String publicKeyText;

    private LocalDateTime created;

    private boolean active;

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

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(byte[] privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKeyText() {
        return publicKeyText;
    }

    public void setPublicKeyText(String publicKeyText) {
        this.publicKeyText = publicKeyText;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SignatureKeyPair that = (SignatureKeyPair) o;
        return id == that.id
                && active == that.active
                && Objects.equals(publicId, that.publicId)
                && Arrays.equals(privateKey, that.privateKey)
                && Objects.equals(publicKeyText, that.publicKeyText)
                && Objects.equals(created, that.created);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(id, publicId, publicKeyText, created, active);
        result = 31 * result + Arrays.hashCode(privateKey);
        return result;
    }
}
