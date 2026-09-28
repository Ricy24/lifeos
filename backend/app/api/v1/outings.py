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

    # 3. Resolve Google Maps API Key (from request or server settings)
    effective_maps_key = (request.google_maps_api_key or settings.GOOGLE_MAPS_API_KEY or "").strip()

    # 4. Generate plan via Google Places API + Gemini AI or Fallback
    plan = await _generate_with_ai_or_places(
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
        radius_km=request.radius_km or 5,
        maps_api_key=effective_maps_key
    )

    return plan


import math

def _haversine_distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return round(R * c, 2)


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


import asyncio
import random


async def _search_google_places_single(
    client: httpx.AsyncClient,
    lat: float,
    lon: float,
    radius_m: int,
    place_type: str,
    keyword: str,
    api_key: str,
    page_token: str = None
) -> tuple:
    """Single Nearby Search request. Returns (results_list, next_page_token)."""
    params = {
        "location": f"{lat},{lon}",
        "radius": str(radius_m),
        "key": api_key,
    }
    if place_type:
        params["type"] = place_type
    if keyword:
        params["keyword"] = keyword
    if page_token:
        params["pagetoken"] = page_token

    url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
    try:
        res = await client.get(url, params=params, timeout=10.0)
        if res.status_code == 200:
            data = res.json()
            results = data.get("results", [])
            npt = data.get("next_page_token")
            return results, npt
    except Exception as e:
        logger.error(f"Error in Google Places Nearby Search ({place_type}/{keyword}): {e}")
    return [], None


async def _search_google_places_nearby(
    client: httpx.AsyncClient,
    lat: float,
    lon: float,
    radius_km: int,
    outing_type: str,
    api_key: str
) -> list:
    """Queries Google Places with MULTIPLE types and keywords in parallel for maximum venue variety."""
    # Map outing types to multiple (type, keyword) queries for diversity
    type_queries = {
        "cita": [
            ("restaurant", "cena romantica"),
            ("restaurant", "restaurante italiano"),
            ("bar", "cocteleria"),
            ("cafe", "cafe terraza"),
            ("restaurant", "restaurante elegante"),
        ],
        "moto": [
            ("restaurant", "restaurante campestre"),
            ("tourist_attraction", "mirador"),
            ("restaurant", "parrilla"),
            ("cafe", "cafe ruta"),
            ("restaurant", "asadero"),
        ],
        "amigos": [
            ("bar", "cerveceria artesanal"),
            ("restaurant", "hamburguesas gourmet"),
            ("bar", "bar deportivo"),
            ("restaurant", "alitas"),
            ("bowling_alley", "bolos"),
        ],
        "café": [
            ("cafe", "cafe especialidad"),
            ("bakery", "panaderia artesanal"),
            ("cafe", "cafe coworking"),
            ("cafe", "cafe postres"),
        ],
        "cafe": [
            ("cafe", "cafe especialidad"),
            ("bakery", "panaderia artesanal"),
            ("cafe", "cafe postres"),
        ],
        "gourmet": [
            ("restaurant", "restaurante gourmet"),
            ("restaurant", "restaurante fusion"),
            ("restaurant", "sushi"),
            ("restaurant", "comida peruana"),
            ("restaurant", "steak house"),
        ],
    }

    # Find the best matching query set
    queries = [("restaurant", "restaurante"), ("cafe", "cafe")]
    for k, v in type_queries.items():
        if k in outing_type.lower():
            queries = v
            break

    radius_m = min(radius_km * 1000, 50000)

    # Launch all queries in parallel
    tasks = [
        _search_google_places_single(client, lat, lon, radius_m, pt, kw, api_key)
        for pt, kw in queries
    ]
    results_list = await asyncio.gather(*tasks, return_exceptions=True)

    # Deduplicate by place_id across all queries
    seen_place_ids = set()
    all_results = []
    for result in results_list:
        if isinstance(result, Exception):
            continue
        places, npt = result
        for p in places:
            pid = p.get("place_id", "")
            if pid and pid not in seen_place_ids:
                seen_place_ids.add(pid)
                all_results.append(p)

    logger.info(f"Google Places: {len(all_results)} unique venues found across {len(queries)} parallel queries")
    return all_results


