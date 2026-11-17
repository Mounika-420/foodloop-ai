"""Train, compare and save the surplus and demand models.

Run:  python train_model.py
Writes models/surplus_model.joblib, models/demand_model.joblib and models/metrics.json.
All numbers in metrics.json are measured here on a held-out test split; nothing is typed by hand.
"""
import json
import sys
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder

import config

SPECS = [
    {"name": "surplus", "csv": "synthetic_donations.csv", "target": "surplusKg",
     "categorical": ["category", "donorType", "dayOfWeek", "season", "area"],
     "numeric": ["month", "eventFlag", "previousQuantityKg"]},
    {"name": "demand", "csv": "synthetic_demand.csv", "target": "demandKg",
     "categorical": ["category", "area", "dayOfWeek"],
     "numeric": ["month", "hourOfDay"]},
]


def candidates():
    return [
        LinearRegression(),
        RandomForestRegressor(n_estimators=120, min_samples_leaf=3, random_state=config.RANDOM_SEED, n_jobs=-1),
        GradientBoostingRegressor(random_state=config.RANDOM_SEED),
    ]


def build_pipeline(estimator, categorical, numeric):
    prep = ColumnTransformer([
        ("cat", OneHotEncoder(handle_unknown="ignore", sparse_output=False), categorical),
        ("num", "passthrough", numeric),
    ])
    return Pipeline([("prep", prep), ("model", estimator)])


def train_one(spec):
    path = config.DATASET_DIR / spec["csv"]
    if not path.exists():
        sys.exit(f"Missing {path}. Run: python datasets/generate_dataset.py")

    df = pd.read_csv(path)
    features = spec["categorical"] + spec["numeric"]
    x_train, x_test, y_train, y_test = train_test_split(
        df[features], df[spec["target"]], test_size=0.2, random_state=config.RANDOM_SEED)

    results = []
    best = None
    for estimator in candidates():
        pipeline = build_pipeline(estimator, spec["categorical"], spec["numeric"])
        pipeline.fit(x_train, y_train)
        pred = pipeline.predict(x_test)
        metrics = {
            "mae": round(float(mean_absolute_error(y_test, pred)), 4),
            "rmse": round(float(np.sqrt(mean_squared_error(y_test, pred))), 4),
            "r2": round(float(r2_score(y_test, pred)), 4),
        }
        row = {"algorithm": type(estimator).__name__, **metrics}
        results.append(row)
        if best is None or metrics["mae"] < best["metrics"]["mae"]:
            best = {"algorithm": row["algorithm"], "metrics": metrics, "pipeline": pipeline}

    config.MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(best["pipeline"], config.MODEL_DIR / f"{spec['name']}_model.joblib", compress=3)

    print(f"\n{spec['name'].upper()}  ({len(x_train)} train / {len(x_test)} test rows)")
    print(f"  {'algorithm':<28}{'MAE':>9}{'RMSE':>9}{'R2':>9}")
    for r in results:
        mark = "  <- selected" if r["algorithm"] == best["algorithm"] else ""
        print(f"  {r['algorithm']:<28}{r['mae']:>9.3f}{r['rmse']:>9.3f}{r['r2']:>9.3f}{mark}")

    return {
        "algorithm": best["algorithm"],
        "metrics": best["metrics"],
        "comparison": results,
        "trainRows": int(len(x_train)),
        "testRows": int(len(x_test)),
        "featureColumns": {"categorical": spec["categorical"], "numeric": spec["numeric"]},
        "target": spec["target"],
        "dataset": "synthetic",
    }


def main():
    report = {
        "modelVersion": config.MODEL_VERSION,
        "trainedAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "randomSeed": config.RANDOM_SEED,
        "dataset": "synthetic",
        "models": {spec["name"]: train_one(spec) for spec in SPECS},
    }
    config.METRICS_FILE.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"\nSaved models and {config.METRICS_FILE.name} in {config.MODEL_DIR}")


if __name__ == "__main__":
    main()