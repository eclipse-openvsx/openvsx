# Common development tasks. Run `make` for the list.
#
# The three run targets are meant to be started in separate terminals: `make dev-env` holds the
# containers the other two talk to, and both `make server` and `make webui` stay in the foreground
# so that they pick up code changes.

# Postgres, the Valkey cluster with its admin UI, and Silo. Elasticsearch is deliberately absent:
# the dev profile searches the database instead (ovsx.databasesearch.enabled).
DEV_PROFILES := db valkey valkey-admin silo

.DEFAULT_GOAL := help
.PHONY: help dev-env dev-env-down server webui

help: ## Show the available targets
	@grep -hE '^[a-z][a-z-]*:.*## ' $(MAKEFILE_LIST) \
		| sort \
		| awk -F ':.*## ' '{ printf "  %-14s %s\n", $$1, $$2 }'

dev-env: ## Start the containers the server and web UI need (Ctrl-C stops them)
	docker compose $(addprefix --profile ,$(DEV_PROFILES)) up

dev-env-down: ## Stop those containers and remove them
	docker compose $(addprefix --profile ,$(DEV_PROFILES)) down

server: ## Run the registry server on http://localhost:8080
	cd server && ./scripts/generate-properties.sh && ./gradlew runServer

webui: ## Run the web UI on http://localhost:3000
	cd webui && yarn install --immutable && yarn dev
