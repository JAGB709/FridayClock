import os
import sys
import threading

import pytest

HERE = os.path.dirname(__file__)
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..")))
sys.path.insert(0, os.path.abspath(os.path.join(HERE, "..", "..", "..", "hermes-skill")))

import mock_clock_server as mock  # noqa: E402

TOKEN = "secreto-del-reloj"


@pytest.fixture()
def clock_url():
    mock.reset()
    server = mock.make_server(port=0, token=TOKEN)
    host, port = server.server_address
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        yield f"http://{host}:{port}"
    finally:
        server.shutdown()


@pytest.fixture()
def make_client(clock_url):
    from fastapi.testclient import TestClient
    from app import Config, create_app

    def _make(**kw):
        cfg = Config(clock_url=kw.pop("clock_url", clock_url), clock_token=kw.pop("clock_token", TOKEN), **kw)
        return TestClient(create_app(cfg), headers={"X-Friday": "1"})
    return _make


@pytest.fixture()
def client(make_client):
    return make_client()
