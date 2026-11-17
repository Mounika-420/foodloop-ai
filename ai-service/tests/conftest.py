import pytest

from app import create_app
from fakes import FakeStore

API_KEY = "test-key"


@pytest.fixture
def headers():
    return {"X-Internal-Api-Key": API_KEY}


@pytest.fixture
def store():
    return FakeStore()


@pytest.fixture
def client(store):
    return create_app(store=store, api_key=API_KEY).test_client()


@pytest.fixture
def make_client():
    def _make(store=None, api_key=API_KEY):
        return create_app(store=store or FakeStore(), api_key=api_key).test_client()
    return _make