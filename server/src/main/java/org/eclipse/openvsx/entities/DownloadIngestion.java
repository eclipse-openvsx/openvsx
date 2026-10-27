/******************************************************************************
 * Copyright (c) 2025 Eclipse Foundation and others
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

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "download_ingestion")
public class DownloadIngestion {

    /** No analytics to write: analytics is off, or the log file held no downloads. */
    public static final String ANALYTICS_NONE = "NONE";

    /** The registry counters are updated but the analytics events are not stored yet. */
    public static final String ANALYTICS_PENDING = "PENDING";

    /** The analytics events are stored. */
    public static final String ANALYTICS_DONE = "DONE";

    @Id
    @GeneratedValue(generator = "downloadIngestionSeq")
    @SequenceGenerator(name = "downloadIngestionSeq", sequenceName = "download_ingestion_seq")
    private long id;

    private String name;

    private String storageType;

    private LocalDateTime processedOn;

    private int executionTime;

    private boolean success;

    private String analyticsStatus = ANALYTICS_NONE;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public LocalDateTime getProcessedOn() {
        return processedOn;
    }

    public void setProcessedOn(LocalDateTime processedOn) {
        this.processedOn = processedOn;
    }

    public int getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(int executionTime) {
        this.executionTime = executionTime;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAnalyticsStatus() {
        return analyticsStatus;
    }

    public void setAnalyticsStatus(String analyticsStatus) {
        this.analyticsStatus = analyticsStatus;
    }
}
