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
        PROJECT_VERSION=$(jq -r .version cli/package.json)
        if [[ "${VERSION}" != "${PROJECT_VERSION}" ]]; then
          echo "cli version '${PROJECT_VERSION}' does not match version from tag '${VERSION}'."
          exit 1
        fi
        ;;

    "webui")
        PROJECT_VERSION=$(jq -r .version webui/package.json)
        if [[ "${VERSION}" != "${PROJECT_VERSION}" ]]; then
          echo "webui version '${PROJECT_VERSION}' does not match version from tag '${VERSION}'."
          exit 1
        fi
        ;;

    "openvsx")
        # TODO: the server component currently does not have a version indicator in the build.gradle
        ;;
esac

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
