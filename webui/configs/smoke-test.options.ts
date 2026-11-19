import { test as base } from '@playwright/test';

/**
 * Defines the configuration options for the smoke tests.
 */
export interface SmokeTestOptions {
    /**
     * Specifies the minimum number of extensions to be verified on the main page.
     */
    minNumberOfExtensions: number;
    /**
     * Configuration for testing searching, opening, and downloading a specific extension.
     * The chosen extension must have more than 1,000 downloads, multiple versions and a changelog.
     */
    extensionToOpen: {
        /** The search query used to find the extension in the registry. */
        searchTerm: string; //
        /** The expected heading of the extension in search results or its detail page. */
        heading: string;
    };
    /**
     * A list of search terms to be tested. Each term is expected to yield a result count
     * that is greater than 0 but less than the total number of extensions available.
     */
    searchTerms: string[];
    /**
     * Categories to be used for testing the 'search by category' feature.
     * Make sure there is at least one extension for each specified category.
     */
    categories: string[];
}

/**
 * Extends the base test fixture with smoke test-specific options, providing
 * a configurable testing environment for smoke tests.
 *
 * These fixtures can be customized in the `playwright.config.ts` file.
 * Refer to the Playwright documentation on fixtures and options for more details:
 * https://playwright.dev/docs/test-fixtures#fixtures-options
 */
export const test = base.extend<SmokeTestOptions>({
    minNumberOfExtensions: [16, { option: true }],
    searchTerms: [['DotJoshJohnson', 'python', 'java'], { option: true }],
    extensionToOpen: [
        {
            searchTerm: 'DotJoshJohnson',
            heading: 'XML Tools'
        },
        { option: true }
    ],
    categories: [['Formatters', 'Programming Languages', 'Snippets'], { option: true }] // Default categories for 'search by category' tests.
});
