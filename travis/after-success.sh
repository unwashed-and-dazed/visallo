#!/bin/bash -e

if [ "${BUILD_DOCS}" ]; then

  if [ -d "docs/_book" ]; then
    docker run --volume $(pwd):/root/visallo \
         -w /root/visallo \
         -e "AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}" \
         -e "AWS_SECRET_ACCESS_KEY=${AWS_SECRET_ACCESS_KEY}" \
         -e "TRAVIS_BRANCH=${TRAVIS_BRANCH}" \
         -e "VERSION_ROOT=${VERSION_ROOT}" \
         --rm -it ${DOCKER_IMAGE} \
         /bin/sh -c "travis/publish-docs.sh"
  fi
else
  if [ ${TRAVIS_REPO_SLUG} = "visallo/visallo" ]; then
    if [ ${TRAVIS_BRANCH} = "master" ] || echo ${TRAVIS_BRANCH} | grep -Eq '^release-.*$'; then
      if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
        docker run --volume ${HOME}/.m2/repository:/root/.m2/repository \
             --volume ${HOME}/.npm:/root/.npm \
             --volume $(pwd):/root/visallo \
             -w /root/visallo \
             -e "MVN_REPO_USERNAME=${DEPLOY_USERNAME}" \
             -e "MVN_REPO_PASSWORD=${DEPLOY_PASSWORD}" \
             --rm -it ${DOCKER_IMAGE} \
             /bin/sh -c "mvn -B -f root/pom.xml deploy && mvn -B -DskipTests deploy"
      fi
    fi
  fi

  REPOS_BRANCH=${TRAVIS_BRANCH}

  if [ ${TRAVIS_BRANCH} = "master" ] || echo ${TRAVIS_BRANCH} | grep -Eq '^release-.*$'; then
    if [ ! -z ${TRAVIS_PULL_REQUEST_BRANCH} ]; then
      REPOS_BRANCH=${TRAVIS_PULL_REQUEST_BRANCH}
    fi
  fi
  
  CREATED_BY="${TRAVIS_REPO_SLUG} branch ${REPOS_BRANCH} commit "`git rev-parse --short HEAD`""

  # Checking to see if repo branch exists in visallo-lts
  set +e
  LTS_BRANCH_TO_BUILD=${REPOS_BRANCH}
  wget --no-cache --spider -d --header="Authorization: token ${GIT_API_ACCESS_TOKEN}" https://api.github.com/repos/visallo/visallo-lts/branches/${REPOS_BRANCH}
  if [ $? != 0 ]; then
    LTS_BRANCH_TO_BUILD=${TRAVIS_BRANCH}
  fi
  set -e

  ./travis/travis-request.sh \
    --repo ${TRAVIS_DOWNSTREAM_REPO} \
    --token ${TRAVIS_ACCESS_TOKEN_PRO} \
    --by "${CREATED_BY}" \
    --branch ${LTS_BRANCH_TO_BUILD} \
    --env "\"REPOS_BRANCH=${REPOS_BRANCH}\",\"DOCKER_IMAGE=${DOCKER_IMAGE}\""
fi
