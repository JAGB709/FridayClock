"""Recordatorios: validación, repeticiones, almacén atómico y vencidos (sin red)."""
import json
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

import reminders as R

MAD = ZoneInfo("Europe/Madrid")


def dt(y, mo, d, h=0, mi=0):
    return datetime(y, mo, d, h, mi, tzinfo=MAD)


def test_clean_text_quita_control_y_espacios():
    assert R.clean_text("  hola\x00\x07 \n  mundo\t") == "hola mundo"


def test_clean_text_vacio_o_largo_es_error():
    with pytest.raises(ValueError):
        R.clean_text(" \x00 ")
    with pytest.raises(ValueError):
        R.clean_text("x" * (R.MAX_TEXT + 1))


def test_parse_at_local_y_con_desfase():
    assert R.parse_at("2026-10-28T08:30", MAD) == dt(2026, 10, 28, 8, 30)
    got = R.parse_at("2026-10-28T08:30:00+00:00", MAD)
    assert got.utcoffset() == timedelta(0)
    with pytest.raises(ValueError):
        R.parse_at("mañana", MAD)


def test_next_after_diario_laborables_semanal():
    now = dt(2026, 10, 30, 9, 0)  # viernes
    at = dt(2026, 10, 30, 8, 0)
    assert R.next_after(at, "daily", now) == dt(2026, 10, 31, 8, 0)
    assert R.next_after(at, "weekdays", now) == dt(2026, 11, 2, 8, 0)  # lunes
    assert R.next_after(at, "weekly", now) == dt(2026, 11, 6, 8, 0)
    assert R.next_after(at, "none", now) is None


def test_next_after_mantiene_hora_local_en_cambio_de_hora():
    # El domingo 25-oct-2026 en Madrid se retrasa el reloj: las 08:00 locales siguen siendo las 08:00.
    at = dt(2026, 10, 24, 8, 0)
    nxt = R.next_after(at, "daily", dt(2026, 10, 24, 8, 1))
    assert nxt == dt(2026, 10, 25, 8, 0)
    nxt2 = R.next_after(nxt, "daily", dt(2026, 10, 25, 8, 1))
    assert (nxt2.hour, nxt2.minute) == (8, 0)


def test_store_add_list_delete_y_persistencia(tmp_path):
    f = tmp_path / "r.json"
    now = dt(2026, 10, 27, 10, 0)
    s = R.ReminderStore(f, MAD)
    r = s.add("Pastillas", dt(2026, 10, 27, 12, 0), "daily", "both", now)
    assert len(r["id"]) == 12 and r["channel"] == "both"
    assert [x["id"] for x in R.ReminderStore(f, MAD).list()] == [r["id"]]
    assert s.delete(r["id"]) is True and s.delete(r["id"]) is False
    assert R.ReminderStore(f, MAD).list() == []


def test_store_valida_canal_repeticion_y_pasado(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    now = dt(2026, 10, 27, 10, 0)
    with pytest.raises(ValueError):
        s.add("x", dt(2026, 10, 27, 12, 0), "nunca", "clock", now)
    with pytest.raises(ValueError):
        s.add("x", dt(2026, 10, 27, 12, 0), "none", "sms", now)
    with pytest.raises(ValueError):
        s.add("x", dt(2026, 10, 27, 9, 0), "none", "clock", now)  # pasado


def test_store_repetido_en_el_pasado_salta_a_la_proxima(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    r = s.add("x", dt(2026, 10, 27, 8, 0), "daily", "clock", dt(2026, 10, 27, 10, 0))
    assert R.parse_at(r["at"], MAD) == dt(2026, 10, 28, 8, 0)


def test_store_limite_de_recordatorios(tmp_path, monkeypatch):
    monkeypatch.setattr(R, "MAX_REMINDERS", 2)
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    now = dt(2026, 10, 27, 10, 0)
    s.add("a", dt(2026, 10, 28, 8), "none", "clock", now)
    s.add("b", dt(2026, 10, 28, 8), "none", "clock", now)
    with pytest.raises(ValueError):
        s.add("c", dt(2026, 10, 28, 8), "none", "clock", now)


def test_escritura_atomica_sin_restos_y_archivo_corrupto(tmp_path):
    f = tmp_path / "r.json"
    s = R.ReminderStore(f, MAD)
    s.add("a", dt(2026, 10, 28, 8), "none", "clock", dt(2026, 10, 27, 10))
    assert [p.name for p in tmp_path.iterdir()] == ["r.json"]
    f.write_text("{no es json", encoding="utf-8")
    assert R.ReminderStore(f, MAD).list() == []  # no rompe el arranque
    assert (tmp_path / "r.json.corrupto").exists()


def test_fire_due_envia_borra_y_reprograma(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    t0 = dt(2026, 10, 27, 10, 0)
    s.add("una vez", dt(2026, 10, 27, 10, 5), "none", "clock", t0)
    s.add("diario", dt(2026, 10, 27, 10, 5), "daily", "telegram", t0)
    s.add("futuro", dt(2026, 10, 27, 18, 0), "none", "both", t0)
    sent = []
    now = dt(2026, 10, 27, 10, 6)
    n = R.fire_due(s, lambda text, ch: sent.append((text, ch)) or True, now)
    assert n == 2 and sorted(sent) == [("diario", "telegram"), ("una vez", "clock")]
    left = {r["text"]: r for r in s.list()}
    assert set(left) == {"diario", "futuro"}
    assert R.parse_at(left["diario"]["at"], MAD) == dt(2026, 10, 28, 10, 5)


def test_fire_due_repetido_muy_vencido_se_salta_pero_unico_se_envia(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    t0 = dt(2026, 10, 27, 7, 0)
    s.add("diario", dt(2026, 10, 27, 8, 0), "daily", "clock", t0)
    s.add("unico", dt(2026, 10, 27, 8, 0), "none", "clock", t0)
    sent = []
    R.fire_due(s, lambda text, ch: sent.append(text) or True, dt(2026, 10, 27, 12, 0), grace_min=10)
    assert sent == ["unico"]
    assert [r["text"] for r in s.list()] == ["diario"]


def test_fire_due_reintenta_y_se_rinde(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    s.add("x", dt(2026, 10, 27, 10, 5), "none", "clock", dt(2026, 10, 27, 10, 0))
    calls = []
    fail = lambda text, ch: calls.append(1) or False  # noqa: E731
    for i in range(R.MAX_TRIES):
        assert len(s.list()) == 1
        R.fire_due(s, fail, dt(2026, 10, 27, 10, 6 + i))
    assert len(calls) == R.MAX_TRIES and s.list() == []


def test_fire_due_una_excepcion_no_tumba_el_ciclo(tmp_path):
    s = R.ReminderStore(tmp_path / "r.json", MAD)
    t0 = dt(2026, 10, 27, 10, 0)
    s.add("a", dt(2026, 10, 27, 10, 5), "none", "clock", t0)

    def boom(text, ch):
        raise RuntimeError("secreto-en-url")

    assert R.fire_due(s, boom, dt(2026, 10, 27, 10, 6)) == 0


def test_archivo_guardado_es_json_valido(tmp_path):
    f = tmp_path / "r.json"
    R.ReminderStore(f, MAD).add("a", dt(2026, 10, 28, 8), "none", "clock", dt(2026, 10, 27, 10))
    data = json.loads(f.read_text(encoding="utf-8"))
    assert data["version"] == 1 and len(data["reminders"]) == 1
