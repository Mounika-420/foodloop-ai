import config
from utils.validation import ValidationError, parse

ROW_SPEC = {
    "month": {"type": "str"},
    "category": {"type": "str"},
    "totalKg": {"type": "float", "min": 0},
    "redistributedKg": {"type": "float", "min": 0},
    "wastedKg": {"type": "float", "min": 0},
}


def _pct(part, whole):
    return round(100.0 * part / whole, 1) if whole > 0 else 0.0


def _rows(payload):
    raw = payload.get("rows")
    if not isinstance(raw, list):
        raise ValidationError({"rows": "must be a list"})
    errors, rows = {}, []
    for index, item in enumerate(raw):
        if not isinstance(item, dict):
            errors[f"rows[{index}]"] = "must be an object"
            continue
        try:
            rows.append(parse(item, ROW_SPEC, f"rows[{index}]."))
        except ValidationError as exc:
            errors.update(exc.details)
    if errors:
        raise ValidationError(errors)
    return rows


def _empty():
    return {"total": 0.0, "redistributed": 0.0, "wasted": 0.0}


def analyze(payload):
    rows = _rows(payload)
    by_category, by_month = {}, {}
    for row in rows:
        for bucket in (by_category.setdefault(row["category"], _empty()), by_month.setdefault(row["month"], _empty())):
            bucket["total"] += row["totalKg"]
            bucket["redistributed"] += row["redistributedKg"]
            bucket["wasted"] += row["wastedKg"]

    top = [
        {"category": name, "totalKg": round(v["total"], 2), "redistributedKg": round(v["redistributed"], 2),
         "wastedKg": round(v["wasted"], 2), "wastePct": _pct(v["wasted"], v["total"])}
        for name, v in sorted(by_category.items(), key=lambda kv: (-kv[1]["wasted"], kv[0]))
    ]
    trend = [
        {"month": month, "totalKg": round(v["total"], 2), "redistributedKg": round(v["redistributed"], 2),
         "wastedKg": round(v["wasted"], 2), "redistributionRatePct": _pct(v["redistributed"], v["total"])}
        for month, v in sorted(by_month.items())
    ]

    insights = []
    total_wasted = sum(v["wasted"] for v in by_category.values())
    total_all = sum(v["total"] for v in by_category.values())
    total_redistributed = sum(v["redistributed"] for v in by_category.values())
    if not rows:
        insights.append("No data supplied")
    else:
        if total_wasted > 0:
            leader = top[0]
            insights.append(f"{leader['category']} is the most wasted category "
                            f"({leader['wastedKg']:.0f} kg, {100.0 * leader['wastedKg'] / total_wasted:.0f}% of all wasted food)")
        else:
            insights.append("No wasted food recorded in the supplied data")
        if len(trend) >= 2 and trend[-2]["wastedKg"] > 0:
            prev, last = trend[-2], trend[-1]
            change = 100.0 * (last["wastedKg"] - prev["wastedKg"]) / prev["wastedKg"]
            verb = "rose" if change > 0 else "fell" if change < 0 else "was unchanged"
            amount = f" by {abs(change):.0f}%" if change != 0 else ""
            insights.append(f"Wasted food {verb}{amount} from {prev['month']} to {last['month']}")
        if total_all > 0:
            insights.append(f"{100.0 * total_redistributed / total_all:.0f}% of food was redistributed overall")

    return {
        "topWastedCategories": top,
        "monthlyTrend": trend,
        "insights": insights,
        "modelName": "aggregation",
        "modelVersion": config.MODEL_VERSION,
        "trainedOnSyntheticData": False,
    }