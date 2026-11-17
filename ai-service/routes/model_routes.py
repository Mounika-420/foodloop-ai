from flask import Blueprint, current_app, jsonify

from services.model_store import NAMES

bp = Blueprint("models", __name__, url_prefix="/api/v1/models")


@bp.get("/info")
def info():
    store = current_app.extensions["store"]
    models = [d for d in (store.describe(name) for name in NAMES) if d is not None]
    return jsonify({"models": models, "modelVersion": store.version, "trainedOnSyntheticData": True})