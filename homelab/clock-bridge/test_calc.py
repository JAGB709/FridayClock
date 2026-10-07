import pytest

from decimal import Decimal

from calc import _int_to_words, _numbers_to_digits, solve, solve_with_value


@pytest.mark.parametrize(
    "text, expected",
    [
        ("¿Cuánto es doce por quince?", "Doce por quince son ciento ochenta."),
        ("cuánto es 7 más 5", "Siete más cinco son doce."),
        ("¿Cuánto es cien menos veinticinco?", "Cien menos veinticinco son setenta y cinco."),
        ("divide ochenta entre cuatro", "Ochenta entre cuatro son veinte."),
        ("¿cuánto es el quince por ciento de doscientos?", "El quince por ciento de doscientos es treinta."),
        ("cuánto es mil doscientos treinta y cuatro más seis", "Mil doscientos treinta y cuatro más seis son mil doscientos cuarenta."),
        ("cuánto es 10 entre 4", "Diez entre cuatro son dos coma cinco."),
        ("quinientos por dos", "Quinientos por dos son mil."),
        ("¿cuánto es uno más cero?", "Uno más cero es uno."),
        ("cuánto es dos mil por trescientos", "Dos mil por trescientos son seiscientos mil."),
    ],
)
def test_cuentas(text, expected):
    assert solve(text) == expected


def test_division_por_cero():
    assert solve("cuánto es cinco entre cero") == "No se puede dividir entre cero."


@pytest.mark.parametrize(
    "text",
    ["¿Cuál es la capital de Francia?", "Cuéntame un chiste", "convierte cien kilómetros a millas", "hola", ""],
)
def test_lo_que_no_es_cuenta_devuelve_none(text):
    assert solve(text) is None


@pytest.mark.parametrize(
    "n, words",
    [
        (0, "cero"), (21, "veintiuno"), (30, "treinta"), (31, "treinta y uno"), (100, "cien"), (101, "ciento uno"),
        (180, "ciento ochenta"), (999, "novecientos noventa y nueve"), (1000, "mil"), (21000, "veintiún mil"),
        (1_000_000, "un millón"), (2_500_000, "dos millones quinientos mil"),
    ],
)
def test_numero_a_palabras(n, words):
    assert _int_to_words(n) == words


def test_palabras_a_cifras():
    assert _numbers_to_digits("doce por quince") == "12 por 15"
    assert _numbers_to_digits("ciento veintitrés más treinta y cinco") == "123 mas 35"


def test_seguimiento_encadena_con_el_resultado_anterior():
    _, res = solve_with_value("¿Cuánto es doce por quince?")
    assert res == 180
    text, res = solve_with_value("¿Y si le sumo diez?", res)
    assert text == "Ciento ochenta más diez son ciento noventa."
    text, res = solve_with_value("y ahora lo divido entre cinco", res)
    assert text == "Ciento noventa entre cinco son treinta y ocho."
    assert res == 38


def test_seguimiento_sin_cuenta_previa_no_se_inventa_nada():
    assert solve("¿Y si le sumo diez?") is None
    assert solve("El resto del día lo paso en casa") is None


def test_seguimiento_con_resta_y_multiplicacion():
    assert solve("y si le resto veinte", Decimal(100)) == "Cien menos veinte son ochenta."
    assert solve("lo multiplico por tres", Decimal(7)) == "Siete por tres son veintiuno."
