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

import { Browser, expect, FrameLocator, Locator, Page, Response } from '@playwright/test';
import { RegistryExtension } from './registry';

/**
 * code-server, driven through its Extensions view. Every step starts from a freshly loaded
 * window: VS Code only shows a changed extension version after a restart, and reloading the same
 * page restores the open view, which a click would then close.
 */
export class VSCode {
    private current?: Page;
    /** Windows already replaced by a newer one, with when that happened. */
    private retired: { window: Page; at: number }[] = [];

    constructor(
        private readonly browser: Browser,
        private readonly baseURL: string
    ) {}

    /** Searches the marketplace, returning the gallery's answer to the browser's own query. */
    async search(query: string): Promise<Response> {
        const page = await this.openExtensionsView();
        const answer = page.waitForResponse(response => isSearchQuery(response.request().postData()));
        await page.keyboard.type(query);
        return answer;
    }

    /** The search result for an extension, labelled with its name, version and publisher. */
    result(extension: RegistryExtension): Locator {
        const label = `^${escape(extension.displayName)}, .*Publisher ${escape(extension.publisher)}`;
        return this.page.getByRole('listitem', { name: new RegExp(label) }).first();
    }

    async openExtension(extension: RegistryExtension): Promise<ExtensionEditor> {
        await this.search(extension.displayName);
        await this.result(extension).click();
        return new ExtensionEditor(this.page);
    }

    /** The installed version of an extension, or undefined when it is not installed. */
    async installedVersion(extension: RegistryExtension): Promise<string | undefined> {
        const editor = await this.openExtension(extension);
        const installation = editor.sidebarSection('Installation');
        await expect(editor.installButton.or(installation).first()).toBeVisible();
        if (!(await installation.isVisible())) {
            return undefined;
        }
        return (await installation.innerText()).match(/Version\s+(\S+)/)?.[1];
    }

    /** Uninstalls an extension that an earlier run, or someone by hand, left installed. */
    async uninstallIfInstalled(extension: RegistryExtension): Promise<void> {
        if (!(await this.installedVersion(extension))) {
            return;
        }
        await new ExtensionEditor(this.page).uninstallButton.click();
        await expect.poll(() => this.installedVersion(extension), { timeout: 60000 }).toBeUndefined();
    }

    /**
     * Installs the newest release that "Install Specific Version..." offers below the installed one,
     * and returns its version. Asked from the window that installed the extension: a newly opened
     * one can report the version as not found.
     */
    async installOlderRelease(extension: RegistryExtension, installed: string): Promise<string> {
        await this.result(extension).click({ button: 'right' });
        // VS Code's menus ignore a press released too quickly
        await this.page.getByRole('menuitem', { name: 'Install Specific Version...' }).click({ delay: 100 });
        const releases = this.page.getByRole('option', { name: /^Release version / });
        await expect(releases.first()).toBeVisible();
        const labels = await releases.evaluateAll(options => options.map(option => option.getAttribute('aria-label')));
        const older = labels
            .map(label => label?.replace('Release version ', ''))
            .find(version => version !== installed);
        if (!older) {
            throw new Error(`VS Code offers no release of ${extension.id} other than ${installed}`);
        }
        await this.page.getByRole('option', { name: `Release version ${older}`, exact: true }).click();
        await expect(releases.first()).toBeHidden();
        return older;
    }

    async close(): Promise<void> {
        for (const { window } of this.retired) {
            await window.context().close();
        }
        this.retired = [];
        await this.current?.context().close();
    }

    private get page(): Page {
        if (!this.current) {
            throw new Error('VS Code is not open yet');
        }
        return this.current;
    }

    /** Opens VS Code in a new browser context, which also restarts code-server's extension host. */
    private async openExtensionsView(): Promise<Page> {
        if (this.current) {
            this.retired.push({ window: this.current, at: Date.now() });
        }
        await this.closeRetiredWindows();
        const context = await this.browser.newContext({ baseURL: this.baseURL, permissions: ['clipboard-read'] });
        this.current = await context.newPage();
        await this.current.goto('/');
        await this.current.getByRole('tab', { name: /^Extensions/ }).click();
        return this.current;
    }

    /**
     * Closes the windows replaced more than ten seconds ago. VS Code shows nothing reliable once an
     * install it asked code-server for is under way, and closing its window too early cancels it.
     */
    private async closeRetiredWindows(): Promise<void> {
        const expired = this.retired.filter(({ at }) => Date.now() - at > 10000);
        this.retired = this.retired.filter(retired => !expired.includes(retired));
        for (const { window } of expired) {
            await window.context().close();
        }
    }
}

