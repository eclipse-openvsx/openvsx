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
package org.eclipse.openvsx.security;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every handler mapped under {@code /admin} has to authorize for itself.
 * <p>
 * {@link SecurityConfig} only gates those paths on having <em>some</em> admin access, so which
 * capability a given endpoint requires is decided solely by the {@code checkPermission} /
 * {@code checkAdminUser} call in the handler. A new handler that forgets that call is reachable by
 * anyone holding any permission at all, and nothing else would notice - hence this test.
 */
class AdminEndpointPermissionCheckTest {

    private static final Set<String> AUTHORIZATION_CHECKS = Set
            .of("checkPermission", "checkAnyPermission", "checkAdminUser");

    @Test
    void everyHandlerUnderAdminAuthorizesForItself() throws IOException {
        var unguarded = new ArrayList<String>();
        for (var controller : adminControllers()) {
            var callsByMethod = callsByMethod(controller);
            for (var method : controller.getDeclaredMethods()) {
                if (isAdminHandler(controller, method)
                        && !reachesAuthorizationCheck(callsByMethod, signature(method))) {
                    unguarded.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }

        assertThat(unguarded)
                .as("handlers under /admin that never reach checkPermission/checkAdminUser")
                .isEmpty();
    }

    /** Sanity check on the scan itself, so the test above cannot pass by finding nothing. */
    @Test
    void findsTheAdminHandlersItIsMeantToGuard() throws IOException {
        var handlers = 0;
        for (var controller : adminControllers()) {
            for (var method : controller.getDeclaredMethods()) {
                if (isAdminHandler(controller, method)) {
                    handlers++;
                }
            }
        }

        assertThat(handlers).isGreaterThan(50);
    }

    private List<Class<?>> adminControllers() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var controllers = new ArrayList<Class<?>>();
        for (var candidate : scanner.findCandidateComponents("org.eclipse.openvsx")) {
            var type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
            if (!basePaths(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class)).isEmpty()
                    || hasAdminMappedMethod(type)) {
                controllers.add(type);
            }
        }
        return controllers;
    }

    private boolean hasAdminMappedMethod(Class<?> type) {
        for (var method : type.getDeclaredMethods()) {
            if (isAdminHandler(type, method)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminHandler(Class<?> controller, Method method) {
        var mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (mapping == null) {
            return false;
        }

        var classPaths = basePathsOf(controller);
        var methodPaths = mapping.path().length > 0 ? mapping.path() : new String[] { "" };
        for (var classPath : classPaths.isEmpty() ? List.of("") : classPaths) {
            for (var methodPath : methodPaths) {
                if ((classPath + methodPath).startsWith("/admin")) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> basePathsOf(Class<?> controller) {
        return basePaths(AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class));
    }

    private List<String> basePaths(RequestMapping mapping) {
        if (mapping == null) {
            return List.of();
        }
        return List.of(mapping.path());
    }

    /**
     * Maps every method of the class to the names it calls, so a handler that delegates its check to a
     * private helper in the same class (as the report endpoints do) still counts as guarded.
     */
    private Map<String, MethodCalls> callsByMethod(Class<?> controller) throws IOException {
        var calls = new HashMap<String, MethodCalls>();
        var internalName = Type.getInternalName(controller);
        new ClassReader(controller.getName()).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String sig, String[] exc) {
                var current = calls.computeIfAbsent(name + descriptor, key -> new MethodCalls());
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String desc, boolean isInterface) {
                        current.calledNames.add(n);
                        if (internalName.equals(owner)) {
                            current.sameClassCalls.add(n + desc);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);
        return calls;
    }

    private boolean reachesAuthorizationCheck(Map<String, MethodCalls> callsByMethod, String start) {
        var seen = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (!seen.add(current)) {
                continue;
            }
            var calls = callsByMethod.get(current);
            if (calls == null) {
                continue;
            }
            if (calls.calledNames.stream().anyMatch(AUTHORIZATION_CHECKS::contains)) {
                return true;
            }
            queue.addAll(calls.sameClassCalls);
        }
        return false;
    }

    private String signature(Method method) {
        return method.getName() + Type.getMethodDescriptor(method);
    }

    private static final class MethodCalls {
        private final Set<String> calledNames = new HashSet<>();
        private final Set<String> sameClassCalls = new HashSet<>();
    }
}
