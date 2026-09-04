# Mirror mode

Open VSX can run as a **mirror** of another registry (usually [open-vsx.org](https://open-vsx.org)). The server periodically copies metadata and extension files from the upstream registry and serves them from this instance.

A working example is [`server/src/dev/resources/application-mirror.yml`](../server/src/dev/resources/application-mirror.yml). Enable it with a Spring profile that includes that file, for example:

```
--spring.profiles.include=ovsx,mirror
```

## Required settings

| Property | Purpose |
| --- | --- |
| `ovsx.data.mirror.enabled` | Set to `true` to turn mirror mode on. |
| `ovsx.data.mirror.server-url` | Upstream registry to copy from, e.g. `https://open-vsx.org`. |
| `ovsx.data.mirror.user-name` | Local user that owns mirrored extensions. |
| `ovsx.data.mirror.schedule` | JobRunr cron expression for the mirror job (5 fields: minute hour day month weekday; not Spring's 6-field form with seconds). |
| `ovsx.data.mirror.requests-per-second` | Rate limit for upstream HTTP calls. |
| `ovsx.upstream.url` | **Absolute** upstream URL used for API calls. A relative path here leads to `Target host is not specified`. |
| `ovsx.storage.primary-service` | Blob storage backend. See [storage](#storage). |

Read-only HTTP methods and allowed endpoints under `ovsx.data.mirror.read-only` keep the mirror from accepting publishes while it is syncing.

## Partial mirror (extension list)

There is no per-version selector. Matching is by extension id (`namespace.name`) or by whole namespace (`namespace.*`). All versions of a matched extension are mirrored.

Include only some extensions (when this list is empty, everything not excluded is mirrored):

```yaml
ovsx:
  data:
    mirror:
      include-extensions:
        - ms-python.python
        - redhat.*
```

Skip some extensions:

```yaml
ovsx:
  data:
    mirror:
      exclude-extensions:
        - vscode.*
```

Exclude wins when both lists match.

## Storage

**Local filesystem storage is not supported in mirror mode.** The mirror job runs outside an HTTP request, so `UrlUtil.getBaseUrl()` has no request context and returns an empty string. File URLs then become relative (`/api/...`) and RestTemplate fails with `Target host is not specified`.

Use a blob store instead:

- Azure Blob (`ovsx.storage.primary-service: azure-blob`) — see the example in `application-mirror.yml`
- Amazon S3 / MinIO (`aws`)
- Google Cloud Storage

The README has the Azure, GCS, and S3 setup steps.

## Air-gapped machines

Mirror mode is a live pull from `ovsx.upstream.url`. The job needs network access to that registry while it is running. It is not a one-shot dump of selected versions.

For a small air-gapped VS Code install:

1. Run a mirror **with network** against open-vsx.org, using `include-extensions` for the ids you need and blob storage (MinIO is fine).
2. After the job has copied those extensions, point VS Code at this registry.
3. A machine that can never reach the upstream registry cannot use mirror mode to populate itself.

Publishing selected `.vsix` files with the `ovsx` CLI into a normal (non-mirror) registry is the other option when you already have the files.
