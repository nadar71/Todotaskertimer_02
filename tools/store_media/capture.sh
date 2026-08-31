#!/usr/bin/env bash
set -euo pipefail

readonly APP_ID="com.indiewalkabout.nowdothis"
readonly TEST_RUNNER="${APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
readonly TEST_CLASS="com.indiewalkabout.nowdothis.storemedia.StoreMediaCaptureTest"
readonly FIXTURE_URI="content://com.indiewalkabout.nowdothis.store-media-fixture"
readonly REMOTE_ROOT="/sdcard/Android/data/${APP_ID}/files/store-media-captures"
readonly CAPTURE_ROOT="store-assets/google-play/source/captures"
readonly ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
readonly EXPECTED_NAMES=(
  "01-focus.png"
  "02-quick-capture.png"
  "03-natural-language.png"
  "04-recurrence.png"
  "05-organize.png"
  "06-portability.png"
)

locale=""
serial=""
while (($#)); do
  case "$1" in
    --locale)
      locale="${2:-}"
      shift 2
      ;;
    --serial)
      serial="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

case "$locale" in
  it-IT) test_method="captureItalianPhoneStory" ;;
  en-US) test_method="captureEnglishPhoneStory" ;;
  *)
    echo "--locale must be it-IT or en-US" >&2
    exit 2
    ;;
esac
if [[ -z "$serial" ]]; then
  echo "--serial is required" >&2
  exit 2
fi
if [[ ! -x "$ADB" ]]; then
  echo "ADB not found at $ADB" >&2
  exit 2
fi
if [[ "$($ADB -s "$serial" get-state 2>/dev/null)" != "device" ]]; then
  echo "Device $serial is not connected" >&2
  exit 2
fi
qemu="$($ADB -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')"
if [[ "$qemu" != "1" ]]; then
  echo "Store-media capture requires a running emulator; $serial reported ro.kernel.qemu=$qemu" >&2
  exit 2
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
$ADB -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
$ADB -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

demo_allowed="$($ADB -s "$serial" shell settings get global sysui_demo_allowed | tr -d '\r')"
app_locale_output="$($ADB -s "$serial" shell cmd locale get-app-locales "$APP_ID" | tr -d '\r')"
if [[ "$app_locale_output" != *" are ["*"]" ]]; then
  echo "Unable to snapshot app locales: $app_locale_output" >&2
  exit 1
fi
previous_app_locales="${app_locale_output##* are [}"
previous_app_locales="${previous_app_locales%]}"
restore_emulator() {
  $ADB -s "$serial" shell am broadcast -a com.android.systemui.demo \
    -e command exit >/dev/null 2>&1 || true
  if [[ "$demo_allowed" == "null" ]]; then
    $ADB -s "$serial" shell settings delete global sysui_demo_allowed >/dev/null
  else
    $ADB -s "$serial" shell settings put global sysui_demo_allowed "$demo_allowed"
  fi
  $ADB -s "$serial" shell cmd locale set-app-locales "$APP_ID" \
    --locales "$previous_app_locales" >/dev/null
}
trap restore_emulator EXIT INT TERM

$ADB -s "$serial" shell settings put global sysui_demo_allowed 1
$ADB -s "$serial" shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
$ADB -s "$serial" shell am broadcast -a com.android.systemui.demo \
  -e command clock -e hhmm 1000 >/dev/null
$ADB -s "$serial" shell am broadcast -a com.android.systemui.demo \
  -e command battery -e level 100 -e plugged false >/dev/null
$ADB -s "$serial" shell am broadcast -a com.android.systemui.demo \
  -e command network -e wifi show -e level 4 -e mobile hide >/dev/null
$ADB -s "$serial" shell am broadcast -a com.android.systemui.demo \
  -e command notifications -e visible false >/dev/null

fixture_result="$($ADB -s "$serial" shell content call \
  --uri "$FIXTURE_URI" --method prepare_store_media --arg "$locale")"
if [[ "$fixture_result" != *"task_count=6"* ]]; then
  echo "Fixture preparation failed: $fixture_result" >&2
  exit 1
fi

$ADB -s "$serial" shell rm -rf "$REMOTE_ROOT/$locale"
rm -rf "$CAPTURE_ROOT/${locale:?}"
instrumentation_output="$($ADB -s "$serial" shell am instrument -w \
  -e class "${TEST_CLASS}#${test_method}" "$TEST_RUNNER" 2>&1)"
printf '%s\n' "$instrumentation_output"
if [[ "$instrumentation_output" != *"OK (1 test)"* ]]; then
  echo "Store-media instrumentation failed" >&2
  exit 1
fi

mkdir -p "$CAPTURE_ROOT/$locale"
$ADB -s "$serial" pull "$REMOTE_ROOT/$locale/." "$CAPTURE_ROOT/$locale/" >/dev/null

for name in "${EXPECTED_NAMES[@]}"; do
  path="$CAPTURE_ROOT/$locale/$name"
  if [[ ! -s "$path" ]]; then
    echo "Missing capture: $locale/$name" >&2
    exit 1
  fi
done

echo "Captured ${#EXPECTED_NAMES[@]} store-media screens for $locale"
