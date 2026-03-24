/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.mail;

import jakarta.annotation.PostConstruct;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.entities.UserData;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;


@Component
public class MailService {
    private final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final boolean disabled;
    private final JobRequestScheduler scheduler;

    @Value("${ovsx.mail.from:}")
    String from;

    @Value("${ovsx.mail.revoked-access-tokens.subject:Open VSX Access Tokens Revoked}")
    String revokedAccessTokensSubject;

    @Value("${ovsx.mail.revoked-access-tokens.template:revoked-access-tokens.html}")
    String revokedAccessTokensTemplate;

    @Value("${ovsx.mail.access-token-expiry.subject:Open VSX Access Token Expiry Notification}")
    String accessTokenExpirySubject;

    @Value("${ovsx.mail.access-token-expiry.template:access-token-expiry-notification.html}")
    String accessTokenExpiryTemplate;

    @Value("${ovsx.mail.access-token-expired.subject:Open VSX Access Token Expired}")
    String accessTokenExpiredSubject;

    @Value("${ovsx.mail.access-token-expired.template:access-token-expired.html}")
    String accessTokenExpiredTemplate;

    public MailService(@Autowired(required = false) JavaMailSender sender, JobRequestScheduler scheduler) {
        this.disabled = sender == null;
        this.scheduler = scheduler;
    }

    @PostConstruct
    public void validate() {
        if (!disabled) {
            if (!StringUtils.hasText(from)) {
                throw new IllegalArgumentException(
                        "ovsx.mail.from is not set while sending mails is enabled");
            }

            if (!StringUtils.hasText(revokedAccessTokensSubject)) {
                throw new IllegalArgumentException(
                        "ovsx.mail.revoked-access-tokens.subject is not set while sending mails is enabled");
            }

            if (!StringUtils.hasText(revokedAccessTokensTemplate)) {
                throw new IllegalArgumentException(
                        "ovsx.mail.revoked-access-tokens.template is not set while sending mails is enabled");
            }
        }
    }

    public void scheduleAccessTokenExpiryNotification(PersonalAccessToken token) {
        if (disabled) {
            return;
        }

        var user = token.getUser();
        var email = user.getEmail();

        if (email == null) {
            logger.warn("Could not send mail to user '{}' due to expiring access token notification: email not known", user.getLoginName());
            return;
        }

        // the fullName might be null
        var name = user.getFullName() == null ? user.getLoginName() : user.getFullName();
        // the token description might be null as well
        var tokenName = token.getDescription() != null ? token.getDescription() : "";

        var variables = Map.<String, Object>of(
                "name", name,
                "tokenName", tokenName,
                "expiryDate", token.getExpiresTimestamp()
        );
        var jobRequest = new SendMailJobRequest(
                from,
                email,
                accessTokenExpirySubject,
                accessTokenExpiryTemplate,
                variables
        );

        scheduler.enqueue(jobRequest);
        logger.debug("Scheduled notification email for expiring token {} to {}", tokenName, email);
    }

    public void scheduleAccessTokenExpiredMail(PersonalAccessToken token) {
        if (disabled) {
            return;
        }

        var user = token.getUser();
        var email = user.getEmail();

        if (email == null) {
            logger.warn("Could not send mail to user '{}' due to expired access token: email not known", user.getLoginName());
            return;
        }

        // the fullName might be null
        var name = user.getFullName() == null ? user.getLoginName() : user.getFullName();
        // the token description might be null as well
        var tokenName = token.getDescription() != null ? token.getDescription() : "";

        var variables = Map.<String, Object>of(
                "name", name,
                "tokenName", tokenName,
                "expiryDate", token.getExpiresTimestamp()
        );
        var jobRequest = new SendMailJobRequest(
                from,
                email,
                accessTokenExpiredSubject,
                accessTokenExpiredTemplate,
                variables
        );

        scheduler.enqueue(jobRequest);
        logger.debug("Scheduled notification email for expired token {} to {}", tokenName, email);
    }

    public void scheduleRevokedAccessTokensMail(UserData user) {
        if (disabled) {
            return;
        }

        if (user.getEmail() == null) {
            logger.warn("Could not send mail to user '{}' due to revoked access token: email not known", user.getLoginName());
            return;
        }

        // the fullName might be null
        var name = user.getFullName() == null ? user.getLoginName() : user.getFullName();
        var variables = Map.<String, Object>of("name", name);
        var jobRequest = new SendMailJobRequest(
                from,
                user.getEmail(),
                revokedAccessTokensSubject,
                revokedAccessTokensTemplate,
                variables
        );

        scheduler.enqueue(jobRequest);
    }
}
