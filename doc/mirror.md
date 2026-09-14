# Mirror Mode

Open VSX can run as a **mirror** of another registry, usually [open-vsx.org](https://open-vsx.org). A
scheduled job copies metadata and extension files from the upstream registry, and this instance
serves them as its own. It is a live pull rather than a one-off export: the job needs to reach the
upstream registry every time it runs.

The properties are documented in
[Open VSX Configuration Properties](configuration.md#mirror-mode); this page is about how they fit
together.

## Turning it on

`server/src/dev/resources/application-mirror.yml` is a working example, mirroring open-vsx.org into
Azure Blob storage. Select it with a Spring profile:

```
--spring.profiles.include=ovsx,mirror
```

At a minimum a mirror sets:

- `ovsx.data.mirror.enabled`, `ovsx.data.mirror.server-url` and `ovsx.data.mirror.user-name` — mirror
  mode itself, the registry to copy from, and the local user the mirrored extensions are published
  as, so its actions are identifiable in the admin log.
- `ovsx.data.mirror.schedule` — when the job runs. This is a
  [JobRunr cron expression](https://www.jobrunr.io/en/documentation/background-methods/recurring-jobs/#using-a-cron-expression):
  five fields (minute, hour, day, month, weekday), not Spring's six-field form with leading seconds.
- `ovsx.upstream.url` — an **absolute** URL of the same registry. It is what API requests this
  instance cannot answer itself are forwarded to.
- `ovsx.server.url` — this mirror's own public URL, not the upstream's. See
  [Server URL](configuration.md#server-url).
- `ovsx.storage.primary-service` — where the copied files are kept. **Not local storage**; see below.

`ovsx.data.mirror.requests-per-second` bounds the load the job puts on the upstream registry, and the
`ovsx.data.mirror.read-only.*` properties keep the mirror from accepting publishes of its own while
it syncs.

## Mirroring part of a registry

`ovsx.data.mirror.include-extensions` and `ovsx.data.mirror.exclude-extensions` select what is
copied. An entry is either an extension id (`ms-python.python`) or a whole namespace
(`redhat.*`):

```yaml
ovsx:
  data:
    mirror:
      include-extensions:
        - ms-python.python
        - redhat.*
      exclude-extensions:
        - vscode.*
```

Four things are worth knowing:

- An empty `include-extensions` mirrors **everything** the upstream offers, minus the exclusions.
- `exclude-extensions` wins wherever both match.
- `namespace.*` is the only wildcard, and it is matched literally rather than as a glob:
  `redhat.*` works, `redhat.j*` matches nothing.
- There is **no version selector**. Every version of a matched extension is mirrored, for every
  target platform.

## Storage

**Local file storage does not work in mirror mode.** Configure a blob store instead —
`ovsx.storage.primary-service` set to `azure-blob`, `aws` (which also covers S3-compatible stores) or
`google-cloud`. The properties are under [File Storage](configuration.md#file-storage), and the
README has setup steps for
[Google Cloud](../README.md#google-cloud-setup), [Azure](../README.md#azure-setup) and
[Amazon S3](../README.md#amazon-s3-setup).

The failure is quiet, which is why it is worth stating plainly. The job downloads and stores the
files successfully, and then every mirrored version stays **inactive**: before activating one, the
job asks its own storage for the file's URL and makes a request to it, and with local storage that
URL is built from the base URL of the current request. The job has no request, so the base URL is
empty, the URL comes out relative, and the request fails. The log shows

```
failed to activate extension, vsix is invalid: /api/<namespace>/<extension>/...
```

Setting `ovsx.server.url` does not help: it is applied by a servlet filter, so it shapes the URLs
built while serving a request and is not available to a background job. This is a known limitation.

## Air-gapped machines

Mirror mode cannot populate a registry that has no route to the upstream. The job fetches metadata
and files over HTTP on every run, so an isolated machine has nothing to copy from.

What does work for an isolated VS Code installation:

1. Run the mirror **on a networked machine**, narrowed with `include-extensions` to the extensions
   you need and backed by a blob store.
2. Move that registry — database and blob store — to the isolated environment, and point VS Code at
   it.

If you already hold the `.vsix` files, publishing them into an ordinary (non-mirror) registry with
the [`ovsx` CLI](https://github.com/eclipse-openvsx/openvsx/blob/main/cli/README.md) is the simpler
route, and it leaves you with a registry that does not expect an upstream at all.
