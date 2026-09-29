/*******************************************************************************
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
 ******************************************************************************/
package org.eclipse.openvsx.entities;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import org.eclipse.openvsx.util.TimeUtil;

/**
 * Admin decision on a quarantined extension scan.
 * Stores whether an admin has allowed or blocked a quarantined extension.
 * One decision per scan (unique constraint on scan_id).
 */
@Entity
@Table(name = "admin_scan_decision")
public class AdminScanDecision implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Decision value: extension was allowed to be published */
    public static final String ALLOWED = "ALLOWED";

    /** Decision value: extension was blocked from publishing */
    public static final String BLOCKED = "BLOCKED";

    @Id
    @GeneratedValue(generator = "adminScanDecisionSeq")
    @SequenceGenerator(name = "adminScanDecisionSeq", sequenceName = "admin_scan_decision_seq", allocationSize = 1)
    private long id;

    /** Reference to the scan this decision applies to (unique - one decision per scan) */
    @OneToOne
    @JoinColumn(name = "scan_id", nullable = false, unique = true)
    private ExtensionScan scan;

    /** Decision: ALLOWED or BLOCKED */
    @Column(name = "decision", nullable = false, length = 20)
    private String decision;

    /** Admin who made the decision */
    @ManyToOne
    @JoinColumn(name = "decided_by_id", nullable = false)
    private UserData decidedBy;

    /** When the decision was made */
    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

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

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public UserData getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UserData decidedBy) {
        this.decidedBy = decidedBy;
    }

    /** Convenience method to get the admin's email (or login name if email is empty) for API responses */
    public String getDecidedByName() {
        if (decidedBy == null) {
            return null;
        }
        var email = decidedBy.getEmail();
        return (email != null && !email.isBlank()) ? email : decidedBy.getLoginName();
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    /** Returns true if this decision allows the extension */
    public boolean isAllowed() {
        return ALLOWED.equals(decision);
    }

    /** Returns true if this decision blocks the extension */
    public boolean isBlocked() {
        return BLOCKED.equals(decision);
    }

    /**
     * Factory method to create an allowed decision.
     */
    public static AdminScanDecision allowed(ExtensionScan scan, UserData decidedBy) {
        var decision = new AdminScanDecision();
        decision.setScan(scan);
        decision.setDecision(ALLOWED);
        decision.setDecidedBy(decidedBy);
        decision.setDecidedAt(TimeUtil.getCurrentUTC());
        return decision;
    }

    /**
     * Factory method to create a blocked decision.
     */
    public static AdminScanDecision blocked(ExtensionScan scan, UserData decidedBy) {
        var decision = new AdminScanDecision();
        decision.setScan(scan);
        decision.setDecision(BLOCKED);
        decision.setDecidedBy(decidedBy);
        decision.setDecidedAt(TimeUtil.getCurrentUTC());
        return decision;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AdminScanDecision that = (AdminScanDecision) o;
        return id == that.id
                && Objects.equals(getScanId(scan), getScanId(that.scan))
                && Objects.equals(decision, that.decision)
                && Objects.equals(getUserId(decidedBy), getUserId(that.decidedBy));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getScanId(scan), decision, getUserId(decidedBy));
    }

    private Long getScanId(ExtensionScan scan) {
        return scan != null ? scan.getId() : null;
    }

    private Long getUserId(UserData user) {
        return user != null ? user.getId() : null;
    }
}
