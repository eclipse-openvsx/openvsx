# Setup
## build.gradle
### Running on dev environment:
- add `jvmArgs = ['-Xverify:none']` to runServer task if you want to attach VisualVM to the server.

### Running against remote server:
- add `jvmArgs = ['-Xverify:none', '-Dcom.sun.management.jmxremote', '-Dcom.sun.management.jmxremote.port=<PORT>', '-Dcom.sun.management.jmxremote.authenticate=false', '-Dcom.sun.management.jmxremote.ssl=false', '-Djava.rmi.server.hostname=<IP_ADDRESS>']` to runServer task if you want to attach VisualVM to the server. Change **<PORT>** and **<IP_ADDRESS>** to the server IP address and the port JMX must listen on.

## Gatling simulations
### Running against remote server:
- Change `baseUrl` property in `resources/application.properties`, or override it per run with `-DbaseUrl=<URL>`.
- If the remote server requires authentication, uncomment and change the `auth` property in `resources/application.properties`
  (or pass `-Dauth=<HEADER>`). Simulations use the `auth` value to set the Authorization request header.

### resources/access-tokens.csv:
- contains API access tokens. `super_token` is pre-seeded for the dev profile; create more via the web UI for testing against a remote server.
- make sure to keep the `access_token` header at the top of the file.

### scala/org/eclipse/openvsx/RegistryAPIPublishExtensionSimulation.scala:
- Defaults to `build-local/test-extensions/`. Either populate it with `.vsix` files (e.g. `./gradlew downloadTestExtensions`) or change `extensionDir` in `resources/application.properties` to a directory that **only** contains extensions.

# Running Gatling

Gradle tasks (group: `Performance testing`):

| Task                    | What it does                                                                                  |
|-------------------------|-----------------------------------------------------------------------------------------------|
| `perfFillDatabase`      | Seeds the DB: creates namespaces, then publishes extensions from `extensionDir`               |
| `perfTestRegistryApi`   | Runs all read-only registry API simulations                                                   |
| `perfTestVscodeAdapter` | Runs all read-only VS Code adapter simulations                                                |
| `perfTestAll`           | Runs `perfTestRegistryApi` + `perfTestVscodeAdapter`                                          |
| `perfStressReadMix`     | Stepped read-only load against `baseUrl`, with an HTML report (see below)                     |
| `gatlingRun`            | Built-in plugin task; pass `--simulation=<FQCN>` to run a single simulation                   |

Typical run against a freshly started server:

```sh
./gradlew perfFillDatabase
./gradlew perfTestAll
```

To run a single simulation:

```sh
./gradlew gatlingRun --simulation=org.eclipse.openvsx.RegistryAPISearchSimulation
```

## Stress test

`perfStressReadMix` raises the arrival rate in equal steps up to a peak (open model, one request per
virtual user, shared connections). Requests are a weighted mix of registry API and VS Code adapter
reads using the CSV data above; it never writes. Run the same load against two deployments to
compare them, for example with and without a CDN or edge proxy in front:

```sh
./gradlew perfStressReadMix -DbaseUrl=https://a.example -Dstress.peakRps=100
./gradlew perfStressReadMix -DbaseUrl=https://b.example -Dstress.peakRps=100
```

| Property             | Default | Meaning                               |
|----------------------|---------|---------------------------------------|
| `stress.peakRps`     | `50`    | Requests per second at the last step  |
| `stress.steps`       | `5`     | Number of steps                       |
| `stress.stepSeconds` | `60`    | How long each step is held            |
| `stress.rampSeconds` | `10`    | Ramp between steps                    |
| `stress.cacheBust`   | `false` | Random `cb` param defeats all caches  |

Reports are written to `build/reports/gatling/`. For a fair comparison:

- Exempt the load generator from rate limiting (a customer covering its address, on a tier well
  above the peak), or both runs measure the limiter.
- Do not run two deployments at once when they share an origin, and alternate which runs first:
  each run warms caches and connection pools for the next.

## Empty the database
If you wish to empty the database after running the Gatling simulations, you can run:
```BEGIN;
DELETE FROM file_resource;
-- extension.latest and extension.preview refer back to extension_version.id
ALTER TABLE extension_version DISABLE TRIGGER ALL;
DELETE FROM extension_version;
ALTER TABLE extension_version ENABLE TRIGGER ALL;
DELETE FROM extension;
DELETE FROM namespace_membership;
DELETE FROM namespace;
COMMIT;
