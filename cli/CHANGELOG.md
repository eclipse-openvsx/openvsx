## Eclipse Open VSX Change Log

This change log covers only the command line interface (CLI) of Open VSX.

### [next] (unreleased)

#### Dependencies

- Bump picomatch from `2.3.1` to `2.3.2` ([#1719](https://github.com/eclipse/openvsx/pull/1719))
- Bump picomatch from `4.0.3` to `4.0.4`
- Bump brace-expansion from `1.1.12` to `1.1.13` ([#1725](https://github.com/eclipse/openvsx/pull/1725))

### [v0.10.10] (Mar. 2026)

#### Dependencies

- Bump ajv from `6.12.6` to `6.14.0` ([#1632](https://github.com/eclipse/openvsx/pull/1632))
- Bump ajv from `8.17.1` to `8.18.0`
- Bump tar from `7.5.7` to `7.5.11` ([#1681](https://github.com/eclipse/openvsx/pull/1681))
- Bump minimatch to `3.1.5`, `9.0.9` and `10.2.4`
- Bump underscore from `1.13.6` to `1.13.8` ([#1656](https://github.com/eclipse/openvsx/pull/1656))
- Bump flatted from `3.3.1` to `3.4.2` ([#1703](https://github.com/eclipse/openvsx/pull/1703))

### [v0.10.9] (Feb. 2026)

#### Fixes

- Support custom registry url including a subpath ([#1542](https://github.com/eclipse/openvsx/pull/1542))

#### Changes

- Move `ovsx` script to `bin` directory ([#1538](https://github.com/eclipse/openvsx/pull/1538))
- Disable the automatic execution of lifecycle scripts by yarn ([#1546](https://github.com/eclipse/openvsx/pull/1546))

#### Dependencies

- Bump lodash from 4.17.21 to 4.17.23 ([#1567](https://github.com/eclipse/openvsx/pull/1567))
- Bump tar from 6.2.1 to 7.5.7

### [v0.10.8] (Jan. 2026)

#### Dependencies

- Upgrade `jws` from `3.2.2` to `4.0.1` ([#1482](https://github.com/eclipse/openvsx/pull/1482))
- Upgrade `@vscode/vsce` from `3.2.1` to `3.7.1` ([#1482](https://github.com/eclipse/openvsx/pull/1482))

### [v0.10.7] (Nov. 2025)

#### Dependencies

- Upgrade `glob` from `10.3.10` to `10.5.0` ([#1423](https://github.com/eclipse/openvsx/pull/1423))
- Upgrade `js-yaml` from `4.1.0` to `4.1.1` ([#1417](https://github.com/eclipse/openvsx/pull/1417))

### [v0.10.6] (Sep. 2025)

#### Dependencies

- Upgrade `tar-fs` from `2.1.3` to `2.1.4` ([#1353](https://github.com/eclipse/openvsx/pull/1353))
- Upgrade `tmp` from `0.2.3` to `0.2.4` ([#1304](https://github.com/eclipse/openvsx/pull/1304))
- Upgrade `form-data` from `4.0.0` to `4.0.4` ([#1291](https://github.com/eclipse/openvsx/pull/1291))

### v0.10.5 (Jul. 2025)

#### Dependencies

- Upgrade `brace-expansion` from `2.0.1` to `2.0.2` ([#1273](https://github.com/eclipse/openvsx/pull/1273))
- Upgrade `brace-expansion` from `1.1.11` to `1.1.12` ([#1261](https://github.com/eclipse/openvsx/pull/1261))

### v0.10.4 (Jun. 2025)

#### Dependencies

- Upgrade `tar-fs` from `2.1.2` to `2.1.3` ([#1252](https://github.com/eclipse/openvsx/pull/1252))

### v0.10.3 (Jun. 2025)

#### Bug Fixes

- Use nullish coalescing ([#1233](https://github.com/eclipse/openvsx/pull/#1233))
- Move personal access token functionality to `pat.ts` ([#1225](https://github.com/eclipse/openvsx/pull/#1225))
- Reduce nested functions in `zip.ts` ([#1223](https://github.com/eclipse/openvsx/pull/#1223))
- Split command and options ([#1222](https://github.com/eclipse/openvsx/pull/#1222))
- Move PAT functionality from `util.ts` to `login.ts` ([#1220](https://github.com/eclipse/openvsx/pull/#1220))
- Expected the Promise rejection reason to be an Error ([#1197](https://github.com/eclipse/openvsx/pull/1197))

#### Dependencies

- Replace `yauzl` with `yauzl-promise` ([#1226](https://github.com/eclipse/openvsx/pull/1226))
- Upgrade `yarn` from `4.5.1` to `4.9.1` ([#1190](https://github.com/eclipse/openvsx/pull/1190))

### v0.10.2 (Apr. 2025)

#### Dependencies

- Upgrade `tar-fs` from `2.1.1` to `2.1.2` ([#1163](https://github.com/eclipse/openvsx/pull/1163))

### v0.10.1 (Nov. 2024)

#### Dependencies

- Upgrade `@vscode/vsce` from `3.1.0` to `3.2.1` ([#1047](https://github.com/eclipse/openvsx/pull/1047))

### v0.10.0 (Oct. 2024)

#### New Features

- Added `login` command to add a namespace to the list of known namespaces ([#1012](https://github.com/eclipse/openvsx/pull/1012))
- Added `logout` command to remove a namespace from the list of known namespaces ([#1012](https://github.com/eclipse/openvsx/pull/1012))
- Added CLI parameter `--packageVersion` to set the version of the provided VSIX packages ([#1013](https://github.com/eclipse/openvsx/pull/1013))

#### Dependencies

- Added dependency to `yauzl` ([#1012](https://github.com/eclipse/openvsx/pull/1012))

### [v0.9.5] (Sep. 2024)

#### Breaking Changes

- The minimum version of Node.js required is now `20` because of the newer `@vscode/vsce`

#### Dependencies

- Upgrade `@vscode/vsce` from `2.25.0` to `3.1.0` ([#994](https://github.com/eclipse/openvsx/pull/994))
- Upgrade `commander` from `6.1.0` to `6.2.1` ([#994](https://github.com/eclipse/openvsx/pull/994))
- Upgrade `tmp` from `0.2.1` to `0.2.3` ([#994](https://github.com/eclipse/openvsx/pull/994))

### [v0.9.4] (Sep. 2024)

#### Dependencies

- Upgrade `micromatch` from `4.0.5` to `4.0.8` ([#978](https://github.com/eclipse/openvsx/pull/978))

### [v0.9.2] (July 2024)

#### Bug Fixes

- Remove default universal for get operation ([#944](https://github.com/eclipse/openvsx/pull/944))

#### Dependencies

- Upgrade `braces` from `3.0.2` to `3.0.3` ([#953](https://github.com/eclipse/openvsx/pull/953))

### [v0.9.1] (Apr. 2024)

#### Bug Fixes

- Add `BufferEncoding` type to parameter ([#896](https://github.com/eclipse/openvsx/pull/896))
- Lower the minimum version of Node.js required from `18` to `16` ([microsoft/vscode-vsce#944](https://github.com/microsoft/vscode-vsce/issues/944))

#### Dependencies

- Upgrade `@vscode/vsce` from `2.24.0` to `2.25.0` ([#896](https://github.com/eclipse/openvsx/pull/896))
- Upgrade `tar` from `6.2.0` to `6.2.1` ([#893](https://github.com/eclipse/openvsx/pull/893))

### [v0.9.0] (Mar. 2024)

#### Breaking Changes

- The minimum version of Node.js required is now `18` because of the newer `@vscode/vsce`

#### Dependencies

- Upgrade `@vscode/vsce` from `2.19.0` to `2.24.0` ([#878](https://github.com/eclipse/openvsx/pull/878))
- Upgrade `semver` from `7.5.2` to `7.6.0` ([#878](https://github.com/eclipse/openvsx/pull/878))

### [v0.8.4] (Mar. 2024)

#### Dependencies

- Upgrade `follow-redirects` from `1.14.8` to `1.15.6` ([#869](https://github.com/eclipse/openvsx/pull/869))
- Upgrade `ip` from `2.0.0` to `2.0.1` ([#858](https://github.com/eclipse/openvsx/pull/858))

### [v0.8.3] (Aug. 2023)

#### Dependencies

- Upgrade `yarn` from `1.22.19` to `3.6.1` ([#793](https://github.com/eclipse/openvsx/pull/793))
- Upgrade `word-wrap` from `1.2.3` to `1.2.4` ([#787](https://github.com/eclipse/openvsx/pull/787))

### [v0.8.2] (July 2023)

#### Dependencies

- Upgrade `vcse` from `2.15.0` to `2.19.0` ([#775](https://github.com/eclipse/openvsx/pull/775))
- Upgrade `semver` from `5.7.1` to `7.5.2` ([#763](https://github.com/eclipse/openvsx/pull/763))

### [v0.8.1] (May. 2023)

#### Dependencies

- Added explicit dependency to `semver` ([#733](https://github.com/eclipse/openvsx/pull/733))

### [v0.8.0] (Jan. 2023)

#### New Features

- Added CLI parameter `--skip-duplicate` to  fail silently if version already exists on the marketplace ([#646](https://github.com/eclipse/openvsx/pull/646))

### [v0.7.1] (Dec. 2022)

#### Dependencies

- Migrated from deprecated `vcse` to `@vscode/vsce` ([#637](https://github.com/eclipse/openvsx/pull/637))

### [v0.7.0] (Dec. 2022)

#### New Features

- Added CLI parameter `--no-dependencies` to disable dependency detection ([#635](https://github.com/eclipse/openvsx/pull/635))

#### Dependencies

- Upgrade `vcse` from `2.7.0` to `2.15.0` ([#635](https://github.com/eclipse/openvsx/pull/635))

### [v0.6.0] (Nov. 2022)

#### New Features

- Added verify-pat command ([#624](https://github.com/eclipse/openvsx/pull/624))

#### Dependencies

- Upgrade `vcse` from `2.6.3` to `2.7.0` ([#621](https://github.com/eclipse/openvsx/pull/621))

### [v0.5.0] (Mar. 2022)

#### New Features

- Added CLI parameter `--target` to support target platforms ([#406](https://github.com/eclipse/openvsx/pull/406))

### [v0.4.0] (Feb. 2022)

#### New Features

- Added CLI parameter `--pre-release` to support pre-releases ([#410](https://github.com/eclipse/openvsx/pull/410))

### [v0.3.0] (Jan. 2022)

#### Breaking Changes

- The minimum version of Node.js required is now `14` because of the newer `vsce`

#### Dependencies

- Upgrade `vsce` from `1.97.0` to `2.6.3` ([#403](https://github.com/eclipse/openvsx/pull/403))
- Upgrade `follow-redirects`
- Upgrade `nth-check`

### [v0.2.1] (Sep. 2021)

#### New Features

- Also accept `LICENCE` files (alternative spelling to `LICENSE`) ([#307](https://github.com/eclipse/openvsx/pull/307))
- Remove `--web` option (it will now be inferred from the `package.json` file)

#### Dependencies

- Upgrade `vsce` from `1.93.0` to `1.97.0`

### [v0.2.0] (Jun. 2021)

#### New Features

- Added CLI parameter `--web` for web extensions ([#262](https://github.com/eclipse/openvsx/pull/262))

#### Bug Fixes

- Fixed handling of unknown commands ([#302](https://github.com/eclipse/openvsx/issues/302))

#### Dependencies

- Updated the `vsce` dependency from 1.84.0 to 1.93.0 ([#300](https://github.com/eclipse/openvsx/issues/300))
- Added explicit dependency to `tmp` ([#254](https://github.com/eclipse/openvsx/issues/254))

### [v0.1.0] (Apr. 2021)

First release of Open VSX with the Eclipse Foundation.
