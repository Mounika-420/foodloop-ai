# Data dictionary (SYNTHETIC data)

Both CSV files are **generated** by `generate_dataset.py` with a fixed random seed. They contain no real donor,
NGO or donation records. Model metrics computed on them describe how well a model learns these generated rules,
not real-world accuracy. Every API response therefore carries `"trainedOnSyntheticData": true`.

## synthetic_donations.csv  (target: `surplusKg`)
| Column | Type | Meaning |
|---|---|---|
| category | text | Food category (Cooked Meals, Bakery, Fruits & Vegetables, Dairy, Sweets) |
| donorType | text | HOTEL, RESTAURANT, BAKERY, SUPERMARKET, CATERER |
| dayOfWeek | text | MON..SUN |
| month | int | 1-12 |
| season | text | WINTER (Oct-Feb), SUMMER (Mar-May), MONSOON (Jun-Sep) |
| eventFlag | 0/1 | 1 when the donor hosted an event (about 12 percent of rows) |
| previousQuantityKg | float | Surplus of the donor's previous comparable donation |
| area | text | Coimbatore locality |
| surplusKg | float | **Target.** Surplus food quantity in kg |

Generation rule: donor-type base quantity x category factor x weekday factor (weekends higher) x season factor
x area factor x festival-month factor x 1.6 when eventFlag = 1, blended 65/35 with the previous quantity,
plus 8 percent Gaussian noise. Bakery donors donate Bakery items 80 percent of the time.

## synthetic_demand.csv  (target: `demandKg`)
| Column | Type | Meaning |
|---|---|---|
| category | text | Food category |
| area | text | Coimbatore locality |
| dayOfWeek | text | MON..SUN |
| month | int | 1-12 |
| hourOfDay | int | 6-22 |
| demandKg | float | **Target.** Kilograms of food the area's NGOs could use in that slot |

Generation rule: area base demand x category factor x meal-time curve (peaks near 13:00 and 19:30)
x weekend factor x festival-month factor, plus 10 percent Gaussian noise.