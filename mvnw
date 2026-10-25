#!/bin/sh
# Local Maven entry point. Uses installed Maven and JDKs; downloads neither.
exec python3 "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/scripts/maven.py" "$@"
