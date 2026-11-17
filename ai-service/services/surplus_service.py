import config
from utils.validation import parse

SPEC = {
    "category": {"type": "str"},
    "donorType": {"type": "str"},
    "dayOfWeek": {"type": "enum", "choices": config.DAYS},
    "month": {"type": "int", "min": 1, "max": 12},
    "season": {"type": "enum", "choices": config.SEASONS, "required": False, "default": None},
    "eventFlag": {"type": "bool", "required": False, "default": False},
    "previousQuantityKg": {"type": "float", "min": 0},
    "area": {"type": "str"},
}


def _warnings(data):
    known = (("category", config.CATEGORIES), ("donorType", config.DONOR_TYPES), ("area", config.AREAS))
    return [f"{field} '{data[field]}' was not seen in the training data"
            for field, values in known if data[field] not in values]


def predict(store, payload):
    data = parse(payload, SPEC)
    row = {
        "category": data["category"],
        "donorType": data["donorType"],
        "dayOfWeek": data["dayOfWeek"],
        "month": data["month"],
        "season": data["season"] or config.season_for_month(data["month"]),
        "eventFlag": int(data["eventFlag"]),
        "previousQuantityKg": data["previousQuantityKg"],
        "area": data["area"],
    }
    raw = store.predict("surplus", row)
    info = store.describe("surplus") or {}
    mae = (info.get("metrics") or {}).get("mae")
    note = (f"model MAE on synthetic test set: {mae} kg (see /api/v1/models/info)"
            if mae is not None else "model metrics unavailable (see /api/v1/models/info)")
    response = {
        "predictedSurplusKg": round(max(0.0, raw), 1),
        "confidenceNote": note,
        "modelName": info.get("algorithm", "unknown"),
        "modelVersion": store.version,
        "trainedOnSyntheticData": True,
    }
    warnings = _warnings(data)
    if warnings:
        response["warnings"] = warnings
    return response