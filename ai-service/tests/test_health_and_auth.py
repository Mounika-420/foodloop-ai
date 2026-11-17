from fakes import FakeStore


def test_health_is_open_and_reports_models(client):
    res = client.get("/health")
    assert res.status_code == 200
    body = res.get_json()
    assert body["status"] == "UP"
    assert body["modelsLoaded"] == {"surplus": True, "demand": True}
    assert body["modelVersion"] == "9.9.9"
    assert body["trainedOnSyntheticData"] is True


def test_health_reports_unloaded_models(make_client):
    body = make_client(store=FakeStore(surplus=False)).get("/health").get_json()
    assert body["modelsLoaded"] == {"surplus": False, "demand": True}


def test_protected_endpoint_without_key_is_401(client):
    res = client.get("/api/v1/models/info")
    assert res.status_code == 401
    assert res.get_json() == {"error": "UNAUTHORIZED"}


def test_wrong_key_is_401(client):
    res = client.get("/api/v1/models/info", headers={"X-Internal-Api-Key": "nope"})
    assert res.status_code == 401


def test_key_not_configured_rejects_everything(make_client):
    res = make_client(api_key="").get("/api/v1/models/info", headers={"X-Internal-Api-Key": ""})
    assert res.status_code == 401


def test_correct_key_passes(client, headers):
    assert client.get("/api/v1/models/info", headers=headers).status_code == 200