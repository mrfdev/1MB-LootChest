#!/bin/bash

actual_path="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
local_repo_path="$actual_path/requirements/local-repo"
echo "$local_repo_path"

if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
  echo "Usage: deploy-to-local-repo.sh <jar-file> <group-id> <artifact-id> <version>"
  exit 1
fi

# The Maven launcher runs from the project root; retain the caller's JAR path.
jar_file="$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")"

"$actual_path/mvnw" deploy:deploy-file \
  -Durl="file://$local_repo_path" \
  -DrepositoryId=local-repo \
  -Dfile="$jar_file" \
  -DgroupId="$2" \
  -DartifactId="$3" \
  -Dversion="$4" \
  -Dpackaging=jar
