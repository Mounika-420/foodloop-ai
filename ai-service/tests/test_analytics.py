URL = "/api/v1/analytics/waste-patterns"
ROWS = [
    {"month": "2026-09", "category": "Dairy", "totalKg": 30, "redistributedKg": 10, "wastedKg": 20},
    {"month": "2026-10", "category": "Dairy", "totalKg": 10, "redistributedKg": 5, "wastedKg": 5},
    {"month": "2026-10", "category": "Bakery", "totalKg": 40, "redistributedKg": 0, "wastedKg": 40},
]


def test_aggregates_categories_and_months(client, headers):
    body = client.post(URL, json={"rows": ROWS}, headers=headers).get_json()
    top = body["topWastedCategories"]
    assert [c["category"] for c in top] == ["Bakery", "Dairy"]
    assert top[0]["wastedKg"] == 40.0 and top[0]["wastePct"] == 100.0
    assert top[1]["wastedKg"] == 25.0 and top[1]["wastePct"] == 62.5
    trend = body["monthlyTrend"]
    assert [m["month"] for m in trend] == ["2026-09", "2026-10"]
    assert trend[0]["redistributionRatePct"] == 33.3
    assert trend[1]["wastedKg"] == 45.0 and trend[1]["redistributionRatePct"] == 10.0


def test_insights_mention_the_top_category(client, headers):
    insights = client.post(URL, json={"rows": ROWS}, headers=headers).get_json()["insights"]
    assert len(insights) >= 2
    assert "Bakery" in insights[0]
    assert any("rose" in text for text in insights)


def test_empty_rows_are_fine(client, headers):
    body = client.post(URL, json={"rows": []}, headers=headers).get_json()
    assert body["topWastedCategories"] == [] and body["monthlyTrend"] == []
    assert body["insights"] == ["No data supplied"]


def test_invalid_rows_are_400(client, headers):
    bad = [dict(ROWS[0], wastedKg=-1)]
    res = client.post(URL, json={"rows": bad}, headers=headers)
    assert res.status_code == 400
    assert "rows[0].wastedKg" in res.get_json()["details"]