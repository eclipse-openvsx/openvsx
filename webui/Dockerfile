FROM node:22-alpine3.22 AS builder

# Copy sources
COPY --chown=node:node package.json tsconfig.json vite.config.mts yarn.lock .yarnrc.yml /home/node/builder/
WORKDIR /home/node/builder
COPY --chown=node:node .yarn ./.yarn/
COPY --chown=node:node configs ./configs/
COPY --chown=node:node index.html ./
COPY --chown=node:node src ./src/
COPY --chown=node:node static ./static/
COPY --chown=node:node test ./test/

# Build and test the library and default app
RUN corepack enable
RUN corepack prepare yarn@stable --activate
RUN yarn install --immutable
RUN yarn test
RUN yarn build:default


FROM node:22-alpine3.22

# Copy build result to runtime directory
COPY --chown=node:node --from=builder /home/node/builder/dist /home/node/webui/dist/
COPY --chown=node:node --from=builder /home/node/builder/lib/default/server.js /home/node/webui/lib/default/
WORKDIR /home/node/webui

# Install Express server
RUN yarn add express@4.21.0
RUN yarn add express-rate-limit@7.4.0

EXPOSE 3000
ENTRYPOINT ["node", "lib/default/server"]
