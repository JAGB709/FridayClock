"""
Puente entre el Mi Smart Clock (app Jarvis Clock) y Hermes Agent.

    reloj --(wav)--> /v1/stt ------> faster-whisper (local)  --> texto
    reloj --(txt)--> /v1/assist ---> Hermes /v1/responses    --> respuesta
                                 \\-> Piper (local, opcional) --> audio

Todo corre en el homelab: ni Google ni nubes de voz. Hermes sigue siendo
el cerebro; con la skill `mi-smart-clock` además puede controlar el reloj
(alarmas, pantalla, voz) desde cualquier canal.
"""

from __future__ import annotations

import base64
import io
import json
import logging
import os
import shutil
import subprocess
import threading
import time
import wave

import httpx
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

from wake import split_wake

log = logging.getLogger("clock-bridge")
logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"))

# --------------------------------------------------------------------------- config

BRIDGE_TOKEN = os.getenv("BRIDGE_TOKEN", "")
HERMES_URL = os.getenv("HERMES_URL", "http://127.0.0.1:8642").rstrip("/")
HERMES_KEY = os.getenv("HERMES_API_KEY", "")  # = API_SERVER_KEY de ~/.hermes/.env
HERMES_MODEL = os.getenv("HERMES_MODEL", "hermes-agent")
HERMES_TIMEOUT = float(os.getenv("HERMES_TIMEOUT", "80"))
# Minutos sin hablar tras los que el reloj empieza conversación nueva en Hermes.
SESSION_IDLE_MIN = float(os.getenv("SESSION_IDLE_MIN", "15"))

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "small")
WHISPER_DEVICE = os.getenv("WHISPER_DEVICE", "cpu")
WHISPER_COMPUTE = os.getenv("WHISPER_COMPUTE", "int8")
WHISPER_THREADS = int(os.getenv("WHISPER_THREADS", "6"))
LANGUAGE = os.getenv("LANGUAGE", "es")

PIPER_BIN = os.getenv("PIPER_BIN", "piper")
PIPER_MODEL = os.getenv("PIPER_MODEL", "")  # p.ej. /models/es_ES-davefx-medium.onnx
# Motor de voz: "kokoro" (más natural, 82M, CPU) o "piper". Si Kokoro falla, se usa Piper.
TTS_ENGINE = os.getenv("TTS_ENGINE", "piper").lower()
KOKORO_MODEL = os.getenv("KOKORO_MODEL", "/models/kokoro-v1.0.onnx")
KOKORO_VOICES = os.getenv("KOKORO_VOICES", "/models/voices-v1.0.bin")
KOKORO_VOICE = os.getenv("KOKORO_VOICE", "ef_dora")  # español, mujer
KOKORO_LANG = os.getenv("KOKORO_LANG", "es")
KOKORO_SPEED = float(os.getenv("KOKORO_SPEED", "1.0"))
VOICE_TARGET_RMS = float(os.getenv("VOICE_TARGET_RMS", "0.18"))  # RMS objetivo (0..1 de fondo de escala)
VOICE_MAX_GAIN = float(os.getenv("VOICE_MAX_GAIN", "6"))
PIPER_SPEAKER = os.getenv("PIPER_SPEAKER", "")  # id de hablante en voces multi-hablante (sharvard: 1 = mujer)

VOICE_INSTRUCTIONS = """\
Estás respondiendo por el altavoz de un reloj despertador inteligente (Mi Smart Clock)
en la mesilla de noche de tu usuario. Lo que escribas se leerá en voz alta.

Reglas de voz:
- Responde en español, breve y natural: 1-3 frases salvo que te pidan detalle.
- Nada de markdown, listas, emojis, URLs ni tablas. Escribe números como se dicen.
- Si necesitas un dato para actuar, haz UNA pregunta corta y termina con "?".

El reloj ya resuelve por sí mismo alarmas simples, temporizadores, la hora y el tiempo
de hoy. Si aun así te piden algo del reloj (alarmas complejas, mostrar algo en su
pantalla, recordatorios), usa la skill `mi-smart-clock`.

Estado actual del reloj (JSON):
{context}
"""

