from services.model_store import ModelNotLoaded


class FakeStore:
    """Stands in for ModelStore so route tests do not need trained models."""
    version = "9.9.9"

    def __init__(self, surplus=True, demand=True):
        self._loaded = {"surplus": surplus, "demand": demand}
        self._values = {"surplus": 19.44, "demand": 33.33}
        self.last_row = {}

    def loaded(self):
        return dict(self._loaded)

    def predict(self, name, row):
        if not self._loaded[name]:
            raise ModelNotLoaded(name)
        self.last_row[name] = dict(row)
        return self._values[name]

    def describe(self, name):
        if not self._loaded[name]:
            return None
        return {"name": name, "algorithm": "FakeRegressor", "version": self.version,
                "trainedAt": "2026-11-17T00:00:00+00:00",
                "metrics": {"mae": 1.5, "rmse": 2.0, "r2": 0.9},
                "dataset": "synthetic", "trainRows": 10, "testRows": 5, "comparison": []}