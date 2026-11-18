import { expect, Locator, Page } from '@playwright/test';
import { SmokeTestOptions, test } from '../../configs/smoke-test.options';

test.describe('OpenVSX', () => {
    test.beforeEach(async ({ page }) => {
        // The search page carries the result count, the extension grid and the
        // category sidebar, and hosts a single search field (the hero on `/` and
        // the nav field share a placeholder, so `/` has two).
        await page.goto('/search');
        await page.waitForLoadState('networkidle');
        await expect(page.getByText(/extensions found/)).toBeVisible();
    });

    test.afterEach(async ({ page }) => {
        await expectNoErrorDialog(page);
    });

    test('shows the expected number of extensions', async ({ page, minNumberOfExtensions }) => {
        // read number of extensions from results text
        expect(await getResultCount(page)).toBeGreaterThanOrEqual(minNumberOfExtensions);

        // scroll down until we've seen at least the minimum expected number of extensions
        let seenNumberOfExtensions = await countExtensionsOnPage(page);
        while (seenNumberOfExtensions < minNumberOfExtensions) {
            await page.mouse.wheel(0, 500);
            seenNumberOfExtensions = await countExtensionsOnPage(page);
        }
        expect(seenNumberOfExtensions).toBeGreaterThanOrEqual(minNumberOfExtensions);
    });

    test('allows to search and select an extension, and read details about it and download it', async ({
        page,
        extensionToOpen
    }) => {
        await openExtension(page, extensionToOpen);

        // check details of the extension
        await expect(page.getByRole('tab', { name: 'Overview' })).toBeVisible();
        await expect(page.getByRole('link', { name: 'Homepage' })).toBeVisible();
        await expect(page.getByRole('link', { name: 'Repository' })).toBeVisible();
        await expect(page.getByText('K downloads')).toBeVisible();

        // check tabs of the extension
        await expect(page.getByRole('tab', { name: 'Changes' })).toBeVisible();
        await expect(page.getByRole('tab', { name: 'Ratings & Reviews' })).toBeVisible();

        // check version switcher of the extension
        expect(await page.getByLabel('Version').getByRole('option').count()).toBeGreaterThan(0);

        // check download succeeded
        const downloadPromise = page.waitForEvent('download');
        await page.getByRole('link', { name: 'Download' }).click();
        expect(await (await downloadPromise).path()).toBeTruthy();

        // check there were no errors during the download
        expect(await (await downloadPromise).failure()).toBeFalsy();
    });

    test('allows to switch to an older version of an extension and back', async ({ page, extensionToOpen }) => {
        await openExtension(page, extensionToOpen);
        const extensionUrl = page.url();

        // the latest version is listed first; the older ones can load after it
        const versionPicker = page.getByLabel('Version');
        const versionOptions = versionPicker.getByRole('option');
        await expect.poll(() => versionOptions.count()).toBeGreaterThan(1);
        const [latest, older] = await versionOptions.evaluateAll(options =>
            options.map(option => (option as HTMLOptionElement).value)
        );
        await expect(versionPicker).toHaveValue(latest);
        await expect(downloadLink(page)).toHaveAttribute('href', versionFileUrl(latest));

        await versionPicker.selectOption(older);
        await expect(page).toHaveURL(`${extensionUrl}/${older}`);
        await expect(versionPicker).toHaveValue(older);
        await expect(downloadLink(page)).toHaveAttribute('href', versionFileUrl(older));

        await versionPicker.selectOption(latest);
        await expect(page).toHaveURL(`${extensionUrl}/${latest}`);
        await expect(versionPicker).toHaveValue(latest);
        await expect(downloadLink(page)).toHaveAttribute('href', versionFileUrl(latest));
    });

    test('allows to search by search terms with changing number of results', async ({ page, searchTerms }) => {
        const initialResultCount = await getResultCount(page);
        for (const extensionToCheck of searchTerms) {
            // reset search term
            await searchField(page).clear();
            await waitForResultCount(page, count => count === initialResultCount);

            // search extension and wait for result count to change
            await searchField(page).fill(extensionToCheck);
            await waitForResultCount(page, count => count < initialResultCount);

            // check we found at least 1 extension with the search tearm
            const resultCount = await getResultCount(page);
            expect(resultCount).toBeGreaterThan(0);
        }
    });

    test('allows to search by category with changing number of results', async ({ page, categories }) => {
        const initialResultCount = await getResultCount(page);
        for (const category of categories) {
            // pick the category from the sidebar and wait for the result count to drop
            await categoryNav(page).getByRole('button', { name: category, exact: true }).click();
            await waitForResultCount(page, count => count < initialResultCount);

            // check we found at least 1 extension with the search term
            const resultCount = await getResultCount(page);
            expect(resultCount).toBeGreaterThan(0);

            // switch back to all categories
            await categoryNav(page).getByRole('button', { name: 'All categories', exact: true }).click();
            await waitForResultCount(page, count => count === initialResultCount);
        }
    });
});

