"""Palabra de activación ("Friday") en la transcripción de Whisper.

El reloj manda al puente cada fragmento de voz que oye; aquí se decide si empieza (o acaba)
llamando a Friday y qué orden viene detrás. Whisper escribe "Friday" dicho con acento español
de formas distintas (Fraidei, Fridei, Fry day…), de ahí la comparación aproximada.
"""

from __future__ import annotations

import re
import unicodedata
from difflib import SequenceMatcher

# Variantes con las que Whisper suele transcribir la palabra dicha en español.
_VARIANTS = ("friday", "fraiday", "fraidei", "fraidey", "fridei", "fridey", "fraide", "fridai", "freiday")
_THRESHOLD = 0.8
_LEADING_WORDS = 3  # solo se busca entre las primeras palabras: "Friday, ¿qué hora es?"
_TRAILING_WORDS = 2  # y entre las últimas: "¿qué hora es, Friday?"

_WORD = re.compile(r"[\wáéíóúüñÁÉÍÓÚÜÑ']+")
_SEPARATORS = " ,.:;¡!¿?-–—…"


def _plain(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.lower())
    return "".join(c for c in s if c.isalnum() and not unicodedata.combining(c))


def _is_wake(candidate: str) -> bool:
    c = _plain(candidate)
    # Todas las variantes empiezan por "fr": evita que "es"+"Friday" cuente como una sola palabra.
    return len(c) >= 4 and c.startswith("fr") and any(SequenceMatcher(None, c, v).ratio() >= _THRESHOLD for v in _VARIANTS)


def split_wake(text: str) -> tuple[bool, str]:
    """(¿llama a Friday?, orden que queda sin la palabra de activación)."""
    words = list(_WORD.finditer(text))
    n = len(words)
    for i in range(min(_LEADING_WORDS, n)):
        for span in (1, 2):  # "Fry day" llega a veces en dos palabras
            if i + span <= n and _is_wake("".join(w.group() for w in words[i : i + span])):
                rest = text[words[i + span - 1].end() :].lstrip(_SEPARATORS)
                return True, (text[: words[i].start()].strip(_SEPARATORS) + " " + rest).strip()
    for i in range(max(0, n - _TRAILING_WORDS), n):
        for span in (1, 2):
            if i + span == n and _is_wake("".join(w.group() for w in words[i:n])):
                return True, text[: words[i].start()].rstrip(_SEPARATORS).strip()
    return False, ""