BRIEFING_PROMPT = """\
Acabo de apagar la alarma. Dame los buenos días en voz alta en 4-6 frases:
fecha y hora, el tiempo de hoy (está en el contexto), mi agenda o recordatorios de
hoy si tienes acceso a ellos, y un titular importante si puedes consultarlo. Sin preguntas."""

app = FastAPI(title="clock-bridge")

# --------------------------------------------------------------------------- auth

def check_token(authorization: str | None = Header(default=None)) -> None:
    if not BRIDGE_TOKEN:
        return
    if authorization != f"Bearer {BRIDGE_TOKEN}":
        raise HTTPException(status_code=401, detail="token inválido")

# --------------------------------------------------------------------------- STT

_whisper = None
_whisper_lock = threading.Lock()


def whisper():
    global _whisper
    with _whisper_lock:
        if _whisper is None:
            from faster_whisper import WhisperModel

            log.info("Cargando Whisper %s (%s/%s)…", WHISPER_MODEL, WHISPER_DEVICE, WHISPER_COMPUTE)
            _whisper = WhisperModel(
                WHISPER_MODEL, device=WHISPER_DEVICE, compute_type=WHISPER_COMPUTE, cpu_threads=WHISPER_THREADS
            )
        return _whisper


# Pistas de vocabulario para Whisper: despertador y, en la escucha continua, el nombre de Friday.
STT_PROMPT = "Pon una alarma a las siete y media. Temporizador de cinco minutos. ¿Qué tiempo hace?"
WAKE_HEAD_SAMPLES = 16000 * 2  # se analiza el arranque (2 s): ahí va el nombre
# Segundo intento en inglés ("Friday" es inglés): duplica el coste por fragmento, así que es opcional.
WAKE_TRY_EN = os.getenv("WAKE_TRY_EN", "0") == "1"
WAKE_EN_MAX_SAMPLES = 16000 * 3
WAKE_PROMPT = "Friday. Friday, ¿qué hora es? Friday, pon una alarma. Friday, apaga la luz."


