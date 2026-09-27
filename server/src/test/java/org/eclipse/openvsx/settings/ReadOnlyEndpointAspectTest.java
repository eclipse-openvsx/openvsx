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

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import org.eclipse.openvsx.RegistryAPI;
import org.eclipse.openvsx.UserAPI;
import org.eclipse.openvsx.admin.AdminAPI;
import org.eclipse.openvsx.json.NamespaceJson;
import org.eclipse.openvsx.json.ResultJson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReadOnlyEndpointAspectTest {

    private final SettingsService settings = mock(SettingsService.class);
    private final ReadOnlyEndpointAspect advisor = new ReadOnlyEndpointAspect(settings);

    @Test
    void classFilterMatchesRegistryUserAndAdminApiControllers() {
        var filter = advisor.getClassFilter();
        assertThat(filter.matches(RegistryAPI.class)).isTrue();
        assertThat(filter.matches(UserAPI.class)).isTrue();
        assertThat(filter.matches(AdminAPI.class)).isTrue();
    }

    @Test
    void classFilterRejectsUnrelatedClasses() {
        assertThat(advisor.getClassFilter().matches(ReadOnlyEndpointAspectTest.class)).isFalse();
    }

    @Test
    void matchesOnlyMutatingOperationsReturningResponseEntity() throws NoSuchMethodException {
        var mutating = RegistryAPI.class.getMethod("createNamespace", NamespaceJson.class, String.class);
        var readOnly = RegistryAPI.class.getMethod("getNamespace", String.class);

        assertThat(advisor.matches(mutating, RegistryAPI.class)).isTrue();
        assertThat(advisor.matches(readOnly, RegistryAPI.class)).isFalse();
    }

    @Test
    void blocksTheCallWhenTheRegistryIsReadOnly() throws Throwable {
        when(settings.isReadOnly()).thenReturn(true);
        var invocation = mock(MethodInvocation.class);

        var result = (ResponseEntity<?>) interceptor().invoke(invocation);

        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(((ResultJson) result.getBody()).getError()).isEqualTo("Registry is in read-only mode.");
        verify(invocation, never()).proceed();
    }

    @Test
    void proceedsWhenTheRegistryIsWritable() throws Throwable {
        when(settings.isReadOnly()).thenReturn(false);
        var invocation = mock(MethodInvocation.class);
        when(invocation.proceed()).thenReturn("ok");

        assertThat(interceptor().invoke(invocation)).isEqualTo("ok");
    }

    private MethodInterceptor interceptor() {
        return (MethodInterceptor) advisor.getAdvice();
    }
}
