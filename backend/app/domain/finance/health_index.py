"""
LifeOS Finance — Pure Financial Health Index Engine
Pure mathematical functions without database or framework dependencies.
Implements the continuous Health Index H according to docs/health_index.md.
"""

from dataclasses import dataclass
from typing import Dict, Optional
import numpy as np


@dataclass(frozen=True)
class HealthIndexBreakdown:
    """Detailed sub-scores and intermediate metrics."""
    liquidity_score: float      # L in [0, 1]
    savings_score: float        # S in [0, 1]
    debt_score: float           # D in [0, 1] (inverted sub-score)
    normalized_weights: Dict[str, float]
    composite_index: float      # H in [0, 100]


DEFAULT_WEIGHTS = {
    "liquidity": 0.4,
    "savings": 0.4,
    "debt": 0.2,
}

DEFAULT_THRESHOLDS = {
    "liquidity_months_target": 6.0,   # 6 months of expenses covered gives L = 1.0
    "savings_rate_target": 0.20,       # 20% savings rate gives S = 1.0
    "debt_ratio_ceiling": 0.36,        # 36% debt-to-income gives D = 0.0
}


def clamp(val: float, low: float = 0.0, high: float = 1.0) -> float:
    """Clamp value between low and high bounds."""
    return float(np.clip(val, low, high))


def calculate_health_index(
    liquidity_months: Optional[float],
    savings_rate: Optional[float],
    debt_to_income_ratio: Optional[float],
    weights: Optional[Dict[str, float]] = None,
    thresholds: Optional[Dict[str, float]] = None,
) -> Optional[HealthIndexBreakdown]:
    """
    Calculate continuous Financial Health Index (H).
    
    Formula:
      H = 100 * (w1 * L + w2 * S + w3 * D)
    
    Returns None if essential inputs are missing (insufficient data).
    """
    # 1. Validation: Return None if essential data is not available
    if liquidity_months is None or savings_rate is None or debt_to_income_ratio is None:
        return None

    # Protect against NaN or negative infinity
    if np.isnan(liquidity_months) or np.isnan(savings_rate) or np.isnan(debt_to_income_ratio):
        return None

    thresh = DEFAULT_THRESHOLDS.copy()
    if thresholds:
        thresh.update(thresholds)

    w = DEFAULT_WEIGHTS.copy()
    if weights:
        w.update(weights)

    # 2. Renormalize weights to sum to 1.0
    total_w = sum(w.values())
    if total_w <= 0:
        raise ValueError("Sum of weights must be positive")
    norm_weights = {k: v / total_w for k, v in w.items()}

    # 3. Compute Sub-scores clamped to [0, 1]
    # L (Liquidity): months of expenses / target (default 6 months)
    l_target = thresh.get("liquidity_months_target", 6.0)
    L = clamp(liquidity_months / l_target) if l_target > 0 else 1.0

    # S (Savings Rate): savings rate / target (default 0.20)
    s_target = thresh.get("savings_rate_target", 0.20)
    S = clamp(savings_rate / s_target) if s_target > 0 else 1.0

    # D (Debt-to-Income): Inverted sub-score. 1 - clamp(ratio / 0.36)
    d_ceiling = thresh.get("debt_ratio_ceiling", 0.36)
    D = 1.0 - clamp(debt_to_income_ratio / d_ceiling) if d_ceiling > 0 else 0.0

    # 4. Composite H in [0, 100]
    H = 100.0 * (
        norm_weights["liquidity"] * L
        + norm_weights["savings"] * S
        + norm_weights["debt"] * D
    )

    H_clamped = clamp(H, 0.0, 100.0)

    return HealthIndexBreakdown(
        liquidity_score=round(float(L), 4),
        savings_score=round(float(S), 4),
        debt_score=round(float(D), 4),
        normalized_weights={k: round(v, 4) for k, v in norm_weights.items()},
        composite_index=round(float(H_clamped), 2),
    )
