"""El parte de buenos días no debe depender del bucle lento de Hermes."""
import pytest

pytest.importorskip("fastapi")

import bridge


CONTEXT = {"now": "2026-10-26T07:30:00", "weekday": "lunes", "location": {"city": "Madrid"},
           "weather": {"description": "soleado", "temp": 14, "max": 20, "min": 9, "rain_prob": 5}}


def _assist(monkeypatch, fast=None, hermes=None):
    calls = []

    def fake_fast(text, context, device, **kw):
        calls.append(("fast", kw))
        if fast is None:
            raise RuntimeError("ollama caído")
        return fast

    def fake_hermes(text, context, device):
        calls.append(("hermes", {}))
        return hermes or "respuesta de hermes"

    monkeypatch.setattr(bridge, "ask_fast", fake_fast)
    monkeypatch.setattr(bridge, "ask_hermes", fake_hermes)
    monkeypatch.setattr(bridge, "synthesize", lambda text: None)
    out = bridge.assist(bridge.AssistRequest(mode="briefing", context=CONTEXT))
    return out, calls


def test_briefing_usa_el_camino_rapido_sin_hermes(monkeypatch):
    out, calls = _assist(monkeypatch, fast="Buenos días, hoy es lunes.")
    assert out["reply"] == "Buenos días, hoy es lunes."
    assert [c[0] for c in calls] == ["fast"]
    assert calls[0][1].get("briefing") is True


def test_briefing_cae_a_hermes_si_ollama_falla(monkeypatch):
    out, calls = _assist(monkeypatch, fast=None, hermes="Buenos días desde Hermes")
    assert out["reply"] == "Buenos días desde Hermes"
    assert [c[0] for c in calls] == ["fast", "hermes"]
