# Eclipse Open VSX Frontend Library Change Log

This change log covers only the frontend library (webui) of Open VSX.

## [next] (unreleased)

### Added

- Add a weekly downloads card to the extension detail page, shown only when the registry reports download analytics as enabled: the last 7 days' downloads, a sparkline of the weekly totals for the year behind it, and the period the headline covers. Hovering moves a marker line and reads out that week instead, and the card shows a skeleton in the same shape while the series loads

## [v1.2.0] (10/09/2026)

### Added

- Add a `/publish` page and turn the navbar's Publish button into a drop target: a file drag anywhere in the app turns the button into a drop area, and every `.vsix` package dropped on it — or on the publish page's own drop area — is queued and uploaded straight away, with no confirmation dialog. The page shows the queue as a line of extension cards — a skeleton while a package uploads, the real card once the registry accepts it, labelled with whatever the registry did with it — and keeps polling anything left under review or still missing its icon
- Export `PublishButton`, which carries the publish link, its `p` shortcut and the app's `.vsix` drop target in one component, so a deployment with its own menu content keeps drag-and-drop publishing
- Add a "Data Consistency" page to the admin dashboard (#1622): a live overview of every registered consistency check's finding count, with actions to refresh it and to fix findings one at a time or all at once
- Show a "Namespace not verified" state on an extension card when it can't be activated because its namespace already exists in a referenced external gallery and hasn't been verified, in both the "My Extensions" and namespace member extension lists. The card keeps its colour and takes a warning-toned frame and icon, since this is the publisher's to fix rather than an extension that is simply switched off
- Show a warning notice with a claim action wherever an unverified namespace is holding something back — the extension settings page when the extension has a namespace ownership conflict, and the namespace settings page for any unverified namespace — making clear the namespace must be claimed (verified) first. The action is the deployment's configured `elements.claimNamespace`, falling back to the namespace access documentation when none is configured. The admin dashboard's extension and namespace views show the same explanation without the claim action, since claiming is the publisher's action to take, not an admin's on someone else's behalf
- Add a "Search Index" page to the admin dashboard: which engine answers searches, how many extensions the index holds against how many it is built from, and a button to rebuild it where there is an index to rebuild. The two counts sit side by side because an index that has quietly lost entries answers searches perfectly well, just with nothing in them, which is indistinguishable from an empty registry unless both numbers are visible at once
- Mark a version that was published through a trusted publishing workflow with an icon next to "Published by" on the extension detail page, linking to the deployment's trusted publishing documentation. The default deployment points that link at the [Trusted Publishing](https://github.com/eclipse-openvsx/openvsx/wiki/Trusted-Publishing) wiki page

### Changed

- Redesign the user settings: a sidebar (a pill strip below `md`) navigating profile, access tokens, trusted publishers, extensions and rate limiting, with the user's namespaces listed alongside it and each one deep-linkable at `/user-settings/namespaces/:namespace`. Every view is rebuilt on the cards, grids, placeholders and theme shape tokens the rest of the site uses, and the namespace detail is now a single component shared with the admin dashboard
- Revoking a single access token asks for confirmation first, like every other destructive action
- Present the unsigned publisher agreement on the access tokens page as a warning notice with a link to sign it, instead of a plain paragraph
- Mark a removed extension version in red across its row — version number, status pill and timeline dot — and disable its delete action. Every version of a rejected extension is marked too, and "Latest" is reserved for extensions the registry actually serves, so one that is inactive or removed has no version marked latest
- Publish from the navbar's Publish button, the `p` shortcut and the settings Extensions tab through the new publish page, replacing the one-file-at-a-time publish dialog
- Add an `outlinedWarning` style to `MuiButton`, so a warning-toned outlined button follows the theme like the secondary and error ones instead of MUI's default half-opacity border
- **Breaking:** `elements.claimNamespace` now receives `{ namespace, extension?, sx? }` instead of `{ extension, sx? }`. The namespace settings page offers the same claim action and has no extension to pass, so implementations must read the namespace from `namespace` rather than `extension.namespace`
- Publishing goes through TanStack Query: `publishExtension` and `createNamespace` are mutation hooks (`usePublishExtension`, `useCreateNamespace`), and both service methods lose their `AbortController` parameter — writes are no longer aborted, and retries are the query client's to own. The user's extension list is a query too (`useUserExtensions`), read by the settings tab and by the publish queue as it follows a package, so a card appears in the list as soon as the registry has the package
- `ExtensionCard` accepts an `Extension` as well as a `SearchEntry`, and takes optional `to`, `linkState`, `overlay`, `footerStart`, `dimmed`, `tone` and `iconPending` props so other surfaces can reuse it instead of copying it
- Show an extension card's rating as one star and the score rather than five stars, and no rating at all on an extension nobody has reviewed. Five icons could not shrink, so beside a long download count the count spilled out of the card

### Added

- Add a `Pill` component — the clickable glass pill the category pills are built on, now usable on its own — and extract the `MonoSlash`, `glassSurface` and `compactControl` page primitives out of the search field, the pills and the search header
- Add `userLoading` to `MainContext`, so custom pages can tell "not logged in" from "still resolving the user"
- Add a `userMenuContent` slot to `PageSettings.elements`: extra entries for the logged-in account menu, rendered above the admin entry. The slot receives a `MenuEntry` component to build entries with, so each entry is styled by the menu it appears in — the desktop and mobile menus style theirs differently, and a consumer cannot match both on its own
- Add an `adminPages` slot to `PageSettings.elements`: extra admin dashboard pages, each declaring a name, icon, optional description and optional category, and each appearing in the side panel, as a card on the dashboard overview and as a route. Contributions are additive — a category name matching a built-in group appends to it, and a page whose path would shadow a built-in one is ignored
- Widen the published API for consumers building their own pages: the request layer (`sendRequest`, `sendNonRetriableRequest`, `ErrorResponse`, `controllerFromSignal`), `MainContext`, `AppProviders`, `NotFound`, `createDefaultTheme` with the `MONO_FONT`/`NAVBAR_HEIGHT` tokens, the `createRoute`/`createAbsoluteURL`/`addQuery`/`formatCompactNumber`/`toRelativeTime` utils, the `useDebouncedCallback` and `useGridCursor` hooks, the navbar-chrome, search-focus and page-search-bar hooks, the category icon helpers, `ExtensionDetailRoutes`, and the `itemIcon`/`MenuItemText` building blocks for `userMenuContent` entries

### Changed

- Rename `ScrollToTop` to `ScrollRestoration`, matching what it does on back/forward navigation
- Rename the extension tint context to `navbar-chrome-context` and add a second channel to it: a page with sections pinned under the navbar can extend the navbar's blur fan down to back them (`useExtendNavbarBlur`)
- Give Popover and Autocomplete popups the same floating-paper treatment as the other menus, and stop Popovers locking body scroll — the lock jumps the scroll position on mobile and shifts the pinned chrome

### Fixed

- Fix a React warning ("Received `true` for a non-boolean attribute `notched`") from the admin dashboard's publisher role filter, whose custom `InputBase` doesn't consume the `notched` prop MUI's `Select` injects for the (unused) outlined variant
- Fix the admin dashboard Scan tab getting stuck on the loading spinner after switching tabs, even though the new tab's data had already loaded successfully
- Fix the page jumping to the top whenever a menu, select or dialog opens.
- Fix the extension detail page's download menu so each target-platform option is clickable across its whole row, not just its text: the option was an inline link nested inside a non-interactive menu item, rather than the menu item itself being the link
- Fix `sendRequest` re-enabling fetch-retry's own retries for the request that follows a 429 wait, because the recursive call didn't forward the original `retry` flag. A `sendStrictRequest`/`sendNonRetriableRequest` call that hit a 429 could end up having its follow-up request retried twice - once by fetch-retry and once by the query client
- Fix the create-namespace dialog acting on Enter when its button is disabled: an empty or over-long name was submitted anyway, and a held key sent the request more than once
- Fix `isError` counting an empty `error` string as an error, and make it and `isSuccess` answer a `null` response body instead of throwing on it (`typeof null === 'object'`). The registry aggregates per-version outcomes when deleting extension versions, so a delete where every version succeeded answered with an empty error next to the success message - which raised an error dialog with nothing in it

### Dependencies

- Remove the `react-dropzone` dependency; the publish page and the navbar's drop target handle their own drag events, and nothing else imports it
- Remove nine dependencies that nothing imports: `clsx`, `prop-types` and `punycode` from the runtime dependencies, and `@types/d3-scale`, `@types/d3-shape`, `@types/prop-types`, `@types/punycode`, `@types/react-transition-group` and `ts-node` from the development ones. All but `@types/punycode` and `ts-node` stay in the tree through MUI or the URL parsers that actually use them, so only those two leave the install
- Bump express from `4.22.1` to `5.2.1` and `@types/express` from `4.17` to `5.0`. Express 4 caps `qs` at `~6.14.0`, which is why a `qs` resolution was needed to move past it; Express 5 declares `^6.14.0`, so that resolution is gone and `qs` resolves to `6.16.0` on its own. The standalone frontend server's catch-all route is now `/{*splat}`, which is how Express 5 spells the `*` it no longer accepts
- Bump @humanfs/node from 0.16.6 to 0.16.8
- Bump js-yaml from 4.3.1 to 4.3.2

## [v1.1.2] (20/08/2026)

### Added

- Add a "Forget user" action to the admin dashboard's publisher details, calling the GDPR erasure endpoint

### Fixed

- Fix the extension overview sidebar overlapping the readme content at narrower ("tablet") window widths: there are now only mobile and desktop layouts, with tablet widths using the desktop layout ([#2068](https://github.com/eclipse-openvsx/openvsx/issues/2068))

## [v1.1.1] (09/08/2026)

### Fixed

- Fix the WebUI build failing on s390x and ppc64le: use postcss instead of lightningcss (no ppc64le binary) for CSS, and bump vite for the Rolldown big-endian sourcemap fix ([#2051](https://github.com/eclipse-openvsx/openvsx/issues/2051))
- Fix the admin publisher revoke dialog prompting for an Eclipse login even when the target publisher's agreement status is `none` or could not be determined (nothing confirmed to revoke)

### Dependencies

- Remove the unused `@mui/base` dependency; it is deprecated upstream (replaced by `@base-ui/react`) and nothing in the codebase imports from it
- Bump socks from `2.8.3` to `2.8.9`
- Bump ip-address from `9.0.5` to `10.4.0`
- Bump postcss from `8.5.22` to `8.5.25`
- Bump vite from `8.1.5` to `8.2.1`
- Bump dompurify from `3.4.12` to `3.4.13`
- Bump js-yaml from `4.3.0` to `4.3.1`
- Bump react-router from `7.18.1` to `7.18.2`

## [v1.1.0] (02/08/2026)

### Added

- Add a home page with hero search, popular searches, a category browser, curated extension rows and get-involved cards
- Add a dedicated search page under `/search` with query, category, sort field and sort order synced to the URL
- Add global keyboard shortcuts
- Add keyboard navigation of search results: `↑`/`↓`
- Add a structured footer
- Add scroll-to-top on forward navigation
- Support searching users and managing their roles in the admin dashboard ([#1847](https://github.com/eclipse-openvsx/openvsx/pull/1847))
- Added an extension details page to admin dashboard and user settings ([#1939](https://github.com/eclipse-openvsx/openvsx/pull/1939))

### Changed

- Redesign the web UI: new navbar with integrated search field, new theme, extension cards, category pills and page layout
- Consolidate the app-wide providers into a single `AppProviders`; keyboard shortcuts and search now wrap every route, including the admin dashboard
- Improve accessibility: visible focus outlines on interactive controls
- Morph the hero search into the navbar search field using the View Transitions API
- Token display in generate-token dialog now uses a masked input with show/hide toggle and copy button ([#1966](https://github.com/eclipse-openvsx/openvsx/pull/1966)
- Migrate admin dashboard to use `@tanstack/react-query` ([#1917](https://github.com/eclipse-openvsx/openvsx/pull/1917)
- Replace formatting from `stylistic` with `prettier` ([#1916](https://github.com/eclipse-openvsx/openvsx/pull/1916))
- Upgrade to vite 8+ and disable manual chunks for bundling ([#1989](https://github.com/eclipse-openvsx/openvsx/pull/1989))
- Upgrade to react-router 7: import everything from `react-router` instead of `react-router-dom`. Consumers passing `additionalRoutes` must upgrade to react-router 7 as well

### Fixed

- Refresh the extension version list in the delete views when a delete fails with a conflict
- Keep the hero-to-navbar search morph working under react-router 7, which wraps location updates in `React.startTransition`

### Dependencies

- Bump brace-expansion from `1.1.11` to `1.1.16` ([#1981](https://github.com/eclipse-openvsx/openvsx/pull/1981))
- Bump brace-expansion from `2.0.2` to `2.1.2` ([#1944](https://github.com/eclipse-openvsx/openvsx/pull/1944))
- Bump brace-expansion from `5.0.3` to `5.0.7` ([#1944](https://github.com/eclipse-openvsx/openvsx/pull/1944))
- Bump tar from `7.5.16` to `7.5.22` ([#1994](https://github.com/eclipse-openvsx/openvsx/pull/1994))
- Bump js-yaml from `4.2.0` to `4.3.0` ([#1976](https://github.com/eclipse-openvsx/openvsx/pull/1976))
- Bump dompurify from `3.4.11` to `3.4.12` ([#1984](https://github.com/eclipse-openvsx/openvsx/pull/1984))
- Bump react-router from `6.30.4` to `7.18.1`
- Remove react-router-dom and its stale `@types/react-router-dom` v5 typings

## [v1.0.2] (23/06/2026)

### Changed

- Migrate unit test framework from mocha to vitest

### Dependencies

- Bump tar from `7.5.11` to `7.5.16` ([#1907](https://github.com/eclipse-openvsx/openvsx/pull/1907))
- Bump js-yaml from `4.1.1` to `4.2.0` ([#1908](https://github.com/eclipse-openvsx/openvsx/pull/1908))
- Bump vite from `7.3.2` to `7.3.5` ([#1905](https://github.com/eclipse-openvsx/openvsx/pull/1905))
- Bump dompurify from `3.4.0` to `3.4.9` ([#1906](https://github.com/eclipse-openvsx/openvsx/pull/1906))
- Bump markdown-it from `14.1.1` to `14.2.0` ([#1904](https://github.com/eclipse-openvsx/openvsx/pull/1904))
- Bump @babel/core from `7.29.0` to `7.29.7` ([#1909](https://github.com/eclipse-openvsx/openvsx/pull/1909))

## [v1.0.1] (11/06/2026)

### Dependencies

- Bump qs from `6.15.1` to `6.15.2` ([#1897](https://github.com/eclipse-openvsx/openvsx/pull/1897))
- Bump react-router and react-router-dom from `6.30.3` to `6.30.4` ([#1897](https://github.com/eclipse-openvsx/openvsx/pull/1897))

## [v1.0.0] (28/05/2026)

### Added

- Add a settings page in the admin dashboard and support putting the registry in read-only mode ([#1835](https://github.com/eclipse-openvsx/openvsx/pull/1835))

## [v0.20.4] (22/05/2026)

### Added

- Add support to retry failed scanner jobs in the admin dashboard ([#1832](https://github.com/eclipse-openvsx/openvsx/pull/1832))
- Display non-terminal scanner jobs in the scan card ([#1836](https://github.com/eclipse-openvsx/openvsx/pull/1836))

## [v0.20.3] (08/05/2026)

### Changed

- Disabled max-width setting for admin dashboard pages to let content grow as needed ([#1809](https://github.com/eclipse-openvsx/openvsx/pull/1809))

### Fixed

- Fix admin dashboard breadcrumbs not being URL-decoded due to #1782 ([#1806](https://github.com/eclipse-openvsx/openvsx/pull/1806))

## [v0.20.2] (28/04/2026)

### Added

- Persist open/closed state for the admin dashboard side-panel ([#1782](https://github.com/eclipse-openvsx/openvsx/pull/1782))
- Add support to delete empty namespaces in the admin dashboard ([#1773](https://github.com/eclipse/openvsx/pull/1773))
- Add support to link to an external URL for remote scanner results ([#1789](https://github.com/eclipse/openvsx/pull/1789))

### Changed

- Refactor admin dashboard side-panel implementation ([#1782](https://github.com/eclipse-openvsx/openvsx/pull/1782))
- Extract breadcrumbs into its own component in the admin dashboard ([#1782](https://github.com/eclipse-openvsx/openvsx/pull/1782))
- Revamp admin dashboard welcome page ([#1782](https://github.com/eclipse-openvsx/openvsx/pull/1782))

### Fixed

- Fix admin dashboard breadcrumbs not being URL-decoded ([#1781](https://github.com/eclipse-openvsx/openvsx/pull/1781))

### Dependencies

- Bump react-avatar-editor from `13.0.2` to `15.1.0` ([#1787](https://github.com/eclipse/openvsx/pull/1787))
- Bump postcss from `8.5.6` to `8.5.10` ([#1793](https://github.com/eclipse/openvsx/pull/1793))

## [v0.20.1] (20/04/2026)

### Changed

- Reduce size of main bundle by splitting it up in separate chunks that are dynamically loaded ([#1750](https://github.com/eclipse/openvsx/pull/1750))
- Display download link also for error'ed security scans on scan card ([#1778](https://github.com/eclipse/openvsx/pull/1778))

### Fixed

- Refactor extension detail page to avoid unnecessary data reloads ([#1760](https://github.com/eclipse/openvsx/pull/1760))
- Fix refreshing of extension readme when switching versions ([#1760](https://github.com/eclipse/openvsx/pull/1760))
- Fix namespace / publisher column on extension detail page ([#1765](https://github.com/eclipse/openvsx/pull/1765))

### Dependencies

- Bump dompurify from `3.3.2` to `3.4.0` ([#1768](https://github.com/eclipse/openvsx/pull/1768))

## [v0.20.0] (13/04/2026)

### Added

- Support rate-limit token management in admin dashboard ([#1698](https://github.com/eclipse/openvsx/pull/1698))
- Support customer membership management in admin dashboard ([#1698](https://github.com/eclipse/openvsx/pull/1698))
- Support displaying usage stats for customer members ([#1698](https://github.com/eclipse/openvsx/pull/1698))
- Calculate and display daily p95 usage stats ([#1698](https://github.com/eclipse/openvsx/pull/1698))

### Fixed

- Avoid loading extension data multiple times on the extension detail page ([#1756](https://github.com/eclipse/openvsx/pull/1756))

## [v0.19.1] (Apr. 2026)

### Fixed

- Display usage stats tick labels in UTC timezone ([#1747](https://github.com/eclipse/openvsx/pull/1747))

### Dependencies

- Bump picomatch from `4.0.3` to `4.0.4` ([#1720](https://github.com/eclipse/openvsx/pull/1720))
- Bump yaml from `1.20.2` to `1.20.3` ([#1717](https://github.com/eclipse/openvsx/pull/1717))
- Bump lodash from `4.17.23` to `4.18.1` ([#1742](https://github.com/eclipse/openvsx/pull/1742))
- Bump vite from `7.3.1` to `7.3.2` ([#1744](https://github.com/eclipse/openvsx/pull/1744))

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
