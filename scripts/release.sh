#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
PROJECT_ROOT=$( dirname "${SCRIPT_DIR}" )

usage() {
  local USAGE
  USAGE="
Usage: $(basename "${0}") [options] <cli|webui|openvsx> <version>

This scripts starts the release process for different components developed in this repository.
Depending on the selected component, the repository will be tagged which triggers a workflow
to build and publish the respective component.

Options:

  -h             show this help
"
  echo "$USAGE"
  exit 1
}


while getopts "h:" o; do
    case "${o}" in
        *)
            usage
            ;;
    esac
done

COMPONENT=${*:$OPTIND:1}
VERSION=${*:$OPTIND+1:1}

if [ -z "${COMPONENT-}" ] || [ -z "${VERSION-}" ]; then
    usage
fi

COMPONENTS=("cli" "webui" "openvsx")

if ! [[ "${COMPONENTS[*]}" =~ ${COMPONENT} ]]; then
    echo "Component '${COMPONENT}' is not known."
    exit 1
fi

case "${COMPONENT}" in
    "cli")
        CHANGELOG="cli/CHANGELOG.md"
        PROJECT_VERSION=$(jq -r .version cli/package.json)
        if [[ "${VERSION}" != "${PROJECT_VERSION}" ]]; then
          echo "cli version '${PROJECT_VERSION}' does not match version from tag '${VERSION}'."
          exit 1
        fi
        ;;

    "webui")
        CHANGELOG="webui/CHANGELOG.md"
        PROJECT_VERSION=$(jq -r .version webui/package.json)
        if [[ "${VERSION}" != "${PROJECT_VERSION}" ]]; then
          echo "webui version '${PROJECT_VERSION}' does not match version from tag '${VERSION}'."
          exit 1
        fi
        ;;

    "openvsx")
        CHANGELOG="server/CHANGELOG.md"
        # TODO: the server component currently does not have a version indicator in the build.gradle
        if ! ./server/scripts/dependency-override-check.sh; then
          echo "openvsx server has dependency overrides behind the Spring Boot BOM; review them first."
          exit 1
        fi
        UNRELEASED=$(awk '/^\| Property / { property = $4 } /^\| Compatibility +\| Unreleased/ { print "  " property }' doc/configuration.md)
        if [ -n "${UNRELEASED}" ]; then
          echo "doc/configuration.md still marks these properties as Unreleased; date them to ${VERSION} first:"
          echo "${UNRELEASED}"
          exit 1
        fi
        ;;
esac

# The newest changelog entry must be this release, not the "[next]" placeholder.
LATEST_ENTRY=$(grep -m1 -E '^#{2,3} \[' "${PROJECT_ROOT}/${CHANGELOG}" || true)
if [[ -z "${LATEST_ENTRY}" || "${LATEST_ENTRY}" == *"[next]"* ]]; then
  echo "${CHANGELOG}: latest entry is '${LATEST_ENTRY:-missing}'; replace [next] with the release version and date first."
  exit 1
fi

echo "Releasing component ${COMPONENT}@${VERSION}..."

echo "Checking for uncommitted changes..."
git -C "${PROJECT_ROOT}" status -s -uno | grep '.' && exit 1

if [[ ${COMPONENT} = "openvsx" ]]; then
    TAG="v${VERSION}"
else
    TAG="${COMPONENT}-${VERSION}"
fi

echo "Tagging repository with '${TAG}'..."
git tag "${TAG}"

echo "Pushing tags to origin..."
git push --tags

echo "Done."
