from typing import List, Optional
from .models import ForecastQuality

def calculate_mad_ratio(values: List[int]) -> float:
    if not values:
        return 0.0
    mean_val = sum(values) / len(values)
    mad = sum(abs(x - mean_val) for x in values) / len(values)
    
    if mean_val == 0:
        return 0.0 if mad == 0 else 1.0  # Force > 0.5 for LOW quality
    
    return mad / abs(mean_val)

def determine_quality(values: List[int]) -> ForecastQuality:
    if not values:
        return ForecastQuality.INSUFFICIENT_DATA
    
    mean_val = sum(values) / len(values)
    mad = sum(abs(x - mean_val) for x in values) / len(values)
    
    if mean_val == 0:
        if mad == 0:
            return ForecastQuality.HIGH
        else:
            return ForecastQuality.LOW
            
    mad_ratio = mad / abs(mean_val)
    
    if mad_ratio < 0.2:
        return ForecastQuality.HIGH
    elif mad_ratio <= 0.5:
        return ForecastQuality.MEDIUM
    else:
        return ForecastQuality.LOW

def calculate_ewma(values: List[int], alpha_scaled: int = 300, scale: int = 1000) -> Optional[int]:
    """
    Calculates EWMA using fixed-point integer math.
    Minimum data: 3 items required to initialize SMA.
    """
    if len(values) < 3:
        return None
        
    # Initialize with SMA of first 3 items
    ewma = sum(values[:3]) // 3
    
    for x in values[3:]:
        numerator = alpha_scaled * x + (scale - alpha_scaled) * ewma
        ewma = (numerator + scale // 2) // scale
        
    return ewma
