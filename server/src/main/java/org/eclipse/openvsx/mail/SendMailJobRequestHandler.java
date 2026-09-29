/********************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
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
package org.eclipse.openvsx.mail;

import java.nio.charset.StandardCharsets;

import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class SendMailJobRequestHandler implements JobRequestHandler<SendMailJobRequest> {

    private final JavaMailSender sender;
    private final TemplateEngine templateEngine;

    public SendMailJobRequestHandler(
            @Autowired(required = false) JavaMailSender sender,
            TemplateEngine templateEngine
    ) {
        this.sender = sender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void run(SendMailJobRequest request) throws Exception {
        var context = new Context();
        context.setVariables(request.getVariables());
        var htmlContent = templateEngine.process(request.getTemplate(), context);

        var message = sender.createMimeMessage();
        var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        helper.setFrom(request.getFrom());
        helper.setTo(request.getTo());
        helper.setSubject(request.getSubject());
        helper.setText(htmlContent, true);
        sender.send(message);
    }
}
