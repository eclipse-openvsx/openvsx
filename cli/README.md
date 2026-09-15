# ovsx

[![Gitpod Ready-to-Code](https://img.shields.io/badge/Gitpod-ready--to--code-blue?logo=gitpod)](https://gitpod.io/#https://github.com/eclipse/openvsx/tree/master/cli)
[![NPM version](https://img.shields.io/npm/v/ovsx)](https://www.npmjs.com/package/ovsx)

Command line interface for [Eclipse Open VSX](https://open-vsx.org/). Run it via [npx](https://www.npmjs.com/package/npx) or install it with
```
npm install --global ovsx
```

`ovsx` uses open-vsx.org as default instance for publishing and downloading extensions. If you are using a different instance, specify it via the `--registryUrl` (or `-r`) argument or an environment variable named `OVSX_REGISTRY_URL`.

Requests give up if they stop making progress for 30 seconds, so a registry that accepts a connection and then goes quiet does not leave the command hanging. `OVSX_TIMEOUT` changes that, in milliseconds; `OVSX_TIMEOUT=0` disables it. It measures inactivity rather than total time, so a large extension downloading slowly is not cut off, and it covers the ID token request made for trusted publishing as well as the registry's own.

### Publish Extensions

You can use `ovsx` for publishing [VS Code extensions](https://code.visualstudio.com/api) to an Open VSX instance. This is very similar to [vsce](https://github.com/microsoft/vscode-vsce), the publishing tool for the [Visual Studio Code Marketplace](https://marketplace.visualstudio.com/vscode).

You must create an Open VSX [personal access token](https://open-vsx.org/user-settings/tokens) before you can use `osvx` to publish. You can either pass the token via the `--pat` (or `-p`) argument, or put it into an environment variable named `OVSX_PAT`.

Variants:
 * `ovsx publish`
   packages the extension in the current working directory using `vsce` and then publishes it.
 * `ovsx publish --packagePath <path>`
   packages the extension in the given path using `vsce` and then publishes it.
 * `ovsx publish <file>`
   publishes an already packaged file.

Before uploading, `ovsx` checks the packaged extension's size against the limit the registry reports on `/api/version`, so an oversized package is rejected locally instead of failing after the whole file has been uploaded.

#### Package managers

Packaging is done by `vsce`, which walks your production dependencies to decide what goes into the `.vsix`. It knows how to do that for two package managers only - npm, and yarn v1 via `--yarn` - and [will not be adding more](https://github.com/microsoft/vscode-vsce/issues/421#issuecomment-1853665094). For anything else, pnpm included, there are two options:

 * `ovsx publish --no-dependencies`
   skips the dependency walk altogether. This is the right answer for a bundled extension - one built with esbuild, webpack or similar, where the bundle already contains everything the extension needs at runtime - regardless of which package manager installed the sources.
 * `ovsx publish --follow-symlinks`
   recurses into symlinked directories instead of packing each symlink as a file. A `node_modules` assembled out of symlinks, as pnpm's is, needs this for the walk to reach the real dependency files.

### Trusted Publishing

Instead of a long-lived personal access token, `ovsx` can publish from a CI workflow with a short-lived token that the registry issues in exchange for an OIDC ID token of the workflow. The registry only issues such a token if the workflow matches a trusted publisher that a namespace owner registered under [trusted publishers](https://open-vsx.org/user-settings/trusted-publishers). No access token needs to be stored as a secret.

On GitHub Actions the workflow needs the `id-token: write` permission, everything else is detected automatically:

```yaml
permissions:
  contents: read
  id-token: write
jobs:
  publish:
    runs-on: ubuntu-latest
    # only needed if the trusted publisher pins an environment
    environment: publish
    steps:
      - uses: actions/checkout@v5
      - uses: actions/setup-node@v5
      - run: npm ci
      - run: npx ovsx publish --trusted-publishing
```

Options:
 * `--trusted-publishing` requires trusted publishing and fails if no ID token can be obtained. Without it, trusted publishing is used whenever an ID token is available and no access token was given; a `--pat` (or `OVSX_PAT`) always takes precedence.
 * `--idToken <token>` passes the ID token explicitly. Use this on CI systems that expose the token as a variable, for example with GitLab CI's [`id_tokens`](https://docs.gitlab.com/ci/yaml/#id_tokens) keyword. The environment variable `OVSX_ID_TOKEN` does the same.
 * `--oidcAudience <audience>` sets the audience requested for the ID token, by default the registry URL. Use it if the registry expects a different audience, and make sure it matches the `aud` claim the registry validates. The environment variable `OVSX_OIDC_AUDIENCE` does the same.

The issued token is valid for a few minutes only and is never written to the token store, but it does carry publishing rights, so treat CI logs accordingly.

### Delete Extensions

You can delete extensions you published with `ovsx unpublish`, the counterpart of `vsce unpublish`. This requires an access token as described above, and the token's user must be a member of the extension's namespace: namespace owners may delete any version, other members only the versions they published themselves. This requires a registry running version 1.2.0 or later.

Deleting is irreversible: the files of a deleted version are removed and its version number stays reserved, so the same version can never be published again. You will be asked for confirmation unless you pass `--force` (or `-f`).

Variants:
 * `ovsx unpublish`
   deletes all versions of the extension in the current working directory, as identified by the `publisher` and `name` fields of its package.json.
 * `ovsx unpublish <extension>`
   deletes all versions of the given extension, identified with the format `namespace.extension`.
 * `ovsx unpublish <extension> --versions <versions...>`
   deletes only the given versions, e.g. `ovsx unpublish foo.bar -v 1.0.0 1.0.1`.
 * `ovsx unpublish <extension> --versions <versions...> --target <targets...>`
   deletes only the given [target platforms](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#platformspecific-extensions) of the given versions, e.g. `ovsx unpublish foo.bar -v 1.0.0 -t linux-x64`.

### Create a Namespace

The `publisher` field of your extension's package.json defines the namespace into which the extension will be published. Before you publish the first extension in a namespace, you must create it. This requires an access token as described above.

 * `ovsx create-namespace <name>`
   creates the specified namespace. The name must correspond to the `publisher` of your extension.

Creating a namespace does _not_ automatically give you the exclusive publishing rights. Initially, everyone will be able to publish an extension with the new namespace. If you want exclusive publishing rights, you can [claim ownership of a namespace](https://github.com/eclipse/openvsx/wiki/Namespace-Access).

### Download Extensions

You can use `ovsx` for downloading extensions from an Open VSX instance. Extensions are identified with the format `namespace.extension`, and an exact version or version range can be specified with the `--versionRange` (or `-v`) argument. The namespace corresponds to the `publisher` entry of the package.json file.

Variants:
 * `ovsx get <extension>`
   downloads an extension and saves it to a file as specified in its download URL in the current working directory. This is usually in the format `namespace.extension-version.vsix`. For [target platform specific extensions](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#platformspecific-extensions) (e.g. `linux-x64`) the format is `namespace.extension-version@target.vsix`.
 * `ovsx get <extension> -o <path>`
   downloads an extension and saves it in the specified file or directory.
 * `ovsx get <extension> --metadata`
   downloads the JSON metadata of an extension and prints it to the standard output.
 * `ovsx get <extension> --metadata -o <path>`
   downloads the JSON metadata of an extension and saves it in the specified file or directory.

### Verify a Downloaded Package

If the registry signs published packages, `ovsx verify <path>` checks a downloaded `.vsix` file's signature against the registry's public key - the same check VS Code itself performs when installing a signed extension. The namespace, extension name and version are read from the package itself, so no extra arguments are needed beyond the file path (and `-t`/`--target` for a [target platform specific extension](https://code.visualstudio.com/api/working-with-extensions/publishing-extension#platformspecific-extensions)).

 * `ovsx verify <extension.vsix>`
   verifies the package's signature and exits with an error if it is missing, invalid, or does not match - meaning the package was not published by, or was tampered with after being published by, the registry it was downloaded from.

If you already have the package, manifest and signature files on disk - e.g. extracted from a registry's signature archive - and don't want `ovsx` to talk to a registry at all, `ovsx verify-signature` verifies that trio entirely offline, mirroring `vsce verify-signature`:

 * `ovsx verify-signature -i <package.vsix> -m <manifest> -s <signature> -k <publicKey>`
   verifies the signature against the package, and cross-checks the manifest's recorded digests against the package's actual contents - reporting which entry doesn't match, if any. Unlike `vsce verify-signature`, a public key must be supplied explicitly (`-k`/`--publicKeyPath`): Open VSX registries each hold their own signing key, rather than trusting a single baked-in Marketplace key the way VS Code's own verifier does.

### Store Access Tokens

The `login` command lets you store an access token for a namespace.

 * `ovsx login <name>`
   the name must correspond to the `publisher` of your extension. `ovsx` will ask you to provide an access token.

The `logout` command lets you remove a stored access token.

 * `ovsx logout <name>`
   the name must correspond to the `publisher` of your extension.

By default `ovsx` stores access tokens in the operating system's credential manager (via [`cross-keychain`](https://www.npmjs.com/package/cross-keychain)), falling back to storing them as plaintext in the `~/.ovsx` file if the credential manager can't be used. You can also set the environment variable `OVSX_STORE=file` to force plaintext storage; this is strongly discouraged, as it leaves your tokens readable by anyone with access to your home directory.
