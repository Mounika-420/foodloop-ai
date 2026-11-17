from flask import Blueprint, jsonify

from services import analytics_service
from utils.http import json_body

bp = Blueprint("analytics", __name__, url_prefix="/api/v1/analytics")


@bp.post("/waste-patterns")
def waste_patterns():
    return jsonify(analytics_service.analyze(json_body()))