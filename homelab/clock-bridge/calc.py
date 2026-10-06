"""Calculadora de español hablado: "¿cuánto es doce por quince?" -> "Doce por quince son ciento ochenta."

Un modelo de 4B sin razonamiento falla las cuentas ("12 por 15 = dieciséis") y razonando tarda 20-45 s.
Una operación binaria se resuelve aquí al instante y sin errores; lo que no se entienda devuelve None
y el puente lo pasa a Hermes.
"""

from __future__ import annotations

import re
from decimal import Decimal, InvalidOperation

_UNITS = {
    "cero": 0, "un": 1, "uno": 1, "una": 1, "dos": 2, "tres": 3, "cuatro": 4, "cinco": 5, "seis": 6,
    "siete": 7, "ocho": 8, "nueve": 9, "diez": 10, "once": 11, "doce": 12, "trece": 13, "catorce": 14,
    "quince": 15, "dieciseis": 16, "diecisiete": 17, "dieciocho": 18, "diecinueve": 19, "veinte": 20,
    "veintiun": 21, "veintiuno": 21, "veintiuna": 21, "veintidos": 22, "veintitres": 23,
    "veinticuatro": 24, "veinticinco": 25, "veintiseis": 26, "veintisiete": 27, "veintiocho": 28,
    "veintinueve": 29,
}
_TENS = {"treinta": 30, "cuarenta": 40, "cincuenta": 50, "sesenta": 60, "setenta": 70, "ochenta": 80, "noventa": 90}
_HUNDREDS = {
    "cien": 100, "ciento": 100, "doscientos": 200, "doscientas": 200, "trescientos": 300, "trescientas": 300,
    "cuatrocientos": 400, "cuatrocientas": 400, "quinientos": 500, "quinientas": 500, "seiscientos": 600,
    "seiscientas": 600, "setecientos": 700, "setecientas": 700, "ochocientos": 800, "ochocientas": 800,
    "novecientos": 900, "novecientas": 900,
}
_NUMBER_WORDS = set(_UNITS) | set(_TENS) | set(_HUNDREDS) | {"mil", "millon", "millones", "y"}

_ONES_OUT = [
    "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once",
    "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte",
    "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete",
    "veintiocho", "veintinueve",
]
_TENS_OUT = {30: "treinta", 40: "cuarenta", 50: "cincuenta", 60: "sesenta", 70: "setenta", 80: "ochenta", 90: "noventa"}
_HUNDREDS_OUT = {
    1: "ciento", 2: "doscientos", 3: "trescientos", 4: "cuatrocientos", 5: "quinientos", 6: "seiscientos",
    7: "setecientos", 8: "ochocientos", 9: "novecientos",
}


def _plain(s: str) -> str:
    return (
        s.lower().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace("ü", "u")
    )


def _words_to_int(words: list[str]) -> int | None:
    """['ciento', 'veinte', 'y', 'tres'] -> 123. None si no es un número bien formado."""
    total = current = 0
    seen = False
    for w in words:
        if w == "y":
            continue
        seen = True
        if w in _UNITS:
            current += _UNITS[w]
        elif w in _TENS:
            current += _TENS[w]
        elif w in _HUNDREDS:
            current += _HUNDREDS[w]
        elif w == "mil":
            total += (current or 1) * 1000
            current = 0
        elif w in ("millon", "millones"):
            total += (current or 1) * 1_000_000
            current = 0
        else:
            return None
    return total + current if seen else None


def _numbers_to_digits(text: str) -> str:
    """Sustituye cada grupo de palabras-número por sus cifras: 'doce por quince' -> '12 por 15'."""
    # "ciento" es palabra-número: sin esto, "por ciento" se leería como "por 100".
    text = re.sub(r"\bpor\s+ciento\b", "porciento", _plain(text))
    tokens = re.findall(r"\d+(?:[.,]\d+)?|[^\W\d_]+|\S", text)
    out: list[str] = []
    i = 0
    while i < len(tokens):
        if tokens[i] in _NUMBER_WORDS and tokens[i] != "y":
            j = i
            while j < len(tokens) and tokens[j] in _NUMBER_WORDS:
                j += 1
            group = tokens[i:j]
            while group and group[-1] == "y":  # una "y" suelta al final no es del número
                group.pop(); j -= 1
            value = _words_to_int(group)
            if value is None:
                out.extend(tokens[i:j])
            else:
                out.append(str(value))
            i = max(j, i + 1)
        else:
            out.append(tokens[i]); i += 1
    return " ".join(out)


def _apocope(words: str) -> str:
    """'uno' -> 'un' delante de 'mil'/'millones' ('veintiuno' -> 'veintiún')."""
    if words.endswith("veintiuno"):
        return words[: -len("veintiuno")] + "veintiún"
    return words[:-1] if words.endswith("uno") else words


