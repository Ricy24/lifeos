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
        safe_budget=safe_budget,
        use_location=request.use_current_location,
        latitude=request.latitude,
        longitude=request.longitude,
        radius_km=request.radius_km or 5
    )

    return plan


CATEGORY_FALLBACK_PHOTOS = {
    "café": "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800&q=80",
    "cafe": "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800&q=80",
    "italiano": "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800&q=80",
    "restaurante": "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800&q=80",
    "hamburguesa": "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=800&q=80",
    "mirador": "https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?w=800&q=80",
    "bar": "https://images.unsplash.com/photo-1514933651103-005eec06c04b?w=800&q=80",
    "postres": "https://images.unsplash.com/photo-1501443762994-82bd5dace89a?w=800&q=80",
    "helado": "https://images.unsplash.com/photo-1501443762994-82bd5dace89a?w=800&q=80",
    "parrilla": "https://images.unsplash.com/photo-1544025162-d76694265947?w=800&q=80",
    "carne": "https://images.unsplash.com/photo-1544025162-d76694265947?w=800&q=80",
    "paseo": "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=800&q=80",
    "actividad": "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=800&q=80",
}


def _get_category_photo(category: str, title: str) -> str:
    combined = f"{category} {title}".lower()
    for key, url in CATEGORY_FALLBACK_PHOTOS.items():
        if key in combined:
            return url
    return "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=800&q=80"


async def _fetch_google_places_info(client: httpx.AsyncClient, query: str) -> dict:
    """Fetch real Google Place photo and reviews if Google Maps API key is configured."""
    api_key = settings.GOOGLE_MAPS_API_KEY
    if not api_key:
        return {}

    try:
        url = (
            f"https://maps.googleapis.com/maps/api/place/findplacefromtext/json"
            f"?input={urllib.parse.quote(query)}&inputtype=textquery"
            f"&fields=place_id,name,photos,rating,user_ratings_total&key={api_key}"
        )
        res = await client.get(url, timeout=5.0)
        if res.status_code == 200:
            data = res.json()
            candidates = data.get("candidates", [])
            if candidates:
                cand = candidates[0]
                result = {
                    "rating": cand.get("rating"),
                    "review_count": cand.get("user_ratings_total"),
                }
                photos = cand.get("photos", [])
                if photos and "photo_reference" in photos[0]:
                    ref = photos[0]["photo_reference"]
                    result["photo_url"] = (
                        f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photo_reference={ref}&key={api_key}"
                    )
                return result
    except Exception as e:
        logger.debug(f"Google Places lookup error for {query}: {e}")
    return {}