def _decode_wav(wav_bytes: bytes):
    """float32 mono 16 kHz desde un WAV de 16 bits (remuestrea si hace falta).

    Evita que faster-whisper decodifique con PyAV, que rompe con cada versión nueva.
    """
    import numpy as np

    with wave.open(io.BytesIO(wav_bytes)) as w:
        if w.getsampwidth() != 2:
            return None
        ch, rate = w.getnchannels(), w.getframerate()
        x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
    if ch > 1:
        x = x[: len(x) - len(x) % ch].reshape(-1, ch).mean(axis=1)
    if rate != 16000 and len(x):
        n = int(len(x) * 16000 / rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x


def transcribe(wav_bytes: bytes, prompt: str = STT_PROMPT, language: str = LANGUAGE) -> str:
    audio = _decode_wav(wav_bytes)
    return transcribe_audio(audio if audio is not None else io.BytesIO(wav_bytes), prompt, language)


def transcribe_audio(audio, prompt: str = STT_PROMPT, language: str = LANGUAGE) -> str:
    segments, _ = whisper().transcribe(
        audio,
        language=language,
        beam_size=1,
        vad_filter=True,
        initial_prompt=prompt,
    )
    return " ".join(s.text.strip() for s in segments).strip()

# --------------------------------------------------------------------------- TTS

def _louder(raw: bytes) -> bytes:
    """Sube el nivel de la voz de Piper: el altavoz del reloj es flojo.

    Ganancia hacia un RMS objetivo (máx. x6) con limitador suave (tanh) para no saturar.
    """
    import numpy as np

    x = np.frombuffer(raw, dtype="<i2").astype(np.float32)
    if x.size == 0:
        return raw
    rms = float(np.sqrt(np.mean(x * x)))
    if rms < 1.0:
        return raw
    gain = min(VOICE_MAX_GAIN, VOICE_TARGET_RMS * 32768.0 / rms)
    if gain <= 1.05:
        return raw
    return (np.tanh(x * gain / 32768.0) * 32767.0).astype("<i2").tobytes()


_kokoro = None
_kokoro_lock = threading.Lock()


def _wav_b64(pcm: bytes, rate: int) -> str:
    buf = io.BytesIO()
    with wave.open(buf, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm)
    return base64.b64encode(buf.getvalue()).decode("ascii")


def synthesize_kokoro(text: str) -> str | None:
    global _kokoro
    import numpy as np

    with _kokoro_lock:
        if _kokoro is None:
            from kokoro_onnx import Kokoro

            log.info("Cargando Kokoro (%s, voz %s)…", KOKORO_MODEL, KOKORO_VOICE)
            _kokoro = Kokoro(KOKORO_MODEL, KOKORO_VOICES)
        samples, rate = _kokoro.create(text, voice=KOKORO_VOICE, speed=KOKORO_SPEED, lang=KOKORO_LANG)
    pcm = (np.clip(samples, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()
    return _wav_b64(_louder(pcm), int(rate))


def synthesize(text: str) -> str | None:
    """WAV en base64 (Kokoro o Piper), o None para que el reloj use su TTS local."""
    if TTS_ENGINE == "kokoro" and text:
        try:
            return synthesize_kokoro(text)
        except Exception:  # noqa: BLE001 - Piper es el plan B
            log.exception("Kokoro falló; pruebo con Piper")
    return synthesize_piper(text)


def synthesize_piper(text: str) -> str | None:
    """WAV en base64 con Piper, o None para que el reloj use su TTS local."""
    if not PIPER_MODEL or not text or not shutil.which(PIPER_BIN):
        return None
    try:
        raw = subprocess.run(
            [PIPER_BIN, "--model", PIPER_MODEL, "--output-raw"]
            + (["--speaker", PIPER_SPEAKER] if PIPER_SPEAKER else []),
            input=text.encode("utf-8"),
            capture_output=True,
            timeout=30,
            check=True,
        ).stdout
        raw = _louder(raw)
        cfg = json.load(open(PIPER_MODEL + ".json", encoding="utf-8"))
        rate = cfg.get("audio", {}).get("sample_rate", 22050)
        buf = io.BytesIO()
        with wave.open(buf, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(raw)
        return base64.b64encode(buf.getvalue()).decode("ascii")
    except Exception:  # noqa: BLE001 - la voz local del reloj es el plan B
        log.exception("Piper falló; el reloj usará su TTS")
        return None

# --------------------------------------------------------------------------- Hermes

_sessions: dict[str, tuple[str, float]] = {}


def conversation_for(device: str) -> str:
    """Conversación con nombre en Hermes; nueva si el reloj lleva un rato callado."""
    now = time.time()
    name, last = _sessions.get(device, ("", 0.0))
    if not name or now - last > SESSION_IDLE_MIN * 60:
        name = f"{device}-{time.strftime('%Y%m%d-%H%M%S')}"
    _sessions[device] = (name, now)
    return name


def ask_hermes(text: str, context: dict, device: str) -> str:
    headers = {"Content-Type": "application/json"}
    if HERMES_KEY:
        headers["Authorization"] = f"Bearer {HERMES_KEY}"
    body = {
        "model": HERMES_MODEL,
        "input": text,
        "instructions": VOICE_INSTRUCTIONS.format(context=json.dumps(context, ensure_ascii=False)),
        "conversation": conversation_for(device),
        "store": True,
    }
    r = httpx.post(f"{HERMES_URL}/v1/responses", json=body, headers=headers, timeout=HERMES_TIMEOUT)
    r.raise_for_status()
    return extract_text(r.json())


def extract_text(resp: dict) -> str:
    """Texto final del asistente: ignora tool calls y comentarios intermedios."""
    parts: list[str] = []
    for item in resp.get("output", []):
        if item.get("type") != "message" or item.get("phase") == "commentary":
            continue
        for c in item.get("content", []):
            if c.get("type") == "output_text":
                parts.append(c.get("text", ""))
    return "\n".join(p for p in parts if p).strip()

# --------------------------------------------------------------------------- API

class AssistRequest(BaseModel):
    text: str | None = None
    audio_b64: str | None = None
    mode: str = "chat"
    device: str = "mi-smart-clock"
    context: dict = {}


@app.get("/health")
def health() -> dict:
    return {"ok": True, "hermes": HERMES_URL, "whisper": WHISPER_MODEL, "piper": bool(PIPER_MODEL)}


@app.post("/v1/stt", dependencies=[Depends(check_token)])
async def stt(request: Request) -> dict:
    audio = await request.body()
    if not audio:
        raise HTTPException(400, "cuerpo vacío")
    t0 = time.time()
    text = await run_in_threadpool(transcribe, audio)
    log.info("STT %.1fs: %s", time.time() - t0, text)
    return {"text": text}


def _check_wake(wav: bytes) -> tuple[bool, str, str]:
    """(¿llama a Friday?, orden, texto). Dos etapas para no gastar CPU en conversaciones ajenas:

    1. Se transcribe solo el arranque del fragmento (donde va el nombre), en español y, si es corto,
       también en inglés ("Friday" es inglés y Whisper en español la deforma).
    2. Solo si ese arranque llama a Friday se transcribe el fragmento entero para sacar la orden.
    """
    audio = _decode_wav(wav)
    if audio is None:
        text = transcribe(wav, WAKE_PROMPT)
        called, command = split_wake(text)
        return called, command, text
    head = audio[:WAKE_HEAD_SAMPLES]
    text = transcribe_audio(head, WAKE_PROMPT)
    called, command = split_wake(text)
    if not called and WAKE_TRY_EN and len(head) <= WAKE_EN_MAX_SAMPLES:
        text_en = transcribe_audio(head, WAKE_PROMPT, "en")
        called, command = split_wake(text_en)
        text = text_en if called else text
    if called and len(audio) > len(head):
        full = transcribe_audio(audio, WAKE_PROMPT)
        ok, full_command = split_wake(full)
        if ok:
            text, command = full, full_command
    return called, command, text


@app.post("/v1/wake", dependencies=[Depends(check_token)])
async def wake(request: Request) -> dict:
    """Escucha continua: el reloj manda cada fragmento de voz; aquí se mira si llama a Friday."""
    audio = await request.body()
    if not audio:
        raise HTTPException(400, "cuerpo vacío")
    t0 = time.time()
    called, command, text = await run_in_threadpool(_check_wake, audio)
    if called:
        log.info("WAKE SI %.1fs: %r -> %r", time.time() - t0, text, command)
    else:
        # No se registra lo que se dice en la habitación: solo que hubo voz y cuánto costó.
        log.info("WAKE no %.1fs (%d bytes de audio)", time.time() - t0, len(audio))
        text = ""
    return {"wake": called, "text": text if called else "", "command": command}


@app.post("/v1/assist", dependencies=[Depends(check_token)])
def assist(req: AssistRequest) -> dict:
    transcript = (req.text or "").strip()
    if not transcript and req.audio_b64:
        transcript = transcribe(base64.b64decode(req.audio_b64))
    if req.mode == "briefing":
        transcript = BRIEFING_PROMPT
    if not transcript:
        return {"transcript": "", "reply": ""}

    t0 = time.time()
    try:
        reply = ask_hermes(transcript, req.context, req.device)
    except httpx.HTTPError:
        log.exception("Hermes no responde")
        reply = "Hermes no me responde ahora mismo."
    log.info("Hermes %.1fs: %s -> %s", time.time() - t0, transcript, reply)

    out = {
        "transcript": "" if req.mode == "briefing" else transcript,
        "reply": reply,
        "actions": [],
        "expect_reply": reply.rstrip().endswith("?"),
    }
    audio = synthesize(reply)
    if audio:
        out["audio_b64"] = audio
    return out


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=os.getenv("HOST", "0.0.0.0"), port=int(os.getenv("PORT", "8765")))
