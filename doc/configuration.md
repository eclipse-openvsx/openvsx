# Open VSX Configuration Properties

The configuration properties the Open VSX server understands, beyond the ones Spring Boot itself
offers. See [Deploying Open VSX](deployment.md) for where to put them and how to run a deployment.

This section describes the special configuration properties supported by the Open VSX server.

## Registry

| Property      | `ovsx.registry.version`
|---------------|-----------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.15.0

The version of the running Open VSX registry instance.

## Publishing

| Property      | `ovsx.publishing.require-license`
|---------------|-----------------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.1.0

Whether published extensions are required to have a license. If active, unlicensed extensions are rejected.

| Property      | `ovsx.publishing.max-content-size`
|---------------|-----------------------------------
| Type          | int
| Default       | `512 * 1024 * 1024` = `512MB`
| Compatibility | Since 0.31.0

The maximum content size the server accepts when publishing an extension.

| Property      | `ovsx.publishing.unsupported-icon-formats`
|---------------|-----------------------------------
| Type          | string[]
| Default       | `svg`
| Compatibility | Since 0.34.1

The list of extensions that are not allowed as icons.

## Web UI

| Property      | `ovsx.webui.url`
|---------------|------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Base URL of the web UI. This is required only if it's different from the server.

| Property      | `ovsx.webui.frontendRoutes`
|---------------|-----------------------------
| Type          | string[]
| Default       | `/extension/**,/namespace/**,/user-settings/**,/admin-dashboard/**`
| Compatibility | Since 0.1.0

Routes to be forwarded to `/` because they are handled by the frontend.

## Search Options

| Property      | `ovsx.search.relevance.rating`
|---------------|---------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.2.0

Weight of user ratings for computing relevance. This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.search.relevance.downloads`
|---------------|------------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.2.0

Weight of download counts for computing relevance. This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.search.relevance.timestamp`
|---------------|------------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.2.0

Weight of publishing timestamps for computing relevance (newer extensions are ranked higher). This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.search.relevance.unverified`
|---------------|------------------------------------------
| Type          | double
| Default       | `0.5`
| Compatibility | Since 0.2.0

Relevance factor for unverified extension versions. The combined relevance from the `averageRating`, `downloadCount` and `timestamp` criteria is multiplied with this value if the publisher of the extension is not a member of the extension's namespace or the namespace has no owner.

| Property      | `ovsx.search.relevance.deprecated`
|---------------|------------------------------------------
| Type          | double
| Default       | `0.5`
| Compatibility | Since 0.17.0

Relevance factor for deprecated extension versions. The combined relevance from the `averageRating`, `downloadCount` and `timestamp` criteria is multiplied with this value if the extension is deprecated.

## Elasticsearch

| Property      | `ovsx.elasticsearch.enabled`
|---------------|------------------------------
| Type          | boolean
| Default       | `true`
| Compatibility | Since 0.1.0

Whether to enable search functionality through Elasticsearch. By switching this off, it is not necessary to deploy Elasticsearch. Cannot be used together with `ovsx.databasesearch.enabled`.

| Property      | `ovsx.elasticsearch.clear-on-start`
|---------------|-------------------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.1.0

Whether to clear and rebuild the search index on startup. If disabled, the index is built only if it does not exist yet. Rebuilding the search index may take several minutes if there are many extensions.

| Property      | `ovsx.elasticsearch.host`
|---------------|---------------------------
| Type          | string
| Default       | `localhost:9200`
| Compatibility | Since 0.1.0

Host and port of the Elasticsearch instance.

| Property      | `ovsx.elasticsearch.ssl`
|---------------|--------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.1.0

Whether to connect with SSL.

| Property      | `ovsx.elasticsearch.username`
|---------------|-------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Username for basic authentication.

| Property      | `ovsx.elasticsearch.password`
|---------------|-------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Password for basic authentication.

| Property      | `ovsx.elasticsearch.truststore`
|---------------|---------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Path to a trust store file for SSL connection.

| Property      | `ovsx.elasticsearch.truststoreProtocol`
|---------------|-----------------------------------------
| Type          | string
| Default       | `TLSv1.2`
| Compatibility | Since 0.1.0

Protocol for SSL connection.

| Property      | `ovsx.elasticsearch.truststorePassword`
|---------------|-----------------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Password for trust store file.

| Property      | `ovsx.elasticsearch.relevance.rating`
|---------------|---------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.1.0, **deprecated** in 0.2.0, **removed** in 0.18.0 (use `ovsx.search.relevance.rating`)

Weight of user ratings for computing relevance. This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.elasticsearch.relevance.downloads`
|---------------|------------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.1.0, **deprecated** in 0.2.0, **removed** in 0.18.0 (use `ovsx.search.relevance.downloads`)

