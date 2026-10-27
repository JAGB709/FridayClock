"""Recordatorios del puente: modelo, validación, almacén en JSON atómico y planificación.

Módulo puro (sin red ni FastAPI). El envío real lo inyecta quien llama (`fire_due`).
El texto de un recordatorio es dato no fiable: aquí solo se limpia y se acota; nunca se
interpreta ni se manda a un modelo.
"""
from __future__ import annotations

import json
import logging
import os
import secrets
import threading
import unicodedata
from datetime import datetime, timedelta, tzinfo
from pathlib import Path

log = logging.getLogger("clock-bridge")

MAX_TEXT = 300
MAX_REMINDERS = 200
MAX_TRIES = 3
REPEATS = ("none", "daily", "weekdays", "weekly")
CHANNELS = ("clock", "telegram", "both")


def clean_text(text: str) -> str:
    """Quita caracteres de control y espacios sobrantes. Vacío o demasiado largo es un error."""
    text = "".join(" " if ch in "\t\n\r" else ch for ch in str(text) if ch in "\t\n\r" or unicodedata.category(ch)[0] != "C")
    text = " ".join(text.split())
    if not text:
        raise ValueError("el texto está vacío")
    if len(text) > MAX_TEXT:
        raise ValueError(f"el texto supera {MAX_TEXT} caracteres")
    return text


def parse_at(value: str, tz: tzinfo) -> datetime:
    """'AAAA-MM-DDTHH:MM' (hora local de tz) o ISO 8601 con desfase."""
    try:
        d = datetime.fromisoformat(str(value).strip())
    except ValueError:
        raise ValueError("la fecha no es válida (usa AAAA-MM-DDTHH:MM)") from None
    return d.replace(tzinfo=tz) if d.tzinfo is None else d


def next_after(at: datetime, repeat: str, now: datetime) -> datetime | None:
    """Primera ocurrencia posterior a `now`, conservando la hora local. None si no se repite."""
    if repeat == "none":
        return None
    tz = at.tzinfo
    base = at
    for i in range(1, 400):
        day = base.date() + timedelta(days=i)
        cand = datetime.combine(day, base.timetz().replace(tzinfo=None), tzinfo=tz)
        if cand <= now:
            continue
        if repeat == "weekdays" and cand.weekday() >= 5:
            continue
        if repeat == "weekly" and cand.weekday() != base.weekday():
            continue
        return cand
    return None


class ReminderStore:
    def __init__(self, path, tz: tzinfo):
        self.path = Path(path)
        self.tz = tz
        self._lock = threading.RLock()
        self._tries: dict[str, int] = {}
        self._items: list[dict] = self._load()

    # ---- disco
    def _load(self) -> list[dict]:
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            items = data["reminders"]
            if not isinstance(items, list):
                raise ValueError
            return [i for i in items if isinstance(i, dict) and {"id", "text", "at", "repeat", "channel"} <= i.keys()]
        except FileNotFoundError:
            return []
        except (ValueError, KeyError, TypeError, OSError):
            log.warning("recordatorios: archivo ilegible, se aparta y se empieza vacío")
            try:
                os.replace(self.path, self.path.with_name(self.path.name + ".corrupto"))
            except OSError:
                pass
            return []

    def _save(self) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        tmp = self.path.with_name(f".{self.path.name}.{secrets.token_hex(4)}.tmp")
        try:
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump({"version": 1, "reminders": self._items}, f, ensure_ascii=False)
                f.flush()
                os.fsync(f.fileno())
            os.replace(tmp, self.path)
        finally:
            if tmp.exists():
                tmp.unlink()

    # ---- operaciones
    def list(self) -> list[dict]:
        with self._lock:
            return sorted((dict(i) for i in self._items), key=lambda i: i["at"])

    def add(self, text: str, at: datetime, repeat: str, channel: str, now: datetime) -> dict:
        text = clean_text(text)
        if repeat not in REPEATS:
            raise ValueError("repetición no válida (none, daily, weekdays, weekly)")
        if channel not in CHANNELS:
            raise ValueError("canal no válido (clock, telegram, both)")
        at = at.astimezone(self.tz)
        if at <= now:
            if repeat == "none":
                raise ValueError("la fecha ya ha pasado")
            at = next_after(at, repeat, now) or at
        with self._lock:
            if len(self._items) >= MAX_REMINDERS:
                raise ValueError(f"máximo de {MAX_REMINDERS} recordatorios")
            item = {"id": secrets.token_hex(6), "text": text, "at": at.isoformat(timespec="minutes"),
                    "repeat": repeat, "channel": channel, "created": now.isoformat(timespec="seconds")}
            self._items.append(item)
            self._save()
            return dict(item)

    def delete(self, rid: str) -> bool:
        with self._lock:
            keep = [i for i in self._items if i["id"] != rid]
            if len(keep) == len(self._items):
                return False
            self._items = keep
            self._tries.pop(rid, None)
            self._save()
            return True

    def due(self, now: datetime) -> list[dict]:
        with self._lock:
            out = []
            for i in self._items:
                try:
                    if parse_at(i["at"], self.tz) <= now:
                        out.append(dict(i))
                except ValueError:
                    continue
            return out

    def settle(self, rid: str, now: datetime, sent: bool) -> None:
        """Tras un envío: borra el de una vez, avanza el repetido; un fallo se reintenta MAX_TRIES veces."""
        with self._lock:
            item = next((i for i in self._items if i["id"] == rid), None)
            if item is None:
                return
            if not sent:
                self._tries[rid] = self._tries.get(rid, 0) + 1
                if self._tries[rid] < MAX_TRIES:
                    return
            self._tries.pop(rid, None)
            nxt = next_after(parse_at(item["at"], self.tz), item["repeat"], now)
            if nxt is None:
                self._items = [i for i in self._items if i["id"] != rid]
            else:
                item["at"] = nxt.isoformat(timespec="minutes")
            self._save()


def fire_due(store: ReminderStore, send, now: datetime, grace_min: float = 10) -> int:
    """Envía los vencidos con send(texto, canal) -> bool. Devuelve cuántos se enviaron bien."""
    sent = 0
    for item in store.due(now):
        late = now - parse_at(item["at"], store.tz)
        if item["repeat"] != "none" and late > timedelta(minutes=grace_min):
            log.info("recordatorio %s: vencido hace mucho, salto a la próxima", item["id"])
            store.settle(item["id"], now, True)
            continue
        try:
            ok = bool(send(item["text"], item["channel"]))
        except Exception as e:  # noqa: BLE001 - el mensaje de la excepción puede llevar URLs con secretos
            log.warning("recordatorio %s: fallo al enviar (%s)", item["id"], type(e).__name__)
            ok = False
        log.info("recordatorio %s: canal=%s enviado=%s (%d car.)", item["id"], item["channel"], ok, len(item["text"]))
        store.settle(item["id"], now, ok)
        sent += ok
    return sent
