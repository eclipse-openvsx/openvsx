/******************************************************************************
 * Copyright (c) 2019 TypeFox and others
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
package org.eclipse.openvsx.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

public final class CollectionUtil {

    private CollectionUtil() {
    }

    public static <S, T> List<T> map(List<S> source, Function<? super S, ? extends T> function) {
        var result = new ArrayList<T>(source.size());
        for (var s : source) {
            var t = function.apply(s);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    public static <S, T> List<T> map(Iterable<S> source, Function<? super S, ? extends T> function) {
        var result = new ArrayList<T>();
        for (var s : source) {
            var t = function.apply(s);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    public static <S, T> T[] toArray(
            @Nullable List<S> source,
            Function<? super S, ? extends T> function,
            IntFunction<T[]> generator
    ) {
        if (source == null) {
            return generator.apply(0);
        }
        var result = generator.apply(source.size());
        for (var i = 0; i < source.size(); i++) {
            result[i] = function.apply(source.get(i));
        }
        return result;
    }

    /**
     * Splits a list into consecutive {@code subList} views of at most {@code size} elements.
     */
    public static <T> List<List<T>> partition(List<T> source, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive, got " + size);
        }
        var result = new ArrayList<List<T>>((source.size() + size - 1) / size);
        for (var from = 0; from < source.size(); from += size) {
            result.add(source.subList(from, Math.min(from + size, source.size())));
        }
        return result;
    }

    public static <T> List<T> limit(Iterable<T> source, int limit) {
        var result = new ArrayList<T>();
        var count = 0;
        for (var s : source) {
            result.add(s);
            count++;
            if (count == limit) {
                return result;
            }
        }
        return result;
    }

}
