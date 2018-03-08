#!/bin/bash
# 
# The following script has been created to interact with the Travis API. It has
# been derived from the Travis API documentation site: https://docs.travis-ci.com/api
# Use under your own risk. 
#

TRAVIS_PRIVATE=travis-ci.com
TRAVIS_PUBLIC=travis-ci.org

# Defaults
travis_url=$TRAVIS_PRIVATE
branch=master
owner=visallo
repo=
auth_token=
triggeredBy=anonymous 
build_state=unknown
verbose=false
global_env=
cancel_existing_build=false

function help {
    echo "travis-request.sh [--pro] [--org] [-b | --branch <name>] [--owner <name>]"
    echo "                  [--by <value>] [-v | --verbose] --repo <name> -t|--token <value>"
    echo ""
    echo "Argument details"
    echo "  --pro                   Use travis-ci.com endpoint for private repositories."
    echo "                          Set by default."
    echo "  --org                   Use travis-ci.org endpoint for public repositories."
    echo "  -b|--branch <name>      Sets active branch. Default: \"master\""
    echo "  -e|--env \"name=value\" Set global environment variables, Multiple: \"name1=value1\",\"name2=value2\""
    echo "  --owner <name>          Sets slug owner name. Default: \"visallo\""
    echo "  --by <value>            String to be added to originator message."
    echo "                          Default: \"anonymous\""
    echo "  -v|--verbose            Prints Travis API responses"
    echo "  --repo <name>           Sets slug repo name. Required field"
    echo "  -t|--token <value>      GitHub or Travis access token. Required field"
    echo ""
}

# Triggers a build for a given owner/repo and branch
function travis_build_request {
  message="Originated by ${triggeredBy}"   

  body="
  \"branch\":\"$branch\",
  \"message\":\"$message\"
  "
  branches_to_build="\"branches\": { \"only\": [\"$branch\"]}"
  if [ ! -z ${global_env} ]; then
    body="${body}, \"config\":{ \"env\": { \"global\": [ ${global_env} ]}, ${branches_to_build}}"
  else
    body="${body}, \"config\":{[${branches_to_build}]}"
  fi

  body="{\"request\": { ${body} }}"

  if [[ "$verbose" == true ]]; then
    echo "Travis build request body:"
    echo "${body}"
    echo ""
  fi

  response=`curl -s -X POST \
    -H "Travis-API-Version: 3" \
    -H "Authorization: token ${auth_token}" \
    -H "Content-Type: application/json" \
    -d "${body}" \
    https://api.${travis_url}/repo/${owner}%2F${repo}/requests`

  if [[ "$verbose" == true ]]; then 
    echo "Travis build request response:"
    echo "$response"
    echo ""
  fi
}

# Cancels the last build for a given owner/repo and branch
function travis_cancel_build {
  build_state=unknown

  response=`curl -s \
    -H "Travis-API-Version: 3" \
    -H "Authorization: token ${auth_token}" \
    https://api.${travis_url}/repo/${owner}%2F${repo}/branch/${branch}`

  if [[ "${verbose}" == true ]]; then
    echo "Travis branch info response:"
    echo ${response}
    echo ""
  fi

  build_state=`cat ${response} | jq --raw-output '.last_build.state'`
  if [[ "$verbose" == true ]]; then
    echo "Build state: " ${build_state}
    echo ""
  fi

  if [[ "${build_state}" == "started" ]]; then
    build_id=`cat ${response} | jq --raw-output '.last_build.id'`

    if [[ "$verbose" == true ]]; then
      echo "Cancelling build: " ${build_state}
      echo ""
    fi

    response=`curl -s \
      -H "Travis-API-Version: 3" \
      -H "Authorization: token ${auth_token}" \
      https://api.${travis_url}/build/${build_id}/cancel`

    if [[ "${verbose}" == true ]]; then
      echo "Travis build cancel response:"
      echo ${response}
      echo ""
    fi
  fi
}

# Parse arguments
while [[ $# -gt 0 ]]
do
  arg="$1"
  case $arg in
    --org)
    travis_url=$TRAVIS_PUBLIC
    ;;
    --pro)
    travis_url=$TRAVIS_PRIVATE
    ;;
    -b|--branch)
    branch=$2
    shift
    ;;
    --owner)
    owner=$2
    shift
    ;;
    --repo)
    repo=$2
    shift
    ;;
    -e|--env)
    global_env=$2
    shift
    ;;
    -t|--token)
    auth_token=$2
    shift
    ;;
    --by)
    triggeredBy=$2
    shift
    ;;
    -c|--cancel-existing)
    cancel_existing_build=true
    ;;
    -v|--verbose)
    verbose=true
    ;;
    -h|--help)
    help
    exit
    ;;
    *)
    help
    exit
    ;;
  esac
  shift 
done

if [[ "$verbose" == true ]]; then
  echo "travis_url=            "$travis_url
  echo "branch=                "$branch
  echo "owner=                 "$owner
  echo "repo=                  "$repo
  echo "triggeredBy=           "$triggeredBy
  echo "verbose=               "$verbose
  echo "cancel_existing_build= "$cancel_existing_build
  echo ""
fi	

# Required fields
if [[ -z "$repo" ]] || [[ -z "$auth_token" ]]; then 
  help
  exit
fi

if [[ "$cancel_existing_build" == "true" ]]; then
  travis_cancel_build
fi

travis_build_request