test.describe('OpenVSX home page', () => {
    test.beforeEach(async ({ page }) => {
        await page.goto('/');
        await page.waitForLoadState('networkidle');
    });

    test.afterEach(async ({ page }) => {
        await expectNoErrorDialog(page);
    });

    test('allows to search from the home page hero', async ({ page, extensionToOpen }) => {
        // the landing page's own search field; its aria-label sets it apart from the nav field
        const hero = page.getByRole('textbox', { name: 'Search extensions' });
        await expect(hero).toBeVisible();

        // typing a query morphs the hero into the search page and filters the results
        await hero.fill(extensionToOpen.searchTerm);
        await expect(page).toHaveURL(/\/search\?/);
        await expect(page.getByRole('link', { name: extensionToOpen.heading })).toBeVisible();
    });
});

/** The extension search field (nav bar on the search page). */
function searchField(page: Page): Locator {
    return page.getByPlaceholder(/search extensions/i);
}

/** Searches for the extension and opens its page. */
async function openExtension(page: Page, extension: SmokeTestOptions['extensionToOpen']): Promise<void> {
    // the result can be listed before the search applies, and the grid re-renders under the click
    const searched = page.waitForResponse(
        response =>
            response.url().includes('/api/-/search') &&
            response.url().includes(encodeURIComponent(extension.searchTerm))
    );
    await searchField(page).fill(extension.searchTerm);
    await searched;
    await page.getByRole('link', { name: extension.heading }).click();
    await expect(page.getByRole('heading', { name: extension.heading, exact: true })).toBeVisible();
}

/** The download button on an extension's page. */
function downloadLink(page: Page): Locator {
    return page.getByRole('link', { name: 'Download' });
}

/** Matches the URL of a file of the given version, e.g. `/api/DotJoshJohnson/xml/2.5.0/file/…`. */
function versionFileUrl(version: string): RegExp {
    return new RegExp(`/${version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}/file/`);
}

/** The category sidebar, whose entries are buttons labelled by category name. */
function categoryNav(page: Page): Locator {
    return page.getByRole('navigation', { name: 'Categories' });
}

async function getResultCount(page: Page): Promise<number> {
    // e.g. "16,647 extensions found" — strip the thousands separators and the label
    const resultsText = await page.getByText(/extensions found/).innerText();
    return Number.parseInt(resultsText.replace(/\D/g, ''), 10);
}

function countExtensionsOnPage(page: Page): Promise<number> {
    return page.locator('.MuiPaper-root.MuiPaper-elevation').count();
}

async function waitForResultCount(page: Page, predicate: (count: number) => boolean, timeout = 10000) {
    const startTime = Date.now();

    let currentCount;
    do {
        currentCount = await getResultCount(page);
        if (predicate(currentCount)) {
            return;
        }
        await page.waitForTimeout(500);
    } while (Date.now() - startTime < timeout);

    throw new Error(`Timeout reached: Condition not met with any count`);
}

async function expectNoErrorDialog(page: Page): Promise<void> {
    const errorDialog = page.locator('h2', { hasText: 'Error' });
    return await expect(errorDialog).toHaveCount(0);
}
