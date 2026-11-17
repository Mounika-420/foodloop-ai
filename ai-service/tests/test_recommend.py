import pytest

URL = "/api/v1/recommend/ngos"
DONATION = {"donationId": 4, "category": "Cooked Meals", "quantityKg": 36.0, "latitude": 11.001,
            "longitude": 76.975, "expiresAt": "2026-11-03T21:00:00", "hoursToExpiry": 12.0}


def candidate(**override):
    base = {"ngoId": 1, "latitude": 11.001, "longitude": 76.975, "remainingCapacityKg": 150.0,
            "acceptedCategories": ["Cooked Meals"], "openRequirementKg": 36.0,
            "availableFrom": "00:00", "availableTo": "23:59"}
    base.update(override)
    return base


@pytest.fixture
def rank(client, headers):
    def _rank(candidates, donation=None, **extra):
        payload = {"donation": donation or DONATION, "candidates": candidates, **extra}
        res = client.post(URL, json=payload, headers=headers)
        assert res.status_code == 200, res.get_json()
        return res.get_json()
    return _rank


def ids(body):
    return [r["ngoId"] for r in body["recommendations"]]


def test_perfect_candidate_scores_100(rank):
    rec = rank([candidate()])["recommendations"][0]
    assert rec["rank"] == 1
    assert rec["matchScore"] == 100.0
    assert rec["distanceKm"] == 0.0
    assert rec["breakdown"] == {"distance": 30.0, "category": 25.0, "quantity": 15.0,
                                "capacity": 15.0, "expiry": 15.0}


def test_no_open_requirement_gets_general_points(rank):
    rec = rank([candidate(openRequirementKg=None)])["recommendations"][0]
    assert rec["breakdown"]["category"] == 10.0
    assert rec["breakdown"]["quantity"] == 7.5
    assert rec["matchScore"] == 77.5


def test_closer_ngo_ranks_first(rank):
    body = rank([candidate(ngoId=1, latitude=11.051), candidate(ngoId=2)])
    assert ids(body) == [2, 1]


def test_ngo_beyond_radius_is_excluded(rank):
    assert ids(rank([candidate(ngoId=1, latitude=11.601), candidate(ngoId=2)])) == [2]


def test_ngo_without_capacity_is_excluded_and_partial_capacity_scores_lower(rank):
    body = rank([candidate(ngoId=1, remainingCapacityKg=0), candidate(ngoId=2, remainingCapacityKg=18.0)])
    assert ids(body) == [2]
    assert body["recommendations"][0]["breakdown"]["capacity"] == 7.5


def test_operating_hours_are_respected(rank):
    donation = dict(DONATION, hoursToExpiry=3.0)  # arrival is 18:00
    body = rank([
        candidate(ngoId=1, availableFrom="08:00", availableTo="17:00"),
        candidate(ngoId=2, availableFrom="08:00", availableTo="21:00"),
        candidate(ngoId=3, availableFrom="22:00", availableTo="06:00"),
        candidate(ngoId=4, availableFrom=None, availableTo=None),
    ], donation=donation)
    assert ids(body) == [2, 4]


def test_ngo_that_cannot_arrive_before_expiry_is_excluded(rank):
    donation = dict(DONATION, hoursToExpiry=0.2)  # 12 minutes left
    body = rank([candidate(ngoId=1, latitude=11.101), candidate(ngoId=2)], donation=donation)
    assert ids(body) == [2]


def test_unaccepted_category_is_excluded(rank):
    body = rank([
        candidate(ngoId=1, acceptedCategories=["Dairy"]),
        candidate(ngoId=2, acceptedCategories=[]),
        candidate(ngoId=3, acceptedCategories=["cooked meals"]),
    ])
    assert ids(body) == [2, 3]


def test_top_n_limits_results(rank):
    body = rank([candidate(ngoId=i) for i in range(1, 5)], topN=2)
    assert [r["rank"] for r in body["recommendations"]] == [1, 2]


def test_ties_are_broken_by_ngo_id(rank):
    body = rank([candidate(ngoId=9), candidate(ngoId=3)])
    assert ids(body) == [3, 9]
    assert [r["rank"] for r in body["recommendations"]] == [1, 2]


def test_breakdown_sums_to_score_and_weights_are_reported(rank):
    body = rank([candidate(latitude=11.031, openRequirementKg=60.0, remainingCapacityKg=20.0)])
    rec = body["recommendations"][0]
    assert sum(rec["breakdown"].values()) == pytest.approx(rec["matchScore"])
    assert 0 < rec["matchScore"] < 100
    assert body["method"] == "weighted-scoring"
    assert body["weights"] == {"distance": 30, "category": 25, "quantity": 15, "capacity": 15, "expiry": 15}
    assert rec["explanation"]


def test_validation_errors_are_400(client, headers):
    res = client.post(URL, json={"candidates": [candidate(latitude=120)], "topN": 0}, headers=headers)
    assert res.status_code == 400
    details = res.get_json()["details"]
    assert {"donation", "candidates[0].latitude", "topN"} <= set(details)


def test_empty_candidate_list_gives_empty_result(rank):
    assert rank([])["recommendations"] == []