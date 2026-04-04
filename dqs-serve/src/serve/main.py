"""Data Quality Service FastAPI application.

Routes are organised in src/serve/routes/ modules.
All routes are prefixed with /api.
The /health endpoint is kept at root level for infrastructure readiness checks.
"""
import asyncio
import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from .db.engine import SessionLocal
from .mcp.tools import mcp
from .routes import datasets as datasets_router
from .routes import lobs as lobs_router
from .routes import search as search_router
from .routes import summary as summary_router
from .services.reference_data import ReferenceDataService

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """FastAPI lifespan context manager — runs startup and shutdown logic.

    Startup: creates ReferenceDataService, calls refresh() to populate cache,
    stores as app.state.reference_data for route handler access.
    Shutdown: no cleanup required (cache is in-memory only).

    Per story dev notes: use lifespan, NOT deprecated @app.on_event("startup").
    Per project-context.md: NEVER use @app.on_event("startup") — use lifespan.
    """
    # Startup — populate reference data cache.
    # [High-3] refresh() is a blocking sync DB call; run it in a thread executor
    # so it does not block the async event loop.
    # [Medium-1] Wrap in try/except to log meaningful error before re-raising.
    svc = ReferenceDataService(db_factory=SessionLocal)
    try:
        await asyncio.to_thread(svc.refresh)
    except Exception:
        logger.exception(
            "Failed to populate ReferenceDataService cache at startup — "
            "check database connectivity and v_lob_lookup_active view."
        )
        raise
    app.state.reference_data = svc
    yield
    # Shutdown — nothing to clean up


app = FastAPI(title="Data Quality Service", lifespan=lifespan)


# ---------------------------------------------------------------------------
# Global error handler — never expose stack traces (project-context.md rule)
# ---------------------------------------------------------------------------


@app.exception_handler(Exception)
async def internal_server_error_handler(request: Request, exc: Exception) -> JSONResponse:
    """Return a generic 500 without leaking internal details or stack traces.

    Intentional HTTP errors raised via HTTPException are re-raised so FastAPI
    can handle them with the correct status code and response.
    """
    if isinstance(exc, HTTPException):
        raise exc
    logger.exception("Unhandled exception for request %s", request.url)
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error", "error_code": "INTERNAL_ERROR"},
    )


# ---------------------------------------------------------------------------
# Health check (root level — not under /api prefix)
# ---------------------------------------------------------------------------


@app.get("/health")
def health_check() -> dict[str, str]:
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# API routers
# ---------------------------------------------------------------------------

app.include_router(summary_router.router, prefix="/api")
app.include_router(lobs_router.router, prefix="/api")
app.include_router(datasets_router.router, prefix="/api")
app.include_router(search_router.router, prefix="/api")

# ---------------------------------------------------------------------------
# MCP layer — additive mount, does not modify existing routes
# ---------------------------------------------------------------------------

app.mount("/mcp", mcp.http_app(path="/"))
