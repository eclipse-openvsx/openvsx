/********************************************************************************
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
 ********************************************************************************/
package org.eclipse.openvsx.web;

import java.util.Locale;

/**
 * The keys a response is tagged with, so that a CDN in front of this registry can be told what to
 * drop when something changes, rather than being given a list of URLs.
 * <p>
 * One published version changes the extension JSON, its target-platform and version variants, the
 * version lists, the gallery's view of it, the namespace it belongs to and its files - a different
 * URL each, several of them carrying query parameters. Naming the thing that changed instead of
 * every URL that shows it is what a surrogate key is for: a CDN that supports them (Fastly calls
 * them surrogate keys, Akamai and Cloudflare cache tags) drops every cached response carrying the
 * key, whatever its URL.
 * <p>
 * The vocabulary is small on purpose - it mirrors what {@code CacheService} already evicts
 * internally, which is extensions and namespaces, never single versions:
 * <ul>
 *     <li>{@code ns/<namespace>} - everything belonging to a namespace.</li>
 *     <li>{@code ext/<namespace>/<extension>} - everything about one extension.</li>
 * </ul>
 * An extension's responses carry both, so a namespace-wide change needs one key rather than a key
 * per extension in it.
 * <p>
 * Keys are lower-cased, because names are matched that way: {@code /api/RedHat/java} and
 * {@code /api/redhat/java} are the same extension and must be purged together, while the stored
 * name has whichever case it was published with. Names themselves cannot contain a space or a
 * slash (see {@code ExtensionValidator}), so neither the space that separates keys in the header
 * nor the slash inside one is ambiguous - though a purge call still has to URL-encode the slash.
 */
public final class SurrogateKey {

    public static final String HEADER = "Surrogate-Key";

    private SurrogateKey() {
    }

    /** The keys for a response about one namespace. */
    public static String namespace(String namespace) {
        return "ns/" + normalize(namespace);
    }

    /** The keys for a response about one extension: the extension itself, and its namespace. */
    public static String extension(String namespace, String extension) {
        return "ext/" + normalize(namespace) + "/" + normalize(extension) + " " + namespace(namespace);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
