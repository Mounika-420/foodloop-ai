from fakes import FakeStore


def test_lists_loaded_models(client, headers):
    body = client.get("/api/v1/models/info", headers=headers).get_json()
    assert [m["name"] for m in body["models"]] == ["surplus", "demand"]
    assert body["models"][0]["algorithm"] == "FakeRegressor"
    assert body["models"][0]["metrics"]["mae"] == 1.5
    assert body["modelVersion"] == "9.9.9"
    assert body["trainedOnSyntheticData"] is True


def test_unloaded_models_are_omitted(make_client, headers):
    client = make_client(store=FakeStore(demand=False))
    body = client.get("/api/v1/models/info", headers=headers).get_json()
    assert [m["name"] for m in body["models"]] == ["surplus"]