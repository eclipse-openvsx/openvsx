## Eclipse Open VSX Change Log

This change log covers only the server of Open VSX.

Releases up to and including v1.2.0 were backfilled from the GitHub release notes. Those list every pull request of the repository, so they also contain web UI and CLI changes.

### [next] (unreleased)

#### Added

- Add fine-grained admin permissions ([#2269](https://github.com/eclipse-openvsx/openvsx/pull/2269))
- Configurable extension size limits with namespace overrides ([#2287](https://github.com/eclipse-openvsx/openvsx/pull/2287))
- Optimize extensionQuery ([#2161](https://github.com/eclipse-openvsx/openvsx/pull/2161))
- Harmonize license headers in the formatting pipeline ([#2265](https://github.com/eclipse-openvsx/openvsx/pull/2265))
- Allow generate-download-logs.sh to query a remote postgres ([#2239](https://github.com/eclipse-openvsx/openvsx/pull/2239))
- Backfill download events from access logs ([#2228](https://github.com/eclipse-openvsx/openvsx/pull/2228))
- Prompt anonymous visitors to log in on the reviews tab ([#2148](https://github.com/eclipse-openvsx/openvsx/pull/2148))
- Add a Caches page to the admin dashboard ([#2203](https://github.com/eclipse-openvsx/openvsx/pull/2203))
- Time-series download analytics ([#2134](https://github.com/eclipse-openvsx/openvsx/pull/2134))
- Give download analytics its own time-series database ([#2133](https://github.com/eclipse-openvsx/openvsx/pull/2133))
- Scope personal access tokens to a namespace or an extension, and add publishing-only tokens ([#2296](https://github.com/eclipse-openvsx/openvsx/pull/2296))

#### Changed

- Accept the personal access token via an Authorization: Bearer header ([#2209](https://github.com/eclipse-openvsx/openvsx/pull/2209))

#### Fixed

- Keep polling async scanners through transient errors, confirm an error status on a second poll before failing a scan, and treat every non-2xx scanner response as an error ([#2307](https://github.com/eclipse-openvsx/openvsx/pull/2307))
- Remove the max-override-size ceiling on extension size overrides ([#2297](https://github.com/eclipse-openvsx/openvsx/pull/2297))
- TP: store extra information ([#2283](https://github.com/eclipse-openvsx/openvsx/pull/2283))
- Lax the claim validation ([#2282](https://github.com/eclipse-openvsx/openvsx/pull/2282))
- Make extension_version.published_by_id NOT NULL ([#2276](https://github.com/eclipse-openvsx/openvsx/pull/2276))
- Un-deprecate extensions removed from the extension-control file ([#2261](https://github.com/eclipse-openvsx/openvsx/pull/2261))
- The listDependencies task fails with config cache ([#2254](https://github.com/eclipse-openvsx/openvsx/pull/2254))
- Report a namespace rename on the registry changes feed ([#2246](https://github.com/eclipse-openvsx/openvsx/pull/2246))
- Resolve extension resources for the target platform VS Code asks for ([#2183](https://github.com/eclipse-openvsx/openvsx/pull/2183))
- Scope the legacy token lookup to version-0 rows ([#2247](https://github.com/eclipse-openvsx/openvsx/pull/2247))
- Prevent duplicate active reviews from concurrent requests ([#2245](https://github.com/eclipse-openvsx/openvsx/pull/2245))
- Make extension control fail-safe and bound its HTTP timeouts ([#2242](https://github.com/eclipse-openvsx/openvsx/pull/2242))
- Delete upstream-proxied spool file even if the client aborts ([#2241](https://github.com/eclipse-openvsx/openvsx/pull/2241))
- Bump tika to 3.3.2 ([#2240](https://github.com/eclipse-openvsx/openvsx/pull/2240))
- Back the download analytics settled-cache with the shared cache infra ([#2235](https://github.com/eclipse-openvsx/openvsx/pull/2235))
- Sync Spotless and jbang-fmt on org.eclipse.jdt.core 3.47.0 ([#2233](https://github.com/eclipse-openvsx/openvsx/pull/2233))
- Repair and stop re-corrupting extensions' stale last-updated date ([#2231](https://github.com/eclipse-openvsx/openvsx/pull/2231))
- Evict the local caches by key prefix too, instead of guessing ([#2222](https://github.com/eclipse-openvsx/openvsx/pull/2222))
- Evict caches once the transaction has committed ([#2220](https://github.com/eclipse-openvsx/openvsx/pull/2220))
- Clear extension caches by pattern where Redis can, as intended ([#2219](https://github.com/eclipse-openvsx/openvsx/pull/2219))
- Bind the analytics durations so the reference can see them ([#2204](https://github.com/eclipse-openvsx/openvsx/pull/2204))
- Point the download log generator at the silo service ([#2207](https://github.com/eclipse-openvsx/openvsx/pull/2207))
- Code review bugs ([#2189](https://github.com/eclipse-openvsx/openvsx/pull/2189))

#### Dependencies

- Slight updates ([#2255](https://github.com/eclipse-openvsx/openvsx/pull/2255))
- Bump jOOQ 3.19.38 ([#2187](https://github.com/eclipse-openvsx/openvsx/pull/2187))

### [v1.2.0] (10/09/2026)

#### Added

- Trusted Publishing support (#1980) ([#2000](https://github.com/eclipse-openvsx/openvsx/pull/2000))
- Add a Data Consistency admin page (#1622) ([#2086](https://github.com/eclipse-openvsx/openvsx/pull/2086))
- Add unpublish command to cli ([#2032](https://github.com/eclipse-openvsx/openvsx/pull/2032))
- Check max extension size in publish before uploading ([#2087](https://github.com/eclipse-openvsx/openvsx/pull/2087))
- User settings overhaul ([#2091](https://github.com/eclipse-openvsx/openvsx/pull/2091))
- Verified check scanner ([#2074](https://github.com/eclipse-openvsx/openvsx/pull/2074))
- Store filesizes for each FileResource ([#2089](https://github.com/eclipse-openvsx/openvsx/pull/2089))
- Some user-settings ajustments ([#2097](https://github.com/eclipse-openvsx/openvsx/pull/2097))
- Add verify and verify-signature commands ([#2107](https://github.com/eclipse-openvsx/openvsx/pull/2107))
- Detach tokens from extension versions ([#2065](https://github.com/eclipse-openvsx/openvsx/pull/2065))
- Publish extensions overhaul ([#2098](https://github.com/eclipse-openvsx/openvsx/pull/2098))
- Add a search index page to the admin dashboard ([#2121](https://github.com/eclipse-openvsx/openvsx/pull/2121))
- Let one trusted publishing token publish every target platform ([#2125](https://github.com/eclipse-openvsx/openvsx/pull/2125))
- Limit TPT tokens to publishing only ([#2127](https://github.com/eclipse-openvsx/openvsx/pull/2127))
- Widen the webui library surface for consumers building their own pages ([#2130](https://github.com/eclipse-openvsx/openvsx/pull/2130))
- Add a show command ([#2153](https://github.com/eclipse-openvsx/openvsx/pull/2153))
- Add search and list commands ([#2154](https://github.com/eclipse-openvsx/openvsx/pull/2154))
- Forward vsce's --follow-symlinks, and document the pnpm story ([#2169](https://github.com/eclipse-openvsx/openvsx/pull/2169))
- Add an admin statistics dashboard ([#2156](https://github.com/eclipse-openvsx/openvsx/pull/2156))
- Show what a search result's score is made of in the admin dashboard ([#2179](https://github.com/eclipse-openvsx/openvsx/pull/2179))
- Keep access tokens valid across a pepper change ([#2162](https://github.com/eclipse-openvsx/openvsx/pull/2162))

#### Changed

- Delete migration_item rows once their job completes ([#2092](https://github.com/eclipse-openvsx/openvsx/pull/2092))
- Tolerate missing storage objects in the file-size backfill ([#2094](https://github.com/eclipse-openvsx/openvsx/pull/2094))
- Backfill file sizes per extension version instead of per file resource ([#2095](https://github.com/eclipse-openvsx/openvsx/pull/2095))
- Throttle migration item scheduling to avoid crowding out real jobs ([#2096](https://github.com/eclipse-openvsx/openvsx/pull/2096))
- Clarify namespace vs. per-publisher verification checks ([#2103](https://github.com/eclipse-openvsx/openvsx/pull/2103))
- Remove unused methods from RepositoryService and repository layer ([#2105](https://github.com/eclipse-openvsx/openvsx/pull/2105))
- Upgrade springdoc-openapi to 3.1.0 for Spring Data 4 compatibility ([#2112](https://github.com/eclipse-openvsx/openvsx/pull/2112))
- Narrow the Spring context for tests that don't need the full application ([#2113](https://github.com/eclipse-openvsx/openvsx/pull/2113))
- Rework prerelease limit SQL ([#2106](https://github.com/eclipse-openvsx/openvsx/pull/2106))
- Handle error results in sendStrictRequest ([#2109](https://github.com/eclipse-openvsx/openvsx/pull/2109))
- Trusted publishing changes ([#2114](https://github.com/eclipse-openvsx/openvsx/pull/2114))
- Remove unused methods from RepositoryService ([#2117](https://github.com/eclipse-openvsx/openvsx/pull/2117))
- Regenerate jooq classes ([#2119](https://github.com/eclipse-openvsx/openvsx/pull/2119))
- Replace the redis cluster in docker-compose with valkey ([#2140](https://github.com/eclipse-openvsx/openvsx/pull/2140))
- Update Spring Boot to 4.0.8 ([#2141](https://github.com/eclipse-openvsx/openvsx/pull/2141))
- Add a Makefile for common development tasks ([#2144](https://github.com/eclipse-openvsx/openvsx/pull/2144))
- Remove codeql and sonar workflows ([#2147](https://github.com/eclipse-openvsx/openvsx/pull/2147))
- Drop the qs resolution and nine unused dependencies ([#2146](https://github.com/eclipse-openvsx/openvsx/pull/2146))
- Add scripts to scrub and import partial db dumps ([#2110](https://github.com/eclipse-openvsx/openvsx/pull/2110))
- Call the access token hash secret a pepper, not a salt ([#2159](https://github.com/eclipse-openvsx/openvsx/pull/2159))
- Stop using EntityManager.merge to obtain managed entities ([#2150](https://github.com/eclipse-openvsx/openvsx/pull/2150))
- Replace the remaining EntityManager.merge updates with find ([#2151](https://github.com/eclipse-openvsx/openvsx/pull/2151))
- Update spotless ([#2176](https://github.com/eclipse-openvsx/openvsx/pull/2176))
- Generate configuration properties reference ([#2145](https://github.com/eclipse-openvsx/openvsx/pull/2145))
- Say that a comment should not recount the bug it fixed ([#2180](https://github.com/eclipse-openvsx/openvsx/pull/2180))
- Move the deployment documentation into the repository ([#2191](https://github.com/eclipse-openvsx/openvsx/pull/2191))
- Split the configuration reference out of the deployment guide ([#2192](https://github.com/eclipse-openvsx/openvsx/pull/2192))
- Check that doc/configuration.md documents the properties the server binds ([#2193](https://github.com/eclipse-openvsx/openvsx/pull/2193))
- Correct the properties the reference documents but the server does not bind ([#2194](https://github.com/eclipse-openvsx/openvsx/pull/2194))
- Document the configuration properties outside ovsx.scanning ([#2195](https://github.com/eclipse-openvsx/openvsx/pull/2195))
- Date the unreleased properties to 1.2.0 ([#2199](https://github.com/eclipse-openvsx/openvsx/pull/2199))

#### Fixed

- Configure /openvsx context path for OpenShift deployment and fix test extension publish paths ([#2080](https://github.com/eclipse-openvsx/openvsx/pull/2080))
- Get rid of most build warnings ([#2085](https://github.com/eclipse-openvsx/openvsx/pull/2085))
- Clear scan admin loading spinner once fresh data lands after a tab switch ([#2084](https://github.com/eclipse-openvsx/openvsx/pull/2084))
- Limit decompressed size when serving vscode/unpkg web resources ([#2060](https://github.com/eclipse-openvsx/openvsx/pull/2060))
- Keep scroll position on scroll lock ([#2101](https://github.com/eclipse-openvsx/openvsx/pull/2101))
- Guard scan recovery on startup with a Postgres advisory lock ([#2104](https://github.com/eclipse-openvsx/openvsx/pull/2104))
- Add /publish and /search to default frontend routes ([#2118](https://github.com/eclipse-openvsx/openvsx/pull/2118))
- Read the real namespace and extension ids out of a lateral join ([#2120](https://github.com/eclipse-openvsx/openvsx/pull/2120))
- Do not reference a one-time token from the version it published ([#2122](https://github.com/eclipse-openvsx/openvsx/pull/2122))
- Stop adding to the inverse versions collection when publishing ([#2123](https://github.com/eclipse-openvsx/openvsx/pull/2123))
- Keep a type on the rate limit headers under OpenAPI 3.1 ([#2124](https://github.com/eclipse-openvsx/openvsx/pull/2124))
- Stop treating an empty error string as an error result ([#2128](https://github.com/eclipse-openvsx/openvsx/pull/2128))
- Keep generated token bodies free of - and _ ([#2142](https://github.com/eclipse-openvsx/openvsx/pull/2142))
- Align the Kubernetes ConfigMap with the Spring Boot 4 properties ([#2158](https://github.com/eclipse-openvsx/openvsx/pull/2158))
- Default personal_access_token.version and .type ([#2166](https://github.com/eclipse-openvsx/openvsx/pull/2166))
- Stop signing a package from allocating several times its size ([#2170](https://github.com/eclipse-openvsx/openvsx/pull/2170))
- Serve the public API's CORS headers whatever ovsx.webui.url says ([#2173](https://github.com/eclipse-openvsx/openvsx/pull/2173))
- Stop the download count spilling out of the extension card ([#2178](https://github.com/eclipse-openvsx/openvsx/pull/2178))
- Make the documented docker compose command work ([#2182](https://github.com/eclipse-openvsx/openvsx/pull/2182))
- Resolve a download once the file is written, not when the response ends ([#2185](https://github.com/eclipse-openvsx/openvsx/pull/2185))
- Give up on a request that stops making progress ([#2186](https://github.com/eclipse-openvsx/openvsx/pull/2186))
- Apply the search boosts the text query is written to apply ([#2174](https://github.com/eclipse-openvsx/openvsx/pull/2174))
- Weigh download counts on a log scale ([#2175](https://github.com/eclipse-openvsx/openvsx/pull/2175))
- Enforce the targetPlatform values the API documents ([#2181](https://github.com/eclipse-openvsx/openvsx/pull/2181))
- Stop deriving the base URL from untrusted X-Forwarded-* headers ([#2196](https://github.com/eclipse-openvsx/openvsx/pull/2196))
- Add fixed height to footer rating ([#2198](https://github.com/eclipse-openvsx/openvsx/pull/2198))

#### Dependencies

- Bump js-yaml from 4.3.1 to 4.3.2 ([#2160](https://github.com/eclipse-openvsx/openvsx/pull/2160))
- Bump the github-actions group with 6 updates ([#2088](https://github.com/eclipse-openvsx/openvsx/pull/2088))
- Bump the github-actions group with 2 updates ([#2111](https://github.com/eclipse-openvsx/openvsx/pull/2111))
- Bump @humanfs/node from 0.16.6 to 0.16.8 in /webui ([#2137](https://github.com/eclipse-openvsx/openvsx/pull/2137))
- Bump fast-uri from 3.1.5 to 3.1.7 in /cli ([#2139](https://github.com/eclipse-openvsx/openvsx/pull/2139))
- Bump qs from 6.15.2 to 6.16.0 in /cli ([#2143](https://github.com/eclipse-openvsx/openvsx/pull/2143))
- Bump @humanfs/node from 0.16.6 to 0.16.8 in /cli ([#2138](https://github.com/eclipse-openvsx/openvsx/pull/2138))
- Bump the github-actions group with 2 updates ([#2172](https://github.com/eclipse-openvsx/openvsx/pull/2172))
- Bump nanoid from 3.3.16 to 3.3.18 in /cli ([#2197](https://github.com/eclipse-openvsx/openvsx/pull/2197))

### [v1.1.2] (20/08/2026)

#### Added

- Removing extension overview sidebar intermediate state ([#2072](https://github.com/eclipse-openvsx/openvsx/pull/2072))
- Add GDPR endpoint to openvsx admin API ([#1982](https://github.com/eclipse-openvsx/openvsx/pull/1982))

#### Changed

- Update check script ([#2046](https://github.com/eclipse-openvsx/openvsx/pull/2046))
- Add SBOM generation workflows for docker, gradle and yarn ([#1999](https://github.com/eclipse-openvsx/openvsx/pull/1999))

#### Fixed

- Smoke tests broken by search UI redesign ([#2069](https://github.com/eclipse-openvsx/openvsx/pull/2069))
- Restore response compression for VS Code Gallery API JSON responses ([#2073](https://github.com/eclipse-openvsx/openvsx/pull/2073))
- Consume request body before sending response ([#2066](https://github.com/eclipse-openvsx/openvsx/pull/2066))
- Handle non-absolute URLs in ExtensionValidator ([#2077](https://github.com/eclipse-openvsx/openvsx/pull/2077))

#### Dependencies

- Bump the github-actions group with 6 updates ([#2063](https://github.com/eclipse-openvsx/openvsx/pull/2063))

### [v1.1.1] (09/08/2026)

#### Fixed

- Add validation for file resource name collisions ([#2036](https://github.com/eclipse-openvsx/openvsx/pull/2036))
- Use non-generic jackson serializers for various caches ([#2037](https://github.com/eclipse-openvsx/openvsx/pull/2037))
- Dependency detection on actual id rather by name alone ([#2038](https://github.com/eclipse-openvsx/openvsx/pull/2038))
- Review of the jooqRepository classes ([#2044](https://github.com/eclipse-openvsx/openvsx/pull/2044))
- Make revoking of publisher contributions more resilient ([#2050](https://github.com/eclipse-openvsx/openvsx/pull/2050))
- Revert to jackson 2.x default behavior for primitives types ([#2052](https://github.com/eclipse-openvsx/openvsx/pull/2052))
- Restore custom error handling ([#2053](https://github.com/eclipse-openvsx/openvsx/pull/2053))
- Webui build on s390x and ppc64le ([#2054](https://github.com/eclipse-openvsx/openvsx/pull/2054))
- Use jackson 2 compatible mapper for extensionquery ([#2061](https://github.com/eclipse-openvsx/openvsx/pull/2061))
- Cap pre-release versions returned by extensionQuery ([#2062](https://github.com/eclipse-openvsx/openvsx/pull/2062))

#### Dependencies

- Bump jOOQ to 3.19.37 ([#2040](https://github.com/eclipse-openvsx/openvsx/pull/2040))
- Bump Jetty to 12.1.12 ([#2043](https://github.com/eclipse-openvsx/openvsx/pull/2043))
- Remove unused @mui/base dependency ([#2058](https://github.com/eclipse-openvsx/openvsx/pull/2058))
- Bump the github-actions group with 4 updates ([#2033](https://github.com/eclipse-openvsx/openvsx/pull/2033))
- Bump postcss from 8.5.22 to 8.5.25 in /webui ([#2042](https://github.com/eclipse-openvsx/openvsx/pull/2042))
- Bump fast-uri from 3.1.4 to 3.1.5 in /cli ([#2041](https://github.com/eclipse-openvsx/openvsx/pull/2041))
- Bump dompurify from 3.4.12 to 3.4.13 in /webui ([#2055](https://github.com/eclipse-openvsx/openvsx/pull/2055))
- Bump js-yaml from 4.3.0 to 4.3.1 in /cli ([#2056](https://github.com/eclipse-openvsx/openvsx/pull/2056))

### [v1.1.0] (02/08/2026)

Upgrade note: Spring Boot 4 bumps Elasticsearch to `9.2.8`, and there is no direct upgrade from the previous version. Either recreate the Elasticsearch PVC from scratch (the index is rebuilt on first start), or first upgrade the existing Elasticsearch image to `8.9.x`, which then upgrades itself to `9.2.8`.

Highlights: Spring Boot 4, Vite 8 for the frontend build, a redesigned web UI, immutable extension versions, and a feed of extension changes at `/api/-/version-changes`.

#### Added

- Migrate admin pages to TanStack Query ([#1917](https://github.com/eclipse-openvsx/openvsx/pull/1917))
- Improve user management page  ([#1847](https://github.com/eclipse-openvsx/openvsx/pull/1847))
- Add a bulk publisher revocation endpoint ([#1860](https://github.com/eclipse-openvsx/openvsx/pull/1860))
- Add extension detail page for user-settings and admin dashboard ([#1939](https://github.com/eclipse-openvsx/openvsx/pull/1939))
- Prevent oversized extension uploads from the client ([#1937](https://github.com/eclipse-openvsx/openvsx/pull/1937))
- Reject namespace display names that collide with other namespaces ([#1957](https://github.com/eclipse-openvsx/openvsx/pull/1957))
- Replace keytar with cross-keychain for token storage ([#1950](https://github.com/eclipse-openvsx/openvsx/pull/1950))
- Masking PAT in CLI and webui ([#1966](https://github.com/eclipse-openvsx/openvsx/pull/1966))
- Refreshing UX/UI ([#1938](https://github.com/eclipse-openvsx/openvsx/pull/1938))
- Use JBang-fmt to format sources ([#1945](https://github.com/eclipse-openvsx/openvsx/pull/1945))
- Refining the UI refresh implementation ([#1996](https://github.com/eclipse-openvsx/openvsx/pull/1996))
- Fail-fast the publication when missing permissions or duplicate version an retry on DataIntegrityViolations due to concurrent publications ([#2008](https://github.com/eclipse-openvsx/openvsx/pull/2008))
- Add --allow-missing-repository option to ovsx publish ([#2012](https://github.com/eclipse-openvsx/openvsx/pull/2012))
- Quarantine based on scanner malicious verdict ([#1991](https://github.com/eclipse-openvsx/openvsx/pull/1991))
- Adding changes feed api ([#1859](https://github.com/eclipse-openvsx/openvsx/pull/1859))

#### Changed

- Adding prettier ([#1916](https://github.com/eclipse-openvsx/openvsx/pull/1916))
- Add OpenAPI annotations for all endpoints in AdminAPI ([#1931](https://github.com/eclipse-openvsx/openvsx/pull/1931))
- Add more pre-commit hook for consistency of source files ([#1932](https://github.com/eclipse-openvsx/openvsx/pull/1932))
- Upgrade to spring boot 4 ([#1925](https://github.com/eclipse-openvsx/openvsx/pull/1925))
- Add SBOM generation and upload workflow for webui and cli packages ([#1640](https://github.com/eclipse-openvsx/openvsx/pull/1640))
- Fix search relevance ([#1385](https://github.com/eclipse-openvsx/openvsx/pull/1385))
- Align jooq generated classes with DB schema ([#1936](https://github.com/eclipse-openvsx/openvsx/pull/1936))
- Update brace-expansion to address CVE-2026-45149 ([#1944](https://github.com/eclipse-openvsx/openvsx/pull/1944))
- Drop PAT jOOQ repository ([#1947](https://github.com/eclipse-openvsx/openvsx/pull/1947))
- Some minor cleanups in jooq repositories ([#1948](https://github.com/eclipse-openvsx/openvsx/pull/1948))
- Move off guava; low hanging fruits ([#1941](https://github.com/eclipse-openvsx/openvsx/pull/1941))
- Disable transitive delete of extensions in extension control by default ([#1951](https://github.com/eclipse-openvsx/openvsx/pull/1951))
- Remove unused dep jsonpath ([#1970](https://github.com/eclipse-openvsx/openvsx/pull/1970))
- Download test extensions ([#1971](https://github.com/eclipse-openvsx/openvsx/pull/1971))
- Upgrade to vite 8 ([#1989](https://github.com/eclipse-openvsx/openvsx/pull/1989))
- Upgrade to react-router 7 ([#1997](https://github.com/eclipse-openvsx/openvsx/pull/1997))
- Make extension versions immutable ([#1983](https://github.com/eclipse-openvsx/openvsx/pull/1983))
- Agent operating rules and skills ([#2006](https://github.com/eclipse-openvsx/openvsx/pull/2006))
- Revise security vulnerability reporting instructions ([#2014](https://github.com/eclipse-openvsx/openvsx/pull/2014))
- Ignore generated tags with __ prefix tag limit ([#2018](https://github.com/eclipse-openvsx/openvsx/pull/2018))
- Remove deprecated fields in ExtensionJson ([#2024](https://github.com/eclipse-openvsx/openvsx/pull/2024))

#### Fixed

- Add lower casing to namespace + extension name in cache keys ([#1924](https://github.com/eclipse-openvsx/openvsx/pull/1924))
- Honor path-style-access parameter in S3Presigner ([#1928](https://github.com/eclipse-openvsx/openvsx/pull/1928))
- Normalize multiple slashes when detecting malicious zip files ([#1933](https://github.com/eclipse-openvsx/openvsx/pull/1933))
- Serialize deleteExtension and publish with a pessimistic lock ([#1919](https://github.com/eclipse-openvsx/openvsx/pull/1919))
- Correctly set http headers when uploading a file to AWS S3 ([#1929](https://github.com/eclipse-openvsx/openvsx/pull/1929))
- Consolidating deletion logic in ExtensionService ([#1935](https://github.com/eclipse-openvsx/openvsx/pull/1935))
- Delete recurrent extension control job when disabled ([#1955](https://github.com/eclipse-openvsx/openvsx/pull/1955))
- Do not reactivate extension versions that have failed scans ([#1952](https://github.com/eclipse-openvsx/openvsx/pull/1952))
- Force parsson 1.1.8 to address CVE-2026-9563 ([#1960](https://github.com/eclipse-openvsx/openvsx/pull/1960))
- Resolve js-yaml to ^5.2.1 to address CVE-2026-59869 ([#1976](https://github.com/eclipse-openvsx/openvsx/pull/1976))
- Upgrade httpcore5 to 5.4.3 to address CVE-2026-54428 ([#1977](https://github.com/eclipse-openvsx/openvsx/pull/1977))
- Normalize section margins ([#1990](https://github.com/eclipse-openvsx/openvsx/pull/1990))
- Backspace is prevented if done fast ([#2005](https://github.com/eclipse-openvsx/openvsx/pull/2005))
- Limit the maximum number of tags per extensin version ([#2009](https://github.com/eclipse-openvsx/openvsx/pull/2009))
- Correctly encode + chars in storage location URLs in case of AWS ([#2011](https://github.com/eclipse-openvsx/openvsx/pull/2011))
- Run MockMvc async handling inline to fix flaky StreamingResponseBody tests ([#2028](https://github.com/eclipse-openvsx/openvsx/pull/2028))

#### Dependencies

- Update tar to 7.5.19 to address CVE-2026-59874 ([#1959](https://github.com/eclipse-openvsx/openvsx/pull/1959))
- Patch updates ([#1972](https://github.com/eclipse-openvsx/openvsx/pull/1972))
- Bump jackson from 2.18.6 to 2.18.8 in /server ([#1923](https://github.com/eclipse-openvsx/openvsx/pull/1923))
- Bump the github-actions group across 1 directory with 5 updates ([#1940](https://github.com/eclipse-openvsx/openvsx/pull/1940))
- Bump the github-actions group with 6 updates ([#1962](https://github.com/eclipse-openvsx/openvsx/pull/1962))
- Bump the github-actions group with 3 updates ([#1979](https://github.com/eclipse-openvsx/openvsx/pull/1979))
- Bump brace-expansion from 1.1.13 to 1.1.16 in /cli ([#1981](https://github.com/eclipse-openvsx/openvsx/pull/1981))
- Bump tar from 7.5.16 to 7.5.21 in /cli ([#1987](https://github.com/eclipse-openvsx/openvsx/pull/1987))
- Bump dompurify from 3.4.11 to 3.4.12 in /webui ([#1984](https://github.com/eclipse-openvsx/openvsx/pull/1984))
- Bump the github-actions group with 7 updates ([#1992](https://github.com/eclipse-openvsx/openvsx/pull/1992))
- Bump tar from 7.5.19 to 7.5.22 in /webui ([#1994](https://github.com/eclipse-openvsx/openvsx/pull/1994))
- Bump brace-expansion from 1.1.16 to 1.1.18 in /cli ([#2031](https://github.com/eclipse-openvsx/openvsx/pull/2031))

### [v1.0.2] (23/06/2026)

#### Changed

- Switch from mocha to vitest ([#1914](https://github.com/eclipse-openvsx/openvsx/pull/1914))
- Add check for unsafe paths to MaliciousZipCheckService ([#1915](https://github.com/eclipse-openvsx/openvsx/pull/1915))

#### Fixed

- Add consistent and strict http headers when serving files inside extensions ([#1922](https://github.com/eclipse-openvsx/openvsx/pull/1922))

#### Dependencies

- Bump tmp from 0.2.6 to 0.2.7 in /cli ([#1903](https://github.com/eclipse-openvsx/openvsx/pull/1903))
- Bump the github-actions group with 2 updates ([#1902](https://github.com/eclipse-openvsx/openvsx/pull/1902))
- Bump tar from 7.5.11 to 7.5.16 in /webui ([#1907](https://github.com/eclipse-openvsx/openvsx/pull/1907))
- Bump js-yaml from 4.1.1 to 4.2.0 in /webui ([#1908](https://github.com/eclipse-openvsx/openvsx/pull/1908))
- Bump vite from 7.3.2 to 7.3.5 in /webui ([#1905](https://github.com/eclipse-openvsx/openvsx/pull/1905))
- Bump dompurify from 3.4.0 to 3.4.9 in /webui ([#1906](https://github.com/eclipse-openvsx/openvsx/pull/1906))
- Bump markdown-it from 14.1.1 to 14.2.0 in /webui ([#1904](https://github.com/eclipse-openvsx/openvsx/pull/1904))
- Bump tar from 7.5.11 to 7.5.16 in /cli ([#1911](https://github.com/eclipse-openvsx/openvsx/pull/1911))
- Bump @babel/core from 7.29.0 to 7.29.7 in /webui ([#1909](https://github.com/eclipse-openvsx/openvsx/pull/1909))
- Bump form-data from 4.0.4 to 4.0.6 in /cli ([#1910](https://github.com/eclipse-openvsx/openvsx/pull/1910))
- Bump js-yaml from 4.1.1 to 4.2.0 in /cli ([#1912](https://github.com/eclipse-openvsx/openvsx/pull/1912))
- Bump markdown-it from 14.1.1 to 14.2.0 in /cli ([#1913](https://github.com/eclipse-openvsx/openvsx/pull/1913))
- Bump dompurify from 3.4.10 to 3.4.11 in /webui ([#1920](https://github.com/eclipse-openvsx/openvsx/pull/1920))
- Bump the github-actions group with 2 updates ([#1921](https://github.com/eclipse-openvsx/openvsx/pull/1921))

### [v1.0.1] (11/06/2026)

#### Changed

- Update to Gradle 9.5.1 ([#1894](https://github.com/eclipse-openvsx/openvsx/pull/1894))

#### Fixed

- Evict all relevant caches when deleting an extension ([#1890](https://github.com/eclipse-openvsx/openvsx/pull/1890))
- Add validation for size/offset parameters for various endpoints ([#1892](https://github.com/eclipse-openvsx/openvsx/pull/1892))
- Properly encode path segments in getUrl ([#1893](https://github.com/eclipse-openvsx/openvsx/pull/1893))

#### Dependencies

- Bump qs and react-router ([#1897](https://github.com/eclipse-openvsx/openvsx/pull/1897))
- Bump the github-actions group across 1 directory with 5 updates ([#1888](https://github.com/eclipse-openvsx/openvsx/pull/1888))
- Pin opentelemetry version to 1.63.0 ([#1899](https://github.com/eclipse-openvsx/openvsx/pull/1899))
- Bump netty to 4.1.135.Final ([#1900](https://github.com/eclipse-openvsx/openvsx/pull/1900))

### [v1.0.0] (28/05/2026)

#### Added

- Add read only mode ([#1835](https://github.com/eclipse-openvsx/openvsx/pull/1835))
- Support TLS for redis connectons ([#1879](https://github.com/eclipse-openvsx/openvsx/pull/1879))

#### Changed

- Remove pull request CodeQL trigger ([#1881](https://github.com/eclipse-openvsx/openvsx/pull/1881))

#### Fixed

- Cache S3Presigner as singleton to prevent IRSA credential refresh failure ([#1856](https://github.com/eclipse-openvsx/openvsx/pull/1856))
- Protect sitemap parsing from possible XXE attacks ([#1876](https://github.com/eclipse-openvsx/openvsx/pull/1876))
- Repo location and yarn version in deployment descriptors ([#1880](https://github.com/eclipse-openvsx/openvsx/pull/1880))
- Include all scanner jobs to be able to retry failed scans ([#1882](https://github.com/eclipse-openvsx/openvsx/pull/1882))
- Fine-tune cache control for public endpoints ([#1883](https://github.com/eclipse-openvsx/openvsx/pull/1883))
- Ensure sequential execution of database insert commands ([#1885](https://github.com/eclipse-openvsx/openvsx/pull/1885))

#### Dependencies

- Bump qs from 6.15.0 to 6.15.2 in /cli ([#1869](https://github.com/eclipse-openvsx/openvsx/pull/1869))
- Bump tmp from 0.2.4 to 0.2.6 in /cli ([#1877](https://github.com/eclipse-openvsx/openvsx/pull/1877))

### [v0.34.6] (22/05/2026)

#### Added

- Adds retry for failed scanner jobs ([#1832](https://github.com/eclipse-openvsx/openvsx/pull/1832))
- Expose scanner job lifecycle in scan json ([#1836](https://github.com/eclipse-openvsx/openvsx/pull/1836))
- Support GET as method for the extensionquery endpoint ([#1867](https://github.com/eclipse-openvsx/openvsx/pull/1867))

#### Changed

- Schedule recurring jobs for scanning only when necessary ([#1840](https://github.com/eclipse-openvsx/openvsx/pull/1840))
- Exclude jobrunr tables from jooq ([#1842](https://github.com/eclipse-openvsx/openvsx/pull/1842))

#### Fixed

- Make Gatling simulations runnable again ([#1838](https://github.com/eclipse-openvsx/openvsx/pull/1838))
- Add proper cache control headers for all public endpoints ([#1863](https://github.com/eclipse-openvsx/openvsx/pull/1863))
- Correctly sort versions returned by latest endpoint in semantic order ([#1865](https://github.com/eclipse-openvsx/openvsx/pull/1865))

#### Dependencies

- Bump brace-expansion from 5.0.5 to 5.0.6 in /cli ([#1846](https://github.com/eclipse-openvsx/openvsx/pull/1846))

### [v0.34.5] (11/05/2026)

#### Changed

- Continue mirror extension versions even if one fails ([#942](https://github.com/eclipse-openvsx/openvsx/pull/942))

#### Fixed

- Exclude displayName check in package.json validation ([#1833](https://github.com/eclipse-openvsx/openvsx/pull/1833))

#### Dependencies

- Bump the github-actions group with 2 updates ([#1830](https://github.com/eclipse-openvsx/openvsx/pull/1830))
- Bump fast-uri from 3.1.0 to 3.1.2 in /cli ([#1829](https://github.com/eclipse-openvsx/openvsx/pull/1829))

### [v0.34.4] (08/05/2026)

#### Added

- Adding duplicate zip entries after normalization check ([#1807](https://github.com/eclipse-openvsx/openvsx/pull/1807))

#### Changed

- Remove max-width setting for admin dashboard ([#1809](https://github.com/eclipse-openvsx/openvsx/pull/1809))
- Check authId when logging in via an oauth2 provider ([#1819](https://github.com/eclipse-openvsx/openvsx/pull/1819))

#### Fixed

- Url decode breadcrumbs ([#1806](https://github.com/eclipse-openvsx/openvsx/pull/1806))
- Validate that metadata in package.json matches manifest ([#1825](https://github.com/eclipse-openvsx/openvsx/pull/1825))
- Correctly calculate available and remaining rate limit tokens ([#1826](https://github.com/eclipse-openvsx/openvsx/pull/1826))
- Using servlet decoded path over request URI ([#1822](https://github.com/eclipse-openvsx/openvsx/pull/1822))

#### Dependencies

- Bump thymeleaf to 3.1.5.RELEASE ([#1814](https://github.com/eclipse-openvsx/openvsx/pull/1814))
- Bump spring boot to 3.5.14 ([#1817](https://github.com/eclipse-openvsx/openvsx/pull/1817))
- Bump postgresql client, netty and ip-address ([#1820](https://github.com/eclipse-openvsx/openvsx/pull/1820))
- Bump netty to 4.1.133.Final ([#1821](https://github.com/eclipse-openvsx/openvsx/pull/1821))
- Update follow-redirects version in package.json ([#1823](https://github.com/eclipse-openvsx/openvsx/pull/1823))

### [v0.34.3] (29/04/2026)

#### Fixed

- Latest version cache deserialization ([#1800](https://github.com/eclipse-openvsx/openvsx/pull/1800))

### [v0.34.2] (28/04/2026)

#### Added

- Add option for admins to delete empty namespaces ([#1773](https://github.com/eclipse-openvsx/openvsx/pull/1773))
- Add config to permit additional routes ([#1785](https://github.com/eclipse-openvsx/openvsx/pull/1785))
- Add Kubernetes deployment manifests for self-hosted OpenVSX ([#1792](https://github.com/eclipse-openvsx/openvsx/pull/1792))

#### Changed

- Add namespace to all OpenShift deployment resources ([#1783](https://github.com/eclipse-openvsx/openvsx/pull/1783))
- Add yarn workspaces ([#1788](https://github.com/eclipse-openvsx/openvsx/pull/1788))
- Add scanner deep links and summary extraction to scan admin dashboard ([#1789](https://github.com/eclipse-openvsx/openvsx/pull/1789))
- Updating admin dashboard sidepanel implementation ([#1782](https://github.com/eclipse-openvsx/openvsx/pull/1782))
- Remove deprecated logo_byes column in Namespace entity ([#1784](https://github.com/eclipse-openvsx/openvsx/pull/1784))

#### Fixed

- Dashboard breadcrumbs are not decoded ([#1781](https://github.com/eclipse-openvsx/openvsx/pull/1781))
- Ensure that `/vscode/gallery/<namespace>/<extension>/latest` only returns the latest versions ([#1791](https://github.com/eclipse-openvsx/openvsx/pull/1791))

#### Dependencies

- Bump postcss from 8.5.6 to 8.5.10 in /webui ([#1793](https://github.com/eclipse-openvsx/openvsx/pull/1793))
- Bump the github-actions group with 4 updates ([#1795](https://github.com/eclipse-openvsx/openvsx/pull/1795))
- Bump spring-security to 6.5.10 ([#1797](https://github.com/eclipse-openvsx/openvsx/pull/1797))
- Bump react-avatar-editor to 15.1.0 ([#1787](https://github.com/eclipse-openvsx/openvsx/pull/1787))

### [v0.34.1] (20/04/2026)

#### Added

- Add useExtensionDetail hook for managing extension details state ([#1760](https://github.com/eclipse-openvsx/openvsx/pull/1760))
- Add icon validation to publish event ([#1766](https://github.com/eclipse-openvsx/openvsx/pull/1766))

#### Changed

- Using dynamic imports for admin and extension pages ([#1750](https://github.com/eclipse-openvsx/openvsx/pull/1750))

#### Fixed

- Published by label text wraps ([#1765](https://github.com/eclipse-openvsx/openvsx/pull/1765))
- Validate that all file resources are available ([#1776](https://github.com/eclipse-openvsx/openvsx/pull/1776))
- Add download url for errroed extension scans ([#1778](https://github.com/eclipse-openvsx/openvsx/pull/1778))

#### Dependencies

- Bump thymeleaf to 3.1.4.RELEASE ([#1774](https://github.com/eclipse-openvsx/openvsx/pull/1774))
- Bump follow-redirects from 1.15.6 to 1.16.0 in /cli ([#1759](https://github.com/eclipse-openvsx/openvsx/pull/1759))
- Bump dompurify from 3.3.2 to 3.4.0 in /webui ([#1768](https://github.com/eclipse-openvsx/openvsx/pull/1768))
- Bump org.bouncycastle:bcpkix-jdk18on from 1.80 to 1.84 in /server ([#1770](https://github.com/eclipse-openvsx/openvsx/pull/1770))
- Bump the github-actions group with 2 updates ([#1775](https://github.com/eclipse-openvsx/openvsx/pull/1775))

### [v0.34.0] (13/04/2026)

#### Changed

- Rate Limiting Phase 2 ([#1698](https://github.com/eclipse-openvsx/openvsx/pull/1698))
- Improve usage stats collection with a local cache ([#1755](https://github.com/eclipse-openvsx/openvsx/pull/1755))

#### Fixed

- Make toSearchEntry fail-safe ([#1751](https://github.com/eclipse-openvsx/openvsx/pull/1751))
- Avoid loading extension data multiple times ([#1756](https://github.com/eclipse-openvsx/openvsx/pull/1756))

#### Dependencies

- Bump the github-actions group with 3 updates ([#1754](https://github.com/eclipse-openvsx/openvsx/pull/1754))

### [v0.33.1] (07/04/2026)

#### Changed

- Update devfile commands to use OpenVSX v0.33.0 ([#1722](https://github.com/eclipse-openvsx/openvsx/pull/1722))
- Make malicious zip file checks a synchronous PublishCheck ([#1721](https://github.com/eclipse-openvsx/openvsx/pull/1721))
- Fix dispatcher race condition when multiple scan jobs are dispatched in one go ([#1740](https://github.com/eclipse-openvsx/openvsx/pull/1740))

#### Fixed

- Display usage stats in UTC timezone ([#1747](https://github.com/eclipse-openvsx/openvsx/pull/1747))

#### Dependencies

- Bump spring boot to 3.5.13 ([#1739](https://github.com/eclipse-openvsx/openvsx/pull/1739))
- Bump picomatch from 4.0.3 to 4.0.4 in /webui ([#1720](https://github.com/eclipse-openvsx/openvsx/pull/1720))
- Bump yaml from 1.10.2 to 1.10.3 in /webui ([#1717](https://github.com/eclipse-openvsx/openvsx/pull/1717))
- Bump picomatch from 2.3.1 to 2.3.2 in /cli ([#1719](https://github.com/eclipse-openvsx/openvsx/pull/1719))
- Bump the github-actions group across 1 directory with 7 updates ([#1710](https://github.com/eclipse-openvsx/openvsx/pull/1710))
- Bump io.netty dependency set from 4.2.7.Final to 4.2.12.Final in /server ([#1724](https://github.com/eclipse-openvsx/openvsx/pull/1724))
- Bump brace-expansion from 1.1.12 to 1.1.13 in /cli ([#1725](https://github.com/eclipse-openvsx/openvsx/pull/1725))
- Bump lodash from 4.17.23 to 4.18.1 in /webui ([#1742](https://github.com/eclipse-openvsx/openvsx/pull/1742))
- Bump vite from 7.3.1 to 7.3.2 in /webui ([#1744](https://github.com/eclipse-openvsx/openvsx/pull/1744))
- Bump the github-actions group across 1 directory with 2 updates ([#1743](https://github.com/eclipse-openvsx/openvsx/pull/1743))
- Bump lodash from 4.17.23 to 4.18.1 in /cli ([#1745](https://github.com/eclipse-openvsx/openvsx/pull/1745))

### [v0.33.0] (25/03/2026)

#### Added

- Adding platform to scan card + some refactoring ([#1650](https://github.com/eclipse-openvsx/openvsx/pull/1650))
- Add support for positron engine ([#1689](https://github.com/eclipse-openvsx/openvsx/pull/1689))
- Improve access token expiry by supporting sending an email when tokens have expired ([#1701](https://github.com/eclipse-openvsx/openvsx/pull/1701))

#### Changed

- Migrate to vite for webui ([#1399](https://github.com/eclipse-openvsx/openvsx/pull/1399))
- Check for Retry-After reposonse header when receiving 429 responses from the server ([#1637](https://github.com/eclipse-openvsx/openvsx/pull/1637))
- Update secret detection configuration ([#1645](https://github.com/eclipse-openvsx/openvsx/pull/1645))
- Update name squatting ([#1646](https://github.com/eclipse-openvsx/openvsx/pull/1646))
- Improve wording for empty extension state ([#1641](https://github.com/eclipse-openvsx/openvsx/pull/1641))
- Fix duplicate scan job key ([#1657](https://github.com/eclipse-openvsx/openvsx/pull/1657))
- Add more transparent error details to admin UI ([#1658](https://github.com/eclipse-openvsx/openvsx/pull/1658))
- Access token expiry ([#1513](https://github.com/eclipse-openvsx/openvsx/pull/1513))
- Remove Content-Length header from forwarded http header options ([#1659](https://github.com/eclipse-openvsx/openvsx/pull/1659))
- Max Scan Concurrency ([#1654](https://github.com/eclipse-openvsx/openvsx/pull/1654))
- Align entity definition with schema migration and update jooq generated sources ([#1664](https://github.com/eclipse-openvsx/openvsx/pull/1664))
- Remove deprecated content column from FileResource entity ([#1667](https://github.com/eclipse-openvsx/openvsx/pull/1667))
- Adapt .devfile.yaml to the recent changes ([#1668](https://github.com/eclipse-openvsx/openvsx/pull/1668))
- Handle license check within PublishExtensionVersionHandler ([#1677](https://github.com/eclipse-openvsx/openvsx/pull/1677))
- Remove Develocity configuration ([#1679](https://github.com/eclipse-openvsx/openvsx/pull/1679))
- Fix race condition wrt file caches ([#1687](https://github.com/eclipse-openvsx/openvsx/pull/1687))
- Do not post reviews when hitting enter, properly align buttons ([#1690](https://github.com/eclipse-openvsx/openvsx/pull/1690))
- Chore[devfile]: revert postgresql image in devfile ([#1699](https://github.com/eclipse-openvsx/openvsx/pull/1699))
- Bump spring boot to 3.5.12 ([#1714](https://github.com/eclipse-openvsx/openvsx/pull/1714))

#### Fixed

- Improve mobile menu item link styling for better usability ([#1598](https://github.com/eclipse-openvsx/openvsx/pull/1598))
- Handle empty fullName / email of users when sending emails ([#1648](https://github.com/eclipse-openvsx/openvsx/pull/1648))
- Update lastUpdatedTime of an Extension when there are changes ([#1670](https://github.com/eclipse-openvsx/openvsx/pull/1670))
- Race condition in GenerateKeyPairJobRequestHandler ([#1669](https://github.com/eclipse-openvsx/openvsx/pull/1669))
- Add size limit when reading data from a zip file entry ([#1673](https://github.com/eclipse-openvsx/openvsx/pull/1673))
- Make query more resilient ([#1697](https://github.com/eclipse-openvsx/openvsx/pull/1697))
- Gracefully handle case of file resources without extension version ([#1706](https://github.com/eclipse-openvsx/openvsx/pull/1706))
- Make error dialog link visible on white background ([#1712](https://github.com/eclipse-openvsx/openvsx/pull/1712))
- Display an accurate number of total hits for search ([#1716](https://github.com/eclipse-openvsx/openvsx/pull/1716))

#### Dependencies

- Bump ajv from 6.12.6 to 6.14.0 in /cli ([#1632](https://github.com/eclipse-openvsx/openvsx/pull/1632))
- Bump tar from 7.5.7 to 7.5.9 in /cli ([#1624](https://github.com/eclipse-openvsx/openvsx/pull/1624))
- Bump @isaacs/brace-expansion from 5.0.0 to 5.0.1 in /webui ([#1638](https://github.com/eclipse-openvsx/openvsx/pull/1638))
- Bump tar from 7.5.7 to 7.5.9 in /webui ([#1639](https://github.com/eclipse-openvsx/openvsx/pull/1639))
- Bump rollup from 4.53.1 to 4.59.0 in /webui ([#1647](https://github.com/eclipse-openvsx/openvsx/pull/1647))
- Bump underscore from 1.13.6 to 1.13.8 in /cli ([#1656](https://github.com/eclipse-openvsx/openvsx/pull/1656))
- Bump the github-actions group across 1 directory with 6 updates ([#1660](https://github.com/eclipse-openvsx/openvsx/pull/1660))
- Bump tar from 7.5.9 to 7.5.10 in /cli ([#1666](https://github.com/eclipse-openvsx/openvsx/pull/1666))
- Bump tar from 7.5.9 to 7.5.10 in /webui ([#1665](https://github.com/eclipse-openvsx/openvsx/pull/1665))
- Bump dompurify from 3.2.4 to 3.3.2 in /webui ([#1671](https://github.com/eclipse-openvsx/openvsx/pull/1671))
- Bump tar from 7.5.10 to 7.5.11 in /webui ([#1678](https://github.com/eclipse-openvsx/openvsx/pull/1678))
- Bump tar from 7.5.10 to 7.5.11 in /cli ([#1681](https://github.com/eclipse-openvsx/openvsx/pull/1681))
- Bump flatted from 3.3.1 to 3.4.2 in /cli ([#1703](https://github.com/eclipse-openvsx/openvsx/pull/1703))
- Bump flatted from 3.3.1 to 3.4.2 in /webui ([#1702](https://github.com/eclipse-openvsx/openvsx/pull/1702))

### [v0.32.3] (18/02/2026)

#### Changed

- Improve error handling in VSCodeAPI wrt file resources ([#1625](https://github.com/eclipse-openvsx/openvsx/pull/1625))
- Check if the IP address string can be resolved to an IPv4 address ([#1627](https://github.com/eclipse-openvsx/openvsx/pull/1627))
- Ensure temporary extension files are deleted during publication ([#1628](https://github.com/eclipse-openvsx/openvsx/pull/1628))

### [v0.32.2] (18/02/2026)

#### Changed

- Fix dynamic rate limiting for customers in state evaluation ([#1623](https://github.com/eclipse-openvsx/openvsx/pull/1623))

### [v0.32.1] (17/02/2026)

#### Changed

- Add `ovsx.logs.aws.max-keys` config parameter ([#1619](https://github.com/eclipse-openvsx/openvsx/pull/1619))

### [v0.32.0] (17/02/2026)

#### Added

- Support customizing the publisher agreement name and contact email ([#1550](https://github.com/eclipse-openvsx/openvsx/pull/1550))
- Display extension unique identifier ([#1590](https://github.com/eclipse-openvsx/openvsx/pull/1590))
- Adding admin logs to the backoffice ([#1582](https://github.com/eclipse-openvsx/openvsx/pull/1582))

#### Changed

- Support removing arbitrary reviews for admins ([#1403](https://github.com/eclipse-openvsx/openvsx/pull/1403))
- Cleanup cli script ([#1538](https://github.com/eclipse-openvsx/openvsx/pull/1538))
- Add support for GfM alerts ([#1535](https://github.com/eclipse-openvsx/openvsx/pull/1535))
- Support custom registry url with a subpath ([#1542](https://github.com/eclipse-openvsx/openvsx/pull/1542))
- Disable scripts by default for yarn ([#1546](https://github.com/eclipse-openvsx/openvsx/pull/1546))
- Fix searching by publisher ([#1578](https://github.com/eclipse-openvsx/openvsx/pull/1578))
- Redirect to `/error` only for html pages, not api calls ([#1577](https://github.com/eclipse-openvsx/openvsx/pull/1577))
- Improve search result relevance by not boosting fuzzy matches anymore ([#1579](https://github.com/eclipse-openvsx/openvsx/pull/1579))
- Suppress MediaTypeNotSupportedException in server logs ([#1580](https://github.com/eclipse-openvsx/openvsx/pull/1580))
- Dependency and build updates ([#1586](https://github.com/eclipse-openvsx/openvsx/pull/1586))
- Security Check Framework ([#1529](https://github.com/eclipse-openvsx/openvsx/pull/1529))
- Cleanup MigrationItemJob ([#1587](https://github.com/eclipse-openvsx/openvsx/pull/1587))
- Use Version catalog ([#1594](https://github.com/eclipse-openvsx/openvsx/pull/1594))
- Add support for analysing fastly log files ([#1599](https://github.com/eclipse-openvsx/openvsx/pull/1599))
- Consistently use javax.annotations package for Nullable and Nonnull annotations ([#1600](https://github.com/eclipse-openvsx/openvsx/pull/1600))
- Simplify sonar workflow to only analyse master branch and enable gradle dependency submission ([#1601](https://github.com/eclipse-openvsx/openvsx/pull/1601))
- Submit scanning jobs in a fail-safe manner ([#1606](https://github.com/eclipse-openvsx/openvsx/pull/1606))
- Add download metrics for Prometheus ([#1607](https://github.com/eclipse-openvsx/openvsx/pull/1607))
- Migrate deprecated javax.annotation to jakarta.annotation ([#1609](https://github.com/eclipse-openvsx/openvsx/pull/1609))
- Crash at startup when local storage directory is not writable ([#1603](https://github.com/eclipse-openvsx/openvsx/pull/1603))
- Evict namespace details json cache after membership change ([#1615](https://github.com/eclipse-openvsx/openvsx/pull/1615))
- Log if a user deletes an extension ([#1616](https://github.com/eclipse-openvsx/openvsx/pull/1616))
- Implement dynamic tier-based rate limiting ([#1569](https://github.com/eclipse-openvsx/openvsx/pull/1569))

#### Dependencies

- Bump lodash from 4.17.21 to 4.17.23 in /webui ([#1557](https://github.com/eclipse-openvsx/openvsx/pull/1557))
- Bump react-router from 6.23.1 to 6.30.2 in /webui ([#1532](https://github.com/eclipse-openvsx/openvsx/pull/1532))
- Bump lodash from 4.17.21 to 4.17.23 in /cli ([#1567](https://github.com/eclipse-openvsx/openvsx/pull/1567))
- Bump @isaacs/brace-expansion from 5.0.0 to 5.0.1 in /cli ([#1583](https://github.com/eclipse-openvsx/openvsx/pull/1583))
- Bump webpack from 5.94.0 to 5.104.1 in /webui ([#1593](https://github.com/eclipse-openvsx/openvsx/pull/1593))

### [v0.31.0] (06/01/2026)

#### Changed

- Upgrade to Java 25 ([#1446](https://github.com/eclipse-openvsx/openvsx/pull/1446))
- Cli 0.10.8 unreleased ([#1465](https://github.com/eclipse-openvsx/openvsx/pull/1465))
- Fix gatling configuration / setup after upgrade to Java 25 ([#1461](https://github.com/eclipse-openvsx/openvsx/pull/1461))
- Fix Gatling tests ([#1466](https://github.com/eclipse-openvsx/openvsx/pull/1466))
- Cleanup sonar workflow ([#1468](https://github.com/eclipse-openvsx/openvsx/pull/1468))
- Create issue template config ([#1472](https://github.com/eclipse-openvsx/openvsx/pull/1472))
- Update mocha and rimraf dev dependencies ([#1473](https://github.com/eclipse-openvsx/openvsx/pull/1473))
- Improve CI workflow ([#1475](https://github.com/eclipse-openvsx/openvsx/pull/1475))
- Search by publisher ([#1447](https://github.com/eclipse-openvsx/openvsx/pull/1447))
- Fix order of parameters passed in the NamingUtil ([#1455](https://github.com/eclipse-openvsx/openvsx/pull/1455))
- Reduce log verbosity of ExpireFileListener to DEBUG instead of INFO ([#1481](https://github.com/eclipse-openvsx/openvsx/pull/1481))
- Only build docker images if respective files have changed ([#1483](https://github.com/eclipse-openvsx/openvsx/pull/1483))
- Only print the error result message when publishing an extension fails ([#1485](https://github.com/eclipse-openvsx/openvsx/pull/1485))
- Properly URI decode wildcard paths from URL endpoints ([#1484](https://github.com/eclipse-openvsx/openvsx/pull/1484))
- Properly validate MAX_CONTENT_SIZE when publishing extensions ([#1458](https://github.com/eclipse-openvsx/openvsx/pull/1458))
- Ensure to retrieve and display up-to-date publisher agreement status ([#1445](https://github.com/eclipse-openvsx/openvsx/pull/1445))
- Add support for CDN front URL configuration for storage providers ([#1452](https://github.com/eclipse-openvsx/openvsx/pull/1452))
- Add AwsDownloadCountService ([#1451](https://github.com/eclipse-openvsx/openvsx/pull/1451))
- Update glob and jws transitive dependencies ([#1482](https://github.com/eclipse-openvsx/openvsx/pull/1482))
- Release v0.31.0 ([#1492](https://github.com/eclipse-openvsx/openvsx/pull/1492))
- Fallback for unsupported gallery color ([#1508](https://github.com/eclipse-openvsx/openvsx/pull/1508))
- Update qs dependency to 6.14.1 ([#1520](https://github.com/eclipse-openvsx/openvsx/pull/1520))
- Pin remaining workflows ([#1521](https://github.com/eclipse-openvsx/openvsx/pull/1521))
- Fix login of blocked users ([#1527](https://github.com/eclipse-openvsx/openvsx/pull/1527))
- Add publishing workflow for CLI ([#1525](https://github.com/eclipse-openvsx/openvsx/pull/1525))
- Add publishing workflow for frontend library ([#1526](https://github.com/eclipse-openvsx/openvsx/pull/1526))
- Improve release workflow ([#1528](https://github.com/eclipse-openvsx/openvsx/pull/1528))

#### Fixed

- Coalesce the max download count in case the database is empty ([#1444](https://github.com/eclipse-openvsx/openvsx/pull/1444))

#### Dependencies

- Bump apache:tika to 3.2.2 version ([#1462](https://github.com/eclipse-openvsx/openvsx/pull/1462))
- Bump jws from 3.2.2 to 3.2.3 in /cli ([#1460](https://github.com/eclipse-openvsx/openvsx/pull/1460))
- Bump glob from 10.4.1 to 10.5.0 in /webui ([#1474](https://github.com/eclipse-openvsx/openvsx/pull/1474))
- Bump the github-actions group with 8 updates ([#1476](https://github.com/eclipse-openvsx/openvsx/pull/1476))
- Bump qs from 6.12.0 to 6.14.1 in /cli ([#1516](https://github.com/eclipse-openvsx/openvsx/pull/1516))
- Bump the github-actions group across 1 directory with 4 updates ([#1512](https://github.com/eclipse-openvsx/openvsx/pull/1512))

### [v0.30.0] (21/11/2025)

#### Added

- Update OpenVSX Publisher agreement to v1.1 for dev document ([#1364](https://github.com/eclipse-openvsx/openvsx/pull/1364))
- Add IRSA and session token support to AWS Storage Service ([#1327](https://github.com/eclipse-openvsx/openvsx/pull/1327))
- Centering banner button ([#1405](https://github.com/eclipse-openvsx/openvsx/pull/1405))

#### Changed

- Allow admins to revoke a user's Personal Access Token ([#1415](https://github.com/eclipse-openvsx/openvsx/pull/1415))
- Fix refreshing eclipse oauth2 token ([#1388](https://github.com/eclipse-openvsx/openvsx/pull/1388))
- Add scorecard workflow and update badges / logo ([#1422](https://github.com/eclipse-openvsx/openvsx/pull/1422))
- Add configuration setting to disable automatic storage migration ([#1425](https://github.com/eclipse-openvsx/openvsx/pull/1425))
- Publisher remove extension ([#1366](https://github.com/eclipse-openvsx/openvsx/pull/1366))

#### Fixed

- Correctly reference the request body class for the `/api/user/namespace/create` operation ([#1414](https://github.com/eclipse-openvsx/openvsx/pull/1414))
- Handle duplicate extensions included in search results ([#1408](https://github.com/eclipse-openvsx/openvsx/pull/1408))

#### Dependencies

- Bump js-yaml from 4.1.0 to 4.1.1 in /cli ([#1417](https://github.com/eclipse-openvsx/openvsx/pull/1417))
- Bump glob from 10.3.10 to 10.5.0 in /cli ([#1423](https://github.com/eclipse-openvsx/openvsx/pull/1423))
- Bump js-yaml from 4.1.0 to 4.1.1 in /webui ([#1424](https://github.com/eclipse-openvsx/openvsx/pull/1424))
- Bump cli version ([#1431](https://github.com/eclipse-openvsx/openvsx/pull/1431))

### [v0.29.1] (30/10/2025)

#### Changed

- Update springdoc ([#1381](https://github.com/eclipse-openvsx/openvsx/pull/1381))

### [v0.29.0] (16/10/2025)

#### Changed

- Caffeine cache fallback ([#1361](https://github.com/eclipse-openvsx/openvsx/pull/1361))
- Add logging to CacheConfig ([#1367](https://github.com/eclipse-openvsx/openvsx/pull/1367))

### [v0.28.0] (30/09/2025)

#### Changed

- Update Spring Boot to 3.5.3 ([#1288](https://github.com/eclipse-openvsx/openvsx/pull/1288))
- Update the deployment instructions for OpenShift ([#1312](https://github.com/eclipse-openvsx/openvsx/pull/1312))
- Makes the web UI auto-reload (with logs) on local dev with docker-compose ([#1318](https://github.com/eclipse-openvsx/openvsx/pull/1318))
- Bump org.springframework.boot to 3.5.5 ([#1325](https://github.com/eclipse-openvsx/openvsx/pull/1325))
- Cache /vscode/gallery/{namespaceName}/{extensionName}/latest ([#1303](https://github.com/eclipse-openvsx/openvsx/pull/1303))
- Prefix personal access token ([#1339](https://github.com/eclipse-openvsx/openvsx/pull/1339))
- Add allowed versions to EclipseService ([#1347](https://github.com/eclipse-openvsx/openvsx/pull/1347))
- Bump springframework-boot to 3.5.6 version ([#1345](https://github.com/eclipse-openvsx/openvsx/pull/1345))
- Redis Caching ([#1277](https://github.com/eclipse-openvsx/openvsx/pull/1277))
- Cli v0.10.6 ([#1352](https://github.com/eclipse-openvsx/openvsx/pull/1352))
- Add tar-fs upgrade to CHANGELOG ([#1354](https://github.com/eclipse-openvsx/openvsx/pull/1354))

#### Dependencies

- Bump form-data from 4.0.0 to 4.0.4 in /cli ([#1291](https://github.com/eclipse-openvsx/openvsx/pull/1291))
- Bump tmp from 0.2.3 to 0.2.4 in /cli ([#1304](https://github.com/eclipse-openvsx/openvsx/pull/1304))
- Bump tar-fs from 2.1.3 to 2.1.4 in /cli ([#1353](https://github.com/eclipse-openvsx/openvsx/pull/1353))

### [v0.27.0] (08/07/2025)

#### Changed

- Find latest active extension version for search ([#1256](https://github.com/eclipse-openvsx/openvsx/pull/1256))
- Make configurable whether migrations only run once per version. ([#1266](https://github.com/eclipse-openvsx/openvsx/pull/1266))
- Remove BlockHostFilter ([#1268](https://github.com/eclipse-openvsx/openvsx/pull/1268))
- Upgrade gradle from 8.1.1 to 8.3 ([#1257](https://github.com/eclipse-openvsx/openvsx/pull/1257))
- Update CHANGELOG and bump version ([#1273](https://github.com/eclipse-openvsx/openvsx/pull/1273))
- Improve search ([#1275](https://github.com/eclipse-openvsx/openvsx/pull/1275))
- Add group Gradle property; add maven-publish plugin ([#1274](https://github.com/eclipse-openvsx/openvsx/pull/1274))
- Cli CHANGELOG for v0.10.5 ([#1278](https://github.com/eclipse-openvsx/openvsx/pull/1278))
- Don't assume ErrorResult in tryResolveNamespaceError ([#1281](https://github.com/eclipse-openvsx/openvsx/pull/1281))

#### Dependencies

- Bump brace-expansion from 1.1.11 to 1.1.12 in /cli ([#1261](https://github.com/eclipse-openvsx/openvsx/pull/1261))

### [v0.26.0] (03/06/2025)

#### Changed

- Add BlockHostFilter ([#1253](https://github.com/eclipse-openvsx/openvsx/pull/1253))
- Fix CVE-2025-22235 by upgrading org.springframework.boot to 3.3.11 ([#1245](https://github.com/eclipse-openvsx/openvsx/pull/1245))
- Cli 0.10.4 ([#1254](https://github.com/eclipse-openvsx/openvsx/pull/1254))

#### Dependencies

- Bump tar-fs from 2.1.2 to 2.1.3 in /cli ([#1252](https://github.com/eclipse-openvsx/openvsx/pull/1252))

### [v0.25.0] (01/06/2025)

#### Changed

- Use publisher agreement after signing ([#1178](https://github.com/eclipse-openvsx/openvsx/pull/1178))
- Automate namespace claim process ([#1179](https://github.com/eclipse-openvsx/openvsx/pull/1179))
- Always enqueue RemoveFileJobRequest ([#1181](https://github.com/eclipse-openvsx/openvsx/pull/1181))
- Fix artifact poisoning ([#1186](https://github.com/eclipse-openvsx/openvsx/pull/1186))
- Run server tests conditionally ([#1192](https://github.com/eclipse-openvsx/openvsx/pull/1192))
- Update Sonar github action ([#1188](https://github.com/eclipse-openvsx/openvsx/pull/1188))
- Add workflow permissions ([#1189](https://github.com/eclipse-openvsx/openvsx/pull/1189))
- Update yarn to 4.9.1 ([#1190](https://github.com/eclipse-openvsx/openvsx/pull/1190))
- Use format specifiers ([#1193](https://github.com/eclipse-openvsx/openvsx/pull/1193))
- Fix peer requirements ([#1191](https://github.com/eclipse-openvsx/openvsx/pull/1191))
- Use dedicated exceptions ([#1194](https://github.com/eclipse-openvsx/openvsx/pull/1194))
- Update server dependencies ([#1195](https://github.com/eclipse-openvsx/openvsx/pull/1195))
- Conditional logging ([#1196](https://github.com/eclipse-openvsx/openvsx/pull/1196))
- Expected the Promise rejection reason to be an Error. ([#1197](https://github.com/eclipse-openvsx/openvsx/pull/1197))
- Add default case to switch ([#1198](https://github.com/eclipse-openvsx/openvsx/pull/1198))
- Don't use Stream.peek ([#1199](https://github.com/eclipse-openvsx/openvsx/pull/1199))
- Catch Exception instead of Throwable ([#1200](https://github.com/eclipse-openvsx/openvsx/pull/1200))
- Add missing @Override annotations ([#1201](https://github.com/eclipse-openvsx/openvsx/pull/1201))
- Remove exception in method declaration ([#1202](https://github.com/eclipse-openvsx/openvsx/pull/1202))
- Replace "as" with upper case format "AS" ([#1203](https://github.com/eclipse-openvsx/openvsx/pull/1203))
- Set defaultValue for boolean ([#1204](https://github.com/eclipse-openvsx/openvsx/pull/1204))
- Remove unused imports ([#1205](https://github.com/eclipse-openvsx/openvsx/pull/1205))
- Reduce complexity queryV2 method ([#1206](https://github.com/eclipse-openvsx/openvsx/pull/1206))
- Reduce complexity upstream browse method ([#1207](https://github.com/eclipse-openvsx/openvsx/pull/1207))
- Reduce complexity upstream asset method ([#1208](https://github.com/eclipse-openvsx/openvsx/pull/1208))
- Use SLF4J fluent API ([#1209](https://github.com/eclipse-openvsx/openvsx/pull/1209))
- Reduce getWebResource complexity ([#1210](https://github.com/eclipse-openvsx/openvsx/pull/1210))
- Reduce OrphanNamespaceMigration complexity ([#1211](https://github.com/eclipse-openvsx/openvsx/pull/1211))
- Reduce DataMirrorJobRequestHandler complexity ([#1212](https://github.com/eclipse-openvsx/openvsx/pull/1212))
- Reduce ExtensionVersionHandler complexity ([#1213](https://github.com/eclipse-openvsx/openvsx/pull/1213))
- Reduce DatabaseSearchService complexity ([#1214](https://github.com/eclipse-openvsx/openvsx/pull/1214))
- Reduce AzureDownloadCountService complexity ([#1215](https://github.com/eclipse-openvsx/openvsx/pull/1215))
- Reduce UrlUtil complexity ([#1216](https://github.com/eclipse-openvsx/openvsx/pull/1216))
- Reduce toExtensionVersionJsonV2 complexity ([#1217](https://github.com/eclipse-openvsx/openvsx/pull/1217))
- Fix missing files migration ([#1218](https://github.com/eclipse-openvsx/openvsx/pull/1218))
- Fix deprecated method calls ([#1219](https://github.com/eclipse-openvsx/openvsx/pull/1219))
- Move PAT functionality from util.ts to login.ts ([#1220](https://github.com/eclipse-openvsx/openvsx/pull/1220))
- Make feed names constant ([#1221](https://github.com/eclipse-openvsx/openvsx/pull/1221))
- Split command and options ([#1222](https://github.com/eclipse-openvsx/openvsx/pull/1222))
- Reduce nested functions in zip.ts ([#1223](https://github.com/eclipse-openvsx/openvsx/pull/1223))
- Enable XML secure processing feature to prevent XXE attacks ([#1224](https://github.com/eclipse-openvsx/openvsx/pull/1224))
- Move personal access token functionality to pat.ts ([#1225](https://github.com/eclipse-openvsx/openvsx/pull/1225))
- Use yauzl-promise instead of yauzl ([#1226](https://github.com/eclipse-openvsx/openvsx/pull/1226))
- Decrease cognitive complexity ([#1227](https://github.com/eclipse-openvsx/openvsx/pull/1227))
- Append instead of overwrite URL ([#1228](https://github.com/eclipse-openvsx/openvsx/pull/1228))
- Merge if statements and use pattern variable ([#1229](https://github.com/eclipse-openvsx/openvsx/pull/1229))
- Use entrySet when both key and value are used ([#1230](https://github.com/eclipse-openvsx/openvsx/pull/1230))
- Simplify assertions ([#1231](https://github.com/eclipse-openvsx/openvsx/pull/1231))
- Add @deprecated javadoc ([#1232](https://github.com/eclipse-openvsx/openvsx/pull/1232))
- Use nullish coalescing ([#1233](https://github.com/eclipse-openvsx/openvsx/pull/1233))
- Extension header styling ([#1242](https://github.com/eclipse-openvsx/openvsx/pull/1242))
- Delete extension using admin token ([#1248](https://github.com/eclipse-openvsx/openvsx/pull/1248))
- V0.25.0-rc.1 changes ([#1249](https://github.com/eclipse-openvsx/openvsx/pull/1249))
- Improve FixMissingFilesJobRequestHandler ([#1250](https://github.com/eclipse-openvsx/openvsx/pull/1250))

### [v0.24.0] (14/04/2025)

**This release empties the `migration_item` table. Make sure all migrations have finished before deploying it.**

#### Changed

- Added a new Github tag badge ([#1157](https://github.com/eclipse-openvsx/openvsx/pull/1157))
- Add unit tests for extensionquery flags ([#1160](https://github.com/eclipse-openvsx/openvsx/pull/1160))
- Remove cache invalidation ([#1161](https://github.com/eclipse-openvsx/openvsx/pull/1161))
- Use regular logger ([#1162](https://github.com/eclipse-openvsx/openvsx/pull/1162))
- Clean up after file resource migration ([#1166](https://github.com/eclipse-openvsx/openvsx/pull/1166))
- Relevance calc debug logging ([#1164](https://github.com/eclipse-openvsx/openvsx/pull/1164))
- Fix CVE-2025-22228 ([#1172](https://github.com/eclipse-openvsx/openvsx/pull/1172))
- Cli v0.10.2 ([#1173](https://github.com/eclipse-openvsx/openvsx/pull/1173))

#### Dependencies

- Bump @babel/runtime from 7.24.6 to 7.26.10 in /webui ([#1153](https://github.com/eclipse-openvsx/openvsx/pull/1153))
- Bump tar-fs from 2.1.1 to 2.1.2 in /cli ([#1163](https://github.com/eclipse-openvsx/openvsx/pull/1163))
- Bump webui to v0.16.2 ([#1174](https://github.com/eclipse-openvsx/openvsx/pull/1174))

### [v0.23.5] (19/03/2025)

#### Changed

- Add delete all confirmation dialog ([#1148](https://github.com/eclipse-openvsx/openvsx/pull/1148))
- Deactivate one time use publish token after extension publish ([#1149](https://github.com/eclipse-openvsx/openvsx/pull/1149))
- [Bug] Emoji is invalid ([#1150](https://github.com/eclipse-openvsx/openvsx/pull/1150))
- Remove jobs for file resources that still exist ([#1151](https://github.com/eclipse-openvsx/openvsx/pull/1151))

#### Dependencies

- Bump version ([#1152](https://github.com/eclipse-openvsx/openvsx/pull/1152))

### [v0.23.4] (18/03/2025)

#### Changed

- Async remove file resource jobs ([#1147](https://github.com/eclipse-openvsx/openvsx/pull/1147))

### [v0.23.3] (17/03/2025)

#### Changed

- Delete queued file resource jobs ([#1144](https://github.com/eclipse-openvsx/openvsx/pull/1144))
- Fix JobNotFoundException ([#1145](https://github.com/eclipse-openvsx/openvsx/pull/1145))

### [v0.23.2] (17/03/2025)

#### Changed

- Delete old migration items ([#1143](https://github.com/eclipse-openvsx/openvsx/pull/1143))

### [v0.23.1] (17/03/2025)

#### Changed

- Chunked migrations ([#1140](https://github.com/eclipse-openvsx/openvsx/pull/1140))
- Delete malicious extension and its dependencies ([#1141](https://github.com/eclipse-openvsx/openvsx/pull/1141))
- Remove file resource job rescheduling ([#1142](https://github.com/eclipse-openvsx/openvsx/pull/1142))

### [v0.23.0] (11/03/2025)

#### Changed

- Readme ([#1118](https://github.com/eclipse-openvsx/openvsx/pull/1118))
- Added Develocity setup ([#1091](https://github.com/eclipse-openvsx/openvsx/pull/1091))
- Dockerfile fix for Develocity access key ([#1124](https://github.com/eclipse-openvsx/openvsx/pull/1124))
- Check enabled malicious extensions ([#1125](https://github.com/eclipse-openvsx/openvsx/pull/1125))
- Make publish_extensions script executable ([#1128](https://github.com/eclipse-openvsx/openvsx/pull/1128))
- Enabled caching by default ([#1127](https://github.com/eclipse-openvsx/openvsx/pull/1127))
- UserAgentHeader can be null ([#1130](https://github.com/eclipse-openvsx/openvsx/pull/1130))
- Remove github token ([#1132](https://github.com/eclipse-openvsx/openvsx/pull/1132))
- Refactor: introduce `AuthUser` interface ([#838](https://github.com/eclipse-openvsx/openvsx/pull/838))
- DOMPurify default import ([#1137](https://github.com/eclipse-openvsx/openvsx/pull/1137))

#### Dependencies

- Bump dompurify from 3.1.5 to 3.2.4 in /webui ([#1123](https://github.com/eclipse-openvsx/openvsx/pull/1123))

### [v0.22.2] (20/02/2025)

#### Changed

- (Java) Parentheses should be removed ([#1111](https://github.com/eclipse-openvsx/openvsx/pull/1111))
- Nested ternary operations ([#1112](https://github.com/eclipse-openvsx/openvsx/pull/1112))
- Java modifier order ([#1113](https://github.com/eclipse-openvsx/openvsx/pull/1113))
- Remove useless curly braces around statement ([#1114](https://github.com/eclipse-openvsx/openvsx/pull/1114))
- Local variables should not shadow class fields ([#1115](https://github.com/eclipse-openvsx/openvsx/pull/1115))
- Fix a "NullPointerException" could be thrown; ([#1116](https://github.com/eclipse-openvsx/openvsx/pull/1116))
- Move replacement JSON to own method ([#1117](https://github.com/eclipse-openvsx/openvsx/pull/1117))
- Add docker deployment ([#1120](https://github.com/eclipse-openvsx/openvsx/pull/1120))

#### Dependencies

- Bump version to 0.15.1 ([#1122](https://github.com/eclipse-openvsx/openvsx/pull/1122))

### [v0.22.1] (13/02/2025)

#### Changed

- Process migrations in chunks ([#1109](https://github.com/eclipse-openvsx/openvsx/pull/1109))

### [v0.22.0] (13/02/2025)

#### Added

- Make OAuth2 configuration optional ([#1057](https://github.com/eclipse-openvsx/openvsx/pull/1057))

#### Changed

- Improve file cache key ([#1099](https://github.com/eclipse-openvsx/openvsx/pull/1099))
- Create new StreamingResponseBody ([#1101](https://github.com/eclipse-openvsx/openvsx/pull/1101))
- Remove FileResource of type resource ([#1100](https://github.com/eclipse-openvsx/openvsx/pull/1100))
- Remove unused queries ([#1102](https://github.com/eclipse-openvsx/openvsx/pull/1102))
- Fix db migration V1_50 ([#1103](https://github.com/eclipse-openvsx/openvsx/pull/1103))
- Always include score when sorting ([#1104](https://github.com/eclipse-openvsx/openvsx/pull/1104))
- Add ability to disable extension control ([#1106](https://github.com/eclipse-openvsx/openvsx/pull/1106))
- Observe /vscode/unpkg and /vscode/asset requests ([#1105](https://github.com/eclipse-openvsx/openvsx/pull/1105))
- Check that userAgentHeader is set ([#1107](https://github.com/eclipse-openvsx/openvsx/pull/1107))
- Webui v0.15.0 release ([#1108](https://github.com/eclipse-openvsx/openvsx/pull/1108))

### [v0.21.1] (03/02/2025)

#### Changed

- Webui v0.14.0 ([#1093](https://github.com/eclipse-openvsx/openvsx/pull/1093))
- Replace links to Gitter with link to Slack workspace ([#1094](https://github.com/eclipse-openvsx/openvsx/pull/1094))
- Use generated file name for file caching ([#1095](https://github.com/eclipse-openvsx/openvsx/pull/1095))
- Debug AzureDownloadCount userAgentHeader ([#1096](https://github.com/eclipse-openvsx/openvsx/pull/1096))
- Synchronize resource file writing ([#1097](https://github.com/eclipse-openvsx/openvsx/pull/1097))

### [v0.21.0] (28/01/2025)

#### Added

- Add configuration for OpenShift deployment and Eclipse Che development ([#1011](https://github.com/eclipse-openvsx/openvsx/pull/1011))

#### Changed

- Check that file exists ([#1054](https://github.com/eclipse-openvsx/openvsx/pull/1054))
- Document empty method body ([#1060](https://github.com/eclipse-openvsx/openvsx/pull/1060))
- Serializable class fields ([#1061](https://github.com/eclipse-openvsx/openvsx/pull/1061))
- Add support for AWS S3 external file storage ([#1059](https://github.com/eclipse-openvsx/openvsx/pull/1059))
- Move login to menu content ([#1056](https://github.com/eclipse-openvsx/openvsx/pull/1056))
- Add engines to Works With section ([#1058](https://github.com/eclipse-openvsx/openvsx/pull/1058))
- Replace SimpleEntry with Map.entry ([#1066](https://github.com/eclipse-openvsx/openvsx/pull/1066))
- Fix change namespace action ([#1065](https://github.com/eclipse-openvsx/openvsx/pull/1065))
- Increase mobile header menu breakpoint to lg ([#1081](https://github.com/eclipse-openvsx/openvsx/pull/1081))
- Test webresource assets ([#1071](https://github.com/eclipse-openvsx/openvsx/pull/1071))
- Fix GHSA-wc7c-xq2f-qp4h ([#1082](https://github.com/eclipse-openvsx/openvsx/pull/1082))
- Log relevance score calculation ([#1085](https://github.com/eclipse-openvsx/openvsx/pull/1085))
- Only show namespace details form when owner ([#1086](https://github.com/eclipse-openvsx/openvsx/pull/1086))
- Use detailsUrl to update namespace details ([#1087](https://github.com/eclipse-openvsx/openvsx/pull/1087))
- Make unresolved dependency query case insensitive. ([#1089](https://github.com/eclipse-openvsx/openvsx/pull/1089))
- Update express to update path-to-regexp ([#1090](https://github.com/eclipse-openvsx/openvsx/pull/1090))

#### Dependencies

- Bump dawidd6/action-download-artifact from 2 to 6 in /.github/workflows ([#1055](https://github.com/eclipse-openvsx/openvsx/pull/1055))
- Bump nanoid from 3.3.7 to 3.3.8 in /webui ([#1069](https://github.com/eclipse-openvsx/openvsx/pull/1069))

### [v0.19.1] (22/01/2025)

#### Changed

- Fix included in this release: https://github.com/eclipse/openvsx/commit/217c6230dcd5da680fd988e17c21e2db925dc294

### [v0.20.0] (20/11/2024)

#### Changed

- Serve resource files on demand ([#1045](https://github.com/eclipse-openvsx/openvsx/pull/1045))
- Remove this empty statement. ([#1046](https://github.com/eclipse-openvsx/openvsx/pull/1046))
- Fix cross-spawn vulnerability ([#1047](https://github.com/eclipse-openvsx/openvsx/pull/1047))
- Fix cross-spawn vulnerability ([#1048](https://github.com/eclipse-openvsx/openvsx/pull/1048))
- Remove redundant assertions ([#1049](https://github.com/eclipse-openvsx/openvsx/pull/1049))
- Remove redundant fragments ([#1050](https://github.com/eclipse-openvsx/openvsx/pull/1050))
- Use constants for string literals ([#1051](https://github.com/eclipse-openvsx/openvsx/pull/1051))
- Remove unused imports ([#1053](https://github.com/eclipse-openvsx/openvsx/pull/1053))
- Fix cli 0.10.1 ([#1052](https://github.com/eclipse-openvsx/openvsx/pull/1052))

### [v0.19.0] (14/11/2024)

#### Changed

- Add system provider for users ([#1038](https://github.com/eclipse-openvsx/openvsx/pull/1038))
- Add getLatest endpoint ([#1037](https://github.com/eclipse-openvsx/openvsx/pull/1037))
- Rate limit requests in load-test-extensions ([#1040](https://github.com/eclipse-openvsx/openvsx/pull/1040))

### [v0.18.1] (08/11/2024)

#### Changed

- VSCodeAPI documentation ([#1031](https://github.com/eclipse-openvsx/openvsx/pull/1031))
- Always set PublisherAgreement ([#1033](https://github.com/eclipse-openvsx/openvsx/pull/1033))
- Call VS Code adapter a compatibility layer ([#1034](https://github.com/eclipse-openvsx/openvsx/pull/1034))
- Add ServerErrorController ([#1035](https://github.com/eclipse-openvsx/openvsx/pull/1035))

### [v0.18.0] (25/10/2024)

#### Changed

- Add counter to unpkg endpoint ([#1001](https://github.com/eclipse-openvsx/openvsx/pull/1001))
- Remove deprecated search configuration properties ([#1002](https://github.com/eclipse-openvsx/openvsx/pull/1002))
- Test smoketest ([#1003](https://github.com/eclipse-openvsx/openvsx/pull/1003))
- Fix method and update github action version ([#1004](https://github.com/eclipse-openvsx/openvsx/pull/1004))
- Debug is_version ([#1005](https://github.com/eclipse-openvsx/openvsx/pull/1005))
- Fix read version ([#1006](https://github.com/eclipse-openvsx/openvsx/pull/1006))
- Compare string literals ([#1007](https://github.com/eclipse-openvsx/openvsx/pull/1007))
- Move smoketest to EclipseFdn/open-vsx.org ([#1008](https://github.com/eclipse-openvsx/openvsx/pull/1008))
- Remove file metrics ([#1009](https://github.com/eclipse-openvsx/openvsx/pull/1009))
- Add ovsx login equivalent to vsce login ([#1012](https://github.com/eclipse-openvsx/openvsx/pull/1012))
- Add packageVersion option ([#1013](https://github.com/eclipse-openvsx/openvsx/pull/1013))
- CLI 0.10.0 ([#1014](https://github.com/eclipse-openvsx/openvsx/pull/1014))
- Update cookie to 0.7.1 ([#1015](https://github.com/eclipse-openvsx/openvsx/pull/1015))
- Add AdminServiceConstructor ([#1016](https://github.com/eclipse-openvsx/openvsx/pull/1016))
- Remove public access modifier from tests ([#1017](https://github.com/eclipse-openvsx/openvsx/pull/1017))
- Remove unused imports ([#1018](https://github.com/eclipse-openvsx/openvsx/pull/1018))
- Field encapsulation ([#1019](https://github.com/eclipse-openvsx/openvsx/pull/1019))
- Restricted Identifiers should not be used as Identifiers ([#1020](https://github.com/eclipse-openvsx/openvsx/pull/1020))
- Remove wrapper annotations ([#1021](https://github.com/eclipse-openvsx/openvsx/pull/1021))
- Set role ([#1026](https://github.com/eclipse-openvsx/openvsx/pull/1026))
- Local file storage ([#1022](https://github.com/eclipse-openvsx/openvsx/pull/1022))
- Update README.md ([#1000](https://github.com/eclipse-openvsx/openvsx/pull/1000))
- Webui 0.13.0 ([#1028](https://github.com/eclipse-openvsx/openvsx/pull/1028))

### [v0.17.0] (25/09/2024)

#### Changed

- Update to express 4.21.0 ([#987](https://github.com/eclipse-openvsx/openvsx/pull/987))
- Ovsx 0.9.5 ([#994](https://github.com/eclipse-openvsx/openvsx/pull/994))
- Extension control ([#988](https://github.com/eclipse-openvsx/openvsx/pull/988))
- Remove unused observations ([#995](https://github.com/eclipse-openvsx/openvsx/pull/995))
- FileResource usage metrics ([#997](https://github.com/eclipse-openvsx/openvsx/pull/997))

#### Dependencies

- Bump express from 4.19.2 to 4.20.0 in /webui ([#985](https://github.com/eclipse-openvsx/openvsx/pull/985))

### [v0.16.4] (11/09/2024)

#### Changed

- Optimize database queries ([#976](https://github.com/eclipse-openvsx/openvsx/pull/976))
- Enable blob deletion ([#982](https://github.com/eclipse-openvsx/openvsx/pull/982))
- Release v0.16.4 ([#984](https://github.com/eclipse-openvsx/openvsx/pull/984))

#### Dependencies

- Bump micromatch from 4.0.5 to 4.0.8 in /cli ([#978](https://github.com/eclipse-openvsx/openvsx/pull/978))
- Bump webpack from 5.91.0 to 5.94.0 in /webui ([#977](https://github.com/eclipse-openvsx/openvsx/pull/977))
- Bump micromatch from 4.0.7 to 4.0.8 in /webui ([#981](https://github.com/eclipse-openvsx/openvsx/pull/981))
- Bump version, published 0.93 as next ([#986](https://github.com/eclipse-openvsx/openvsx/pull/986))

### [v0.16.3] (13/08/2024)

#### Changed

- Update bucket4j ([#974](https://github.com/eclipse-openvsx/openvsx/pull/974))

### [v0.16.2] (12/08/2024)

#### Changed

- Update Spring Boot to v3.2.4 ([#969](https://github.com/eclipse-openvsx/openvsx/pull/969))
- Update yarn to 4.3.1 ([#970](https://github.com/eclipse-openvsx/openvsx/pull/970))
- Missing express-rate-limit in Docker image ([#971](https://github.com/eclipse-openvsx/openvsx/pull/971))

### [v0.16.1] (31/07/2024)

#### Changed

- Update to Spring Boot 3.2.2 ([#967](https://github.com/eclipse-openvsx/openvsx/pull/967))

### [v0.16.0] (23/07/2024)

#### Added

- Add publisher agreement compliance check ([#954](https://github.com/eclipse-openvsx/openvsx/pull/954))

#### Changed

- Only use refresh token ([#958](https://github.com/eclipse-openvsx/openvsx/pull/958))
- Remove default universal for get operation ([#944](https://github.com/eclipse-openvsx/openvsx/pull/944))
- Replace Tomcat with Jetty ([#959](https://github.com/eclipse-openvsx/openvsx/pull/959))
- Use Jetty Server ([#960](https://github.com/eclipse-openvsx/openvsx/pull/960))
- Cli 0.9.2 ([#961](https://github.com/eclipse-openvsx/openvsx/pull/961))

#### Dependencies

- Bump braces from 3.0.2 to 3.0.3 in /cli ([#953](https://github.com/eclipse-openvsx/openvsx/pull/953))

### [v0.15.8] (24/06/2024)

#### Changed

- Generate .signature.manifest ([#937](https://github.com/eclipse-openvsx/openvsx/pull/937))
- Fix banner double button ([#940](https://github.com/eclipse-openvsx/openvsx/pull/940))
- Update signature validation logic ([#939](https://github.com/eclipse-openvsx/openvsx/pull/939))
- Fix Internal Server Error on publish ([#946](https://github.com/eclipse-openvsx/openvsx/pull/946))
- Use streams to limit memory usage ([#951](https://github.com/eclipse-openvsx/openvsx/pull/951))

### [v0.15.7] (05/06/2024)

#### Changed

- Fix extension list debounce ([#932](https://github.com/eclipse-openvsx/openvsx/pull/932))
- Don't use nullish coalescing everywhere ([#933](https://github.com/eclipse-openvsx/openvsx/pull/933))
- Webui 0.11.9 ([#934](https://github.com/eclipse-openvsx/openvsx/pull/934))

### [v0.15.6] (04/06/2024)

#### Changed

- Create signature ZIP archive ([#928](https://github.com/eclipse-openvsx/openvsx/pull/928))
- Debounce webui search ([#924](https://github.com/eclipse-openvsx/openvsx/pull/924))
- Log warning when publish fails ([#929](https://github.com/eclipse-openvsx/openvsx/pull/929))
- Add smoke tests ([#867](https://github.com/eclipse-openvsx/openvsx/pull/867))
- Fix ip SSRF improper categorization in isPublic ([#930](https://github.com/eclipse-openvsx/openvsx/pull/930))

#### Dependencies

- Bump webui version ([#931](https://github.com/eclipse-openvsx/openvsx/pull/931))

### [v0.15.5] (17/05/2024)

#### Changed

- Trim quotes from eclipse_person_id ([#919](https://github.com/eclipse-openvsx/openvsx/pull/919))

### [v0.15.4] (16/05/2024)

#### Changed

- Use optional chain expression ([#915](https://github.com/eclipse-openvsx/openvsx/pull/915))
- Use nullish coalescing ([#916](https://github.com/eclipse-openvsx/openvsx/pull/916))
- Use public profile ([#917](https://github.com/eclipse-openvsx/openvsx/pull/917))

### [v0.15.3] (15/05/2024)

#### Changed

- Hande Eclipse API expired token ([#914](https://github.com/eclipse-openvsx/openvsx/pull/914))

### [v0.15.2] (15/05/2024)

#### Changed

- Use constructor injection ([#906](https://github.com/eclipse-openvsx/openvsx/pull/906))
- Observe publish endpoint ([#910](https://github.com/eclipse-openvsx/openvsx/pull/910))
- Simplify publisher agreement ([#911](https://github.com/eclipse-openvsx/openvsx/pull/911))

### [v0.15.1] (22/04/2024)

#### Changed

- Optimize namespace details endpoint ([#900](https://github.com/eclipse-openvsx/openvsx/pull/900))
- Add verified field to search results ([#901](https://github.com/eclipse-openvsx/openvsx/pull/901))
- Get file urls in one query ([#904](https://github.com/eclipse-openvsx/openvsx/pull/904))
- Add missing fields ([#905](https://github.com/eclipse-openvsx/openvsx/pull/905))

### [v0.15.0] (17/04/2024)

#### Changed

- Display ordered list of extension versions in admin dashboard ([#886](https://github.com/eclipse-openvsx/openvsx/pull/886))
- Expose version of the registry service via API endpoint ([#860](https://github.com/eclipse-openvsx/openvsx/pull/860))
- Fix server vulnerabilities ([#891](https://github.com/eclipse-openvsx/openvsx/pull/891))
- Observe namespace details ([#894](https://github.com/eclipse-openvsx/openvsx/pull/894))
- Update cli ([#896](https://github.com/eclipse-openvsx/openvsx/pull/896))
- Remove unused ([#899](https://github.com/eclipse-openvsx/openvsx/pull/899))

#### Fixed

- Case-sensitive urls ([#890](https://github.com/eclipse-openvsx/openvsx/pull/890))

#### Dependencies

- Bump tar from 6.2.0 to 6.2.1 in /cli ([#893](https://github.com/eclipse-openvsx/openvsx/pull/893))
- Bump tar from 6.1.15 to 6.2.1 in /webui ([#892](https://github.com/eclipse-openvsx/openvsx/pull/892))
- Bump express from 4.18.2 to 4.19.2 in /webui ([#885](https://github.com/eclipse-openvsx/openvsx/pull/885))
- Bump webui ([#898](https://github.com/eclipse-openvsx/openvsx/pull/898))

### [v0.14.6] (28/03/2024)

#### Changed

- Fix expression injection in head_branch ([#876](https://github.com/eclipse-openvsx/openvsx/pull/876))
- Provide rate limit feature to prevent DOS attack ([#877](https://github.com/eclipse-openvsx/openvsx/pull/877))
- Update semver to fix security vulnerability ([#878](https://github.com/eclipse-openvsx/openvsx/pull/878))
- Simplify getCookieValueByKey ([#879](https://github.com/eclipse-openvsx/openvsx/pull/879))
- Handle 404 status when generating signature ([#880](https://github.com/eclipse-openvsx/openvsx/pull/880))
- Use database query instead of VersionService ([#882](https://github.com/eclipse-openvsx/openvsx/pull/882))
- Update cli CHANGELOG ([#883](https://github.com/eclipse-openvsx/openvsx/pull/883))

#### Dependencies

- Bump version ([#884](https://github.com/eclipse-openvsx/openvsx/pull/884))

### [v0.14.5] (20/03/2024)

#### Changed

- Docker compose file and extended documentation ([#854](https://github.com/eclipse-openvsx/openvsx/pull/854))
- Fix `sortBy` and `sortOrder` ([#861](https://github.com/eclipse-openvsx/openvsx/pull/861))
- Update webui and cli versions ([#871](https://github.com/eclipse-openvsx/openvsx/pull/871))
- Handle download NullPointerException for migrations ([#872](https://github.com/eclipse-openvsx/openvsx/pull/872))
- Optimize Sitemap ([#873](https://github.com/eclipse-openvsx/openvsx/pull/873))
- Change observed endpoints ([#874](https://github.com/eclipse-openvsx/openvsx/pull/874))

#### Dependencies

- Bump ip from 2.0.0 to 2.0.1 in /webui ([#859](https://github.com/eclipse-openvsx/openvsx/pull/859))
- Bump ip from 2.0.0 to 2.0.1 in /cli ([#858](https://github.com/eclipse-openvsx/openvsx/pull/858))
- Bump follow-redirects from 1.14.8 to 1.15.6 in /cli ([#869](https://github.com/eclipse-openvsx/openvsx/pull/869))

### [v0.14.4] (18/03/2024)

#### Changed

- Make versions endpoint case insensitive ([#852](https://github.com/eclipse-openvsx/openvsx/pull/852))
- Add application tag for monitoring ([#853](https://github.com/eclipse-openvsx/openvsx/pull/853))
- Configure tracing ([#857](https://github.com/eclipse-openvsx/openvsx/pull/857))

### [v0.14.3] (29/01/2024)

#### Changed

- Review public id updates ([#851](https://github.com/eclipse-openvsx/openvsx/pull/851))

### [v0.14.2] (11/12/2023)

#### Changed

- Fix "unique_extension_public_id" unique constraint violation ([#822](https://github.com/eclipse-openvsx/openvsx/pull/822))
- Sonar config ([#829](https://github.com/eclipse-openvsx/openvsx/pull/829))
- Java analysis overwrites typescript analysis ([#830](https://github.com/eclipse-openvsx/openvsx/pull/830))
- Configure java tests and libraries ([#831](https://github.com/eclipse-openvsx/openvsx/pull/831))
- Save PR number ([#832](https://github.com/eclipse-openvsx/openvsx/pull/832))
- Disable build steps to speed up debugging ([#833](https://github.com/eclipse-openvsx/openvsx/pull/833))
- Sonar use absolute paths to java libraries ([#834](https://github.com/eclipse-openvsx/openvsx/pull/834))
- Add test coverage ([#835](https://github.com/eclipse-openvsx/openvsx/pull/835))
- Copy libraries after running gradle ([#836](https://github.com/eclipse-openvsx/openvsx/pull/836))
- Add unittests for admin stats job to find negative downloads bug ([#843](https://github.com/eclipse-openvsx/openvsx/pull/843))

#### Dependencies

- Bump webui to version 0.11.2 ([#821](https://github.com/eclipse-openvsx/openvsx/pull/821))

### [v0.14.1] (26/10/2023)

#### Changed

- Fix yarn publish commands ([#800](https://github.com/eclipse-openvsx/openvsx/pull/800))
- Fix gitpod workspace ([#806](https://github.com/eclipse-openvsx/openvsx/pull/806))
- Simplify license logic ([#807](https://github.com/eclipse-openvsx/openvsx/pull/807))
- Simplify admin statistics ([#814](https://github.com/eclipse-openvsx/openvsx/pull/814))
- Update cli to yarn 4.0.0 ([#820](https://github.com/eclipse-openvsx/openvsx/pull/820))

#### Dependencies

- Bump webui to 0.11.1 ([#801](https://github.com/eclipse-openvsx/openvsx/pull/801))
- Bump get-func-name from 2.0.0 to 2.0.2 in /webui ([#812](https://github.com/eclipse-openvsx/openvsx/pull/812))
- Bump postcss from 8.4.24 to 8.4.31 in /webui ([#815](https://github.com/eclipse-openvsx/openvsx/pull/815))

### [v0.14.0] (08/08/2023)

#### Changed

- Exclude gatling-charts-highcharts ([#790](https://github.com/eclipse-openvsx/openvsx/pull/790))
- Add recommended files to the repo ([#794](https://github.com/eclipse-openvsx/openvsx/pull/794))
- Add NOTICE.md to repo ([#797](https://github.com/eclipse-openvsx/openvsx/pull/797))
- Lower allVersions and default response size from 200 items to 100 items ([#796](https://github.com/eclipse-openvsx/openvsx/pull/796))
- React version upgrade? ([#793](https://github.com/eclipse-openvsx/openvsx/pull/793))
- Update development documentation for elastic search ([#791](https://github.com/eclipse-openvsx/openvsx/pull/791))

#### Dependencies

- Bump word-wrap from 1.2.3 to 1.2.4 in /cli ([#787](https://github.com/eclipse-openvsx/openvsx/pull/787))
- Bump word-wrap from 1.2.3 to 1.2.4 in /webui ([#788](https://github.com/eclipse-openvsx/openvsx/pull/788))
- Bump semver from 6.3.0 to 6.3.1 in /webui ([#798](https://github.com/eclipse-openvsx/openvsx/pull/798))
- Bump cli to 0.8.3 ([#799](https://github.com/eclipse-openvsx/openvsx/pull/799))

### [v0.13.4] (18/07/2023)

#### Changed

- Fix v0.13.1 ([#786](https://github.com/eclipse-openvsx/openvsx/pull/786))

### [v0.13.3] (18/07/2023)

#### Changed

- Use job instead of @Async ([#785](https://github.com/eclipse-openvsx/openvsx/pull/785))

### [v0.13.2] (15/07/2023)

#### Changed

- Update review list right after user posting/revoking ([#761](https://github.com/eclipse-openvsx/openvsx/pull/761))
- Showing download count of extensions, make empty rating stars less attractive ([#742](https://github.com/eclipse-openvsx/openvsx/pull/742))
- Update gradle plugins ([#762](https://github.com/eclipse-openvsx/openvsx/pull/762))
- Try load asset from extension.vsixmanifest ([#767](https://github.com/eclipse-openvsx/openvsx/pull/767))
- Use download.run ([#771](https://github.com/eclipse-openvsx/openvsx/pull/771))
- Make namespace and extension query case-insensitive ([#772](https://github.com/eclipse-openvsx/openvsx/pull/772))
- -name is not a valid option ([#769](https://github.com/eclipse-openvsx/openvsx/pull/769))
- Update @vscode/vsce to 2.19.0 ([#775](https://github.com/eclipse-openvsx/openvsx/pull/775))
- Error when publish the second version of the same extension ([#774](https://github.com/eclipse-openvsx/openvsx/pull/774))

#### Dependencies

- Bump semver from 5.7.1 to 7.5.2 in /cli ([#763](https://github.com/eclipse-openvsx/openvsx/pull/763))
- Bump semver from 6.3.0 to 6.3.1 in /webui ([#778](https://github.com/eclipse-openvsx/openvsx/pull/778))

### [v0.13.1] (20/06/2023)

#### Changed

- Remove unique review constraint ([#760](https://github.com/eclipse-openvsx/openvsx/pull/760))

### [v0.13.0] (20/06/2023)

#### Changed

- Updated jOOQ classes ([#748](https://github.com/eclipse-openvsx/openvsx/pull/748))
- Add paging to query endpoints ([#754](https://github.com/eclipse-openvsx/openvsx/pull/754))
- Enforce implicit unique constraints in database ([#756](https://github.com/eclipse-openvsx/openvsx/pull/756))
- Cleanup admin statistics endpoints ([#757](https://github.com/eclipse-openvsx/openvsx/pull/757))
- Upgrade to Java 17 ([#759](https://github.com/eclipse-openvsx/openvsx/pull/759))

#### Dependencies

- Bump webui to 0.9.6 ([#747](https://github.com/eclipse-openvsx/openvsx/pull/747))

### [v0.12.0] (29/05/2023)

#### Changed

- Add `semver` dependency to the `ovsx` package ([#733](https://github.com/eclipse-openvsx/openvsx/pull/733))
- Evict all latest extension versions ([#734](https://github.com/eclipse-openvsx/openvsx/pull/734))
- Only check if host is not empty for http and https URLs ([#741](https://github.com/eclipse-openvsx/openvsx/pull/741))
- Fix search icon contrast issues ([#730](https://github.com/eclipse-openvsx/openvsx/pull/730))
- Deprecate allVersions map ([#746](https://github.com/eclipse-openvsx/openvsx/pull/746))
- //open-vsx.org/admin/report API returning 500 ([#737](https://github.com/eclipse-openvsx/openvsx/pull/737))

#### Dependencies

- Bump ovsx to 0.8.1 ([#735](https://github.com/eclipse-openvsx/openvsx/pull/735))

### [v0.11.1] (18/04/2023)

#### Changed

- Release v0.11.0 fixes ([#728](https://github.com/eclipse-openvsx/openvsx/pull/728))

### [v0.11.0] (18/04/2023)

#### Changed

- Use AutoCloseable TempFile ([#725](https://github.com/eclipse-openvsx/openvsx/pull/725))
- Extension repository signing ([#673](https://github.com/eclipse-openvsx/openvsx/pull/673))

### [v0.10.0] (06/04/2023)

#### Changed

- Replace `@Scheduled` with JobRunr recurring job ([#721](https://github.com/eclipse-openvsx/openvsx/pull/721))
- [Feature] Get the checksum of the VSIX file from the API ([#678](https://github.com/eclipse-openvsx/openvsx/pull/678))
- Log admin report queries ([#722](https://github.com/eclipse-openvsx/openvsx/pull/722))
- Inefficient delete extension code?  ([#723](https://github.com/eclipse-openvsx/openvsx/pull/723))
- Also rename checksum file on namespace change ([#724](https://github.com/eclipse-openvsx/openvsx/pull/724))

### [v0.9.7] (01/04/2023)

#### Changed

- Merge ExtensionVersion before persisting FileResource ([#719](https://github.com/eclipse-openvsx/openvsx/pull/719))

### [v0.9.6] (29/03/2023)

#### Changed

- Add set up guide for MacOS ([#671](https://github.com/eclipse-openvsx/openvsx/pull/671))
- Need to deal with collisions when renaming and merging namespaces ([#717](https://github.com/eclipse-openvsx/openvsx/pull/717))

### [v0.9.5] (22/03/2023)

#### Changed

- Delete temp file after use ([#701](https://github.com/eclipse-openvsx/openvsx/pull/701))
- Deprecate `/api/-/query` POST endpoint ([#700](https://github.com/eclipse-openvsx/openvsx/pull/700))
- Move around some imports to rebuild postQuery Docker image ([#702](https://github.com/eclipse-openvsx/openvsx/pull/702))
- Fix docs: only use straight quotes ([#704](https://github.com/eclipse-openvsx/openvsx/pull/704))
- Renaming a namespace appears to leave incorrect URLs. ([#708](https://github.com/eclipse-openvsx/openvsx/pull/708))
- Download count update partition ElasticSearch search entries update ([#710](https://github.com/eclipse-openvsx/openvsx/pull/710))
- Delete access token not allowed to load tokens ([#713](https://github.com/eclipse-openvsx/openvsx/pull/713))
- Fix 'Statement inside of curly braces should be on next line' ([#714](https://github.com/eclipse-openvsx/openvsx/pull/714))
- Remove trailing space ([#715](https://github.com/eclipse-openvsx/openvsx/pull/715))

### [v0.9.4] (14/03/2023)

#### Changed

- Unable to delete namespace logo ([#696](https://github.com/eclipse-openvsx/openvsx/pull/696))

### [v0.9.3] (09/03/2023)

#### Changed

- Create new StringBuilder ([#692](https://github.com/eclipse-openvsx/openvsx/pull/692))
- Log query string too ([#693](https://github.com/eclipse-openvsx/openvsx/pull/693))
- Corrupted metadata extension in some extensions regarding targetPlatform ([#687](https://github.com/eclipse-openvsx/openvsx/pull/687))
- Unable to update namespace details ([#694](https://github.com/eclipse-openvsx/openvsx/pull/694))

### [v0.9.1] (06/03/2023)

#### Changed

- Use explicit joins to improve query performance ([#683](https://github.com/eclipse-openvsx/openvsx/pull/683))
- Use IN clauses instead of JOIN for FileResource jOOQ queries ([#685](https://github.com/eclipse-openvsx/openvsx/pull/685))
- Delay running migrations ([#686](https://github.com/eclipse-openvsx/openvsx/pull/686))

### [v0.9.0] (26/02/2023)

#### Added

- OVSX - Support skip if already published ([#646](https://github.com/eclipse-openvsx/openvsx/pull/646))

#### Changed

- Migrate from deprecated `vsce` to `@vscode/vsce` ([#637](https://github.com/eclipse-openvsx/openvsx/pull/637))
- Update cli CHANGELOG ([#638](https://github.com/eclipse-openvsx/openvsx/pull/638))
- Log long running requests ([#639](https://github.com/eclipse-openvsx/openvsx/pull/639))
- /api/-/query response size ([#454](https://github.com/eclipse-openvsx/openvsx/pull/454))
- Unintended vsix resource name generation changed #574 ([#589](https://github.com/eclipse-openvsx/openvsx/pull/589))
- Remove Equinusocio.vsc-material-theme-30.0.0.vsix ([#640](https://github.com/eclipse-openvsx/openvsx/pull/640))
- Missing property LocalizedLanguages in vscode-language-pack-zh-hans ext ([#643](https://github.com/eclipse-openvsx/openvsx/pull/643))
- Ovsx 0.8.0 ([#647](https://github.com/eclipse-openvsx/openvsx/pull/647))
- Search via UI omits second page of ten search results ([#652](https://github.com/eclipse-openvsx/openvsx/pull/652))
- Implement sponsor link ([#642](https://github.com/eclipse-openvsx/openvsx/pull/642))
- Can't search @category:"language packs" correctly in Code - OSS ([#658](https://github.com/eclipse-openvsx/openvsx/pull/658))
- Search should not be case sensitive ([#656](https://github.com/eclipse-openvsx/openvsx/pull/656))
- Add restAPI for downloading latest version of an extension ([#657](https://github.com/eclipse-openvsx/openvsx/pull/657))
- Mirror feature ([#586](https://github.com/eclipse-openvsx/openvsx/pull/586))
- Missing VsixManifest asset type for extension versions ([#665](https://github.com/eclipse-openvsx/openvsx/pull/665))
- //open-vsx.org/admin/report API returning 500 ([#666](https://github.com/eclipse-openvsx/openvsx/pull/666))
- Added download terms to page settings object, use in detailed overview ([#252](https://github.com/eclipse-openvsx/openvsx/pull/252))
- Fix Map.of doesn't allow null values ([#668](https://github.com/eclipse-openvsx/openvsx/pull/668))
- Take amount of reviews into account when sorting by Rating ([#672](https://github.com/eclipse-openvsx/openvsx/pull/672))
- Access token dialog improvements ([#661](https://github.com/eclipse-openvsx/openvsx/pull/661))
- Search bar improvements ([#662](https://github.com/eclipse-openvsx/openvsx/pull/662))
- [Admin] Add Ability to Change Namespace ([#469](https://github.com/eclipse-openvsx/openvsx/pull/469))
- Add a way to proxy upstream requests instead of external links ([#460](https://github.com/eclipse-openvsx/openvsx/pull/460))
- Insert unique extension public id ([#677](https://github.com/eclipse-openvsx/openvsx/pull/677))
- Improve information from search result ([#676](https://github.com/eclipse-openvsx/openvsx/pull/676))
- Export NamespaceDetailComponent ([#679](https://github.com/eclipse-openvsx/openvsx/pull/679))
- Move mainHeadTags to main.tsx ([#680](https://github.com/eclipse-openvsx/openvsx/pull/680))
- Merge entity before calling remove ([#681](https://github.com/eclipse-openvsx/openvsx/pull/681))
- Read input stream before try to get VSIX manifest from zip file ([#682](https://github.com/eclipse-openvsx/openvsx/pull/682))

#### Dependencies

- Bump express from 4.17.1 to 4.17.3 in /webui ([#636](https://github.com/eclipse-openvsx/openvsx/pull/636))
- Bump json5 from 1.0.1 to 1.0.2 in /webui ([#648](https://github.com/eclipse-openvsx/openvsx/pull/648))
- Bump webui version to 0.7.1 ([#654](https://github.com/eclipse-openvsx/openvsx/pull/654))

### [v0.7.0] (08/12/2022)

#### Changed

- Improve search in VS Code ([#611](https://github.com/eclipse-openvsx/openvsx/pull/611))
- Another round of optimizing transactions. ([#633](https://github.com/eclipse-openvsx/openvsx/pull/633))
- Unknown option '--no-dependencies' ([#635](https://github.com/eclipse-openvsx/openvsx/pull/635))

#### Dependencies

- Bump minimatch from 3.0.4 to 3.1.2 in /webui ([#630](https://github.com/eclipse-openvsx/openvsx/pull/630))

### [v0.6.0] (30/11/2022)

### [v0.1.0] (13/04/2021)

First release of Open VSX with the Eclipse Foundation.
