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

Homelab bridge: CI only does `python -m py_compile homelab/clock-bridge/bridge.py`. Run with `docker compose up -d --build` (copy `.env.example` → `.env`) or `python bridge.py`; health at `:8765/health`.

CI (`.github/workflows/build-apk.yml`) runs exactly: Android unit tests + assemble, then py_compile of `bridge.py`, `clock.py` and `homelab/friday-panel/app.py`, then the skill's pytest and the panel's pytest.

## Architecture

Four independent pieces that talk over HTTP:

1. **`app-android/`** — the Friday app on the clock (launcher, alarms, assistant, HTTP server).
2. **`homelab/clock-bridge/bridge.py`** — FastAPI: `/v1/stt` (faster-whisper), `/v1/assist` (forwards to Hermes `/v1/responses`, optional Piper TTS audio back). Auth via `BRIDGE_TOKEN`.
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

### Constraints to respect

- **Zero runtime dependencies** in the app (no AppCompat/Compose/OkHttp/Play Services) — framework + Kotlin stdlib only, for 1 GB RAM. UI is built programmatically (`ui/Ui.kt`), not via layouts. JUnit and `org.json` are test-only (the unit-test `android.jar` stubs `org.json`, hence the real artifact in `testImplementation`).
- `minSdk 26`, `compileSdk/targetSdk 34`; the app targets only the Android 10 clock. Release is signed with the debug key on purpose.
- Secrets/URLs (clock token, `CLOCK_URL`, bridge token) come from env, `~/.config/mi-smart-clock.json`, or ADB config (`ConfigReceiver`) — never hardcode.
- `device-kit` scripts default to `DRY_RUN=1`; destructive steps need `DRY_RUN=0` and typed confirmation. Never `pm uninstall` — Google packages are only `pm disable-user`'d (reversible). Rooting uses the clock's own patched `boot` (decision D1), not the vendor boot with the port-1337 root shell.
