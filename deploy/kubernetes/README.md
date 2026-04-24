# OpenVSX Kubernetes Deployment

Kubernetes manifests for deploying a self-hosted [OpenVSX](https://github.com/eclipse/openvsx) extension registry.

## Architecture

```
                  ┌──────────┐
                  │  Ingress │  (TLS termination)
                  └────┬─────┘
                       │
          ┌────────────┼────────────┐
          │ /api, /user, /login,    │ /  (all other paths)
          │ /oauth2, /admin, ...    │
          │                         │
   ┌──────▼──────┐          ┌───────▼──────┐
   │ OpenVSX     │          │ OpenVSX      │
   │ Server :8080│          │ WebUI :3000  │
   └──┬───────┬──┘          └──────────────┘
      │       │
┌─────▼────┐ ┌▼──────────────┐
│PostgreSQL│ │ Elasticsearch │
│ :5432    │ │ :9200         │
└──────────┘ └───────────────┘

┌──────────────┐
│ OVSX CLI     │  (utility pod)
└──────────────┘
```

The Ingress splits traffic between the server and the WebUI based on the URL path. API, authentication, and admin routes go to the Spring Boot server; everything else is served by the WebUI (React SPA on Express). The WebUI resolves its backend URL from the browser's `location.host`, so both services must share the same hostname.

## Components

| File | Resources | Description |
|------|-----------|-------------|
| `namespace.yaml` | Namespace | `openvsx` namespace |
| `secrets.yaml` | Secret (x2) | Database credentials, OVSX PAT |
| `configmap.yaml` | ConfigMap | Spring Boot `application.yml` for the server |
| `postgresql.yaml` | Deployment, Service, PVC | PostgreSQL 16 database (1Gi storage) |
| `elasticsearch.yaml` | Deployment, Service | Elasticsearch 8.7.1 single-node for search |
| `openvsx-server.yaml` | Deployment, Service, PVC | OpenVSX server application (5Gi extension storage) |
| `openvsx-webui.yaml` | Deployment, Service | React frontend served via Express on port 3000 |
| `ovsx-cli.yaml` | Deployment | Utility pod with the `ovsx` CLI for admin tasks |
| `ingress.yaml` | Ingress | Nginx ingress with path-based routing and TLS |

## Image Architectures

The pre-built images from `ghcr.io/eclipse-openvsx/` are **amd64-only**. If your cluster runs on a different architecture (e.g. Apple Silicon / arm64), you must build the images from source:

| Image | Default | Architectures | Source Dockerfile |
|-------|---------|---------------|-------------------|
| OpenVSX Server | `ghcr.io/eclipse-openvsx/openvsx-server:latest` | amd64 | `server/Dockerfile` |
| OpenVSX WebUI | `ghcr.io/eclipse-openvsx/openvsx-webui:latest` | amd64 | `webui/Dockerfile` |

## Prerequisites

- Kubernetes 1.24+
- An Ingress controller (nginx-ingress by default)
- `kubectl` configured for your cluster
- _(Optional)_ A GitHub OAuth App for user authentication via GitHub login

## Quick Start

### 1. Configure secrets

Encode your values in base64 and update `secrets.yaml`:

```bash
# Database password
echo -n 'your-db-password' | base64

# Personal access token for the OVSX CLI
echo -n 'your-pat-token' | base64
```

### 2. Configure the Ingress hostname

Edit `ingress.yaml` and replace `openvsx.example.com` with your actual domain. Update the `secretName` under `tls` to point to a valid TLS certificate Secret, or remove the `tls` block to disable HTTPS.

### 3. Deploy

Apply manifests in order (secrets and config before workloads):

```bash
kubectl apply -f deploy/kubernetes/namespace.yaml
kubectl apply -f deploy/kubernetes/secrets.yaml
kubectl apply -f deploy/kubernetes/configmap.yaml
kubectl apply -f deploy/kubernetes/postgresql.yaml
kubectl apply -f deploy/kubernetes/elasticsearch.yaml
kubectl apply -f deploy/kubernetes/openvsx-server.yaml
kubectl apply -f deploy/kubernetes/openvsx-webui.yaml
kubectl apply -f deploy/kubernetes/ovsx-cli.yaml
kubectl apply -f deploy/kubernetes/ingress.yaml
```

Or apply everything at once:

```bash
kubectl apply -f deploy/kubernetes/
```

### 4. Verify

```bash
# Watch pods come up
kubectl -n openvsx get pods -w

# Check the server logs
kubectl -n openvsx logs -f deployment/openvsx-server

# Check the webui logs
kubectl -n openvsx logs -f deployment/openvsx-webui

# Test the API once the server is ready
kubectl -n openvsx port-forward svc/openvsx-server 8080:8080
curl http://localhost:8080/api/version

# Test the WebUI
kubectl -n openvsx port-forward svc/openvsx-webui 3000:3000
# Open http://localhost:3000 in a browser
```

The server has a long startup time (~1-2 minutes) due to Spring Boot initialization, Flyway migrations, and Elasticsearch index creation. The WebUI starts in seconds.

## Configuration

### Application settings

The server's Spring Boot configuration lives in the `openvsx-server-config` ConfigMap (`configmap.yaml`). Key settings:

| Setting | Default | Description |
|---------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://postgresql:5432/openvsx` | Database connection URL |
| `ovsx.elasticsearch.host` | `elasticsearch:9200` | Elasticsearch endpoint |
| `ovsx.storage.local.directory` | `/tmp/extensions` | Local extension file storage path |
| `ovsx.elasticsearch.clear-on-start` | `true` | Rebuild search index on startup |
| `ovsx.integrity.key-pair` | `create` | Integrity key pair mode (`create`, `renew`, `delete`) |
| `bucket4j.enabled` | `false` | API rate limiting |
| `ovsx.registry.version` | _(empty)_ | Version string displayed in the UI (e.g. `0.33.0`) |

### Resource requirements

| Component | CPU request | CPU limit | Memory request | Memory limit |
|-----------|------------|-----------|----------------|--------------|
| PostgreSQL | 200m | 1 | 512Mi | 2Gi |
| Elasticsearch | 500m | 1 | 1Gi | 2Gi |
| OpenVSX Server | 100m | 1 | 512Mi | 4Gi |
| OpenVSX WebUI | 50m | 500m | 128Mi | 512Mi |
| OVSX CLI | 20m | 250m | 128Mi | 256Mi |

### Persistent storage

| PVC | Default size | Mount path | Purpose |
|-----|-------------|------------|---------|
| `postgres-pvc` | 1Gi | `/var/lib/postgresql/data` | Database files |
| `extensions-pvc` | 5Gi | `/tmp/extensions` | Extension file storage |

Adjust the `storage` values in the PVC specs as needed for your expected extension volume.

## Ingress Routing

The Ingress uses path-based routing so the WebUI and Server share a single hostname. The WebUI SPA derives the server URL from the browser's `location.host`, so this shared-hostname setup is required.

| Path prefix | Backend |
|-------------|---------|
| `/api` | openvsx-server:8080 |
| `/user` | openvsx-server:8080 |
| `/login` | openvsx-server:8080 |
| `/logout` | openvsx-server:8080 |
| `/oauth2` | openvsx-server:8080 |
| `/login-providers` | openvsx-server:8080 |
| `/admin` | openvsx-server:8080 |
| `/actuator` | openvsx-server:8080 |
| `/documents` | openvsx-server:8080 |
| `/swagger-ui` | openvsx-server:8080 |
| `/v3/api-docs` | openvsx-server:8080 |
| `/` (everything else) | openvsx-webui:3000 |

## Publishing Extensions

Before publishing extensions, you need to create a user and a personal access token (PAT) directly in the database. Without GitHub OAuth configured, this is the only way to authenticate.

### 1. Create a user in the database

```bash
kubectl -n openvsx exec -it deployment/postgresql -- psql -U openvsx -d openvsx
```

```sql
INSERT INTO user_data (id, login_name, role) VALUES (1001, 'admin', 'admin');
```

### 2. Create a personal access token

```sql
INSERT INTO personal_access_token (id, user_data, value, active, created_timestamp, description, notified)
VALUES (1001, 1001, 'your_token_value', true, NOW(), 'CLI token', false);
```

### 3. Publish using the OVSX CLI

The CLI pod runs as a long-lived utility container. Exec into it to publish or manage extensions:

```bash
kubectl -n openvsx exec -it deployment/ovsx-cli -- sh

# Inside the pod:
ovsx publish my-extension.vsix
ovsx get publisher.extension
```

The `OVSX_REGISTRY_URL` and `OVSX_PAT` environment variables are pre-configured. Update the `ovsx-pat` secret in `secrets.yaml` to match the token value you inserted into the database.

## Deploying without Elasticsearch

To use database-based search instead of Elasticsearch:

1. Skip applying `elasticsearch.yaml`
2. Update `configmap.yaml` — set `ovsx.databasesearch.enabled` to `true` and remove the `ovsx.elasticsearch` section

## Deploying without the WebUI

If you use a combined server image that already bundles the WebUI (e.g. built from `deploy/docker/Dockerfile`):

1. Skip applying `openvsx-webui.yaml`
2. Simplify `ingress.yaml` to route all traffic to the server on port 8080

## Monitoring

The server exposes Actuator endpoints for monitoring:

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Health status (used by probes) |
| `/actuator/metrics` | Application metrics |
| `/actuator/prometheus` | Prometheus-compatible metrics |

## Customization

### Using a different Ingress controller

Replace the `ingressClassName` and annotations in `ingress.yaml`. For example, for Traefik:

```yaml
spec:
  ingressClassName: traefik
```

### Cloud-based extension storage

To use cloud storage (S3, GCS, Azure Blob) instead of local PVC storage, add the appropriate environment variables to the server deployment and update the ConfigMap. See the [OpenVSX documentation](https://github.com/eclipse/openvsx/wiki) for details on storage configuration.