def _int_to_words(n: int) -> str:
    if n < 0:
        return "menos " + _int_to_words(-n)
    if n < 30:
        return _ONES_OUT[n]
    if n < 100:
        t, u = divmod(n, 10)
        return _TENS_OUT[t * 10] + (f" y {_ONES_OUT[u]}" if u else "")
    if n == 100:
        return "cien"
    if n < 1000:
        h, r = divmod(n, 100)
        return _HUNDREDS_OUT[h] + (f" {_int_to_words(r)}" if r else "")
    if n < 1_000_000:
        th, r = divmod(n, 1000)
        head = "mil" if th == 1 else f"{_apocope(_int_to_words(th))} mil"
        return head + (f" {_int_to_words(r)}" if r else "")
    if n < 1_000_000_000:
        m, r = divmod(n, 1_000_000)
        head = "un millón" if m == 1 else f"{_apocope(_int_to_words(m))} millones"
        return head + (f" {_int_to_words(r)}" if r else "")
    raise ValueError("número demasiado grande")


def _number_out(d: Decimal) -> str:
    d = d.quantize(Decimal("0.01")).normalize()
    if d == d.to_integral_value():
        return _int_to_words(int(d))
    whole, frac = f"{d:f}".split(".")
    sign = "menos " if d < 0 else ""
    whole = whole.lstrip("-")
    return f"{sign}{_int_to_words(int(whole))} coma {' '.join(_ONES_OUT[int(c)] for c in frac)}"


_NUM = r"(\d+(?:[.,]\d+)?)"
_PERCENT = re.compile(rf"{_NUM}\s*(?:porciento|%)\s*de\s*{_NUM}")
_BINARY = re.compile(
    rf"{_NUM}\s*(multiplicado por|dividido entre|dividido por|por|x|mas|menos|entre|\+|-|\*|/)\s*{_NUM}"
)
_OPS = {
    "por": "*", "x": "*", "*": "*", "multiplicado por": "*", "mas": "+", "+": "+", "menos": "-", "-": "-",
    "entre": "/", "dividido entre": "/", "dividido por": "/", "/": "/",
}
_VERB = {"*": "por", "+": "más", "-": "menos", "/": "entre"}


def _dec(s: str) -> Decimal:
    return Decimal(s.replace(",", "."))


_FOLLOW = re.compile(
    r"\b(?:le |lo |la )?(sumo|sumas|sumamos|anado|anades|resto|restas|quito|quitas|multiplico|multiplicas|"
    r"divido|divides)(?: por| entre)? (\d+(?:[.,]\d+)?)"
)
_FOLLOW_OPS = {
    "sumo": "+", "sumas": "+", "sumamos": "+", "anado": "+", "anades": "+", "resto": "-", "restas": "-",
    "quito": "-", "quitas": "-", "multiplico": "*", "multiplicas": "*", "divido": "/", "divides": "/",
}


def _apply(a: Decimal, op: str, b: Decimal) -> tuple[str, Decimal] | None:
    if op == "/" and b == 0:
        return "No se puede dividir entre cero.", a
    res = a * b if op == "*" else a + b if op == "+" else a - b if op == "-" else a / b
    verb = "son" if abs(res) != 1 else "es"
    return f"{_number_out(a).capitalize()} {_VERB[op]} {_number_out(b)} {verb} {_number_out(res)}.", res


def solve_with_value(text: str, last: Decimal | None = None) -> tuple[str, Decimal] | None:
    """(respuesta hablada, resultado) de una cuenta simple, o None si no la entiende.

    `last` es el resultado de la cuenta anterior: permite seguimientos como "¿y si le sumo diez?".
    """
    try:
        t = _numbers_to_digits(text)
        m = _PERCENT.search(t)
        if m:
            pct, base = _dec(m.group(1)), _dec(m.group(2))
            res = base * pct / 100
            return f"El {_number_out(pct)} por ciento de {_number_out(base)} es {_number_out(res)}.", res
        m = _BINARY.search(t)
        if m:
            return _apply(_dec(m.group(1)), _OPS[m.group(2)], _dec(m.group(3)))
        m = _FOLLOW.search(t)
        if m and last is not None:
            return _apply(last, _FOLLOW_OPS[m.group(1)], _dec(m.group(2)))
        return None
    except (InvalidOperation, ValueError, KeyError, ZeroDivisionError):
        return None


def solve(text: str, last: Decimal | None = None) -> str | None:
    """Respuesta hablada a una cuenta simple, o None si no la entiende."""
    r = solve_with_value(text, last)
    return r[0] if r else None
