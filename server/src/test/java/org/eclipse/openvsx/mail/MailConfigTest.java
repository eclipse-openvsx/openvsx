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
package org.eclipse.openvsx.mail;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MailConfigTest {

    // Regression: MailConfig used to rely on Spring Boot's ThymeleafAutoConfiguration to turn
    // its SpringResourceTemplateResolver bean into an actual TemplateEngine - which is also
    // what registered a ThymeleafViewResolver for Spring MVC, resolving arbitrary view names
    // (e.g. the default "error" view) through this same mail-only resolver. Building the
    // engine here directly proves mail rendering still works without any Spring Boot
    // autoconfiguration involved at all - not even a Spring Boot ApplicationContext, just the
    // plain Spring ApplicationContext SpringResourceTemplateResolver needs for classpath
    // resource loading.
    @Test
    void rendersARealMailTemplateWithoutAnySpringBootAutoconfiguration() {
        var mailConfig = new MailConfig();
        var applicationContext = new GenericApplicationContext();
        applicationContext.refresh();

        var resolver = mailConfig.templateResolver(applicationContext);
        var engine = mailConfig.templateEngine(resolver);

        var context = new Context();
        context.setVariable("name", "Jane Doe");
        context.setVariable("tokenName", "My Token");
        context.setVariable("expiryDate", LocalDate.of(2026, 6, 15));

        var html = engine.process("access-token-expired", context);

        assertThat(html).contains("Jane Doe");
        assertThat(html).contains("My Token");
        assertThat(html).contains("15-06-2026");
    }

    // Regression: spring-boot-thymeleaf (the autoconfiguration module) must not be reachable
    // from the classpath at all - if it were, ThymeleafAutoConfiguration would register a
    // ThymeleafViewResolver for Spring MVC regardless of what MailConfig itself does.
    @Test
    void thymeleafAutoConfigurationIsNotOnTheClasspath() {
        assertThatThrownBy(
                () -> Class.forName("org.springframework.boot.thymeleaf.autoconfigure.ThymeleafAutoConfiguration"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
