import { existsSync } from 'node:fs';
import path from 'node:path';
import { defineConfig, devices } from '@playwright/test';
import { SmokeTestOptions } from './smoke-test.options';

/** A registry the smoke tests can run against; `options` overrides the defaults where its data differs. */
interface Environment {
    baseURL: string;
    options?: Partial<SmokeTestOptions>;
}

const environments: Record<string, Environment> = {
    production: { baseURL: 'https://open-vsx.org' },
    staging: {
        baseURL: 'https://staging.open-vsx.org',
        // XML Tools has fewer than 1,000 downloads on staging
        options: { extensionToOpen: { searchTerm: 'npm-intellisense', heading: 'npm Intellisense' } }
    },
    local: { baseURL: 'http://localhost:3000' }
};

const envFile = path.resolve(__dirname, '..', '.env');
if (existsSync(envFile)) {
    process.loadEnvFile(envFile);
}

// empty counts as unset: CI passes unset workflow inputs as empty strings
const environmentName = process.env.OVSX_E2E_ENV || 'production';
const environment = environments[environmentName];
if (!environment) {
    throw new Error(
        `Unknown OVSX_E2E_ENV '${environmentName}', expected one of: ${Object.keys(environments).join(', ')}`
    );
}

const { OVSX_E2E_BASE_URL, OVSX_E2E_HTTP_USERNAME, OVSX_E2E_HTTP_PASSWORD } = process.env;

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig<SmokeTestOptions>({
    testDir: '../test/e2e',
    /* Run tests in files in parallel */
    fullyParallel: true,
    /* Fail the build on CI if you accidentally left test.only in the source code. */
    forbidOnly: !!process.env.CI,
    /* Retry on CI only */
    retries: process.env.CI ? 2 : 0,
    /* Opt out of parallel tests on CI. */
    workers: process.env.CI ? 1 : undefined,
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: 'html',
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
        /* Base URL to use in actions like `await page.goto('/')`. */
        baseURL: OVSX_E2E_BASE_URL || environment.baseURL,
        // for deployments behind HTTP basic auth
        httpCredentials:
            OVSX_E2E_HTTP_USERNAME && OVSX_E2E_HTTP_PASSWORD
                ? { username: OVSX_E2E_HTTP_USERNAME, password: OVSX_E2E_HTTP_PASSWORD }
                : undefined,
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        ...environment.options
    },

    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] }
        }
    ]
});
