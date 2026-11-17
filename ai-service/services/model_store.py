"""Loads the trained pipelines and metrics.json once at startup."""
import json
import logging
from pathlib import Path

import joblib
import pandas as pd

import config

log = logging.getLogger(__name__)
NAMES = ("surplus", "demand")


class ModelNotLoaded(Exception):
    def __init__(self, name):
        super().__init__(f"model '{name}' is not loaded")
        self.model_name = name


class ModelStore:
    def __init__(self, model_dir=None, metrics_file=None):
        self.model_dir = Path(model_dir or config.MODEL_DIR)
        self.metrics_file = Path(metrics_file or config.METRICS_FILE)
        self._report = {}
        self._models = {}
        self._load()

    def _load(self):
        if self.metrics_file.exists():
            try:
                self._report = json.loads(self.metrics_file.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                log.exception("Could not read %s", self.metrics_file)
        for name in NAMES:
            path = self.model_dir / f"{name}_model.joblib"
            entry = self._report.get("models", {}).get(name)
            if not path.exists() or entry is None:
                log.warning("Model '%s' not loaded (run train_model.py)", name)
                continue
            try:
                self._models[name] = joblib.load(path)
            except Exception:  # corrupted / incompatible file must not stop the service starting
                log.exception("Could not load model '%s'", name)

    @property
    def version(self):
        return self._report.get("modelVersion", config.MODEL_VERSION)

    def loaded(self):
        return {name: name in self._models for name in NAMES}

    def predict(self, name, row):
        model = self._models.get(name)
        if model is None:
            raise ModelNotLoaded(name)
        columns = self._report["models"][name]["featureColumns"]
        ordered = columns["categorical"] + columns["numeric"]
        return float(model.predict(pd.DataFrame([row], columns=ordered))[0])

    def describe(self, name):
        entry = self._report.get("models", {}).get(name)
        if entry is None or name not in self._models:
            return None
        return {
            "name": name,
            "algorithm": entry["algorithm"],
            "version": self.version,
            "trainedAt": self._report.get("trainedAt"),
            "metrics": entry["metrics"],
            "dataset": entry.get("dataset", "synthetic"),
            "trainRows": entry.get("trainRows"),
            "testRows": entry.get("testRows"),
            "comparison": entry.get("comparison", []),
        }