Weight of download counts for computing relevance. This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.elasticsearch.relevance.timestamp`
|---------------|------------------------------------------
| Type          | double
| Default       | `1.0`
| Compatibility | Since 0.1.0, **deprecated** in 0.2.0, **removed** in 0.18.0 (use `ovsx.search.relevance.timestamp`)

Weight of publishing timestamps for computing relevance (newer extensions are ranked higher). This has an impact on the order of search results when `sortBy` is set to `relevance`.

| Property      | `ovsx.elasticsearch.relevance.unverified`
|---------------|------------------------------------------
| Type          | double
| Default       | `0.5`
| Compatibility | Since 0.1.0, **deprecated** in 0.2.0, **removed** in 0.18.0 (use `ovsx.search.relevance.unverified`)

Relevance factor for unverified extension versions. The combined relevance from the `averageRating`, `downloadCount` and `timestamp` criteria is multiplied with this value if the publisher of the extension is not a member of the extension's namespace or the namespace has no owner.

## Database Search

| Property      | `ovsx.databasesearch.enabled`
|---------------|------------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.2.0

Whether to enable search functionality though DB queries. Cannot be used together with `ovsx.elasticsearch.enabled`.

## File Storage

| Property      | `ovsx.storage.azure.service-endpoint`
|---------------|---------------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Azure blob service endpoint URL (without parameters, must end with a slash). This is required in order to enable the Azure storage service. Example: `https://openvsx.blob.core.windows.net/`.

| Property      | `ovsx.storage.azure.sas-token`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

The full query string containing the Azure SAS (Shared Access Signature) token.

| Property      | `ovsx.storage.azure.blob-container`
|---------------|-------------------------------------
| Type          | string
| Default       | `openvsx-resources`
| Compatibility | Since 0.1.0

Name of the Azure blob container.

| Property      | `ovsx.storage.gcp.project-id`
|---------------|-------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

GCP project id. This can be omitted if the GCP client is able to detect the project from the environment.

| Property      | `ovsx.storage.gcp.bucket-id`
|---------------|------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

GCP bucket id. This is required in order to enable the Google Cloud storage service. Note that in order to upload files you need to authenticate with the storage service, e.g. by putting service account credentials into a file and pointing the environment variable `GOOGLE_APPLICATION_CREDENTIALS` to that file. Unauthenticated access is possible when migrating from GCP to another storage provider.

| Property | `ovsx.storage.aws.access-key-id` |
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

AWS Access Key ID used to authenticate the OpenVSX storage client.

| Property | `ovsx.storage.aws.secret-access-key` |
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

AWS Secret Access Key paired with the Access Key ID. Must be provided to enable AWS S3 storage.

| Property | `ovsx.storage.aws.session-token` |
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

AWS session token to provide access for only a limited amount of time.

| Property | `ovsx.storage.aws.region`|
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

AWS region of the S3 bucket. Example: us-east-2.

| Property | `ovsx.storage.aws.bucket` |
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

Name of the S3 bucket used to store uploaded VSIX files. The bucket must already exist.

| Property | `ovsx.storage.aws.service-endpoint` |
|----------|-------------------------------------|
| Type | string |
| Default |  |
| Compatibility | Since 0.21.0 |

S3 service endpoint URL (must not contain query parameters).
Required for enabling AWS storage.
Example: https://s3.us-east-2.amazonaws.com.

| Property | `ovsx.storage.aws.path-style-access` |
|----------|-------------------------------------|
| Type | boolean |
| Default | false |
| Compatibility | Since 0.21.0 |

