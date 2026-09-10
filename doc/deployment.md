# Deploying Open VSX

How to deploy and configure a self-hosted Open VSX registry. For the architectural overview -
what the components are and how they fit together - see
[Deploying Open VSX](https://github.com/eclipse-openvsx/openvsx/wiki/Deploying-Open-VSX) in the
wiki.

This document describes the server as it stands on `main`. The `Compatibility` row of each
property says which release introduced it.

## Getting Started

You can quickly spin up an Open VSX server and webui by [cloning the repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/cloning-a-repository) and running the [`build.sh`](../deploy/docker/build.sh) script. Additionally you need a [PostgreSQL](https://www.postgresql.org) instance and an [Elasticsearch](https://www.elastic.co/elasticsearch/) instance. To use the user and admin sections of the webui, you need to configure an [OAuth2](configuration.md#oauth2) provider. To disable login you need to remove the `spring.security.oauth2` block in the [application.yml](../deploy/docker/configuration/application.yml) file. For troubleshooting and further configuration, see issue [#703](https://github.com/eclipse/openvsx/issues/703).

## Configuring application.yml

The Open VSX server is configured using an `application.yml` file. See the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config) for more information on this configuration format.

The server application will automatically load the configuration file if you put it into the directory `/home/openvsx/server/config` of the server image. There are several ways to do this, e.g. you can extend the Docker image or use a Kubernetes ConfigMap.

You can use all configuration properties offered by Spring to set up your deployment. In particular, you need to [configure a datasource](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-connect-to-production-database) to connect the application with the database.

## Configuration Properties

The properties the server understands are listed in [Open VSX Configuration Properties](configuration.md).

## Adding the Web UI

The Docker image of the server application does not include the web UI. The reason for this is that the UI can be customized. In case you don't need customization, you can use the [default web UI image](https://github.com/orgs/eclipse/packages/container/package/openvsx-webui) and deploy it next to the server.

Customization of the UI is done by creating an npm package with a dependency on [openvsx-webui](https://www.npmjs.com/package/openvsx-webui), which is a library of [React](https://reactjs.org) components. A minimal frontend app is shown in the following TypeScript-React (`tsx`) code.

```tsx
import * as ReactDOM from 'react-dom';
import * as React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { ThemeProvider } from '@material-ui/styles';
import { Main, ExtensionRegistryService } from 'openvsx-webui';

const App: React.FunctionComponent = () => {
    const theme = ...        // Define a Material UI theme
    const pageSettings = ... // Define page settings (see below)
    const service = new ExtensionRegistryService();
    return <ThemeProvider theme={theme}>
        <Main pageSettings={pageSettings} service={service} />
    </ThemeProvider>;
};

const node = document.getElementById('main');
ReactDOM.render(<BrowserRouter><App /></BrowserRouter>, node);
```

See the [Material UI documentation](https://material-ui.com/customization/theming/) and the [default theme](../webui/src/default/theme.tsx) to learn how to define a theme.

The page settings schema is defined [in this interface](../webui/src/page-settings.ts). It includes general settings as well as React components to be rendered in specific parts of the UI.

## Upstream Registry Instance

It is possible to [configure another Open VSX instance as upstream](configuration.md#upstream-registry). This means that API requests to extensions that are not found locally are forwarded to the upstream instance. With this mechanism, you can keep selected extensions in your own (private) instance and delagate to another (public) instance for all other extensions. For example, you can use `https://open-vsx.org/` as upstream instance to enable access to all public extensions, but still point users to your private instance.

## HTTPS Configuration

A reverse proxy can be used to make an Open VSX instance available over HTTPS. The rest of this
section shows how to configure [NGINX](https://nginx.org/) as that reverse proxy.

Set [`ovsx.server.url`](configuration.md#server-url) to the URL the registry is served at when you
put a proxy in front of it. The absolute URLs in a response - download links, icons, an extension's
API URLs - are otherwise derived from the `X-Forwarded-Host`, `X-Forwarded-Proto` and
`X-Forwarded-Prefix` headers of each request, and those are written by whoever sent the request.
They are only read from a peer in
[`ovsx.server.trusted-proxies`](configuration.md#server-url), which by default is the loopback and
private ranges; a proxy reaching the server from a public address needs that list extended, or the
registry emits internal-host URLs.

### 1. Create an OpenSSL Self-Signed Certificate

If you don't have a self-signed certificate, you can create one using the following steps:

Create a self-signed certificate with [Certs Maker](https://github.com/soulteary/certs-maker) using the following command:

```bash
docker run --rm -it -e CERT_DNS="<YOUR_PUBLIC_IP>" -v $(pwd)/certs:/ssl soulteary/certs-maker
```

The path to the certificate files is as follows:

```bash
ls $(pwd)/certs
```

Copy the certificate files to the NGINX configuration directory:

```bash
sudo mkdir -p /etc/nginx/ssl
sudo cp $(pwd)/certs/<YOUR_PUBLIC_IP>.crt /etc/nginx/ssl/
sudo cp $(pwd)/certs/<YOUR_PUBLIC_IP>.key /etc/nginx/ssl/
```

### 2. Configuring NGINX

Create and edit the site configuration:

```bash
sudo nano /etc/nginx/sites-available/openvsx
```

The site configuration is as follows:

```nginx
# Handle HTTP requests on port 80
server {
    listen 80;
    server_name <YOUR_PUBLIC_IP>;
    # Redirect all HTTP requests to HTTPS
    location / {
        return 301 https://$host$request_uri;
    }
}
# Handle HTTPS requests on port 443
server {
    listen 443 ssl;
    server_name <YOUR_PUBLIC_IP>;
    ssl_certificate /etc/nginx/ssl/<YOUR_PUBLIC_IP>.crt;
    ssl_certificate_key /etc/nginx/ssl/<YOUR_PUBLIC_IP>.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_prefer_server_ciphers on;
    location / {
        proxy_pass http://<YOUR_PUBLIC_IP>:8080;
        proxy_set_header Host $host;
        # A literal, not $host: $host is the Host header the client sent, and this server block is
        # the default for the port, so a request naming any host at all reaches it. Forwarding that
        # would let a client choose the host in the URLs the registry emits, and those responses are
        # cached and served to everyone else. Setting ovsx.server.url settles it regardless.
        proxy_set_header X-Forwarded-Host <YOUR_PUBLIC_IP>;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Create a symbolic link and reload NGINX:

```bash
sudo ln -s /etc/nginx/sites-available/openvsx /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Check and update the configuration:

```bash
sudo grep -r '<YOUR_PUBLIC_IP>' /etc/nginx/
sudo mv /etc/nginx/sites-available/<YOUR_PUBLIC_IP>.conf /etc/nginx/sites-available/<YOUR_PUBLIC_IP>.conf.disabled
sudo rm /etc/nginx/sites-enabled/<YOUR_PUBLIC_IP>.conf
sudo ln -s /etc/nginx/sites-available/openvsx /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```
