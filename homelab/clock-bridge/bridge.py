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
LANGUAGE = os.getenv("LANGUAGE", "es")

PIPER_BIN = os.getenv("PIPER_BIN", "piper")
PIPER_MODEL = os.getenv("PIPER_MODEL", "")  # p.ej. /models/es_ES-davefx-medium.onnx

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
            _whisper = WhisperModel(WHISPER_MODEL, device=WHISPER_DEVICE, compute_type=WHISPER_COMPUTE)
        return _whisper


def transcribe(wav_bytes: bytes) -> str:
    segments, _ = whisper().transcribe(
        io.BytesIO(wav_bytes),
        language=LANGUAGE,
        beam_size=1,
        vad_filter=True,
        # Pista de vocabulario típico de un despertador.
        initial_prompt="Pon una alarma a las siete y media. Temporizador de cinco minutos. ¿Qué tiempo hace?",
    )
    return " ".join(s.text.strip() for s in segments).strip()

# --------------------------------------------------------------------------- TTS

def synthesize(text: str) -> str | None:
    """WAV en base64 con Piper, o None para que el reloj use su TTS local."""
    if not PIPER_MODEL or not text or not shutil.which(PIPER_BIN):
        return None
    try:
        raw = subprocess.run(
            [PIPER_BIN, "--model", PIPER_MODEL, "--output-raw"],
            input=text.encode("utf-8"),
            capture_output=True,
            timeout=30,
            check=True,
        ).stdout
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
    text = transcribe(audio)
    log.info("STT %.1fs: %s", time.time() - t0, text)
    return {"text": text}


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
