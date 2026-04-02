# Changelog

## xxx - unreleased


## v0.29.1 - 30/10/2025

This release of Open VSX consists of:
 * [ovsx CLI v0.10.6](https://www.npmjs.com/package/ovsx/v/0.10.6)
 * [openvsx-webui frontend library v0.16.4](https://www.npmjs.com/package/openvsx-webui/v/0.16.4)
 * [openvsx-server Docker image v0.29.1](https://github.com/eclipse/openvsx/pkgs/container/openvsx-server/560869451?tag=v0.29.1)
 * [openvsx-webui Docker image v0.29.1](https://github.com/eclipse/openvsx/pkgs/container/openvsx-webui/560869529?tag=v0.29.1)

### Changed

* Update springdoc by @amvanbaren in [#1381](https://github.com/eclipse/openvsx/pull/1381)

## v0.29.0 - 16/10/2025

This release of Open VSX consists of:
 * [ovsx CLI v0.10.6](https://www.npmjs.com/package/ovsx/v/0.10.6)
 * [openvsx-webui frontend library v0.16.4](https://www.npmjs.com/package/openvsx-webui/v/0.16.4)
 * [openvsx-server Docker image v0.29.0](https://github.com/eclipse/openvsx/pkgs/container/openvsx-server/546469427?tag=v0.29.0)
 * [openvsx-webui Docker image v0.29.0](https://github.com/eclipse/openvsx/pkgs/container/openvsx-webui/546469528?tag=v0.29.0)

### Added

* Caffeine cache fallback by @amvanbaren in https://github.com/eclipse/openvsx/pull/1361
* Add logging to CacheConfig by @amvanbaren in https://github.com/eclipse/openvsx/pull/1367

## v0.28.0 - 30/09/2025

This release of Open VSX consists of:
 * [ovsx CLI v0.10.6](https://www.npmjs.com/package/ovsx/v/0.10.6)
 * [openvsx-webui frontend library v0.16.4](https://www.npmjs.com/package/openvsx-webui/v/0.16.4)
 * [openvsx-server Docker image v0.28.0](https://github.com/eclipse/openvsx/pkgs/container/openvsx-server/530374904?tag=v0.28.0)
 * [openvsx-webui Docker image v0.28.0](https://github.com/eclipse/openvsx/pkgs/container/openvsx-webui/530374977?tag=v0.28.0)

## Changes

* Update Spring Boot to 3.5.3 by @amvanbaren in https://github.com/eclipse/openvsx/pull/1288
* chore: update the deployment instructions for OpenShift by @svor in https://github.com/eclipse/openvsx/pull/1312
* Bump form-data from 4.0.0 to 4.0.4 in /cli by @dependabot[bot] in https://github.com/eclipse/openvsx/pull/1291
* Bump tmp from 0.2.3 to 0.2.4 in /cli by @dependabot[bot] in https://github.com/eclipse/openvsx/pull/1304
* Makes the web UI auto-reload (with logs) on local dev with docker-compose by @davidgomes in https://github.com/eclipse/openvsx/pull/1318
* chore: bump org.springframework.boot to 3.5.5 by @svor in https://github.com/eclipse/openvsx/pull/1325
* Cache /vscode/gallery/{namespaceName}/{extensionName}/latest by @amvanbaren in https://github.com/eclipse/openvsx/pull/1303
* Prefix personal access token by @amvanbaren in https://github.com/eclipse/openvsx/pull/1339
* Add allowed versions to EclipseService by @amvanbaren in https://github.com/eclipse/openvsx/pull/1347
* chore: bump springframework-boot to 3.5.6 version by @svor in https://github.com/eclipse/openvsx/pull/1345
* Redis Caching by @amvanbaren in https://github.com/eclipse/openvsx/pull/1277
* cli v0.10.6 by @amvanbaren in https://github.com/eclipse/openvsx/pull/1352
* Bump tar-fs from 2.1.3 to 2.1.4 in /cli by @dependabot[bot] in https://github.com/eclipse/openvsx/pull/1353
* Add tar-fs upgrade to CHANGELOG by @amvanbaren in https://github.com/eclipse/openvsx/pull/1354

## v0.27.0 - 08/07/2025

This release of Open VSX consists of:
 * [ovsx CLI](https://www.npmjs.com/package/ovsx/v/0.10.5)
 * [openvsx-webui frontend library](https://www.npmjs.com/package/openvsx-webui/v/0.16.4)
 * [openvsx-server Docker image](https://github.com/eclipse/openvsx/pkgs/container/openvsx-server/456165768?tag=v0.27.0)
 * [openvsx-webui Docker image](https://github.com/eclipse/openvsx/pkgs/container/openvsx-webui/456165856?tag=v0.27.0)

## Changes

* Find latest active extension version for search by @amvanbaren in https://github.com/eclipse/openvsx/pull/1256
* Make configurable whether migrations only run once per version. by @amvanbaren in https://github.com/eclipse/openvsx/pull/1266
* Remove BlockHostFilter by @amvanbaren in https://github.com/eclipse/openvsx/pull/1268
* chore: upgrade gradle from 8.1.1 to 8.3 by @svor in https://github.com/eclipse/openvsx/pull/1257
* Bump brace-expansion from 1.1.11 to 1.1.12 in /cli by @dependabot in https://github.com/eclipse/openvsx/pull/1261
* cli: Update CHANGELOG and bump version by @amvanbaren in https://github.com/eclipse/openvsx/pull/1273
* Improve search by @amvanbaren in https://github.com/eclipse/openvsx/pull/1275
* chore: add group Gradle property; add maven-publish plugin by @svor in https://github.com/eclipse/openvsx/pull/1274
* cli CHANGELOG for v0.10.5 by @amvanbaren in https://github.com/eclipse/openvsx/pull/1278
* webui: don't assume ErrorResult in tryResolveNamespaceError by @amvanbaren in https://github.com/eclipse/openvsx/pull/1281

## v0.26.0 - 03/06/2025

This release of Open VSX consists of:
 * [ovsx CLI v0.10.4](https://www.npmjs.com/package/ovsx/v/0.10.4)
 * [openvsx-webui frontend library v0.16.3](https://www.npmjs.com/package/openvsx-webui/v/0.16.3)
 * [openvsx-server Docker image v0.26.0](https://github.com/orgs/eclipse/packages/container/openvsx-server/429972698?tag=v0.26.0)
 * [openvsx-webui Docker image v0.26.0](https://github.com/orgs/eclipse/packages/container/openvsx-webui/429972726?tag=v0.26.0)

## Changes

* Add BlockHostFilter by @amvanbaren in https://github.com/eclipse/openvsx/pull/1253
* Bump tar-fs from 2.1.2 to 2.1.3 in /cli by @dependabot in https://github.com/eclipse/openvsx/pull/1252
* chore: Fix CVE-2025-22235 by upgrading org.springframework.boot to 3.3.11 by @svor in https://github.com/eclipse/openvsx/pull/1245
* cli 0.10.4 by @amvanbaren in https://github.com/eclipse/openvsx/pull/1254
