/******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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
package org.eclipse.openvsx.mail;

import java.util.Map;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

/**
 * A {@code JobRequest} to send templated emails.
 */
public class SendMailJobRequest implements JobRequest {

    private String from;
    private String to;
    private String subject;
    private String template;

    // FIXME: since using jackson 3, serialization of JobRequest arguments
    //        is restricted by default, more information at
    //        <a href="https://www.jobrunr.io/en/documentation/serialization/jackson3/#exploring-alternatives-to-additional-polymorphic-type-validation">...</a>
    //        RegistryApplication injects a custom Jackson3JsonMapper that also allows subtypes from java.util and java.time
    //        but we should revisit the variables
    private Map<String, Object> variables;

    public SendMailJobRequest() {
    }

    public SendMailJobRequest(
            String from,
            String to,
            String subject,
            String template,
            Map<String, Object> variables
    ) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.template = template;
        this.variables = variables;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    @Override
    public Class<? extends JobRequestHandler<?>> getJobRequestHandler() {
        return SendMailJobRequestHandler.class;
    }
}
