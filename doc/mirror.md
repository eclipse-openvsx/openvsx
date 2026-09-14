# Mirror Mode

Open VSX can run as a **mirror** of another registry, usually [open-vsx.org](https://open-vsx.org). A
scheduled job copies the upstream registry's *metadata* — namespaces, extensions, versions and the
records describing their files — and this instance then answers for them as its own.

The packages themselves are **not** copied. The job downloads each one to read and verify it, records
what it found, and throws the file away; [Storage](#storage) below is about the consequence, which is
that a mirror has to be pointed at object storage that already holds the upstream's files.

It is a live pull rather than a one-off export: the job reaches the upstream registry on every run.

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
- `ovsx.server.url` — recommended rather than required: without it the absolute URLs in a response
  are derived from the incoming request, which is correct behind a proxy that sets the forwarded
  headers and wrong when it does not. Set it to this mirror's own public URL, never the upstream's.
  See [Server URL](configuration.md#server-url).
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

### A partial mirror deletes everything it does not match

This is the part to get right before the first run. After walking the upstream sitemap, the job
purges every extension in the local database that is not in the set it just matched:

```java
var extensionIds = processUrls(sitemap.getElementsByTagName("url"), mirrorUser);
deleteOtherExtensions(extensionIds, mirrorUser);
```

So narrowing `include-extensions`, or adding to `exclude-extensions`, does not merely stop copying
those extensions — the next run **removes the ones already mirrored**. Anything published locally is
removed too, since it is not in the upstream sitemap either. A mirror is not a registry to publish
your own extensions into.

The one reassurance: if the sitemap cannot be fetched at all the job logs `failed to fetch sitemap`
and returns before the purge, so an unreachable upstream does not empty the registry.

## Storage

A mirror stores no files. For each mirrored resource it records a row saying where the file would
be, and builds the download URL from `ovsx.storage.*` and the object's key when someone asks for it:

```java
// the bytes were extracted from the mirrored package to build this TempFile, even though
// they aren't uploaded to storage here (mirror mode serves resources on the fly), so the
// size is still known and worth recording.
```

The mirror therefore has to be configured against **object storage that already holds the upstream's
files**, under the same keys. That is what the bundled example does: its Azure endpoint is
`https://openvsxorg.blob.core.windows.net/`, open-vsx.org's own blob storage, so the mirror serves
the upstream's objects directly while owning the metadata itself. Set
`ovsx.storage.primary-service` to `azure-blob`, `aws` (which also covers S3-compatible stores) or
`google-cloud`; the properties are under [File Storage](configuration.md#file-storage), and the
README has setup steps for [Google Cloud](../README.md#google-cloud-setup),
[Azure](../README.md#azure-setup) and [Amazon S3](../README.md#amazon-s3-setup).

**Local file storage does not work in mirror mode**, which follows from the same thing: there are no
local bytes to serve. The failure is quiet, so it is worth recognising. Every mirrored version stays
**inactive**, because before activating one the job asks its own storage for the file's URL and makes
a request to it — and local storage builds that URL from the base URL of the *current request*. A
background job has no request, the base URL is empty, and the URL comes out relative:

```
failed to activate extension, vsix is invalid: /api/<namespace>/<extension>/...
```

`ovsx.server.url` does not rescue it either: it is applied by a servlet filter, so it shapes URLs
built while serving a request and is not available to a background job. This is a known limitation.

## Air-gapped machines

Mirror mode cannot populate a registry that has no route to the upstream. Every run fetches the
sitemap and each matched extension's metadata; packages are downloaded only for extensions whose
upstream timestamp is newer than what is held locally. Either way the upstream has to be reachable,
so an isolated machine has nothing to copy from.

What does work for an isolated VS Code installation:

1. Run the mirror **on a networked machine**, narrowed with `include-extensions` to the extensions
   you need and backed by a blob store.
2. Move that registry — database and object storage — to the isolated environment, and point VS Code
   at it.
3. **Turn mirror mode off on the copy**: clear `ovsx.data.mirror.enabled` and `ovsx.upstream.url`.
   Left set, the recurring job and the upstream fallback paths keep reaching for a registry that is
   not there. Nothing is deleted when they fail — the job returns as soon as the sitemap cannot be
   fetched — but the errors are noise that hides real ones.

If you already hold the `.vsix` files, publishing them into an ordinary (non-mirror) registry with
the [`ovsx` CLI](https://github.com/eclipse-openvsx/openvsx/blob/main/cli/README.md) is the simpler
route, and it leaves you with a registry that does not expect an upstream at all.
