"""
LifeOS Finance — Common Schema Utilities
"""

from decimal import Decimal
from typing import Annotated, Any
from pydantic import BaseModel, PlainSerializer, field_serializer

# Serializer ensuring Decimal/Numeric is always output as a JSON float/number
DecimalFloat = Annotated[
    Decimal | float,
    PlainSerializer(lambda v: float(v) if v is not None else 0.0, return_type=float, when_used="json"),
]

OptionalDecimalFloat = Annotated[
    Decimal | float | None,
    PlainSerializer(lambda v: float(v) if v is not None else None, return_type=float | None, when_used="json"),
]


class ContractBaseModel(BaseModel):
    """Base schema that guarantees any Decimal value serializes to JSON number (float)."""

    @field_serializer("*", mode="plain", check_fields=False)
    def serialize_decimals_to_float(self, v: Any) -> Any:
        if isinstance(v, Decimal):
            return float(v)
        return v
