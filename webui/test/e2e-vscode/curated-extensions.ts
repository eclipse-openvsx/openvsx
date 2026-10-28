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

/**
 * The extensions every spec runs against. The tests read what to expect from the registry, so an
 * entry only needs to be published on every registry the suite targets (the local test
 * extensions, staging and open-vsx.org) with two or more releases, a universal build, a changelog,
 * a README heading and contributed settings.
 */
export const curatedExtensions = ['DotJoshJohnson.xml', 'redhat.vscode-yaml', 'usernamehw.errorlens'];

/**
 * Extensions with an extension pack or dependencies, which VS Code lists on their page. The specs
 * only open them: what they pull in is too large to install in a test.
 */
export const curatedExtensionsWithDependencies = ['vscjava.vscode-java-pack', 'vscjava.vscode-java-test'];

/**
 * Extensions published with pre-releases. A registry without one of them, as staging is for
 * GitLens, skips its test.
 */
export const curatedPreReleaseExtensions = ['eamodio.gitlens'];
