"""Decide si una petición de voz necesita al agente (Hermes, con herramientas) o basta Qwen directo.

Hermes manda a Qwen un prompt de ~16.000 tokens con todas sus herramientas y deja que el modelo
razone en cada vuelta: 4-9 s hasta para "¿capital de Francia?". Lo cotidiano (charla, cultura,
cuentas, consejos) se contesta directo en menos de un segundo; Hermes queda para lo que toca el
mundo exterior: correo, agenda, búsquedas, mensajes, domótica, servicios de la casa.
"""

from __future__ import annotations

import re

# Temas que exigen herramientas. Mejor pasarse de agente (más lento) que inventar una respuesta.
_AGENT = re.compile(
    r"\b("
    r"correo|correos|e-?mails?|gmail|calendario|agenda|reuni[oó]n|reuniones|cita|citas|"
    r"recu[eé]rda(?:me)?|recordatorios?|av[ií]same|"
    r"busca(?:me)?|b[uú]scame|investiga|googlea|noticias|titulares|"
    r"telegram|whatsapp|mensajes?|manda(?:le)?|env[ií]a(?:le)?|"
    r"nextcloud|jellyfin|immich|n8n|home ?assistant|"
    r"enciende|apaga|luces?|persianas?|termostato|calefacci[oó]n|aire acondicionado|"
    r"descarga|archivos?|ficheros?|notas?|tareas?|lista de la compra|"
    r"hermes|agente"
    r")\b",
    re.IGNORECASE,
)


def needs_agent(text: str) -> bool:
    """True si la petición debe ir a Hermes."""
    return bool(_AGENT.search(text or ""))


# Cálculo y datos exactos: un modelo de 4B sin razonamiento falla ("12 por 15 = dieciséis"), así que
# estas preguntas dejan que Qwen piense un poco. Más lentas (5-9 s), pero correctas.
_THINK = re.compile(
    r"\d|"
    r"\b(cu[aá]nto|cu[aá]ntos|cu[aá]ntas)\b.*\b(es|son|suman|resulta|queda|quedan|mide|pesa|hay|falta|faltan|tiene|tienen)\b|"
    r"\b(por|m[aá]s|menos|entre|dividido|dividid[oa]s?|multiplic\w+|sum(?:a|o|as|ar|arle|ado|amos)|resta(?:r|rle|do)?|"
    r"ra[ií]z|potencia|porcentaje|por ciento|mitad|doble|triple|cuadrado|cubo|promedio|media|"
    r"convierte|conversi[oó]n|calcula|calcular|bisiesto|d[ií]as hasta|a[nñ]os desde)\b",
    re.IGNORECASE,
)


def needs_thinking(text: str) -> bool:
    """True si conviene dejar que Qwen razone antes de contestar (cuentas, conversiones, fechas)."""
    return bool(_THINK.search(text or ""))
