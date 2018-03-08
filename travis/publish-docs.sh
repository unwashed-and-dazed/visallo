#!/bin/bash -eu

if [ "${TRAVIS_BRANCH}" = "${VERSION_ROOT}" ]; then
  # Copy to root => docs.visallo.org/*
  aws s3 sync docs/_book s3://docs-visallo-com --delete --exclude "versions/*"
  # Copy to versioned => docs.visallo.org/versions/$BRANCH
  aws s3 sync s3://docs-visallo-com "s3://docs-visallo-com/versions/${TRAVIS_BRANCH}" --delete --exclude "versions/*"
  # Invalidate cloudfront distribution (whole deployment)
  aws cloudfront create-invalidation --distribution-id E1FQ5XMTOW2BH8 --paths "/*"
else
  # Copy to versioned => docs.visallo.org/versions/$BRANCH
  aws s3 sync docs/_book "s3://docs-visallo-com/versions/${TRAVIS_BRANCH}" --delete
  # Invalidate cloudfront distribution (just this version)
  aws cloudfront create-invalidation --distribution-id E1FQ5XMTOW2BH8 --paths "/versions/${TRAVIS_BRANCH}/*"
fi
