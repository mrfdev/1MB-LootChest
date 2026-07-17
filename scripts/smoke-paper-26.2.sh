#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNNER="${LOOTCHEST_TEST_RUNNER:-/Users/floris/Projects/Codex/servers/run-test-server}"
STARTUP_TIMEOUT="${LOOTCHEST_SMOKE_STARTUP_TIMEOUT:-180}"
COMMAND_TIMEOUT="${LOOTCHEST_SMOKE_COMMAND_TIMEOUT:-45}"

usage() {
  printf 'Usage: %s <LootChest Paper 26.2 jar>\n' "$(basename "$0")" >&2
}

fail() {
  printf '[smoke] FAIL: %s\n' "$*" >&2
  printf '[smoke] Raw log: %s\n' "$RAW_LOG" >&2
  printf '[smoke] Clean log: %s\n' "$CLEAN_LOG" >&2
  exit 1
}

if [[ "$#" -ne 1 ]]; then
  usage
  exit 2
fi

JAR_INPUT="$1"
if [[ "$JAR_INPUT" != /* ]]; then
  JAR_INPUT="$ROOT/$JAR_INPUT"
fi
[[ -f "$JAR_INPUT" ]] || {
  printf '[smoke] Jar not found: %s\n' "$JAR_INPUT" >&2
  exit 2
}
JAR="$(cd "$(dirname "$JAR_INPUT")" && pwd)/$(basename "$JAR_INPUT")"
[[ -x "$RUNNER" ]] || {
  printf '[smoke] Test runner is not executable: %s\n' "$RUNNER" >&2
  exit 2
}

STAMP="$(date '+%Y%m%d-%H%M%S')"
RUN_DIR="$ROOT/target/smoke-paper-26.2/$STAMP"
RAW_LOG="$RUN_DIR/console.raw.log"
CLEAN_LOG="$RUN_DIR/console.log"
FIFO="$RUN_DIR/console.in"
SERVER_PID=""
mkdir -p "$RUN_DIR"
mkfifo "$FIFO"

refresh_log() {
  if [[ -f "$RAW_LOG" ]]; then
    perl -pe 's/\e\[[0-9;?]*[ -\/]*[@-~]//g' "$RAW_LOG" > "$CLEAN_LOG"
  fi
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" >/dev/null 2>&1; then
    printf 'stop\n' >&3 2>/dev/null || true
    sleep 5
    if kill -0 "$SERVER_PID" >/dev/null 2>&1; then
      kill "$SERVER_PID" >/dev/null 2>&1 || true
    fi
    wait "$SERVER_PID" >/dev/null 2>&1 || true
  fi
  exec 3>&- || true
  rm -f "$FIFO"
  refresh_log
  exit "$status"
}
trap cleanup EXIT INT TERM

wait_for_log() {
  local text="$1"
  local description="$2"
  local timeout="$3"
  local elapsed=0

  while (( elapsed < timeout )); do
    refresh_log
    if grep -Fq "$text" "$CLEAN_LOG" 2>/dev/null; then
      printf '[smoke] OK: %s\n' "$description"
      return 0
    fi
    if ! kill -0 "$SERVER_PID" >/dev/null 2>&1; then
      wait "$SERVER_PID" >/dev/null 2>&1 || true
      SERVER_PID=""
      fail "server exited while waiting for $description"
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  fail "timed out after ${timeout}s waiting for $description"
}

send_and_wait() {
  local command="$1"
  local response="$2"
  local description="$3"

  printf '[smoke] Command: %s\n' "$command"
  printf '%s\n' "$command" >&3
  wait_for_log "$response" "$description" "$COMMAND_TIMEOUT"
}

printf '[smoke] Jar: %s\n' "$JAR"
printf '[smoke] Starting isolated Paper 26.2 instance...\n'
"$RUNNER" \
  --paper 26.2 \
  --project "lootchest-smoke-$STAMP" \
  --project-dir "$ROOT" \
  --plugin "$JAR" \
  --require-plugin \
  --foreground \
  < "$FIFO" > "$RAW_LOG" 2>&1 &
SERVER_PID=$!
exec 3> "$FIFO"

wait_for_log "[LootChest] Plugin loaded" "LootChest enabled" "$STARTUP_TIMEOUT"
send_and_wait "lc info" "Targets Paper 26.2" "/lc info reported the Paper target"
send_and_wait "lc help" "Lootbox commands" "/lc help responded"
send_and_wait "lc list" "LootChests:" "/lc list responded"
send_and_wait \
  "lc reload" \
  "Configuration, locale, chest data, and LootChests were reloaded." \
  "/lc reload completed"
send_and_wait "lc despawnall" "All LootChests were despawned." "/lc despawnall completed"
send_and_wait "lc respawnall" "All LootChests were respawned." "/lc respawnall completed"

printf '[smoke] Command: stop\n'
printf 'stop\n' >&3
wait_for_log "[LootChest] Disabling LootChest" "LootChest began clean shutdown" "$COMMAND_TIMEOUT"
exec 3>&-

if ! wait "$SERVER_PID"; then
  SERVER_PID=""
  fail "Paper exited with a non-zero status"
fi
SERVER_PID=""
refresh_log

ERROR_PATTERN='Error occurred while (enabling|disabling) LootChest|NoClassDefFoundError|NoSuchMethodError|ClassNotFoundException|UnsupportedClassVersionError|CommandException|PluginClassLoader.*LootChest|zip file closed|\[ERROR\].*(LootChest|lootchest)|Exception.*fr\.black_eyes|fr\.black_eyes.*Exception'
if grep -Eiq "$ERROR_PATTERN" "$CLEAN_LOG"; then
  grep -Ein "$ERROR_PATTERN" "$CLEAN_LOG" >&2 || true
  fail "a LootChest compatibility error was found in the server log"
fi

PORT="$(sed -n 's/.*Paper 26\.2 ready on 127\.0\.0\.1:\([0-9][0-9]*\).*/\1/p' "$CLEAN_LOG" | tail -n 1)"
[[ -n "$PORT" ]] || fail "could not determine the temporary Paper port"

for _ in {1..10}; do
  if ! lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  fail "Paper stopped but port $PORT is still listening"
fi

INSTANCE_DIR="$(sed -n 's/.*starting foreground server in //p' "$CLEAN_LOG" | tail -n 1)"
printf '[smoke] PASS: Paper 26.2 compatibility smoke test completed.\n'
printf '[smoke] Instance: %s\n' "${INSTANCE_DIR:-unknown}"
printf '[smoke] Port %s is free.\n' "$PORT"
printf '[smoke] Raw log: %s\n' "$RAW_LOG"
printf '[smoke] Clean log: %s\n' "$CLEAN_LOG"
