/********************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.scanning;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for checking extension files for potentially malicious zip extra fields.
 * <p>
 * Implements PublishCheck to be auto-discovered by PublishCheckRunner.
 * Always enabled and enforced.
 */
@Service
@Order(4)
public class MaliciousZipCheckService implements PublishCheck {

    public static final String CHECK_TYPE = "MALICIOUS_ZIP_CHECK";
    private static final String RULE_NAME = "EXTRA_FIELDS_DETECTED";
    private static final String MESSAGE = "VSIX extension file contains zip entries with potentially harmful extra fields";

    @Override
    public String getCheckType() {
        return CHECK_TYPE;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isEnforced() {
        return true;
    }

    @Override
    public String getUserFacingMessage(List<Failure> failures) {
        return MESSAGE;
    }

    @Override
    public PublishCheck.Result check(Context context) {
        var potentiallyMalicious = context.processor().isPotentiallyMalicious();
        if (potentiallyMalicious) {
            return PublishCheck.Result.fail(RULE_NAME, MESSAGE);
        } else {
            return PublishCheck.Result.pass();
        }
    }
}
