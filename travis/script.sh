#!/bin/bash -e

if [ "${BUILD_DOCS}" ]; then

  # only build if merging into real branch
  if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then

    # Check if this branch should be built as part of documentation
    if [[ ${VERSION_LIST} =~ (^|[[:space:]])${TRAVIS_BRANCH}($|[[:space:]]) ]]; then
      docker run --volume ${HOME}/.m2/repository:/root/.m2/repository \
             --volume ${HOME}/.npm:/root/.npm \
             --volume $(pwd):/root/visallo \
             -w /root/visallo \
             -e "VERSION_CURRENT=${TRAVIS_BRANCH}" \
             -e "VERSION_LIST=${VERSION_LIST}" \
             -e "GITBOOK_EMBED_GITHUB_API_TOKEN=${GITBOOK_EMBED_GITHUB_API_TOKEN}" \
             --rm -it ${DOCKER_IMAGE} \
             /bin/sh -c "make -C docs link-check-external"
    else
      echo "Branch not found in VERSION_LIST for docs, skipping"
    fi
  fi

else
  echo "Building Visallo"
  docker run --volume ${HOME}/.m2/repository:/root/.m2/repository \
             --volume ${HOME}/.npm:/root/.npm \
             --volume $(pwd):/root/visallo \
             -w /root/visallo \
             -e "MVN_REPO_USERNAME=${DEPLOY_USERNAME}" \
             -e "MVN_REPO_PASSWORD=${DEPLOY_PASSWORD}" \
             --rm -it ${DOCKER_IMAGE} \
             /bin/sh -c "mvn -B -fae test -DlogQuiet"
fi
