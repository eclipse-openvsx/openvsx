import { expect, Page } from "@playwright/test";
import { test } from "../../configs/smoke-test.options";

test.describe('OpenVSX', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle');
  });

  test.afterEach(async ({ page }) => {
    await expectNoErrorDialog(page);
  });

  test('shows the expected number of extensions', async ({ page, minNumberOfExtensions }) => {
    // read number of extensions from results text
    expect((await getResultCount(page))).toBeGreaterThanOrEqual(minNumberOfExtensions);

    // scroll down until we've seen at least the minimum expected number of extensions
    let seenNumberOfExtensions = await countExtensionsOnPage(page);
    while (seenNumberOfExtensions < minNumberOfExtensions) {
      await page.mouse.wheel(0, 500);
      seenNumberOfExtensions = await countExtensionsOnPage(page);
    }
    expect(seenNumberOfExtensions).toBeGreaterThanOrEqual(minNumberOfExtensions);
  });

  test('allows to search and select an extension, and read details about it and download it', async ({ page, extensionToOpen }) => {
    // search and open the extension
    await page.getByPlaceholder('Search').fill(extensionToOpen.searchTerm);
    await page.getByRole('link', { name: extensionToOpen.heading }).click();

    // check details of the extension
    await expect(page.getByRole('heading', { name: extensionToOpen.heading, exact: true })).toBeVisible();
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
    expect((await (await downloadPromise).path())).toBeTruthy();

    // check there were no errors during the download
    expect((await (await downloadPromise).failure())).toBeFalsy();
  });

  test('allows to search by search terms with changing number of results', async ({ page, searchTerms }) => {
    const initialResultCount = await getResultCount(page);
    for (const extensionToCheck of searchTerms) {
      // reset search term
      await page.getByPlaceholder('Search').clear();
      await waitForResultCount(page, count => count === initialResultCount);

      // search extension and wait for result count to change
      await page.getByPlaceholder('Search').fill(extensionToCheck);
      await waitForResultCount(page, count => count < initialResultCount);

      // check we found at least 1 extension with the search tearm
      const resultCount = await getResultCount(page);
      expect(resultCount).toBeGreaterThan(0);
    }
  });

  test('allows to search by category with changing number of results', async ({ page, categories }) => {
    const initialResultCount = await getResultCount(page);
    for (const category of categories) {
      // search extension and wait for result count to change
      await page.getByRole('combobox').filter({ hasText: 'All Categories' }).click();
      await page.getByRole('option', { name: category }).click();
      await waitForResultCount(page, count => count < initialResultCount);

      // check we found at least 1 extension with the search term
      const resultCount = await getResultCount(page);
      expect(resultCount).toBeGreaterThan(0);

      // switch back to main category
      await page.getByRole('combobox').filter({ hasText: category }).click();
      await page.getByRole('option', { name: 'All Categories' }).click();
      await waitForResultCount(page, count => count === initialResultCount);
    }
  });

});

async function getResultCount(page: Page): Promise<number> {
  const resultsText = await page.getByText('Results').innerText();
  return Number.parseInt(resultsText.split(' ')[0]);
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
