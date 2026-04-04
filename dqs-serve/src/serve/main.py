"""Data Quality Service FastAPI application.

Routes are organised in src/serve/routes/ modules.
All routes are prefixed with /api.
The /health endpoint is kept at root level for infrastructure readiness checks.
"""
import logging

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse

from .routes import lobs as lobs_router
from .routes import summary as summary_router

logger = logging.getLogger(__name__)

app = FastAPI(title="Data Quality Service")


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
