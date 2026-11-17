from flask import Blueprint, current_app, jsonify

bp = Blueprint("health", __name__)


@bp.get("/health")
def health():
    store = current_app.extensions["store"]
    return jsonify({
        "status": "UP",
        "modelsLoaded": store.loaded(),
        "modelVersion": store.version,
        "trainedOnSyntheticData": True,
    })