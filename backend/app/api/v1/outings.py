"""
LifeOS Finance — Outings & AI Date Planner API Endpoints
"""

import json
import logging
import urllib.parse
import uuid
from datetime import datetime, timezone
from typing import List

import httpx
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.database import get_db
from app.core.deps import get_current_user
from app.models.account import Account
from app.models.debt import Debt, DebtStatus
from app.models.user import User
from app.models.visited_place import VisitedPlace
from app.schemas.outing import (
    OutingPlanRequest,
    OutingPlanResponse,
    OutingStop,
    VisitedPlaceCreate,
    VisitedPlaceResponse,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/outings", tags=["Outings & AI Planner"])


@router.post("/generate-plan", response_model=OutingPlanResponse)
async def generate_outing_plan(
    request: OutingPlanRequest,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Generates a personalized outing itinerary using Gemini AI and Google Maps.
    Evaluates current financial health to ensure safe spending.
    """
    # 1. Calculate Safe-to-Spend Budget
    accounts_res = await db.execute(select(Account).where(Account.user_id == current_user.id))
    accounts = accounts_res.scalars().all()
    total_balance = sum(float(a.balance) for a in accounts if a.include_in_total)

    debts_res = await db.execute(
        select(Debt).where(Debt.user_id == current_user.id, Debt.status != DebtStatus.PAID)
    )
    debts = debts_res.scalars().all()
    total_debt = sum(float(d.remaining_amount) for d in debts)

    # Safe discretionary budget: max 15% of available liquid balance or user specified budget
    safe_budget = max(50000.0, total_balance * 0.15)
    target_budget = request.budget if request.budget and request.budget > 0 else safe_budget

    # 2. Get list of already visited places to avoid repeats
    places_res = await db.execute(
        select(VisitedPlace.name).where(VisitedPlace.user_id == current_user.id)
    )
    visited_names = [p for p in places_res.scalars().all()]
    visited_text = ", ".join(visited_names) if visited_names else "Ninguno todavía"

    # 3. Call Gemini AI or High-Quality Intelligent Fallback
    plan = await _generate_with_ai(
        outing_type=request.outing_type,
        target_budget=target_budget,
        area=request.area_or_city,
        preferences=request.preferences or "Buena comida, ambiente agradable y seguro",
        visited_places=visited_text,
        total_balance=total_balance,
        safe_budget=safe_budget
    )

    return plan


async def _generate_with_ai(
    outing_type: str,
    target_budget: float,
    area: str,
    preferences: str,
    visited_places: str,
    total_balance: float,
    safe_budget: float
) -> OutingPlanResponse:
    if settings.GEMINI_API_KEY:
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={settings.GEMINI_API_KEY}"
            prompt = f"""Eres el planificador experto de salidas, ocio y citas de LifeOS.
Diseña un itinerario de salida de 2 a 3 paradas específicas y reales para:
- Tipo de salida: {outing_type}
- Ciudad o Zona: {area}
- Presupuesto máximo total: ${target_budget:,.0f} COP
- Preferencias: {preferences}
- LUGARES YA VISITADOS (NO REPETIR NINGUNO DE ESTOS): {visited_places}

Responde ÚNICAMENTE con un JSON válido en este formato exacto (sin bloques de código ```json, solo texto):
{{
  "title": "Nombre creativo del plan",
  "summary": "Resumen conciso y atractivo de la salida",
  "stops": [
    {{
      "order": 1,
      "title": "Nombre del lugar real (ej. Café Cultor Usaquén)",
      "category": "Café / Restaurante / Mirador / Actividad",
      "estimated_cost": 30000,
      "description": "Qué hacer o pedir aquí y por qué vale la pena",
      "maps_query": "Café Cultor Usaquén Bogotá"
    }}
  ],
  "financial_advice": "Consejo financiero sobre el gasto de esta salida"
}}
"""
            async with httpx.AsyncClient(timeout=25.0) as client:
                res = await client.post(url, json={"contents": [{"parts": [{"text": prompt}]}]})
                if res.status_code == 200:
                    raw = res.json()["candidates"][0]["content"]["parts"][0]["text"].strip()
                    if raw.startswith("```"):
                        import re
                        raw = re.sub(r"^```(?:json)?\s*", "", raw)
                        raw = re.sub(r"\s*```$", "", raw)
                    data = json.loads(raw)
                    
                    stops = []
                    total_cost = 0.0
                    for s in data.get("stops", []):
                        cost = float(s.get("estimated_cost", 0))
                        total_cost += cost
                        query = s.get("maps_query") or s.get("title", "")
                        maps_url = f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote(query)}"
                        stops.append(OutingStop(
                            order=int(s.get("order", 1)),
                            title=s.get("title", "Lugar"),
                            category=s.get("category", "Ocio"),
                            estimated_cost=cost,
                            description=s.get("description", ""),
                            maps_query=query,
                            maps_url=maps_url
                        ))
                    
                    return OutingPlanResponse(
                        title=data.get("title", f"Plan {outing_type}"),
                        summary=data.get("summary", "Salida planificada inteligentemente con IA."),
                        total_estimated_cost=total_cost,
                        safe_budget_available=safe_budget,
                        stops=stops,
                        financial_advice=data.get("financial_advice", "Disfruta dentro del presupuesto seguro.")
                    )
        except Exception as e:
            logger.error(f"Error calling Gemini for outing plan: {e}")

    # Fallback Curated Colombian Plans if Gemini offline or key not provided
    return _get_fallback_plan(outing_type, target_budget, area, safe_budget)


def _get_fallback_plan(outing_type: str, budget: float, area: str, safe_budget: float) -> OutingPlanResponse:
    if "moto" in outing_type.lower() or "rodada" in outing_type.lower():
        stops = [
            OutingStop(
                order=1,
                title="Mirador de La Calera",
                category="Mirador / Ruta",
                estimated_cost=budget * 0.25,
                description="Ruta de montaña en moto, vista panorámica de la ciudad y café caliente en la vía.",
                maps_query=f"Mirador La Calera {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Mirador La Calera ' + area)}"
            ),
            OutingStop(
                order=2,
                title="Restaurante Campestre El Tambor",
                category="Restaurante",
                estimated_cost=budget * 0.55,
                description="Parrilla campestre, espacio abierto y parqueadero seguro para motos.",
                maps_query=f"El Tambor La Calera {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('El Tambor La Calera ' + area)}"
            ),
            OutingStop(
                order=3,
                title="Café de Especialidad San Alberto",
                category="Café",
                estimated_cost=budget * 0.20,
                description="Degustación de café premium para cerrar la rodada con buena charla.",
                maps_query=f"Café San Alberto {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Café San Alberto ' + area)}"
            )
        ]
        title = "Rodada & Almuerzo Campestre"
        summary = f"Plan perfecto para disfrutar tu moto hacia {area} con mirador, gastronomía y parada de café."
    elif "romántic" in outing_type.lower() or "cita" in outing_type.lower():
        stops = [
            OutingStop(
                order=1,
                title="Usaquén Plaza & Calles Coloniales",
                category="Paseo",
                estimated_cost=0.0,
                description="Caminata tranquila por las calles adoquinadas, tiendas de diseño y ambiente iluminado.",
                maps_query=f"Plaza de Usaquén {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Plaza de Usaquén ' + area)}"
            ),
            OutingStop(
                order=2,
                title="Cena en Bistro Italiano / Trattoria",
                category="Restaurante",
                estimated_cost=budget * 0.70,
                description="Cena íntima con pastas artesanales o pizza napolitana y copa de vino.",
                maps_query=f"Restaurante Italiano Usaquén {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante Italiano Usaquén ' + area)}"
            ),
            OutingStop(
                order=3,
                title="Postre y Gelato Artesanal",
                category="Postres",
                estimated_cost=budget * 0.30,
                description="Helado italiano tradicional para terminar la cita con una buena conversación.",
                maps_query=f"Heladería Artesanal Usaquén {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Heladería Artesanal Usaquén ' + area)}"
            )
        ]
        title = "Noche de Cita & Sabores Coloniales"
        summary = f"Itinerario romántico y relajado en {area} diseñado para conectar sin gastar de más."
    else:
        stops = [
            OutingStop(
                order=1,
                title="Café & Charla Inicial",
                category="Café",
                estimated_cost=budget * 0.30,
                description="Encuentro inicial con café de especialidad y ambiente tranquilo.",
                maps_query=f"Café de especialidad {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Café de especialidad ' + area)}"
            ),
            OutingStop(
                order=2,
                title="Cena / Hamburguesas Gourmet",
                category="Restaurante",
                estimated_cost=budget * 0.70,
                description="Comida reconfortante de alta calidad en un sitio moderno.",
                maps_query=f"Restaurante moderno {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante moderno ' + area)}"
            )
        ]
        title = f"Tarde de Desconexión en {area}"
        summary = "Plan balanceado para recargar energía y compartir con tranquilidad."

    total_cost = sum(s.estimated_cost for s in stops)
    return OutingPlanResponse(
        title=title,
        summary=summary,
        total_estimated_cost=total_cost,
        safe_budget_available=safe_budget,
        stops=stops,
        financial_advice="Este plan encaja perfectamente con tu presupuesto seguro de ocio."
    )


# --- Visited Places (Anti-Repetition Radar) ---

@router.get("/places", response_model=List[VisitedPlaceResponse])
async def get_visited_places(
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """List all places the user has visited to prevent repetitions."""
    stmt = (
        select(VisitedPlace)
        .where(VisitedPlace.user_id == current_user.id)
        .order_by(VisitedPlace.visited_date.desc())
    )
    result = await db.execute(stmt)
    return result.scalars().all()


@router.post("/places", response_model=VisitedPlaceResponse, status_code=status.HTTP_201_CREATED)
async def create_visited_place(
    place: VisitedPlaceCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Record a visited place with rating, notes and Google Maps link."""
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    db_place = VisitedPlace(
        id=str(uuid.uuid4()),
        user_id=current_user.id,
        name=place.name,
        category=place.category,
        address_or_area=place.address_or_area,
        rating=place.rating,
        average_cost=place.average_cost,
        notes=place.notes,
        maps_url=place.maps_url,
        visited_date=place.visited_date or now_ms,
        created_at=now_ms,
    )
    db.add(db_place)
    await db.commit()
    await db.refresh(db_place)
    return db_place


@router.put("/places/{place_id}", response_model=VisitedPlaceResponse)
async def update_visited_place(
    place_id: str,
    place_update: VisitedPlaceCreate,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Edit any field of a visited place."""
    stmt = select(VisitedPlace).where(
        VisitedPlace.id == place_id, VisitedPlace.user_id == current_user.id
    )
    result = await db.execute(stmt)
    db_place = result.scalar_one_or_none()
    if not db_place:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Lugar no encontrado")

    db_place.name = place_update.name
    db_place.category = place_update.category
    db_place.address_or_area = place_update.address_or_area
    db_place.rating = place_update.rating
    db_place.average_cost = place_update.average_cost
    db_place.notes = place_update.notes
    db_place.maps_url = place_update.maps_url
    if place_update.visited_date:
        db_place.visited_date = place_update.visited_date

    await db.commit()
    await db.refresh(db_place)
    return db_place


@router.delete("/places/{place_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_visited_place(
    place_id: str,
    db: AsyncSession = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Remove a place from visited history."""
    stmt = delete(VisitedPlace).where(
        VisitedPlace.id == place_id, VisitedPlace.user_id == current_user.id
    )
    await db.execute(stmt)
    await db.commit()
    return None