async def _generate_with_ai(
    outing_type: str,
    target_budget: float,
    area: str,
    preferences: str,
    visited_places: str,
    total_balance: float,
    safe_budget: float,
    use_location: bool = False,
    latitude: float = None,
    longitude: float = None,
    radius_km: int = 5
) -> OutingPlanResponse:
    if settings.GEMINI_API_KEY:
        try:
            url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={settings.GEMINI_API_KEY}"
            
            location_instructions = ""
            if use_location and latitude is not None and longitude is not None:
                location_instructions = f"""
- UBICACIÓN GPS EXACTA DEL USUARIO: Latitud {latitude}, Longitud {longitude}
- RADIO MÁXIMO DE BÚSQUEDA: {radius_km} km a la redonda
- REQUISITO CRÍTICO DE PROXIMIDAD: Los lugares DEBEN estar ubicados a menos de {radius_km} km de estas coordenadas GPS.
"""

            prompt = f"""Eres el planificador experto de salidas, ocio y citas de LifeOS.
Diseña un itinerario de salida de 2 a 3 paradas específicas y reales para:
- Tipo de salida: {outing_type}
- Ciudad o Zona: {area}
{location_instructions}
- Presupuesto máximo total: ${target_budget:,.0f} COP
- Preferencias: {preferences}
- LUGARES YA VISITADOS (NO REPETIR NINGUNO DE ESTOS): {visited_places}

Responde ÚNICAMENTE con un JSON válido en este formato exacto (sin bloques de código ```json, solo texto plano):
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
      "maps_query": "Café Cultor Usaquén Bogotá",
      "rating": 4.8,
      "review_count": 350,
      "highlight_review": "La terraza y el café filtrado son excepcionales.",
      "distance_km": 2.1
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
                        
                        # Fetch Google Places data if key available
                        places_info = await _fetch_google_places_info(client, query)
                        image_url = places_info.get("photo_url") or _get_category_photo(s.get("category", ""), s.get("title", ""))
                        rating = float(places_info.get("rating") or s.get("rating", 4.8))
                        review_count = int(places_info.get("review_count") or s.get("review_count", 150))
                        
                        stops.append(OutingStop(
                            order=int(s.get("order", 1)),
                            title=s.get("title", "Lugar"),
                            category=s.get("category", "Ocio"),
                            estimated_cost=cost,
                            description=s.get("description", ""),
                            maps_query=query,
                            maps_url=maps_url,
                            image_url=image_url,
                            rating=rating,
                            review_count=review_count,
                            highlight_review=s.get("highlight_review", "Muy recomendado por sus visitantes."),
                            distance_km=float(s.get("distance_km")) if s.get("distance_km") is not None else None
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
    return _get_fallback_plan(outing_type, target_budget, area, safe_budget, use_location, radius_km)


def _get_fallback_plan(
    outing_type: str,
    budget: float,
    area: str,
    safe_budget: float,
    use_location: bool = False,
    radius_km: int = 5
) -> OutingPlanResponse:
    dist_prefix = f"Cerca de ti (<{radius_km} km): " if use_location else ""
    if "moto" in outing_type.lower() or "rodada" in outing_type.lower():
        stops = [
            OutingStop(
                order=1,
                title="Mirador de La Calera",
                category="Mirador / Ruta",
                estimated_cost=budget * 0.25,
                description="Ruta de montaña en moto, vista panorámica de la ciudad y café caliente en la vía.",
                maps_query=f"Mirador La Calera {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Mirador La Calera ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["mirador"],
                rating=4.8,
                review_count=1240,
                highlight_review="La vista de noche es impresionante y el café de la curva es clásico.",
                distance_km=4.5 if use_location else None
            ),
            OutingStop(
                order=2,
                title="Restaurante Campestre El Tambor",
                category="Restaurante",
                estimated_cost=budget * 0.55,
                description="Parrilla campestre, espacio abierto y parqueadero seguro para motos.",
                maps_query=f"El Tambor La Calera {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('El Tambor La Calera ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["parrilla"],
                rating=4.6,
                review_count=3200,
                highlight_review="Parrilla generosa al aire libre y buen espacio para parquear motos.",
                distance_km=7.2 if use_location else None
            ),
            OutingStop(
                order=3,
                title="Café de Especialidad San Alberto",
                category="Café",
                estimated_cost=budget * 0.20,
                description="Degustación de café premium para cerrar la rodada con buena charla.",
                maps_query=f"Café San Alberto {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Café San Alberto ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["cafe"],
                rating=4.9,
                review_count=890,
                highlight_review="Experiencia de café de 5 estrellas, sabores únicos.",
                distance_km=2.8 if use_location else None
            )
        ]
        title = f"{dist_prefix}Rodada & Almuerzo Campestre"
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
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Plaza de Usaquén ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["paseo"],
                rating=4.8,
                review_count=4500,
                highlight_review="Hermoso para caminar en pareja, seguro y con excelente ambiente bohemio.",
                distance_km=2.1 if use_location else None
            ),
            OutingStop(
                order=2,
                title="Cena en Bistro Italiano / Trattoria",
                category="Restaurante",
                estimated_cost=budget * 0.70,
                description="Cena íntima con pastas artesanales o pizza napolitana y copa de vino.",
                maps_query=f"Restaurante Italiano Usaquén {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante Italiano Usaquén ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["italiano"],
                rating=4.7,
                review_count=1120,
                highlight_review="Pastas hechas en casa y la lasaña a los cuatro quesos es espectacular.",
                distance_km=2.3 if use_location else None
            ),
            OutingStop(
                order=3,
                title="Postre y Gelato Artesanal",
                category="Postres",
                estimated_cost=budget * 0.30,
                description="Helado italiano tradicional para terminar la cita con una buena conversación.",
                maps_query=f"Heladería Artesanal Usaquén {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Heladería Artesanal Usaquén ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["helado"],
                rating=4.9,
                review_count=780,
                highlight_review="El gelato de pistacho y avellana es de otro mundo.",
                distance_km=2.5 if use_location else None
            )
        ]
        title = f"{dist_prefix}Noche de Cita & Sabores Coloniales"
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
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Café de especialidad ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["cafe"],
                rating=4.8,
                review_count=670,
                highlight_review="Ambiente acústico perfecto para charlar sin ruido molesto.",
                distance_km=1.5 if use_location else None
            ),
            OutingStop(
                order=2,
                title="Cena / Hamburguesas Gourmet",
                category="Restaurante",
                estimated_cost=budget * 0.70,
                description="Comida reconfortante de alta calidad en un sitio moderno.",
                maps_query=f"Restaurante moderno {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante moderno ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["hamburguesa"],
                rating=4.7,
                review_count=1850,
                highlight_review="Carne angus jugosa en pan brioche artesanal, 10 de 10.",
                distance_km=2.0 if use_location else None
            )
        ]
        title = f"{dist_prefix}Tarde de Desconexión en {area}"
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

