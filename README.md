# LifeOS Finance

LifeOS Finance es una aplicacion personal de finanzas con backend FastAPI y cliente Android Kotlin/Jetpack Compose. El sistema esta pensado para trabajar offline y sincronizar cuando la API esta disponible.

## Estado

Phase 1 (foundation) y la mayor parte de Phase 2 estan implementadas:

- Autenticacion JWT, cuentas y transacciones.
- Resumen financiero, balance y net worth.
- Deudas, pagos y scoring de prioridad.
- Metas con progreso y wishlist.
- Recepcion de enlaces compartidos, incluyendo Mercado Libre.
- Dashboard con grafica Vico de gastos mensuales.
- Preferencias de tema Material 3 con DataStore.
- Resumenes diarios y semanales por Telegram mediante endpoints protegidos.
- Insights financieros opcionales con Gemini u OpenAI.
- Enlaces de ubicaciones de transacciones abiertos en Google Maps.
- Room offline-first y sincronizacion Retrofit.

## Estructura

```text
backend/   API FastAPI, modelos SQLAlchemy, Alembic y servicios financieros
app/       Aplicacion Android Kotlin/Compose
```

La arquitectura detallada esta en [ARCHITECTURE.md](ARCHITECTURE.md).

## Requisitos

- JDK 17. El proyecto fija el daemon de Gradle y los targets Kotlin en JVM 17.
- Android Studio con Android SDK 34.
- Python 3.12 recomendado.
- PostgreSQL para el backend.

## Backend local

```powershell
cd backend
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
uvicorn app.main:app --reload
```

La API queda disponible en `http://localhost:8000`. Documentacion interactiva:

- Swagger: `http://localhost:8000/docs`
- ReDoc: `http://localhost:8000/redoc`
- Health: `http://localhost:8000/health`

Para ejecutar las pruebas:

```powershell
python -m pytest -q
```

En produccion se deben ejecutar las migraciones Alembic antes de arrancar la API:

```powershell
alembic upgrade head
```

## Android

Abrir la raiz del proyecto en Android Studio y sincronizar Gradle. Desde PowerShell:

```powershell
.\gradlew.bat assembleDebug --no-daemon --no-configuration-cache --console=plain
```

APK debug generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

El cliente usa la API desplegada configurada en `NetworkModule.kt`. Para desarrollo local, cambia `BASE_URL` por la URL accesible desde el dispositivo o emulador.

## Configuracion

Las variables del backend estan documentadas en [backend/.env.example](backend/.env.example). No guardes secretos en Git. El endpoint de tareas cron requiere el header `X-Cron-Secret`.

Integraciones externas:

- Telegram requiere `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALLOWED_USER_ID` y `CRON_SECRET`.
- AI requiere `AI_PROVIDER=gemini` con `GEMINI_API_KEY`, o `AI_PROVIDER=openai` con `OPENAI_API_KEY`.
- Google Maps se abre mediante una URL de busqueda y no requiere API key; usa el campo de ubicacion de cada transaccion.

## Flujo offline-first

1. La UI observa datos de Room mediante `Flow`.
2. El repositorio muestra inmediatamente el estado local.
3. Las entidades pendientes se envian a Retrofit.
4. El repositorio descarga la copia remota y actualiza Room.
5. Si la red falla, la UI conserva los datos locales.

## Licencia y datos

Este repositorio no incluye credenciales ni datos financieros reales. Configura tus propios secretos y una base PostgreSQL antes de desplegarlo.