Whether to use a path-style endpoint where the bucket name is part of the path.

| Property      | `ovsx.storage.primary-service`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

External storage service to use if multiple are active (`azure-blob` or `google-cloud`). All files that are not in the primary service are automatically migrated on application startup.

| Property      | `ovsx.storage.external-resource-types`
|---------------|----------------------------------------
| Type          | string[]
| Default       | `*`
| Compatibility | Since 0.1.0

Resource types to store in an external storage provider, or `*` for all types. Possible values are `download` (i.e. the `vsix` file), `manifest`, `icon`, `readme`, `license`, `changelog`, `vsixmanifest`, `sha256`, `signature`, `publicKey`, `resource` and `namespace-logo`. If only a subset of those types is chosen, the remaining types are stored locally. Up to version `0.17.0` files are stored as byte arrays in the database. From version `0.18.0` files are stored in the local file system. The `ovsx.storage.local.directory` property must also be configured for this feature to work.

| Property      | `ovsx.storage.migration.enabled`
|---------------|--------------------------------
| Type          | boolean
| Default       | `true`
| Compatibility | Since 0.30.0

Whether to run storage type migration.

| Property      | `ovsx.storage.migration-delay`
|---------------|--------------------------------
| Type          | long
| Default       | `500`
| Compatibility | Since 0.1.0

Delay in milliseconds between storage type migration of each file. This delay is important to avoid excessive load in the server application, since migration is performed by the server itself on startup. Longer delays decrease server load, but increase the total duration of file migration.

| Property      | `ovsx.storage.local.directory`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.18.0

Base directory for local file storage. This is required in order to enable the local storage service.

| Property      | `ovsx.storage.cdn.enabled`
|---------------|--------------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.31.0

Whether a CDN front URL shall be used for configured storage providers.

| Property      | `ovsx.storage.cdn.services`
|---------------|--------------------------------
| Type          | map
| Default       | `{}`
| Compatibility | Since 0.31.0

Key/Value Pairs in the format `<storage provider>`:`<CDN front URL>` to configure for each storage provider a specific CDN front URL.
Example:

```
ovsx:
  storage:
    cdn:
      services:
        aws: https://my-aws.cdn.url
```

Supported storage providers: `aws`, `azure`, `gcp`.

## AWS Download Logs

| Property      | `ovsx.logs.aws.bucket`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.31.0

Name of the S3 bucket used to store uploaded log files. The bucket must already exist.

The same credentials as defined in `ovsx.storage.aws` will be used to access the S3 log bucket.

| Property      | `ovsx.logs.aws.log-location-prefix`
|---------------|--------------------------------
| Type          | string
| Default       | `AWSLogs/`
| Compatibility | Since 0.31.0

The log location prefix to use for searching available logs in the defined bucket.

| Property      | `ovsx.logs.aws.format`
|---------------|--------------------------------
| Type          | string
| Default       | cloudfront
| Compatibility | Since 0.32.0

The type of log format to process. Supports `cloudfront` or `fastly`.

| Property      | `ovsx.logs.aws.cron`
|---------------|--------------------------------
| Type          | string
| Default       | 0 10 * * * *
| Compatibility | Since 0.32.0

The schedule in crontab format to run the AWS download logs job.

## Azure Download Logs

| Property      | `ovsx.logs.azure.sas-token`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.6.0

The full query string containing the Azure SAS (Shared Access Signature) token to get Azure download logs.

| Property      | `ovsx.logs.azure.service-endpoint`
|---------------|--------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.6.0

Azure download logs blob service endpoint URL. This is required in order to enable the Azure download count service.

| Property      | `ovsx.logs.azure.blob-container`
|---------------|--------------------------------
| Type          | string
| Default       | `insights-logs-storageread`
| Compatibility | Since 0.6.0

Name of the Azure download logs blob container.

| Property      | `ovsx.logs.azure.cron`
|---------------|--------------------------------
| Type          | string
| Default       | 0 5 * * * *
| Compatibility | Since 0.32.0

The schedule in crontab format to run the Azure download logs job.

## Upstream Registry

