"""Tiny request validator that reports every problem at once (field path -> message)."""
import copy
import math
from datetime import datetime, time


class ValidationError(Exception):
    def __init__(self, details):
        super().__init__("validation failed")
        self.details = details


def _check_range(value, rule):
    low, high = rule.get("min"), rule.get("max")
    if low is not None and value < low:
        raise ValueError(f"must be >= {low}")
    if high is not None and value > high:
        raise ValueError(f"must be <= {high}")


def _str(value, rule):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("must be a non-empty string")
    return value.strip()


def _enum(value, rule):
    choices = rule["choices"]
    if not isinstance(value, str) or value.strip().upper() not in choices:
        raise ValueError("must be one of " + ", ".join(choices))
    return value.strip().upper()


def _float(value, rule):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError("must be a number")
    _check_range(value, rule)
    return float(value)


def _int(value, rule):
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError("must be an integer")
    _check_range(value, rule)
    return value


def _bool(value, rule):
    if not isinstance(value, bool):
        raise ValueError("must be true or false")
    return value


def _time(value, rule):
    if not isinstance(value, str):
        raise ValueError("must be a time like 08:00")
    try:
        return time.fromisoformat(value)
    except ValueError:
        raise ValueError("must be a time like 08:00") from None


def _datetime(value, rule):
    if not isinstance(value, str):
        raise ValueError("must be an ISO-8601 date-time")
    try:
        parsed = datetime.fromisoformat(value)
    except ValueError:
        raise ValueError("must be an ISO-8601 date-time") from None
    return parsed.replace(tzinfo=None)


def _str_list(value, rule):
    if not isinstance(value, list) or any(not isinstance(v, str) or not v.strip() for v in value):
        raise ValueError("must be a list of non-empty strings")
    return [v.strip() for v in value]


_CONVERTERS = {"str": _str, "enum": _enum, "float": _float, "int": _int, "bool": _bool,
               "time": _time, "datetime": _datetime, "str_list": _str_list}


def parse(payload, spec, prefix=""):
    """Validate payload against spec. Optional fields may carry a "default". Raises ValidationError."""
    errors = {}
    out = {}
    for key, rule in spec.items():
        path = prefix + key
        if key not in payload or payload[key] is None:
            if rule.get("required", True):
                errors[path] = "is required"
            elif "default" in rule:
                out[key] = copy.copy(rule["default"])
            continue
        try:
            out[key] = _CONVERTERS[rule["type"]](payload[key], rule)
        except ValueError as exc:
            errors[path] = str(exc)
    if errors:
        raise ValidationError(errors)
    return out