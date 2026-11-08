#!/usr/bin/env bash
# SeniorLauncher on-device smoke test (emulator or phone):
#   - it becomes the home app and Home brings it up
#   - charging is shown in words ("Charging"), and low battery too
#   - more tiles than fit make a second page, Next goes there, Home goes back
# Usage: scripts/smoke.sh [serial]
set -u
PKG=app.phueber.seniorlauncher
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/home/philipp/.local/opt/jdk-17.0.20.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"
SERIAL="${1:-$(adb devices | awk '/emulator-[0-9]+\tdevice/{print $1; exit}')}"
[ -n "$SERIAL" ] || { echo "FAIL no device"; exit 2; }
SH() { adb -s "$SERIAL" shell "$@" | tr -d '\r'; }
XML=$(mktemp)
PREV_HOME=$(SH cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
cleanup() {
  SH dumpsys battery reset >/dev/null 2>&1
  [ -n "$PREV_HOME" ] && [ "${PREV_HOME%%/*}" != "$PKG" ] \
    && SH cmd package set-home-activity "$PREV_HOME" >/dev/null 2>&1
  rm -f "$XML"
}
pass() { echo "PASS $*"; }
fail() { echo "FAIL $*"; cleanup; exit 1; }

dump_ui() { # retry: uiautomator dump flakes with "could not get idle state"
  for _ in 1 2 3 4 5 6; do
    SH uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 \
      && adb -s "$SERIAL" pull /sdcard/ui.xml "$XML" >/dev/null 2>&1 \
      && grep -q '<hierarchy' "$XML" && return 0
    sleep 1
  done
  return 1
}
# has_text REGEX: some node's text or content-desc matches (case-insensitive)
has_text() { dump_ui && grep -qiE "(text|content-desc)=\"[^\"]*$1" "$XML"; }
wait_text() { for _ in 1 2 3 4 5 6 7 8; do has_text "$1" && return 0; sleep 1; done; return 1; }
tap_text() { # tap the first node whose text or content-desc starts with $1
  dump_ui || return 1
  local xy
  xy=$(python3 -I - "$XML" "$1" <<'PY'
import sys, re, xml.etree.ElementTree as ET
f, val = sys.argv[1], sys.argv[2].lower()
for n in ET.parse(f).getroot().iter('node'):
    for k in ('text', 'content-desc'):
        if n.get(k, '').lower().startswith(val):
            a, b, c, d = map(int, re.findall(r'\d+', n.get('bounds')))
            print((a + c) // 2, (b + d) // 2); sys.exit(0)
sys.exit(1)
PY
) || return 1
  SH input tap $xy >/dev/null
}
home_resumed() { SH dumpsys activity activities | grep -E 'topResumedActivity' | grep -q "$PKG/.HomeActivity"; }

echo "== $SERIAL (API $(SH getprop ro.build.version.sdk)) =="
(cd "$ROOT" && ./gradlew assembleDebug -q) && pass "assembleDebug" || fail "assembleDebug"
APK=$(ls "$ROOT"/app/build/outputs/apk/debug/SeniorLauncher-*-debug.apk | head -1)
adb -s "$SERIAL" install -r "$APK" 2>&1 | grep -q Success && pass "install" || fail "install"
SH pm clear "$PKG" >/dev/null
SH settings put system time_12_24 24
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  SH settings put global $s 0 >/dev/null; done
SH input keyevent KEYCODE_WAKEUP >/dev/null; SH wm dismiss-keyguard >/dev/null 2>&1

# 1. Home app
SH cmd package set-home-activity "$PKG/.HomeActivity" >/dev/null
SH input keyevent KEYCODE_HOME >/dev/null; sleep 2
home_resumed && pass "Home shows SeniorLauncher" || fail "HomeActivity not resumed after Home"
wait_text 'Apps' && pass "helper bar visible" || fail "no Apps button on home"

# 2. Battery and charging, in words
SH dumpsys battery set level 35; SH dumpsys battery unplug
wait_text 'Battery' && ! has_text 'Charging' && pass "on battery: 'Battery', no 'Charging'" \
  || fail "battery text wrong when unplugged"
SH dumpsys battery set ac 1; SH dumpsys battery set status 2
wait_text 'Charging' && pass "charging shown in words" || fail "'Charging' not shown while plugged in"
SH dumpsys battery set ac 0; SH dumpsys battery set status 3; SH dumpsys battery set level 12
wait_text 'Battery low' && pass "low battery shown in words" || fail "'Battery low' not shown at 12 %"
SH dumpsys battery reset

# 3. Paging: add every app from the Apps screen until a second page exists
tap_text 'Apps' || fail "cannot open Apps"
sleep 2
for _ in 1 2 3 4 5 6 7 8 9 10; do
  tap_text 'Add' || { SH input swipe 540 1600 540 900 300 >/dev/null; sleep 1; tap_text 'Add' || break; }
  sleep 0.5
done
SH input keyevent KEYCODE_HOME >/dev/null; sleep 2
wait_text 'Page 1 of' && pass "more tiles than fit: pages" || fail "no 'Page 1 of N' after adding apps"
tap_text 'Next' || fail "no Next button"
wait_text 'Page 2 of' && pass "Next goes to page 2" || fail "Next did not change the page"
SH input keyevent KEYCODE_HOME >/dev/null
wait_text 'Page 1 of' && pass "Home returns to page 1" || fail "Home did not return to page 1"

cleanup
echo "ALL PASS"
