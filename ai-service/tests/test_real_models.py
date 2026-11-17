"""Run against the models produced by train_model.py (skipped when they have not been trained)."""
import json

import pytest

import config
from app import create_app
from services.model_store import ModelStore

_trained = config.METRICS_FILE.exists() and all(
    (config.MODEL_DIR / f"{name}_model.joblib").exists() for name in ("surplus", "demand"))
pytestmark = pytest.mark.skipif(not _trained, reason="run train_model.py first")

HEADERS = {"X-Internal-Api-Key": "k"}
KNOWN_ALGORITHMS = {"LinearRegression", "RandomForestRegressor", "GradientBoostingRegressor"}


@pytest.fixture(scope="module")
def store():
    return ModelStore()


def test_big_hotel_weekend_event_beats_small_weekday_bakery(store):
    big = {"category": "Cooked Meals", "donorType": "CATERER", "dayOfWeek": "SAT", "month": 11,
           "season": "WINTER", "eventFlag": 1, "previousQuantityKg": 80.0, "area": "RS Puram"}
    small = {"category": "Dairy", "donorType": "BAKERY", "dayOfWeek": "TUE", "month": 6,
             "season": "MONSOON", "eventFlag": 0, "previousQuantityKg": 2.0, "area": "Singanallur"}
    assert store.predict("surplus", big) > store.predict("surplus", small) + 50


def test_demand_peaks_in_the_evening(store):
    base = {"category": "Cooked Meals", "area": "Ukkadam", "dayOfWeek": "SAT", "month": 11}
    assert store.predict("demand", dict(base, hourOfDay=19)) > 1.5 * store.predict("demand", dict(base, hourOfDay=8))


def test_contract_sample_requests_work_end_to_end(store):
    client = create_app(store=store, api_key="k").test_client()
    surplus = client.post("/api/v1/predict/surplus", headers=HEADERS, json={
        "category": "Cooked Meals", "donorType": "HOTEL", "dayOfWeek": "MON", "month": 11,
        "season": "WINTER", "eventFlag": False, "previousQuantityKg": 22.0, "area": "RS Puram"})
    assert surplus.status_code == 200
    body = surplus.get_json()
    assert 0 <= body["predictedSurplusKg"] <= 300
    assert body["modelName"] in KNOWN_ALGORITHMS
    assert body["trainedOnSyntheticData"] is True

    demand = client.post("/api/v1/predict/demand", headers=HEADERS, json={
        "category": "Cooked Meals", "area": "Ukkadam", "dayOfWeek": "MON", "month": 11, "hourOfDay": 18})
    assert demand.status_code == 200
    assert demand.get_json()["predictedDemandKg"] >= 0


def test_models_info_matches_metrics_file(store):
    report = json.loads(config.METRICS_FILE.read_text(encoding="utf-8"))
    client = create_app(store=store, api_key="k").test_client()
    models = {m["name"]: m for m in client.get("/api/v1/models/info", headers=HEADERS).get_json()["models"]}
    for name in ("surplus", "demand"):
        assert models[name]["algorithm"] == report["models"][name]["algorithm"]
        assert models[name]["metrics"]["mae"] == report["models"][name]["metrics"]["mae"]