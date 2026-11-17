import config
from utils.validation import parse

SPEC = {
    "category": {"type": "str"},
    "area": {"type": "str"},
    "dayOfWeek": {"type": "enum", "choices": config.DAYS},
    "month": {"type": "int", "min": 1, "max": 12},
    "hourOfDay": {"type": "int", "min": 0, "max": 23},
}
TRAINED_HOURS = (6, 22)


def _warnings(data):
    warnings = []
    for field, values in (("category", config.CATEGORIES), ("area", config.AREAS)):
        if data[field] not in values:
            warnings.append(f"{field} '{data[field]}' was not seen in the training data")
    low, high = TRAINED_HOURS
    if not low <= data["hourOfDay"] <= high:
        warnings.append(f"hourOfDay {data['hourOfDay']} is outside the trained range {low}-{high}; treat with caution")
    return warnings


def predict(store, payload):
    data = parse(payload, SPEC)
    row = {key: data[key] for key in ("category", "area", "dayOfWeek", "month", "hourOfDay")}
    raw = store.predict("demand", row)
    info = store.describe("demand") or {}
    mae = (info.get("metrics") or {}).get("mae")
    note = (f"model MAE on synthetic test set: {mae} kg (see /api/v1/models/info)"
            if mae is not None else "model metrics unavailable (see /api/v1/models/info)")
    response = {
        "predictedDemandKg": round(max(0.0, raw), 1),
        "confidenceNote": note,
        "modelName": info.get("algorithm", "unknown"),
        "modelVersion": store.version,
        "trainedOnSyntheticData": True,
    }
    warnings = _warnings(data)
    if warnings:
        response["warnings"] = warnings
    return response