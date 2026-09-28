from fastapi import APIRouter
from typing import Dict, Any

router = APIRouter()

@router.post("/impact")
def simulate_impact():
    return {"message": "Simulation endpoints are implemented in domain layer for testing"}
