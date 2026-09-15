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

/**
 * Labels of the currently rendered menu entries, in DOM order. A plain selector rather
 * than `getAllByRole`: jsdom 30.0.0 throws on the second `getComputedStyle` of an element
 * carrying a percentage `calc()` — MUI's menu paper has one — and role queries compute
 * styles per candidate to filter out inaccessible nodes.
 */
export function menuItemLabels(): string[] {
    return Array.from(document.querySelectorAll('[role="menuitem"]')).map(item => item.textContent ?? '');
}
