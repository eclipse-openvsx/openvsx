/******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Records validation failures detected during pre-validation phase.
 */
@Entity
@Table(name = "extension_validation_failure")
public class ExtensionValidationFailure implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(generator = "extensionValidationFailureSeq")
    @SequenceGenerator(
        name = "extensionValidationFailureSeq",
        sequenceName = "extension_validation_failure_seq",
        allocationSize = 1
    )
    private long id;

    /** Reference to the parent scan that this failure belongs to */
    @ManyToOne
    @JoinColumn(name = "scan_id", nullable = false)
    private ExtensionScan scan;

    /**
     * Type of validation check that failed.
     * This is a flexible string field to allow new validation types without code changes.
     * Use CHECK_TYPE constants from ValidationCheck implementations.
     */
    @Column(name = "validation_type", nullable = false, length = 100)
    private String checkType;

    /** Name of the specific validation rule that failed */
    @Column(name = "rule_name", nullable = false, length = 255)
    private String ruleName;

    /** Detailed explanation of why the validation failed */
    @Column(name = "validation_failure_reason", nullable = false, length = 1024)
    private String validationFailureReason;

    /** Whether this failure was enforced at the time it was detected. */
    @Column(name = "enforced", nullable = false)
    private boolean enforced = true;

    /** Timestamp when the validation failure was detected */
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public ExtensionScan getScan() {
        return scan;
    }

    public void setScan(ExtensionScan scan) {
        this.scan = scan;
    }

    public String getCheckType() {
        return checkType;
    }

    public void setCheckType(String checkType) {
        this.checkType = checkType;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getValidationFailureReason() {
        return validationFailureReason;
    }

    public void setValidationFailureReason(String validationFailureReason) {
        this.validationFailureReason = validationFailureReason;
    }

    public boolean isEnforced() {
        return enforced;
    }

    public void setEnforced(boolean enforced) {
        this.enforced = enforced;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }

    /**
     * Factory method to create a validation failure with a specific check type.
     * Use CHECK_TYPE constants from ValidationCheck implementations.
     */
    public static ExtensionValidationFailure create(
            String checkType,
            String ruleName,
            String reason
    ) {
        var failure = new ExtensionValidationFailure();
        failure.setCheckType(checkType);
        failure.setRuleName(ruleName);
        failure.setValidationFailureReason(reason);
        failure.setEnforced(true);
        failure.setDetectedAt(TimeUtil.getCurrentUTC());
        return failure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExtensionValidationFailure that = (ExtensionValidationFailure) o;
        return id == that.id
                && Objects.equals(getId(scan), getId(that.scan))
                && Objects.equals(checkType, that.checkType)
                && Objects.equals(ruleName, that.ruleName)
                && Objects.equals(validationFailureReason, that.validationFailureReason)
                && enforced == that.enforced
                && Objects.equals(detectedAt, that.detectedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                getId(scan),
                checkType,
                ruleName,
                validationFailureReason,
                enforced,
                detectedAt);
    }

    private Long getId(ExtensionScan scan) {
        return scan != null ? scan.getId() : null;
    }
}
