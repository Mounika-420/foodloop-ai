from flask import Blueprint, current_app, jsonify

from services import demand_service, surplus_service
from utils.http import json_body

bp = Blueprint("predict", __name__, url_prefix="/api/v1/predict")


@bp.post("/surplus")
def surplus():
    return jsonify(surplus_service.predict(current_app.extensions["store"], json_body()))


@bp.post("/demand")
def demand():
    return jsonify(demand_service.predict(current_app.extensions["store"], json_body()))