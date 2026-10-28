ARG OPENVSX_VERSION

# Builder image to compile the website
FROM registry.access.redhat.com/ubi9:9.5-1730489303 as builder

WORKDIR /workdir

RUN yum update -q -y && \
    yum install --nodocs -y \
    git \
  && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL https://rpm.nodesource.com/setup_20.x -o nodesource_setup.sh \
  && bash nodesource_setup.sh \
  && yum install -y nodejs \
  && node -v \
  && rm -rf nodesource_setup.sh

ARG OPENVSX_VERSION
ENV VERSION=$OPENVSX_VERSION

RUN git clone --branch ${VERSION} --depth 1 https://github.com/eclipse/openvsx.git /workdir

# Run the committed Yarn release directly; no global Yarn is installed.
RUN cd webui \
  && yarn() { node .yarn/releases/yarn-4.18.1.cjs "$@"; } \
  && yarn --version \
  && yarn install --immutable \
  && yarn build:default

# Main image derived from openvsx-server
FROM ghcr.io/eclipse-openvsx/openvsx-server:${OPENVSX_VERSION}
ARG OPENVSX_VERSION

COPY --from=builder --chown=openvsx:openvsx /workdir/webui/dist/ BOOT-INF/classes/static/
COPY /application.yml config/

USER root
RUN sed -i "s/OPENVSX_VERSION/${OPENVSX_VERSION}/g" config/application.yml
USER openvsx
# Local storage:
RUN \
  mkdir -p /tmp/extensions && \
  chmod 777 /tmp/extensions