/** The page VS Code shows for one extension. */
export class ExtensionEditor {
    readonly root: Locator;
    readonly header: Locator;
    readonly icon: Locator;
    readonly installButton: Locator;
    readonly uninstallButton: Locator;
    readonly preReleaseBadge: Locator;
    /** The contribution types listed under Features. */
    readonly features: Locator;
    /** The rendered README or changelog, whichever tab is shown. */
    readonly document: FrameLocator;

    constructor(private readonly page: Page) {
        this.root = page.locator('.extension-editor');
        this.header = this.root.locator('.header');
        this.icon = this.header.locator('img').first();
        this.installButton = this.header.getByRole('button', { name: 'Install', exact: true });
        this.uninstallButton = this.header.getByRole('button', { name: 'Uninstall' });
        this.preReleaseBadge = this.header.getByText('pre-release', { exact: true });
        this.features = this.root.getByRole('list', { name: 'Extension Features' });
        this.document = page
            .locator('iframe.webview')
            .filter({ visible: true })
            .contentFrame()
            .locator('#active-frame')
            .contentFrame();
    }

    async show(tab: 'Details' | 'Features' | 'Changelog' | 'Dependencies'): Promise<void> {
        await this.root.locator('.navbar').getByRole('button').filter({ hasText: tab }).click();
    }

    /** A section of the sidebar, such as Marketplace, Categories or Resources. */
    sidebarSection(title: string): Locator {
        const heading = this.root.locator('.additional-details-title').filter({ hasText: new RegExp(`^${title}$`) });
        return heading.locator('..');
    }

    /** A dependency listed under Dependencies, labelled with its name, version and publisher. */
    dependency(displayName: string): Locator {
        return this.root.getByRole('treeitem', { name: new RegExp(`^${escape(displayName)}, `) });
    }

    /** The URL a resource link opens: code-server asks first for another origin, and opens a window otherwise. */
    async openResource(name: 'Repository' | 'License' | 'Marketplace'): Promise<string> {
        const prompt = this.page.getByRole('dialog').filter({ hasText: 'open the external website' });
        // closed as soon as it opens, so none is left for the next link to be mistaken for
        const opened: string[] = [];
        const record = (window: Page) => {
            opened.push(window.url());
            void window.close();
        };
        this.page.context().on('page', record);
        try {
            await this.sidebarSection('Resources').getByText(name, { exact: true }).click();
            await expect.poll(async () => opened.length > 0 || (await prompt.isVisible())).toBe(true);
        } finally {
            this.page.context().off('page', record);
        }

        if (opened.length) {
            return opened[0];
        }
        // copied rather than read off the prompt, which shortens query strings
        await prompt.getByRole('button', { name: 'Copy' }).click();
        await expect(prompt).toBeHidden();
        return this.page.evaluate(() => navigator.clipboard.readText());
    }

    async install(): Promise<void> {
        await this.installButton.click();
        // VS Code only asks on the first install from a publisher
        const trust = this.page.getByRole('button', { name: 'Trust Publisher & Install' });
        await expect(trust.or(this.uninstallButton).first()).toBeVisible({ timeout: 60000 });
        if (await trust.isVisible()) {
            await trust.click();
        }
        await expect(this.uninstallButton).toBeVisible({ timeout: 60000 });
    }

    async installPreRelease(): Promise<void> {
        // VS Code's menus ignore a press released too quickly
        await this.header.getByRole('button', { name: 'More Actions...' }).first().click({ delay: 100 });
        await this.page.getByRole('menuitem', { name: 'Install Pre-Release Version' }).click({ delay: 100 });
        // asked only on the first install from a publisher, and nothing else shows the install started
        const trust = this.page.getByRole('button', { name: 'Trust Publisher & Install' });
        if (
            await trust.waitFor({ timeout: 5000 }).then(
                () => true,
                () => false
            )
        ) {
            await trust.click();
        }
    }

    async switchToRelease(): Promise<void> {
        await this.header.getByRole('button').filter({ hasText: 'Switch to Release Version' }).click();
    }

    async updateTo(version: string): Promise<void> {
        const update = this.header.getByRole('button', { name: `Update to v${version}` });
        await update.click();
        await expect(update).toBeHidden({ timeout: 60000 });
    }
}

function isSearchQuery(body: string | null): boolean {
    // a text (10) or category (5) criterion: the query the Extensions view sends when opened has neither
    return !!body && /"filterType":(5|10),/.test(body);
}

function escape(text: string): string {
    return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
