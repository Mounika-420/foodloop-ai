"""Shared constants for the FoodLoop AI service."""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
DATASET_DIR = BASE_DIR / "datasets"
MODEL_DIR = BASE_DIR / "models"
METRICS_FILE = MODEL_DIR / "metrics.json"

MODEL_VERSION = "1.0.0"
RANDOM_SEED = 42
SURPLUS_ROWS = 6000
DEMAND_ROWS = 5000

# Required in production; deliberately no default (same rule as JWT_SECRET in the Java backend).
API_KEY = os.environ.get("AI_SERVICE_API_KEY", "")

CATEGORIES = ["Cooked Meals", "Bakery", "Fruits & Vegetables", "Dairy", "Sweets"]
DONOR_TYPES = ["HOTEL", "RESTAURANT", "BAKERY", "SUPERMARKET", "CATERER"]
AREAS = ["RS Puram", "Race Course", "Gandhipuram", "Peelamedu",
         "Saibaba Colony", "Town Hall", "Ukkadam", "Singanallur"]
DAYS = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]
SEASONS = ["WINTER", "SUMMER", "MONSOON"]


def season_for_month(month: int) -> str:
    """Coimbatore-style seasons: Oct-Feb winter, Mar-May summer, Jun-Sep monsoon."""
    if month in (10, 11, 12, 1, 2):
        return "WINTER"
    if month in (3, 4, 5):
        return "SUMMER"
    return "MONSOON"