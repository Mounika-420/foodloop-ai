"""Weighted NGO ranking. Same formula and weights as the Java RuleBasedMatcher (30/25/15/15/15)."""
from datetime import timedelta

import config
from utils.geo import haversine_km
from utils.validation import ValidationError, parse

MAX_RADIUS_KM = 50.0
SPEED_KMH = 30.0
FULL_EXPIRY_MARGIN_HOURS = 6.0
MAX_CANDIDATES = 500
WEIGHTS = {"distance": 30, "category": 25, "quantity": 15, "capacity": 15, "expiry": 15}
CATEGORY_GENERAL_POINTS = 10.0
QUANTITY_GENERAL_POINTS = 7.5

DONATION_SPEC = {
    "donationId": {"type": "int", "required": False, "default": None},
    "category": {"type": "str"},
    "quantityKg": {"type": "float", "min": 0.01},
    "latitude": {"type": "float", "min": -90, "max": 90},
    "longitude": {"type": "float", "min": -180, "max": 180},
    "expiresAt": {"type": "datetime"},
    "hoursToExpiry": {"type": "float", "min": 0},
}
CANDIDATE_SPEC = {
    "ngoId": {"type": "int"},
    "latitude": {"type": "float", "min": -90, "max": 90},
    "longitude": {"type": "float", "min": -180, "max": 180},
    "remainingCapacityKg": {"type": "float", "min": 0},
    "acceptedCategories": {"type": "str_list", "required": False, "default": []},
    "openRequirementKg": {"type": "float", "min": 0, "required": False, "default": None},
    "availableFrom": {"type": "time", "required": False, "default": None},
    "availableTo": {"type": "time", "required": False, "default": None},
}
TOP_N_SPEC = {"topN": {"type": "int", "min": 1, "max": 50, "required": False, "default": 5}}


def _merge(errors, call):
    try:
        return call()
    except ValidationError as exc:
        errors.update(exc.details)
        return None


def parse_request(payload):
    errors = {}
    donation = None
    raw_donation = payload.get("donation")
    if not isinstance(raw_donation, dict):
        errors["donation"] = "must be an object"
    else:
        donation = _merge(errors, lambda: parse(raw_donation, DONATION_SPEC, "donation."))

    candidates = []
    raw_candidates = payload.get("candidates")
    if not isinstance(raw_candidates, list):
        errors["candidates"] = "must be a list"
    elif len(raw_candidates) > MAX_CANDIDATES:
        errors["candidates"] = f"at most {MAX_CANDIDATES} candidates"
    else:
        for index, item in enumerate(raw_candidates):
            if not isinstance(item, dict):
                errors[f"candidates[{index}]"] = "must be an object"
                continue
            parsed = _merge(errors, lambda item=item, index=index: parse(item, CANDIDATE_SPEC, f"candidates[{index}]."))
            if parsed is not None:
                candidates.append(parsed)

    top = _merge(errors, lambda: parse(payload, TOP_N_SPEC))
    if errors:
        raise ValidationError(errors)
    return donation, candidates, top["topN"]


def _within_hours(moment, start, end):
    if start is None or end is None:
        return True
    if start <= end:
        return start <= moment <= end
    return moment >= start or moment <= end


def _explain(dist, category, has_requirement, quantity, capacity, margin_hours):
    if dist <= 5:
        parts = [f"Very close ({dist:.1f} km)"]
    elif dist <= 15:
        parts = [f"Close by ({dist:.1f} km)"]
    else:
        parts = [f"Farther away ({dist:.1f} km)"]
    parts.append(f"has an open requirement for {category}" if has_requirement else f"accepts {category}")
    parts.append(f"capacity covers {quantity:g} kg" if capacity >= quantity
                 else f"capacity covers {capacity:g} of {quantity:g} kg")
    parts.append(f"reachable with {margin_hours:.1f} h to spare before expiry")
    return ", ".join(parts)


def _score(donation, cand, now):
    quantity = donation["quantityKg"]
    dist = haversine_km(donation["latitude"], donation["longitude"], cand["latitude"], cand["longitude"])
    if dist > MAX_RADIUS_KM or cand["remainingCapacityKg"] <= 0:
        return None
    accepted = [c.casefold() for c in cand["acceptedCategories"]]
    if accepted and donation["category"].casefold() not in accepted:
        return None

    expires = donation["expiresAt"]
    arrival = now + timedelta(seconds=round(dist / SPEED_KMH * 3600.0))
    if not arrival < expires:
        return None
    if not _within_hours(arrival.time(), cand["availableFrom"], cand["availableTo"]):
        return None

    margin_hours = int((expires - arrival).total_seconds() // 60) / 60.0
    requirement = cand["openRequirementKg"]
    has_requirement = requirement is not None and requirement > 0

    breakdown = {
        "distance": WEIGHTS["distance"] * (1.0 - dist / MAX_RADIUS_KM),
        "category": WEIGHTS["category"] if has_requirement else CATEGORY_GENERAL_POINTS,
        "quantity": (WEIGHTS["quantity"] * min(1.0, quantity / requirement)
                     if has_requirement else QUANTITY_GENERAL_POINTS),
        "capacity": WEIGHTS["capacity"] * min(1.0, cand["remainingCapacityKg"] / quantity),
        "expiry": WEIGHTS["expiry"] * min(1.0, margin_hours / FULL_EXPIRY_MARGIN_HOURS),
    }
    breakdown = {key: round(value, 2) for key, value in breakdown.items()}
    return {
        "ngoId": cand["ngoId"],
        "matchScore": round(sum(breakdown.values()), 2),
        "distanceKm": round(dist, 2),
        "breakdown": breakdown,
        "explanation": _explain(dist, donation["category"], has_requirement, quantity,
                                cand["remainingCapacityKg"], margin_hours),
    }


def recommend(payload):
    donation, candidates, top_n = parse_request(payload)
    now = donation["expiresAt"] - timedelta(hours=donation["hoursToExpiry"])
    scored = [s for s in (_score(donation, c, now) for c in candidates) if s is not None]
    scored.sort(key=lambda s: (-s["matchScore"], s["distanceKm"], s["ngoId"]))

    recommendations = []
    for rank, item in enumerate(scored[:top_n], start=1):
        recommendations.append({
            "ngoId": item["ngoId"],
            "rank": rank,
            "matchScore": item["matchScore"],
            "distanceKm": item["distanceKm"],
            "breakdown": item["breakdown"],
            "explanation": item["explanation"],
        })
    return {
        "recommendations": recommendations,
        "method": "weighted-scoring",
        "weights": dict(WEIGHTS),
        "modelName": "weighted-scoring",
        "modelVersion": config.MODEL_VERSION,
        "trainedOnSyntheticData": False,
    }