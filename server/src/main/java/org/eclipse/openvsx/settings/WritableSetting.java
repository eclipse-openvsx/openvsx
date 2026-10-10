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
package org.eclipse.openvsx.settings;

import java.util.Map;

import org.springframework.http.HttpStatus;

import org.eclipse.openvsx.util.ErrorResultException;

/**
 * One runtime setting of the registry, as a single value of type {@code T} that decodes itself from,
 * and encodes itself back into, the rows it is stored under. Adding a setting is one new
 * {@code @Component} implementing this; {@link SettingsService} knows what a setting is, never what
 * any particular one means.
 * <p>
 * The rows are handed in rather than fetched, so one save reads the store exactly once and no
 * setting sees a store it is halfway through writing.
 */
public interface WritableSetting<T> {

    /** Stable and unique. Not necessarily a row key - a setting owning several rows names them in {@link #toRows}. */
    String getName();

    /** This setting's value, with its own defaults filling the rows the store does not hold. */
    T read(SettingRows stored);

    /** A row the update does not carry is one the caller isn't touching, so it keeps its current value. */
    T merge(T current, SettingRows update);

    /**
     * This value as the rows it is stored under. A save writes all of them in one transaction; the
     * order only shapes the admin log line.
     */
    Map<String, Object> toRows(T value);

    /**
     * Refuses a value the store must never hold. A rule should skip a field that {@code current}
     * already holds unchanged: an admin has to be able to switch a banner off without also fixing a
     * message that was written before the rule existed.
     */
    default void validate(T value, T current) {
    }

    /** How a changed row reads in the admin log, whose message column caps at 512 characters. */
    default String describe(String rowKey, Object value) {
        return rowKey + " -> " + value;
    }

    /** The one refusal the settings endpoints produce, so no setting invents its own status. */
    static ErrorResultException reject(String message) {
        return new ErrorResultException(message, HttpStatus.BAD_REQUEST);
    }
}
