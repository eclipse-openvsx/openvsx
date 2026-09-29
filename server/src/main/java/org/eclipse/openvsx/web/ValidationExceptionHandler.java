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
package org.eclipse.openvsx.web;

import java.lang.reflect.Method;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;

import org.eclipse.openvsx.json.ResultJson;

@RestControllerAdvice
public class ValidationExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, HandlerMethod handlerMethod) {
        try {
            ResolvableType resolvableType = ResolvableType.forMethodReturnType(handlerMethod.getMethod());

            // Get the first type argument (e.g. Foo from ResponseEntity<Foo>)
            Class<?> typeArg = resolvableType.getGeneric(0).resolve();
            if (typeArg != null && ResultJson.class.isAssignableFrom(typeArg)) {
                var errors = ex.getConstraintViolations().stream()
                        .map(cv -> Iterables.getLast(cv.getPropertyPath()) + ": " + cv.getMessage())
                        .collect(Collectors.joining(", "));

                Method staticMethod = typeArg.getMethod("error", String.class);
                var json = (ResultJson) staticMethod.invoke(null, errors);
                return new ResponseEntity<>(json, HttpStatus.BAD_REQUEST);
            }
        } catch (Exception _) {
        }

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty(
                "errors",
                ex.getConstraintViolations().stream()
                        .map(cv -> Iterables.getLast(cv.getPropertyPath()) + ": " + cv.getMessage())
                        .toList());
        return new ResponseEntity<>(problem, HttpStatus.BAD_REQUEST);
    }
}
