"""FoodLoop AI service (Flask). Stateless, no database access. Run: python app.py"""
import hmac
import os

from flask import Flask, jsonify, request
from werkzeug.exceptions import HTTPException

import config
from routes import analytics_routes, health_routes, model_routes, predict_routes, recommend_routes
from services.model_store import ModelNotLoaded, ModelStore
from utils.validation import ValidationError


def create_app(store=None, api_key=None):
    app = Flask(__name__)
    app.json.sort_keys = False
    app.config["API_KEY"] = config.API_KEY if api_key is None else api_key
    app.extensions["store"] = store if store is not None else ModelStore()

    for module in (health_routes, predict_routes, recommend_routes, analytics_routes, model_routes):
        app.register_blueprint(module.bp)

    @app.before_request
    def require_api_key():
        if request.path == "/health":
            return None
        expected = app.config["API_KEY"]
        supplied = request.headers.get("X-Internal-Api-Key", "")
        if not expected or not hmac.compare_digest(supplied.encode("utf-8"), expected.encode("utf-8")):
            return jsonify({"error": "UNAUTHORIZED"}), 401
        return None

    @app.errorhandler(ValidationError)
    def handle_validation(exc):
        return jsonify({"error": "VALIDATION_ERROR", "details": exc.details}), 400

    @app.errorhandler(ModelNotLoaded)
    def handle_not_loaded(exc):
        return jsonify({"error": "MODEL_NOT_LOADED"}), 503

    @app.errorhandler(HTTPException)
    def handle_http(exc):
        return jsonify({"error": exc.name.upper().replace(" ", "_")}), exc.code

    @app.errorhandler(Exception)
    def handle_unexpected(exc):
        app.logger.exception("Unhandled error")
        return jsonify({"error": "PREDICTION_FAILED"}), 500

    return app


if __name__ == "__main__":
    if not config.API_KEY:
        print("WARNING: AI_SERVICE_API_KEY is not set, every protected endpoint will answer 401.")
    create_app().run(host=os.environ.get("AI_SERVICE_HOST", "127.0.0.1"),
                     port=int(os.environ.get("AI_SERVICE_PORT", "5000")))