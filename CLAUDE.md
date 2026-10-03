# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

"Friday": firmware-side app + homelab services that turn a Xiaomi Mi Smart Clock (X04G, MT8167, 1 GB RAM, Android 10, no Google) into an alarm clock and voice assistant. The brain is Hermes Agent + Qwen (Ollama) on a homelab; clock and homelab talk over Tailscale. Docs, comments and UI strings are in **Spanish** — keep that convention. `README.md` is the full install guide (Parts A–C); `PLAN.md`, `docs/decisions.md` and `docs/research-notes.md` hold the rationale.

## Commands

Android app (JDK 17 + Android SDK; module `:app-android`, package `com.jarvis.clock`):
```bash
./gradlew testDebugUnitTest                                  # JVM unit tests
./gradlew testDebugUnitTest --tests 'com.jarvis.clock.AlarmTest'   # single test class
./gradlew assembleDebug assembleRelease                      # APKs in app-android/build/outputs/apk/
./gradlew lintDebug                                          # informative only in CI
```

Hermes skill (Python; tests run against `mock_clock_server.py`, no device needed):
```bash
cd hermes-skill && pip install pytest httpx && pytest -q
pytest -q tests/test_clock_tools.py -k <name>               # single test
```

Homelab panel (`homelab/friday-panel`, FastAPI + web móvil):
```bash
cd homelab/friday-panel && pip install -r requirements.txt pytest && pytest -q   # usa el mock de hermes-skill
```

Homelab bridge: CI only does `python -m py_compile homelab/clock-bridge/bridge.py`. Run with `docker compose up -d --build` (create `.env`, see README A5) or `python bridge.py`; health at `:8765/health`. Useful `.env` keys beyond the README: `TTS_ENGINE=kokoro` (+ `KOKORO_VOICE=ef_dora`, models in `./models`), `PIPER_SPEAKER` (sharvard: 0=male, 1=female), `WHISPER_THREADS`, `WAKE_TRY_EN=1` (doubles the cost of every wake check).

CI (`.github/workflows/build-apk.yml`) runs exactly: Android unit tests + assemble, then py_compile of `bridge.py`, `clock.py` and `homelab/friday-panel/app.py`, then the skill's pytest and the panel's pytest.

## Architecture

Four independent pieces that talk over HTTP:

1. **`app-android/`** — the Friday app on the clock (launcher, alarms, assistant, HTTP server).
2. **`homelab/clock-bridge/bridge.py`** — FastAPI: `/v1/stt` (faster-whisper), `/v1/wake` (wake-word gate, see below), `/v1/assist` (forwards to Hermes `/v1/responses`, plus TTS audio back: Kokoro if `TTS_ENGINE=kokoro`, else/fallback Piper). Auth via `BRIDGE_TOKEN`. Whisper decodes the clock's WAV itself with numpy (`_decode_wav`) — never let faster-whisper decode through PyAV, whose newer versions break it. `wake.py` is pure Python (tests: `cd homelab/clock-bridge && pytest -q test_wake.py`).
3. **`hermes-skill/`** — lets Hermes drive the clock. `mi-smart-clock/` is the agentskills.io skill (stdlib-only `scripts/clock.py` CLI); `clock_tools.py` exposes the same actions as importable Python functions returning `{"ok": bool, ...}` (never raises).
4. **`device-kit/`** — numbered bash scripts (`00_preflight` … `07_smoke_test`, `99_rollback`) to root/debloat the clock via mtkclient/adb. Shared helpers in `lib/common.sh`.

### Android app — the key idea: one action layer, three entry points

`assistant/Actions.kt` is the single place that executes clock actions (set/cancel alarm, timer, speak, display, volume, Bluetooth…). It is reused by:
- **Local intents** (`assistant/LocalIntents.kt`): Spanish, LLM-free parsing of alarm/timer/time/weather/volume commands. Runs first so alarms work with no network.
- **Backend replies** (`assistant/Backend.kt`): anything not matched locally goes to the bridge → Hermes, which returns text plus a list of actions fed to `Actions.executeAll`.
- **HTTP API** (`server/HttpServer.kt` + `server/Api.kt`, port 8080, token auth, 503 if no token set): what Hermes/`clock.py` call. `POST /actions` accepts a batch.

When adding a capability, add it to `Actions`, then wire the HTTP route in `Api.kt`, the skill (`clock.py`, `clock_tools.py`, `SKILL.md`, mock server + tests), and the README API table so the entry points stay consistent.

`assistant/Assistant.kt` orchestrates listen (VAD via `VoiceRecorder`, or system SpeechRecognizer fallback) → understand (local, then backend) → act → speak (`Tts`, bridge/Piper audio or system TTS), re-listening if Friday asks a question. `ClockService` is the foreground service hosting the HTTP server; `Events` is the shared state/event bus.

Alarms (`alarm/`) use `AlarmManager.setAlarmClock()` and must never depend on network or the homelab; `BootReceiver` reschedules after reboot. Ring service falls back to beeps if the GSI has no ringtones.

