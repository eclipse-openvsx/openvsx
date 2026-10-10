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

/**
 * Published once the settings have changed and the change has committed. Only the node that made the
 * change publishes it; the nodes told through {@link SettingsUpdateChannel} do not, so a listener
 * runs once per change rather than once per node.
 */
public record SettingsChangedEvent() {}
