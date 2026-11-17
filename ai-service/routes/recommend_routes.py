from flask import Blueprint, jsonify

from services import matching_service
from utils.http import json_body

bp = Blueprint("recommend", __name__, url_prefix="/api/v1/recommend")


@bp.post("/ngos")
def ngos():
    return jsonify(matching_service.recommend(json_body()))