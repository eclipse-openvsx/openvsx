# Eclipse Open VSX Frontend Library Change Log

This change log covers only the frontend library (webui) of Open VSX.

## [next] (unreleased)

### Dependencies

- Bump picomatch from `4.0.3` to `4.0.4` ([#1720](https://github.com/eclipse/openvsx/pull/1720))

## [v0.19.0] (Mar. 2026)

### Added

- Add support for `positron` engine in the extension detail view and filter out unknown engines ([#1689](https://github.com/eclipse/openvsx/pull/1689))

### Changed

- Switch from webpack to vite for building the bundle ([#1399](https://github.com/eclipse/openvsx/pull/1399))
- Add more target information and useful links to scan card ([#1650](https://github.com/eclipse/openvsx/pull/1650))
- Do not post a review when hitting enter anymore ([#1690](https://github.com/eclipse/openvsx/pull/1690))

### Fixed

- Check `Retry-After` http header when receiving `429` responses from the server ([#1637](https://github.com/eclipse/openvsx/pull/1637))
- Menu items link clicks now capture the whole menu item area ([#1598](https://github.com/eclipse/openvsx/pull/1598))
- Fix color of error links in `ErrorDialog` to make them visible ([#1712](https://github.com/eclipse/openvsx/pull/1712))

### Dependencies

- Bump @isaacs/brace-expansion from `5.0.0` to `5.0.1` ([#1638](https://github.com/eclipse/openvsx/pull/1638))
- Bump minimatch from to `3.1.5`, `9.0.9` and `10.2.4` respectively
- Bump tar from `7.5.9` to `7.5.11` ([#1678](https://github.com/eclipse/openvsx/pull/1678))
- Bump dompurify from `3.2.4` to `3.3.2` ([#1671](https://github.com/eclipse/openvsx/pull/1671))
- Bump flatten from `3.3.1` to `3.4.2` ([#1702](https://github.com/eclipse/openvsx/pull/1702))

## [v0.18.0] (Feb. 2026)

### Added

- Support removing reviews by admins ([#1403](https://github.com/eclipse/openvsx/pull/1403))
- Support for GitHub flavored markdown alerts ([#1535](https://github.com/eclipse/openvsx/pull/1535))
- Support customizing the publisher agreement name and contact email ([#1550](https://github.com/eclipse/openvsx/pull/1550))
- Display the unique identifier on the extension details page ([#1590](https://github.com/eclipse/openvsx/pull/1590))
- Add admin pages to edit / view data for dynamic rate limits ([#1569](https://github.com/eclipse/openvsx/pull/1569))
- Add ability to browse admin logs via the admin dashboard ([#1582](https://github.com/eclipse/openvsx/pull/1582))

### Changed

- Disable the automatic execution of lifecycle scripts by yarn ([#1546](https://github.com/eclipse/openvsx/pull/1546))

### Dependencies

- Upgrade `lodash` from `4.17.21` to `4.17.23` ([#1557](https://github.com/eclipse/openvsx/pull/1557))
- Upgrade `react-router` from `6.23.1` to `6.30.3` ([#1532](https://github.com/eclipse/openvsx/pull/1532))
- Upgrade `react-router-dom` from `6.23.1` to `6.30.3`
- Upgrade `@playwright/test` from `1.57.0` to `1.58.0`

## [v0.17.1] (Jan. 2026)

### Dependencies

- Upgrade `rimraf` from `6.0.1` to `6.1.2`
- Upgrade `@playwright/test` from `1.49.0` to `1.55.1`
- Upgrade `mocha` from `10.8.2` to `11.7.5`
- Upgrade `ts-mocha` from `10.0.0` to `11.1.0`
- Add `ts-node` version `10.9.2`