async def _fetch_google_places_info(client: httpx.AsyncClient, query: str, api_key: str) -> dict:
    """Fetch real Google Place photo and reviews if Google Maps API key is configured."""
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


def _classify_place_category(place: dict) -> str:
    """Extract a human-readable category from Google Places types."""
    types = place.get("types", [])
    type_labels = {
        "restaurant": "Restaurante",
        "cafe": "Café",
        "bar": "Bar",
        "bakery": "Panadería",
        "tourist_attraction": "Atracción",
        "night_club": "Discoteca",
        "bowling_alley": "Entretenimiento",
        "meal_takeaway": "Comida Rápida",
        "meal_delivery": "Delivery",
    }
    for t in types:
        if t in type_labels:
            return type_labels[t]
    return "Lugar"


def _price_level_label(price_level: int) -> str:
    """Convert Google price_level (0-4) to readable label."""
    labels = {0: "Gratis", 1: "Económico $", 2: "Moderado $$", 3: "Costoso $$$", 4: "Muy Costoso $$$$"}
    return labels.get(price_level, "Sin datos")


async def _generate_with_ai_or_places(
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
    radius_km: int = 5,
    maps_api_key: str = ""
) -> OutingPlanResponse:
    logger.info(
        f"[OUTINGS] Generating plan: type={outing_type}, budget={target_budget}, area={area}, "
        f"use_location={use_location}, lat={latitude}, lon={longitude}, radius={radius_km}km, "
        f"maps_key_present={'YES (' + str(len(maps_api_key)) + ' chars)' if maps_api_key else 'NO (EMPTY)'}"
    )
    async with httpx.AsyncClient(timeout=25.0) as client:
        # PATH A: GPS + Google Maps API → real venues via parallel multi-type Nearby Search
        if use_location and latitude is not None and longitude is not None and maps_api_key:
            logger.info(f"[OUTINGS] → PATH A: Google Places Nearby Search (GPS + API Key)")
            nearby_results = await _search_google_places_nearby(
                client=client,
                lat=latitude,
                lon=longitude,
                radius_km=radius_km,
                outing_type=outing_type,
                api_key=maps_api_key
            )

            # Filter out visited places
            visited_lower = [v.strip().lower() for v in visited_places.split(",") if v.strip()]
            filtered = [
                p for p in nearby_results
                if not any(v in p.get("name", "").lower() for v in visited_lower)
            ]

            # Calculate distance and enforce strict radius filtering
            places_with_dist = []
            for p in filtered:
                p_lat = p.get("geometry", {}).get("location", {}).get("lat")
                p_lng = p.get("geometry", {}).get("location", {}).get("lng")
                if p_lat is not None and p_lng is not None:
                    d = _haversine_distance_km(latitude, longitude, p_lat, p_lng)
                    # STRICT: only include places truly within the user's radius
                    if d <= radius_km * 1.05:
                        p["_dist"] = d
                        places_with_dist.append(p)

            # Filter out places with very low ratings or no ratings
            quality_places = [
                p for p in places_with_dist
                if float(p.get("rating", 0)) >= 3.5 and int(p.get("user_ratings_total", 0)) >= 5
            ]
            # Fallback if filtering is too aggressive
            if len(quality_places) < 3:
                quality_places = places_with_dist

            if quality_places:
                # Sort by quality score (rating * log(reviews + 1)) with randomization
                quality_places.sort(
                    key=lambda p: (
                        float(p.get("rating", 4.0)) * math.log(int(p.get("user_ratings_total", 1)) + 1)
                    ),
                    reverse=True
                )

                # Pick top N with some randomization for variety
                max_venues = min(10, len(quality_places))
                # Top-tier (best 40%), mid-tier (next 35%), discovery (rest)
                top_tier_count = max(1, int(max_venues * 0.4))
                mid_tier_count = max(1, int(max_venues * 0.35))

                top_tier = quality_places[:max(3, len(quality_places) // 3)]
                mid_tier = quality_places[len(top_tier):len(top_tier) + max(3, len(quality_places) // 3)]
                discovery = quality_places[len(top_tier) + len(mid_tier):]

                # Shuffle within tiers for variety on each request
                random.shuffle(top_tier)
                random.shuffle(mid_tier)
                random.shuffle(discovery)

                selected = []
                selected.extend(top_tier[:top_tier_count])
                selected.extend(mid_tier[:mid_tier_count])
                remaining = max_venues - len(selected)
                if remaining > 0 and discovery:
                    selected.extend(discovery[:remaining])
                # Fill from any remaining if we still need more
                if len(selected) < max_venues:
                    all_remaining = [p for p in quality_places if p not in selected]
                    selected.extend(all_remaining[:max_venues - len(selected)])

                # Sort final selection by distance for a natural itinerary
                selected.sort(key=lambda p: p.get("_dist", 999))

                stops = []
                total_cost = 0.0
                num_stops = len(selected)

                for idx, p in enumerate(selected):
                    p_name = p.get("name", "Lugar")
                    p_lat = p.get("geometry", {}).get("location", {}).get("lat", latitude)
                    p_lng = p.get("geometry", {}).get("location", {}).get("lng", longitude)
                    dist = p.get("_dist") or _haversine_distance_km(latitude, longitude, p_lat, p_lng)

                    # Distribute budget proportionally
                    cost = round(target_budget / num_stops)
                    total_cost += cost

                    # Real Google photo
                    photos = p.get("photos", [])
                    photo_url = None
                    if photos and "photo_reference" in photos[0]:
                        ref = photos[0]["photo_reference"]
                        photo_url = f"https://maps.googleapis.com/maps/api/place/photo?maxwidth=800&photo_reference={ref}&key={maps_api_key}"
                    if not photo_url:
                        photo_url = _get_category_photo(outing_type, p_name)

                    vicinity = p.get("vicinity", area)
                    rating = float(p.get("rating", 0))
                    reviews_cnt = int(p.get("user_ratings_total", 0))
                    price_level = p.get("price_level")
                    category = _classify_place_category(p)
                    price_label = _price_level_label(price_level) if price_level is not None else ""

                    # Build a rich description
                    desc_parts = [f"{p_name} en {vicinity}."]
                    if rating > 0:
                        desc_parts.append(f"Calificación {rating}⭐ ({reviews_cnt} reseñas).")
                    if price_label:
                        desc_parts.append(f"Nivel de precios: {price_label}.")
                    desc_parts.append(f"A {dist:.1f} km de ti.")

                    stops.append(OutingStop(
                        order=idx + 1,
                        title=p_name,
                        category=category,
                        estimated_cost=cost,
                        description=" ".join(desc_parts),
                        maps_query=f"{p_name} {vicinity}",
                        maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote(p_name + ' ' + vicinity)}",
                        image_url=photo_url,
                        rating=rating,
                        review_count=reviews_cnt,
                        highlight_review=f"A solo {dist:.1f} km de tu ubicación actual." + (f" Precio: {price_label}" if price_label else ""),
                        distance_km=round(dist, 1),
                        latitude=p_lat,
                        longitude=p_lng,
                        address=vicinity,
                        price_level=price_level
                    ))

                return OutingPlanResponse(
                    title=f"📍 {outing_type} — {len(stops)} lugares a ≤{radius_km} km",
                    summary=f"Encontramos {len(stops)} sitios reales en Google Maps cerca de ti. Toca un marcador en el mapa para ver detalles.",
                    total_estimated_cost=total_cost,
                    safe_budget_available=safe_budget,
                    stops=stops,
                    financial_advice=f"Presupuesto estimado ${total_cost:,.0f} COP dentro de tu disponibilidad segura de ocio."
                )
            else:
                logger.warning(f"[OUTINGS] PATH A: No quality places found after filtering. raw_results={len(nearby_results)}, filtered={len(filtered)}, within_radius={len(places_with_dist)}, quality={len(quality_places) if 'quality_places' in dir() else 'N/A'}")

        # PATH B: Gemini AI Generation
        logger.info(f"[OUTINGS] → PATH B: Trying Gemini AI (key={'YES' if settings.GEMINI_API_KEY else 'NO'})")
        if settings.GEMINI_API_KEY:
            try:
                url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={settings.GEMINI_API_KEY}"
                
                location_instructions = ""
                if use_location and latitude is not None and longitude is not None:
                    location_instructions = f"""
- UBICACIÓN GPS EXACTA DEL USUARIO: Latitud {latitude}, Longitud {longitude}
- RADIO MÁXIMO DE BÚSQUEDA: {radius_km} km a la redonda
- REQUISITO CRÍTICO DE PROXIMIDAD: Los lugares DEBEN existir y estar ubicados a menos de {radius_km} km de estas coordenadas GPS.
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
      "distance_km": 2.1,
      "latitude": 4.695,
      "longitude": -74.032,
      "address": "Calle 119 # 5-18, Bogotá"
    }}
  ],
  "financial_advice": "Consejo financiero sobre el gasto de esta salida"
}}
"""
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
                        places_info = await _fetch_google_places_info(client, query, maps_api_key)
                        image_url = places_info.get("photo_url") or _get_category_photo(s.get("category", ""), s.get("title", ""))
                        rating = float(places_info.get("rating") or s.get("rating", 4.8))
                        review_count = int(places_info.get("review_count") or s.get("review_count", 150))
                        
                        s_lat = s.get("latitude") or (latitude if latitude else 4.6097)
                        s_lng = s.get("longitude") or (longitude if longitude else -74.0817)
                        dist = float(s.get("distance_km")) if s.get("distance_km") is not None else (_haversine_distance_km(latitude, longitude, s_lat, s_lng) if latitude and longitude else None)
                        
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
                            distance_km=dist,
                            latitude=s_lat,
                            longitude=s_lng,
                            address=s.get("address", area)
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

    # PATH C: Fallback Curated Colombian Plans
    logger.warning(f"[OUTINGS] → PATH C: FALLBACK plan (no Google Places results, no Gemini). This produces GENERIC names!")
    return _get_fallback_plan(
        outing_type=outing_type,
        budget=target_budget,
        area=area,
        safe_budget=safe_budget,
        use_location=use_location,
        radius_km=radius_km,
        latitude=latitude,
        longitude=longitude
    )


def _get_fallback_plan(
    outing_type: str,
    budget: float,
    area: str,
    safe_budget: float,
    use_location: bool = False,
    radius_km: int = 5,
    latitude: float = None,
    longitude: float = None
) -> OutingPlanResponse:
    dist_prefix = f"Cerca de ti (<{radius_km} km): " if use_location else ""
    user_lat = latitude if (use_location and latitude is not None) else 4.695
    user_lng = longitude if (use_location and longitude is not None) else -74.032
    
    # Generate realistic nearby coordinates within user's requested radius
    def _nearby_coord(idx: int):
        offset = min(float(radius_km) * 0.25 * (idx + 1), float(radius_km) * 0.8) / 111.0
        s_lat = round(user_lat + (offset * (1 if idx % 2 == 0 else -1)), 6)
        s_lng = round(user_lng + (offset * (1 if idx > 0 else -1)), 6)
        dist = _haversine_distance_km(user_lat, user_lng, s_lat, s_lng)
        return s_lat, s_lng, dist

    if "moto" in outing_type.lower() or "rodada" in outing_type.lower():
        lat1, lng1, d1 = _nearby_coord(0)
        lat2, lng2, d2 = _nearby_coord(1)
        lat3, lng3, d3 = _nearby_coord(2)
        stops = [
            OutingStop(
                order=1,
                title="Mirador Panorámico & Café de Curva",
                category="Mirador / Ruta",
                estimated_cost=budget * 0.25,
                description=f"Ruta panorámica segura en moto en {area}, vista increíble y parada para café caliente.",
                maps_query=f"Mirador panoramico {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Mirador panoramico ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["mirador"],
                rating=4.8,
                review_count=1240,
                highlight_review="La vista de noche es impresionante y el café de la curva es clásico.",
                distance_km=d1 if use_location else None,
                latitude=lat1,
                longitude=lng1,
                address=f"Zona Mirador, {area}"
            ),
            OutingStop(
                order=2,
                title="Restaurante Campestre & Parrilla",
                category="Restaurante",
                estimated_cost=budget * 0.55,
                description="Parrilla artesanal, espacio abierto y parqueadero seguro para motos.",
                maps_query=f"Restaurante campestre parrilla {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante campestre parrilla ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["parrilla"],
                rating=4.6,
                review_count=3200,
                highlight_review="Parrilla generosa al aire libre y buen espacio para parquear motos.",
                distance_km=d2 if use_location else None,
                latitude=lat2,
                longitude=lng2,
                address=f"Corredor Gastronómico, {area}"
            ),
            OutingStop(
                order=3,
                title="Café de Especialidad & Repostería",
                category="Café",
                estimated_cost=budget * 0.20,
                description="Degustación de café especial colombiano para cerrar la rodada con buena charla.",
                maps_query=f"Cafe especialidad {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Cafe especialidad ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["cafe"],
                rating=4.9,
                review_count=890,
                highlight_review="Experiencia de café de 5 estrellas, sabores únicos.",
                distance_km=d3 if use_location else None,
                latitude=lat3,
                longitude=lng3,
                address=f"Plaza Principal, {area}"
            )
        ]
        title = f"{dist_prefix}Rodada & Almuerzo Campestre"
        summary = f"Plan perfecto para disfrutar tu moto hacia {area} con mirador, gastronomía y parada de café."
    elif "romántic" in outing_type.lower() or "cita" in outing_type.lower():
        lat1, lng1, d1 = _nearby_coord(0)
        lat2, lng2, d2 = _nearby_coord(1)
        lat3, lng3, d3 = _nearby_coord(2)
        stops = [
            OutingStop(
                order=1,
                title="Paseo por Calles Coloniales & Parque",
                category="Paseo",
                estimated_cost=0.0,
                description=f"Caminata tranquila por calles peatonales con ambiente iluminado y tiendas de diseño en {area}.",
                maps_query=f"Paseo peatonal parque {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Paseo peatonal parque ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["paseo"],
                rating=4.8,
                review_count=4500,
                highlight_review="Hermoso para caminar en pareja, seguro y con excelente ambiente bohemio.",
                distance_km=d1 if use_location else None,
                latitude=lat1,
                longitude=lng1,
                address=f"Centro Histórico / Parque, {area}"
            ),
            OutingStop(
                order=2,
                title="Cena en Trattoria & Bistro Italiano",
                category="Restaurante",
                estimated_cost=budget * 0.70,
                description="Cena íntima con pastas artesanales o pizza napolitana en horno de piedra con copa de vino.",
                maps_query=f"Restaurante Italiano {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Restaurante Italiano ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["italiano"],
                rating=4.7,
                review_count=1120,
                highlight_review="Pastas hechas en casa y la lasaña a los cuatro quesos es espectacular.",
                distance_km=d2 if use_location else None,
                latitude=lat2,
                longitude=lng2,
                address=f"Calle Gourmet, {area}"
            ),
            OutingStop(
                order=3,
                title="Postre & Gelato Artesanal",
                category="Postres",
                estimated_cost=budget * 0.30,
                description="Helado italiano tradicional de pistacho o café para terminar la cita con una buena conversación.",
                maps_query=f"Heladería Artesanal {area}",
                maps_url=f"https://www.google.com/maps/search/?api=1&query={urllib.parse.quote('Heladería Artesanal ' + area)}",
                image_url=CATEGORY_FALLBACK_PHOTOS["helado"],
                rating=4.9,
                review_count=780,
                highlight_review="El gelato de pistacho y avellana es de otro mundo.",
                distance_km=d3 if use_location else None,
                latitude=lat3,
                longitude=lng3,
                address=f"Paseo Comercial, {area}"
            )
        ]
        title = f"{dist_prefix}Noche de Cita & Sabores Íntimos"
        summary = f"Itinerario romántico y relajado en {area} diseñado para conectar sin gastar de más."
    else:
        lat1, lng1, d1 = _nearby_coord(0)
        lat2, lng2, d2 = _nearby_coord(1)
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
                distance_km=d1 if use_location else None,
                latitude=lat1,
                longitude=lng1,
                address=f"Avenida Principal, {area}"
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
                distance_km=d2 if use_location else None,
                latitude=lat2,
                longitude=lng2,
                address=f"Zona Gastronómica, {area}"
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

