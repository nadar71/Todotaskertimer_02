#!/usr/bin/env bash
set -euo pipefail

readonly APP_ID="com.indiewalkabout.nowdothis"
readonly TEST_RUNNER="${APP_ID}.test/androidx.test.runner.AndroidJUnitRunner"
readonly TEST_CLASS="com.indiewalkabout.nowdothis.storemedia.StoreMediaCaptureTest"
readonly FIXTURE_URI="content://com.indiewalkabout.nowdothis.store-media-fixture"
readonly REMOTE_ROOT="/sdcard/Android/data/${APP_ID}/files/store-media-captures"
readonly CAPTURE_ROOT="store-assets/google-play/source/captures"
readonly ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
readonly REQUIRED_API=33
readonly REQUIRED_SIZE="1080x2400"
readonly REQUIRED_DENSITY="420"
readonly REQUIRED_FONT_SCALE="1.0"
readonly REQUIRED_NIGHT_MODE="1"
readonly REQUIRED_TIMEZONE="Europe/Rome"
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
api="$($ADB -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ ! "$api" =~ ^[0-9]+$ || "$api" -lt "$REQUIRED_API" ]]; then
  echo "Store-media capture requires an API $REQUIRED_API+ emulator; $serial reported API $api" >&2
  exit 2
fi
wm_size_output="$($ADB -s "$serial" shell wm size | tr -d '\r')"
native_size="$(sed -n 's/^Physical size: //p' <<<"$wm_size_output" | head -n 1)"
if [[ "$native_size" != "$REQUIRED_SIZE" ]]; then
  echo "Store-media capture requires native $REQUIRED_SIZE; $serial reported ${native_size:-unknown}" >&2
  exit 2
fi
wm_density_output="$($ADB -s "$serial" shell wm density | tr -d '\r')"
native_density="$(sed -n 's/^Physical density: //p' <<<"$wm_density_output" | head -n 1)"
if [[ "$native_density" != "$REQUIRED_DENSITY" ]]; then
  echo "Store-media capture requires native density $REQUIRED_DENSITY; $serial reported ${native_density:-unknown}" >&2
  exit 2
fi

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
$ADB -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null
$ADB -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk >/dev/null

demo_allowed="$($ADB -s "$serial" shell settings get global sysui_demo_allowed | tr -d '\r')"
font_scale="$($ADB -s "$serial" shell settings get system font_scale | tr -d '\r')"
night_mode="$($ADB -s "$serial" shell settings get secure ui_night_mode | tr -d '\r')"
timezone="$($ADB -s "$serial" shell getprop persist.sys.timezone | tr -d '\r')"
auto_time_zone="$($ADB -s "$serial" shell settings get global auto_time_zone | tr -d '\r')"
accelerometer_rotation="$($ADB -s "$serial" shell settings get system accelerometer_rotation | tr -d '\r')"
user_rotation="$($ADB -s "$serial" shell settings get system user_rotation | tr -d '\r')"
window_animation_scale="$($ADB -s "$serial" shell settings get global window_animation_scale | tr -d '\r')"
transition_animation_scale="$($ADB -s "$serial" shell settings get global transition_animation_scale | tr -d '\r')"
animator_duration_scale="$($ADB -s "$serial" shell settings get global animator_duration_scale | tr -d '\r')"
previous_size_override="$(sed -n 's/^Override size: //p' <<<"$wm_size_output" | head -n 1)"
previous_density_override="$(sed -n 's/^Override density: //p' <<<"$wm_density_output" | head -n 1)"
app_locale_output="$($ADB -s "$serial" shell cmd locale get-app-locales "$APP_ID" | tr -d '\r')"
if [[ "$app_locale_output" != *" are ["*"]" ]]; then
  echo "Unable to snapshot app locales: $app_locale_output" >&2
  exit 1
fi
previous_app_locales="${app_locale_output##* are [}"
previous_app_locales="${previous_app_locales%]}"
restore_setting() {
  local namespace="$1"
  local key="$2"
  local value="$3"
  if [[ "$value" == "null" || -z "$value" ]]; then
    $ADB -s "$serial" shell settings delete "$namespace" "$key" >/dev/null
  else
    $ADB -s "$serial" shell settings put "$namespace" "$key" "$value" >/dev/null
  fi
}
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
  restore_setting system font_scale "$font_scale"
  restore_setting secure ui_night_mode "$night_mode"
  restore_setting system accelerometer_rotation "$accelerometer_rotation"
  restore_setting system user_rotation "$user_rotation"
  restore_setting global window_animation_scale "$window_animation_scale"
  restore_setting global transition_animation_scale "$transition_animation_scale"
  restore_setting global animator_duration_scale "$animator_duration_scale"
  if [[ -n "$timezone" ]]; then
    $ADB -s "$serial" shell settings put global auto_time_zone 0 >/dev/null
    $ADB -s "$serial" shell cmd alarm set-timezone "$timezone" >/dev/null
  fi
  restore_setting global auto_time_zone "$auto_time_zone"
  if [[ -n "$previous_size_override" ]]; then
    $ADB -s "$serial" shell wm size "$previous_size_override" >/dev/null
  else
    $ADB -s "$serial" shell wm size reset >/dev/null
  fi
  if [[ -n "$previous_density_override" ]]; then
    $ADB -s "$serial" shell wm density "$previous_density_override" >/dev/null
  else
    $ADB -s "$serial" shell wm density reset >/dev/null
  fi
}
trap restore_emulator EXIT INT TERM

$ADB -s "$serial" shell wm size "$REQUIRED_SIZE" >/dev/null
$ADB -s "$serial" shell wm density "$REQUIRED_DENSITY" >/dev/null
$ADB -s "$serial" shell settings put system font_scale "$REQUIRED_FONT_SCALE"
$ADB -s "$serial" shell settings put secure ui_night_mode "$REQUIRED_NIGHT_MODE"
$ADB -s "$serial" shell settings put global auto_time_zone 0
$ADB -s "$serial" shell cmd alarm set-timezone "$REQUIRED_TIMEZONE" >/dev/null
$ADB -s "$serial" shell settings put system accelerometer_rotation 0
$ADB -s "$serial" shell settings put system user_rotation 0
$ADB -s "$serial" shell settings put global window_animation_scale 0
$ADB -s "$serial" shell settings put global transition_animation_scale 0
$ADB -s "$serial" shell settings put global animator_duration_scale 0
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