### Wake word ("Friday" without pressing the mic)

There is no on-device speech model (1 GB RAM). `WakeWord` (started by `ClockService`) loops `VoiceRecorder.recordUtterance`, which only cuts speech segments with the VAD; each segment goes to the bridge `/v1/wake`, where Whisper transcribes just the first ~2 s and `wake.py` fuzzy-matches "Friday" (Whisper spells it Fraidei/Fry day…). Only if it matches is the whole segment transcribed to get the command. The loop pauses (`WakeWord.blocker()`) while the assistant is busy, an alarm rings, music plays or the mic is muted; `MicGate` stops it grabbing the mic while a command is being recorded. The bridge deliberately does not log what is said when there is no match (privacy: it hears the whole room).

### Quirks of the real clock (learned the hard way)

- **Mic is very quiet** (speech peaks ~200-500/32768, noise rms 3-6): `VoiceRecorder` normalizes (up to x60) and uses a low relative threshold. The digital mic emits a loud start-up pop in the first ~0.3 s of every `AudioRecord` — skipped, never used for calibration. Impacts/button clicks arrive as loud low-frequency thumps (>70 % of energy <200 Hz): rejected by `highFrequencyRatio` (speech ≈0.15, thumps <0.005). A thump sent to Whisper comes back as hallucinations ("¡Suscríbete!").
- **Speaker is quiet and the HAL resets mixer tweaks**: `tinymix Lineout_PGA_GAIN` goes back to +2 dB as soon as audio plays, so loudness is done in software (alarm: stream at max + `LoudnessEnhancer`; voice: `_louder` in the bridge).
- **Hardware buttons**: Xiaomi sets `ro.mi.handle_volume_key_in_oem_app=true`, so the framework hands volume keys to the (disabled) Google assistant app and apps never see them. The patched `boot` flips it to `false` in `overlay.d/init.adb.rc` (`on post-fs-data`); Friday's `MainActivity.onKeyDown` then handles vol ± (5 % steps) and `KEYCODE_MUTE` (mic mute). Magisk's `/data/adb/*.d` scripts do **not** run (the stub app never populated `/data/adb/magisk`), so persistent tweaks go in the boot overlay.
- **Magisk 30.7 `boot_patch.sh` breaks this clock's fstab**: it truncates the `system` line (loses `logical,first_stage_mount`) → resets into fastboot. The `fstab.mt8167` in the ramdisk is edited by hand first (no `avb`, no `fileencryption`, as Ayra's boot) and Magisk is run with `KEEPVERITY=true KEEPFORCEENCRYPT=true`. Use the armeabi-v7a Magisk binaries (32-bit kernel) and the x86_64 `magiskboot` on the host. Flashing `boot` can be done from Android with `dd` onto `/dev/block/platform/bootdevice/by-name/boot` (verify by re-reading); BROM is only needed when it does not boot.
- **No keyboard, no usable Settings pages**: stock has no IME (HeliBoard is installed in `device-kit/vendor`), and Android Settings crashes on "Connected devices" (no `UsbManager`). Open Wi-Fi with `Settings.ACTION_WIFI_SETTINGS`; Bluetooth pairing must start from `SubSettings -e :settings:show_fragment com.android.settings.bluetooth.BluetoothPairingDetail`, otherwise Android only posts a notification the clock can't show and the pairing times out after 30 s.
- **Tailscale** needs "always-on VPN" (`settings put secure always_on_vpn_app com.tailscale.ipn` + `appops set com.tailscale.ipn ACTIVATE_VPN allow`) to come up after a reboot, and logs in with an auth key (the clock has no browser). `Prefs.bindTailscaleOnly` makes the HTTP API unreachable from the LAN.
- `adb shell` is root without authentication in this build (`ro.secure 0`), SELinux permissive — fine for a bedside clock, not for anything exposed.
- Release builds run R8: `Log.d` is stripped, use `Log.i` for diagnostics. `uiautomator dump` + `input tap` drive stock Settings from adb when needed.

### Constraints to respect

- **Zero runtime dependencies** in the app (no AppCompat/Compose/OkHttp/Play Services) — framework + Kotlin stdlib only, for 1 GB RAM. UI is built programmatically (`ui/Ui.kt`), not via layouts. JUnit and `org.json` are test-only (the unit-test `android.jar` stubs `org.json`, hence the real artifact in `testImplementation`).
- `minSdk 26`, `compileSdk/targetSdk 34`; the app targets only the Android 10 clock. Release is signed with the debug key on purpose.
- Secrets/URLs (clock token, `CLOCK_URL`, bridge token) come from env, `~/.config/mi-smart-clock.json`, or ADB config (`ConfigReceiver`) — never hardcode.
- `device-kit` scripts default to `DRY_RUN=1`; destructive steps need `DRY_RUN=0` and typed confirmation. Never `pm uninstall` — Google packages are only `pm disable-user`'d (reversible). Rooting uses the clock's own patched `boot` (decision D1), not the vendor boot with the port-1337 root shell.
