# OpenVSX Smoke Tests

The OpenVSX Smoke Tests are designed to ensure that the most critical functionalities of an OpenVSX instance are operational.

The tests in [`smoke-test.spec.ts`](smoke-test.spec.ts) are designed to be executed against a local instance, a staging instance or the production instance at open-vsx.org.
By default, [`playwright.config.ts`](../../configs/playwright.config.ts) is configured to run against open-vsx.org directly.

## Setup

Begin by installing all necessary dependencies in the [`webui/`](../..) directory:

```bash
npm install --with-deps
```

## Running Tests

The smoke tests run against the webui and can be executed in various modes, each suitable for different stages of development and debugging:

- _Headless Mode_ (default): Use `yarn smoke-tests` in [`webui/`](../..), which runs tests in headless mode. This mode is faster and ideal if you are just interested in the results and for CI/CD environments.
- _Headed Mode_: If you want to observe the tests execution in a browser window, you can run them in headed mode using `yarn smoke-tests --headed`.
- _UI Mode_: Playwright provides a dedicated UI mode with `yarn smoke-tests --ui`. The UI mode provides a visual interactive interface to execute and step through tests.
- _Debugging_: For debugging, Playwright provides a `--debug` mode, which opens the Playwright Inspector for a more in-depth examination of test execution.

More detailed information on running tests can be found in the [Playwright documentation](https://playwright.dev/docs/running-tests) and the [command line options guide](https://playwright.dev/docs/test-cli).

## Customizing Test Execution

`OVSX_E2E_ENV` selects the registry the tests run against:

| `OVSX_E2E_ENV`         | Registry                     |
| ---------------------- | ---------------------------- |
| `production` (default) | https://open-vsx.org         |
| `staging`              | https://staging.open-vsx.org |
| `local`                | http://localhost:3000        |

```bash
OVSX_E2E_ENV=local yarn smoke-tests
```

`OVSX_E2E_BASE_URL` points the tests at any other deployment, and `OVSX_E2E_HTTP_USERNAME` and `OVSX_E2E_HTTP_PASSWORD` set HTTP basic auth credentials. They can also go in `webui/.env`, which is git-ignored.

To run against a local registry, start it from the repository root with `make dev-env`, `make server` and `make webui`, each in its own terminal, and publish the test extensions to it.

To add an environment, or to change the test data from [`smoke-test.options.ts`](../../configs/smoke-test.options.ts) for one, edit `environments` in [`playwright.config.ts`](../../configs/playwright.config.ts).
