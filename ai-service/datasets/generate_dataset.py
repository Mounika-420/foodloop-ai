"""Generate the SYNTHETIC training data for the surplus and demand models.

No real donation data is used anywhere. The numbers follow simple, documented rules
(see DATA_DICTIONARY.md) plus random noise, with a fixed seed so results are reproducible.
Run:  python datasets/generate_dataset.py
"""
import sys
from pathlib import Path

import numpy as np
import pandas as pd

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import config  # noqa: E402

DONOR_BASE_KG = {"HOTEL": 28, "RESTAURANT": 16, "BAKERY": 11, "SUPERMARKET": 32, "CATERER": 45}
CATEGORY_SURPLUS_FACTOR = {"Cooked Meals": 1.0, "Bakery": 0.55, "Fruits & Vegetables": 0.9,
                           "Dairy": 0.45, "Sweets": 0.35}
DAY_SURPLUS_FACTOR = {"MON": 0.9, "TUE": 0.9, "WED": 0.95, "THU": 1.0, "FRI": 1.15, "SAT": 1.3, "SUN": 1.25}
SEASON_SURPLUS_FACTOR = {"WINTER": 1.0, "SUMMER": 0.95, "MONSOON": 0.9}
AREA_SURPLUS_FACTOR = {"RS Puram": 1.15, "Race Course": 1.1, "Gandhipuram": 1.1, "Peelamedu": 1.0,
                       "Saibaba Colony": 0.95, "Town Hall": 1.05, "Ukkadam": 0.9, "Singanallur": 0.85}
FESTIVAL_MONTH_FACTOR = {10: 1.15, 11: 1.2, 12: 1.1, 1: 1.1, 4: 1.05}

AREA_DEMAND_BASE = {"Ukkadam": 52, "Town Hall": 48, "Gandhipuram": 44, "Singanallur": 44,
                    "Peelamedu": 40, "RS Puram": 32, "Saibaba Colony": 32, "Race Course": 28}
CATEGORY_DEMAND_FACTOR = {"Cooked Meals": 1.0, "Bakery": 0.5, "Fruits & Vegetables": 0.6,
                          "Dairy": 0.4, "Sweets": 0.2}


def generate_surplus(n: int, rng: np.random.Generator) -> pd.DataFrame:
    donor_type = rng.choice(config.DONOR_TYPES, size=n, p=[0.25, 0.30, 0.15, 0.15, 0.15])
    category = rng.choice(config.CATEGORIES, size=n, p=[0.45, 0.15, 0.15, 0.10, 0.15])
    bakery_donor = (donor_type == "BAKERY") & (rng.random(n) < 0.8)
    category = np.where(bakery_donor, "Bakery", category)

    df = pd.DataFrame({
        "category": category,
        "donorType": donor_type,
        "dayOfWeek": rng.choice(config.DAYS, size=n),
        "month": rng.integers(1, 13, size=n),
        "area": rng.choice(config.AREAS, size=n),
        "eventFlag": (rng.random(n) < 0.12).astype(int),
    })
    df["season"] = df["month"].map(config.season_for_month)

    structural = (
        df["donorType"].map(DONOR_BASE_KG)
        * df["category"].map(CATEGORY_SURPLUS_FACTOR)
        * df["dayOfWeek"].map(DAY_SURPLUS_FACTOR)
        * df["season"].map(SEASON_SURPLUS_FACTOR)
        * df["area"].map(AREA_SURPLUS_FACTOR)
        * df["month"].map(lambda m: FESTIVAL_MONTH_FACTOR.get(m, 1.0))
        * np.where(df["eventFlag"] == 1, 1.6, 1.0)
    )
    previous = structural * rng.lognormal(mean=0.0, sigma=0.18, size=n)
    target = (0.65 * structural + 0.35 * previous) * (1 + rng.normal(0.0, 0.08, size=n))

    df["previousQuantityKg"] = previous.round(2)
    df["surplusKg"] = np.clip(target, 0.5, None).round(2)
    return df[["category", "donorType", "dayOfWeek", "month", "season", "eventFlag",
               "previousQuantityKg", "area", "surplusKg"]]


def generate_demand(n: int, rng: np.random.Generator) -> pd.DataFrame:
    df = pd.DataFrame({
        "category": rng.choice(config.CATEGORIES, size=n, p=[0.45, 0.15, 0.15, 0.10, 0.15]),
        "area": rng.choice(config.AREAS, size=n),
        "dayOfWeek": rng.choice(config.DAYS, size=n),
        "month": rng.integers(1, 13, size=n),
        "hourOfDay": rng.integers(6, 23, size=n),
    })
    hour = df["hourOfDay"].to_numpy(dtype=float)
    hour_factor = (0.4
                   + 0.9 * np.exp(-((hour - 13.0) / 2.0) ** 2)
                   + 1.1 * np.exp(-((hour - 19.5) / 2.0) ** 2))
    weekend = np.where(df["dayOfWeek"].isin(["SAT", "SUN"]), 1.2, 1.0)
    structural = (
        df["area"].map(AREA_DEMAND_BASE)
        * df["category"].map(CATEGORY_DEMAND_FACTOR)
        * hour_factor
        * weekend
        * df["month"].map(lambda m: FESTIVAL_MONTH_FACTOR.get(m, 1.0))
    )
    target = structural * (1 + rng.normal(0.0, 0.10, size=n))
    df["demandKg"] = np.clip(target, 0.5, None).round(2)
    return df


def main() -> None:
    rng = np.random.default_rng(config.RANDOM_SEED)
    config.DATASET_DIR.mkdir(parents=True, exist_ok=True)

    surplus = generate_surplus(config.SURPLUS_ROWS, rng)
    surplus.to_csv(config.DATASET_DIR / "synthetic_donations.csv", index=False)
    demand = generate_demand(config.DEMAND_ROWS, rng)
    demand.to_csv(config.DATASET_DIR / "synthetic_demand.csv", index=False)

    print(f"synthetic_donations.csv: {len(surplus)} rows, surplusKg mean {surplus['surplusKg'].mean():.2f}")
    print(f"synthetic_demand.csv:    {len(demand)} rows, demandKg mean {demand['demandKg'].mean():.2f}")
    print("All data is SYNTHETIC (seed %d)." % config.RANDOM_SEED)


if __name__ == "__main__":
    main()