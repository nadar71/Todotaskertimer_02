#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
readonly CAPTURE_SCRIPT="$SCRIPT_DIR/capture.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

write_fake_tools() {
  local root="$1"
  mkdir -p "$root/sdk/platform-tools"
  # shellcheck disable=SC2016 # Write runtime variables into the fake executable.
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'printf '\''%q '\'' "$@" >> "$FAKE_COMMAND_LOG"' \
    'printf '\''\n'\'' >> "$FAKE_COMMAND_LOG"' \
    'case "$*" in' \
    '  *" get-state") echo device ;;' \
    '  *" shell getprop ro.kernel.qemu") echo "$FAKE_QEMU" ;;' \
    '  *" shell settings get global sysui_demo_allowed") echo 0 ;;' \
    '  *" shell cmd locale get-app-locales "*)' \
    '    echo "Locales for com.indiewalkabout.nowdothis for user 0 are [$FAKE_PREVIOUS_LOCALES]"' \
    '    ;;' \
    '  *" shell content call "*) echo "Bundle[{task_count=6}]" ;;' \
    '  *" shell am instrument "*) echo "FAILURES!!!" ;;' \
    'esac' \
    > "$root/sdk/platform-tools/adb"
  chmod +x "$root/sdk/platform-tools/adb"

  # shellcheck disable=SC2016 # Write runtime variables into the fake executable.
  printf '%s\n' \
    '#!/usr/bin/env bash' \
    'printf '\''gradlew %s\n'\'' "$*" >> "$FAKE_COMMAND_LOG"' \
    > "$root/gradlew"
  chmod +x "$root/gradlew"
}

run_capture() {
  local root="$1"
  local qemu="$2"
  local previous_locales="$3"
  local output_file="$4"
  set +e
  (
    cd "$root"
    ANDROID_HOME="$root/sdk" \
      FAKE_COMMAND_LOG="$root/commands.log" \
      FAKE_QEMU="$qemu" \
      FAKE_PREVIOUS_LOCALES="$previous_locales" \
      bash "$CAPTURE_SCRIPT" --locale it-IT --serial emulator-5554
  ) >"$output_file" 2>&1
  local status=$?
  set -e
  return "$status"
}

assert_logged_command() {
  local log="$1"
  shift
  local expected=""
  local argument
  for argument in "$@"; do
    printf -v expected '%s%q ' "$expected" "$argument"
  done
  grep -Fqx -- "$expected" "$log" || fail "missing command: $expected"
}

test_non_emulator_is_rejected_before_destructive_commands() {
  local root
  root="$(mktemp -d)"
  trap 'rm -rf "$root"' RETURN
  write_fake_tools "$root"

  if run_capture "$root" 0 "" "$root/output.log"; then
    fail "non-emulator capture unexpectedly succeeded"
  fi
  grep -Fq "requires a running emulator" "$root/output.log" ||
    fail "non-emulator rejection was not clear"
  assert_logged_command \
    "$root/commands.log" -s emulator-5554 shell getprop ro.kernel.qemu
  if grep -Eq '(^gradlew | install | content call )' "$root/commands.log"; then
    fail "destructive command ran before emulator rejection"
  fi
}

test_locale_is_restored_after_instrumentation_failure() {
  local previous_locales="$1"
  local root
  root="$(mktemp -d)"
  trap 'rm -rf "$root"' RETURN
  write_fake_tools "$root"

  if run_capture "$root" 1 "$previous_locales" "$root/output.log"; then
    fail "forced instrumentation failure unexpectedly succeeded"
  fi
  grep -Fq "Store-media instrumentation failed" "$root/output.log" ||
    fail "instrumentation failure did not reach the host trap"
  assert_logged_command \
    "$root/commands.log" -s emulator-5554 shell cmd locale get-app-locales \
    com.indiewalkabout.nowdothis
  assert_logged_command \
    "$root/commands.log" -s emulator-5554 shell cmd locale set-app-locales \
    com.indiewalkabout.nowdothis --locales "$previous_locales"
}

test_non_emulator_is_rejected_before_destructive_commands
test_locale_is_restored_after_instrumentation_failure "fr-FR,en-US"
test_locale_is_restored_after_instrumentation_failure ""
echo "PASS: capture host safety tests"
