from flask import request

from utils.validation import ValidationError


def json_body():
    payload = request.get_json(silent=True)
    if not isinstance(payload, dict):
        raise ValidationError({"body": "must be a JSON object"})
    return payload