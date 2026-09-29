/******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
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

import jakarta.persistence.*;

@Entity
public class MigrationItem {

    @Id
    @GeneratedValue(generator = "migrationItemSeq")
    @SequenceGenerator(name = "migrationItemSeq", sequenceName = "migration_item_seq")
    private long id;

    private long entityId;

    private boolean migrationScheduled;

    private String jobName;

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public long getEntityId() {
        return entityId;
    }

    public void setEntityId(long entityId) {
        this.entityId = entityId;
    }

    public boolean isMigrationScheduled() {
        return migrationScheduled;
    }

    public void setMigrationScheduled(boolean migrationScheduled) {
        this.migrationScheduled = migrationScheduled;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }
}
