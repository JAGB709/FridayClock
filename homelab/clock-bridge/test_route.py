import pytest

from route import needs_agent, needs_thinking


@pytest.mark.parametrize(
    "text",
    [
        "¿Cuál es la capital de Francia?",
        "Cuéntame un chiste",
        "¿Cuánto es doce por quince?",
        "Dame una receta fácil de tortilla",
        "¿Cómo se dice gracias en neerlandés?",
        "Háblame de la Revolución Francesa",
        "Estoy cansado, anímame un poco",
    ],
)
def test_charla_va_directa(text):
    assert not needs_agent(text)


@pytest.mark.parametrize(
    "text",
    [
        "¿Tengo algún correo nuevo?",
        "¿Qué tengo en la agenda mañana?",
        "Recuérdame llamar a mamá el viernes",
        "Busca el horario del tren a Ámsterdam",
        "Enciende la luz del salón",
        "Apaga las luces",
        "Mándale un mensaje a Marta",
        "Pregúntale a Hermes qué hay de nuevo",
        "Dime las noticias de hoy",
    ],
)
def test_herramientas_van_al_agente(text):
    assert needs_agent(text)


@pytest.mark.parametrize(
    "text",
    [
        "¿Cuánto es doce por quince?",
        "¿Y si le sumo diez?",
        "¿Cuántos días tiene febrero en un año bisiesto?",
        "Calcula el quince por ciento de doscientos",
        "Convierte cien kilómetros a millas",
        "¿Cuánto es 7 más 5?",
        "Divide ochenta entre cuatro",
    ],
)
def test_calculo_deja_pensar(text):
    assert needs_thinking(text)


@pytest.mark.parametrize(
    "text",
    [
        "¿Cuál es la capital de Francia?",
        "Cuéntame un chiste",
        "Anímame un poco, estoy cansado",
        "Háblame de la Revolución Francesa",
        "¿Qué me recomiendas para cenar algo ligero?",
        "Hola",
    ],
)
def test_charla_no_piensa(text):
    assert not needs_thinking(text)
