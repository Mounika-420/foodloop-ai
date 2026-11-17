from fakes import FakeStore

SURPLUS_OK = {"category": "Cooked Meals", "donorType": "HOTEL", "dayOfWeek": "MON", "month": 11,
              "season": "WINTER", "eventFlag": False, "previousQuantityKg": 22.0, "area": "RS Puram"}
DEMAND_OK = {"category": "Cooked Meals", "area": "Ukkadam", "dayOfWeek": "MON", "month": 11, "hourOfDay": 18}
SURPLUS_URL = "/api/v1/predict/surplus"
DEMAND_URL = "/api/v1/predict/demand"


def test_surplus_success_shape(client, headers):
    res = client.post(SURPLUS_URL, json=SURPLUS_OK, headers=headers)
    assert res.status_code == 200
    body = res.get_json()
    assert body["predictedSurplusKg"] == 19.4
    assert body["modelName"] == "FakeRegressor"
    assert body["modelVersion"] == "9.9.9"
    assert body["trainedOnSyntheticData"] is True
    assert "1.5" in body["confidenceNote"]
    assert "warnings" not in body


def test_surplus_passes_clean_features_to_the_model(client, store, headers):
    client.post(SURPLUS_URL, json=SURPLUS_OK, headers=headers)
    assert store.last_row["surplus"] == {
        "category": "Cooked Meals", "donorType": "HOTEL", "dayOfWeek": "MON", "month": 11,
        "season": "WINTER", "eventFlag": 0, "previousQuantityKg": 22.0, "area": "RS Puram"}


def test_surplus_missing_fields_are_400_with_details(client, headers):
    res = client.post(SURPLUS_URL, json={}, headers=headers)
    assert res.status_code == 400
    body = res.get_json()
    assert body["error"] == "VALIDATION_ERROR"
    assert {"category", "donorType", "dayOfWeek", "month", "previousQuantityKg", "area"} <= set(body["details"])


def test_surplus_invalid_values_are_400(client, headers):
    bad = dict(SURPLUS_OK, month=13, dayOfWeek="FUNDAY", previousQuantityKg=-1, eventFlag="yes")
    res = client.post(SURPLUS_URL, json=bad, headers=headers)
    assert res.status_code == 400
    assert {"month", "dayOfWeek", "previousQuantityKg", "eventFlag"} <= set(res.get_json()["details"])


def test_non_json_body_is_400(client, headers):
    res = client.post(SURPLUS_URL, data="not json", headers={**headers, "Content-Type": "text/plain"})
    assert res.status_code == 400
    assert "body" in res.get_json()["details"]


def test_unknown_category_adds_a_warning(client, headers):
    res = client.post(SURPLUS_URL, json=dict(SURPLUS_OK, category="Moon Cheese"), headers=headers)
    assert res.status_code == 200
    assert any("Moon Cheese" in w for w in res.get_json()["warnings"])


def test_demand_success(client, store, headers):
    res = client.post(DEMAND_URL, json=DEMAND_OK, headers=headers)
    assert res.status_code == 200
    body = res.get_json()
    assert body["predictedDemandKg"] == 33.3
    assert body["trainedOnSyntheticData"] is True
    assert store.last_row["demand"]["hourOfDay"] == 18


def test_demand_validation_is_400(client, headers):
    res = client.post(DEMAND_URL, json=dict(DEMAND_OK, hourOfDay=30), headers=headers)
    assert res.status_code == 400
    assert "hourOfDay" in res.get_json()["details"]


def test_demand_hour_outside_training_range_warns(client, headers):
    res = client.post(DEMAND_URL, json=dict(DEMAND_OK, hourOfDay=3), headers=headers)
    assert res.status_code == 200
    assert any("hourOfDay" in w for w in res.get_json()["warnings"])


def test_model_not_loaded_is_503(make_client, headers):
    client = make_client(store=FakeStore(surplus=False))
    res = client.post(SURPLUS_URL, json=SURPLUS_OK, headers=headers)
    assert res.status_code == 503
    assert res.get_json() == {"error": "MODEL_NOT_LOADED"}


def test_unexpected_model_error_is_500(make_client, headers):
    class BrokenStore(FakeStore):
        def predict(self, name, row):
            raise RuntimeError("boom")

    res = make_client(store=BrokenStore()).post(SURPLUS_URL, json=SURPLUS_OK, headers=headers)
    assert res.status_code == 500
    assert res.get_json() == {"error": "PREDICTION_FAILED"}