ARG OPENVSX_VERSION

# Builder image to compile the website
FROM ubuntu AS builder

WORKDIR /workdir

# See https://github.com/nodesource/distributions/blob/main/README.md#debinstall
RUN apt-get update \
  && apt-get install --no-install-recommends -y \
    bash \
    ca-certificates \
    git \
    curl \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/* \
  && curl -sSL https://deb.nodesource.com/setup_24.x | bash - \
  && apt-get install -y nodejs \
  && apt-get clean \
  && corepack enable \
  && corepack prepare yarn@4.9.1 --activate

ARG OPENVSX_VERSION
ENV VERSION=$OPENVSX_VERSION

RUN git clone --branch ${VERSION} --depth 1 https://github.com/eclipse-openvsx/openvsx.git /workdir
COPY ./configuration /workdir/configuration

RUN cd webui \
  && yarn install --immutable \
  && yarn build \
  && yarn build:default

# Main image derived from openvsx-server
FROM ghcr.io/eclipse-openvsx/openvsx-server:${OPENVSX_VERSION}
ARG OPENVSX_VERSION

COPY --from=builder --chown=openvsx:openvsx /workdir/webui/static/ BOOT-INF/classes/static/
COPY --from=builder --chown=openvsx:openvsx /workdir/configuration/application.yml config/

RUN sed -i "s/<OPENVSX_VERSION>/$OPENVSX_VERSION/g" config/application.yml