| Property      | `ovsx.upstream.url`
|---------------|---------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Base URL of the [upstream registry instance](deployment.md#upstream-registry-instance).

## VS Code

| Property      | `ovsx.vscode.upstream.gallery-url`
|---------------|------------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Gallery URL of a registry instance from which to fetch extension UUIDs. These UUIDs are required by VS Code to identify and auto-update installed extensions. If no upstream gallery is set, random UUIDs are generated for all published extensions.

| Property      | `ovsx.vscode.upstream.update-on-start`
|---------------|------------------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.14.2

Whether to update public ids on startup from the upstream registry instance.

## Eclipse

| Property      | `ovsx.eclipse.base-url`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Base URL of the Eclipse API.

| Property      | `ovsx.eclipse.publisher-agreement.version`
|---------------|--------------------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0

Current version of the Eclipse Publisher Agreement. Since 0.28.0 only used to sign Eclipse Publisher Agreement.

| Property      | `ovsx.eclipse.publisher-agreement.allowed-versions`
|---------------|--------------------------------------------
| Type          | string[]
| Default       |
| Compatibility | Since 0.28.0

Allowed versions of the Eclipse Publisher Agreement. Used to check Eclipse Publisher Agreement.

| Property      | `ovsx.eclipse.publisher-agreement.timezone`
|---------------|---------------------------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.1.0, **removed** in 0.15.2

`java.time.ZoneId` for timestamps returned by the Eclipse API.

| Property      | `ovsx.eclipse.check-compliance-on-start`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.1.0

Whether to check for publisher compliance on startup. A publisher is compliant when they have signed the Eclipse Publisher Agreement. The `ovsx.eclipse.publisher-agreement.version` property must also be configured for this check to run.

## Migrations

| Property      | `ovsx.migrations.delay.seconds`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.9.1

Delay in seconds to run migrations. This delay is important to avoid distributing migration jobs to the old server instance, which prevents server shutdown.

| Property      | `ovsx.migrations.once-per-version`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.27.0

Only run migrations once per version. Useful in production where server instances are frequently restarted.

## Mirror Mode

| Property      | `ovsx.data.mirror.enabled`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.9.0

Whether to enable mirror mode.

| Property      | `ovsx.data.mirror.server-url`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.9.0

Base URL of the Open VSX instance to mirror.

| Property      | `ovsx.data.mirror.schedule`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.9.0

When to run the mirror job. A [CRON expression](https://www.jobrunr.io/en/documentation/background-methods/recurring-jobs/#using-a-cron-expression) is expected.

| Property      | `ovsx.data.mirror.user-name`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.9.0

A username for the mirror mode user, so that its actions can be identified in the admin logs.

| Property      | `ovsx.data.mirror.requests-per-second`
|---------------|-------------------------
| Type          | double
| Default       | `Double.MAX_VALUE`
| Compatibility | Since 0.9.0

Limit the amount of requests per second to reduce strain on the mirrored Open VSX instance.

| Property      | `ovsx.data.mirror.read-only.disallowed-methods`
|---------------|-------------------------
| Type          | string[]
| Default       |
| Compatibility | Since 0.9.0

Disallowed HTTP methods (POST, GET, etc.) for server endpoints to limit access in mirror mode, e.g. disallow POST method to block publishing.

| Property      | `ovsx.data.mirror.read-only.allowed-endpoints`
|---------------|-------------------------
| Type          | string[]
| Default       |
| Compatibility | Since 0.9.0

Allowed server endpoints in mirror mode to override disallowed methods, e.g. disallow POST method but allow posting reviews.

## Foreground HTTP Connection Pool

The foreground HTTP connection pool is used to make REST requests (upstream, Eclipse API) while serving requests.

| Property      | `ovsx.foregroundHttpConnPool.maxTotal`
|---------------|-------------------------
| Type          | integer
| Default       | `20`
| Compatibility | Since 0.9.0

The maximum total HTTP connections.

| Property      | `ovsx.foregroundHttpConnPool.defaultMaxPerRoute`
|---------------|-------------------------
| Type          | integer
| Default       | `20`
| Compatibility | Since 0.9.0

The default maximum HTTP connections per route.

| Property      | `ovsx.foregroundHttpConnPool.connectionRequestTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `10000`
| Compatibility | Since 0.9.0

The time in milliseconds to wait for a connection from the HTTP connection pool.

| Property      | `ovsx.foregroundHttpConnPool.connectTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `10000`
| Compatibility | Since 0.9.0

The time in milliseconds to establish the connection with the remote host.

| Property      | `ovsx.foregroundHttpConnPool.socketTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `10000`
| Compatibility | Since 0.9.0

The maximum time of inactivity in milliseconds between two data packets.

## Background HTTP Connection Pool

The background HTTP connection pool is used to download files in background processing (migrations, mirror mode).

| Property      | `ovsx.backgroundHttpConnPool.maxTotal`
|---------------|-------------------------
| Type          | integer
| Default       | `20`
| Compatibility | Since 0.9.0

The maximum total HTTP connections.

| Property      | `ovsx.backgroundHttpConnPool.defaultMaxPerRoute`
|---------------|-------------------------
| Type          | integer
| Default       | `20`
| Compatibility | Since 0.9.0

The default maximum HTTP connections per route.

| Property      | `ovsx.backgroundHttpConnPool.connectionRequestTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `30000`
| Compatibility | Since 0.9.0

The time in milliseconds to wait for a connection from the HTTP connection pool.

| Property      | `ovsx.backgroundHttpConnPool.connectTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `30000`
| Compatibility | Since 0.9.0

The time in milliseconds to establish the connection with the remote host.

| Property      | `ovsx.backgroundHttpConnPool.socketTimeout`
|---------------|-------------------------
| Type          | integer
| Default       | `60000`
| Compatibility | Since 0.9.0

The maximum time of inactivity in milliseconds between two data packets.

## Extension Control

| Property      | `ovsx.extension-control.enabled`
|---------------|-------------------------
| Type          | boolean
| Default       | `true`
| Compatibility | Since 0.22.0

Whether to run a nightly job to check for malicious and deprecated extensions. Cannot be used together with `ovsx.mirror.enabled`.

| Property      | `ovsx.extension-control.update-on-start`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.17.0

Whether to run a job on startup to check for malicious and deprecated extensions. If disabled, the job still runs nightly. Has no effect when `ovsx.extension-control.enabled` is `false`.

## Extension Integrity

| Property      | `ovsx.integrity.key-pair`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.11.0

Whether to generate a signature archive (sigzip) for published extensions. By default it does nothing. There are 3 modes: `create`, `renew` and `delete`. When `renew` is specified, then on startup a new keypair is generated and a new signature is generated for each extension version in the background. When `create` is specified then on startup, if no keypair exists, a keypair is created and a signature is generated for each extension version in the background. A signature is generated for newly published extension versions in both `renew` and `create` modes. The `delete` mode deletes the keypair and all generated signatures.

## OAuth2

The `ovsx.oauth2.attribute-names.[provider-name].*` configuration properties allow mapping a login provider's attributes to Open VSX user data. By default it provides an attribute name configuration for [GitHub](https://docs.github.com/en/free-pro-team@latest/developers/apps/building-oauth-apps). It is possible to configure attribute name mappings for multiple login providers. Make sure to also configure Spring Security using its `spring.security.oauth2.client.registration.[provider-name].*` and `spring.security.oauth2.client.provider.[provider-name].*` properties.

| Property      | `ovsx.oauth2.attribute-names.[provider-name].avatar-url`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.23.0

Avatar URL login provider attribute.

| Property      | `ovsx.oauth2.attribute-names.[provider-name].email`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.23.0

Email address login provider attribute.

| Property      | `ovsx.oauth2.attribute-names.[provider-name].full-name`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.23.0

Full name login provider attribute.

| Property      | `ovsx.oauth2.attribute-names.[provider-name].login-name`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.23.0

Username or another login provider attribute that can be used to uniquely identify users. The registry uses `login-name` combined with `provider-name` to retrieve a user from the database.

| Property      | `ovsx.oauth2.attribute-names.[provider-name].provider-url`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.23.0

Profile page login provider attribute

## Caching
Since v0.28.0 Open VSX uses Spring Data Redis for caching. Previous Open VSX versions used Ehcache.<br/>
You can use the standard Spring Data Redis application properties to configure a connection.

| Property      | `ovsx.redis.embedded`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.28.0, **removed** in 0.29.0

Whether to run an embedded Redis server. Useful for development and small deployments.

| Property      | `ovsx.redis.enabled`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.29.0

Whether to use Redis for caching. Caffeine (in-memory cache) is used by default.

| Property      | `ovsx.caching.files-extension.tti`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to idle for caching extension files.

| Property      | `ovsx.caching.files-extension.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `20`
| Compatibility | Since 0.28.0

Maximum amount of extension files to keep in cache.

| Property      | `ovsx.caching.files-webresource.tti`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to idle for caching webresource files.

| Property      | `ovsx.caching.files-webresource.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `150`
| Compatibility | Since 0.28.0

Maximum amount of webresource files to keep in cache.

| Property      | `ovsx.caching.files-browse.tti`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to idle for caching `/vscode/unpkg` JSON responses.

| Property      | `ovsx.caching.files-browse.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `50`
| Compatibility | Since 0.28.0

Maximum amount of `/vscode/unpkg` JSON responses to keep in cache.

| Property      | `ovsx.caching.average-review-rating.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `P3D`
| Compatibility | Since 0.28.0

Time to live for caching calculated average review rating. The average review rating is calculated and updated every day by a recurring job. The default duration is 3 days to make sure the cached value doesn't expire in between job runs.

| Property      | `ovsx.caching.average-review-rating.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1`
| Compatibility | Since 0.29.0

Cache for calculated average review rating. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.namespace-details-json.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to live for caching `/api/{namespace}/details` JSON responses

| Property      | `ovsx.caching.namespace-details-json.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.29.0

Maximum amount of namespace details JSON responses to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.database-search.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to live for caching search results. Has no effect when `ovsx.databasesearch.enabled` is `false`.

| Property      | `ovsx.caching.database-search.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.29.0

Maximum amount of database search results to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.extension-json.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to live for caching extension JSON responses (i.e. `/api/{namespace}/{extension}`, `/api/{namespace}/{extension}/{version}`, `/api/{namespace}/{extension}/{target}` and `/api/{namespace}/{extension}/{target}/{version}`).

| Property      | `ovsx.caching.extension-json.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.29.0

Maximum amount of extension JSON responses to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.latest-extension-version.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to live for caching latest extension version data.

| Property      | `ovsx.caching.latest-extension-version.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.29.0

Maximum amount of latest extension version data to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.latest-extension-version-vscode.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.34.2

Time to live for caching latest extension version data for the vscode adapter api.

| Property      | `ovsx.caching.latest-extension-version-vscode.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.34.2

Maximum amount of latest extension version data for the vscode adapter api to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.latest-extension-versions-by-platform.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.34.2

Time to live for caching latest extension version data grouped by target platform.

| Property      | `ovsx.caching.latest-extension-versions-by-platform.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.34.2

Maximum amount of latest extension version data grouped by target platform to keep in cache. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.sitemap.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.28.0

Time to live for caching `/sitemap.xml`.

| Property      | `ovsx.caching.sitemap.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1`
| Compatibility | Since 0.29.0

Cache for sitemap. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.malicious-extensions.ttl`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `P3D`
| Compatibility | Since 0.28.0

Time to live for caching malicious extension identifiers. The malicious extension identifiers can be updated every day by a recurring job or fetched on demand. The default duration is 3 days to make sure the cached identifiers don't expire in between job runs.

| Property      | `ovsx.caching.malicious-extensions.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1`
| Compatibility | Since 0.29.0

Cache for malicious extensions. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.rate-limiting.name`
|---------------|-------------------------
| Type          | string
| Default       | `buckets`
| Compatibility | Since 0.29.0

Cache name used for rate-limiting. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.rate-limiting.tti`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `PT1H`
| Compatibility | Since 0.29.0

Rate-limiting time to idle duration. Does not apply to Redis cache manager.

| Property      | `ovsx.caching.rate-limiting.max-size`
|---------------|-------------------------
| Type          | long
| Default       | `1024`
| Compatibility | Since 0.29.0

Maximum amount of rate-limiting entries to keep in cache. Does not apply to Redis cache manager.

## Personal Access Token

| Property      | `ovsx.token-prefix`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.28.0
| Deprecated    | Since 0.33.0

Add a prefix to a personal access token to make it identifiable for improved security tooling support.

| Property      | `ovsx.access-token.prefix`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.33.0

Add a prefix to a personal access token to make it identifiable for improved security tooling support.
Replaces `ovsx.token-prefix`.

| Property      | `ovsx.access-token.expiration`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `P90D`, 90 days
| Compatibility | Since 0.33.0

The duration of time a newly created access token is going to be valid. A value of `0` disables expiry of access tokens.

| Property      | `ovsx.access-token.notification`
|---------------|-------------------------
| Type          | ISO 8601 duration
| Default       | `P7D`, 7 days
| Compatibility | Since 0.33.0

The duration of time before the expiration of a token to send a notification email to the user. A value of `0` disables sending a notification.

| Property      | `ovsx.access-token.max-token-notifications`
|---------------|-------------------------
| Type          | string
| Default       | 100
| Compatibility | Since 0.33.0

The number of token expiry notification emails to be sent out during one execution of the respective job.

| Property      | `ovsx.access-token.send-expired-mail`
|---------------|-------------------------
| Type          | boolean
| Default       | `false`
| Compatibility | Since 0.33.0

Whether an email shall be sent to the user if a token has expired.


| Property      | `ovsx.access-token.expiration-schedule`
|---------------|-------------------------
| Type          | string
| Default       | every 15 min
| Compatibility | Since 0.33.0

The cron schedule of the job to expire access tokens.

| Property      | `ovsx.access-token.notification-schedule`
|---------------|-------------------------
| Type          | string
| Default       | every 15 min
| Compatibility | Since 0.33.0

The cron schedule of the job to notify about expiring access tokens.

## Email

The Open VSX server uses Spring mail to send emails. See the [common application properties](https://docs.spring.io/spring-boot/appendix/application-properties/index.html#appendix.application-properties.mail) documentation to configure email.

| Property      | `ovsx.mail.from`
|---------------|-------------------------
| Type          | string
| Default       |
| Compatibility | Since 0.30.0

Email address of the mail sender set in the email `From` header.

| Property      | `ovsx.mail.revoked-access-tokens.subject`
|---------------|-------------------------
| Type          | string
| Default       | `Open VSX Access Tokens Revoked`
| Compatibility | Since 0.30.0

The `Subject` header for the revoked access tokens email. This email is sent to a user when an admin has revoked the user's personal access tokens.

| Property      | `ovsx.mail.revoked-access-tokens.template`
|---------------|-------------------------
| Type          | string
| Default       | `revoked-access-tokens.html`
| Compatibility | Since 0.30.0

Name of the [Thymeleaf](https://www.thymeleaf.org) template to use for the revoked access tokens email. Templates should be put into the `mail-templates` classpath directory.

| Property      | `ovsx.mail.access-token-expiry.subject`
|---------------|-------------------------
| Type          | string
| Default       | `Open VSX Access Token Expiry Notification`
| Compatibility | Since 0.33.0

The `Subject` header for the access token expiry notification email. This email is sent to a user when an access token is going to expire soon.

| Property      | `ovsx.mail.access-token-expiry.template`
|---------------|-------------------------
| Type          | string
| Default       | `access-token-expiry-notification.html`
| Compatibility | Since 0.33.0

Name of the [Thymeleaf](https://www.thymeleaf.org) template to use for the access token expiry notification email. Templates should be put into the `mail-templates` classpath directory.

| Property      | `ovsx.mail.access-token-expired.subject`
|---------------|-------------------------
| Type          | string
| Default       | `Open VSX Access Token Expired`
| Compatibility | Since 0.33.0

The `Subject` header for the access token expired email. This email is sent to a user when an access token has expired.

| Property      | `ovsx.mail.access-token-expired.template`
|---------------|-------------------------
| Type          | string
| Default       | `access-token-expired.html`
| Compatibility | Since 0.33.0

Name of the [Thymeleaf](https://www.thymeleaf.org) template to use for the access token expired email. Templates should be put into the `mail-templates` classpath directory.
