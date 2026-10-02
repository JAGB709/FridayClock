import pytest

from wake import split_wake


@pytest.mark.parametrize(
    "text, command",
    [
        ("Friday", ""),
        ("Friday, ¿qué hora es?", "qué hora es?"),
        ("friday pon una alarma a las siete", "pon una alarma a las siete"),
        ("Fraidei, ¿qué tiempo hace?", "qué tiempo hace?"),
        ("Fry day, apaga la alarma", "apaga la alarma"),
        ("Oye, Friday, dime un chiste", "Oye dime un chiste"),
        ("¿Qué hora es, Friday?", "¿Qué hora es"),
        ("FRIDAY.", ""),
    ],
)
def test_detecta_la_palabra(text, command):
    ok, rest = split_wake(text)
    assert ok, text
    assert rest == command


@pytest.mark.parametrize(
    "text",
    [
        "",
        "¿Qué hora es?",
        "Ayer fui a la playa con mi hermano",
        "Mañana es viernes",
        "Hace mucho frío",
        "Subtítulos realizados por la comunidad de Amara.org",
        "Gracias por ver el vídeo",
        "Ya es tarde para ir a cenar con ellos hoy",
    ],
)
def test_no_se_activa_con_otras_frases(text):
    assert split_wake(text) == (False, "")
