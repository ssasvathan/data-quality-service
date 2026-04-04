"""Shared FastAPI dependency functions for dqs-serve.

Extracted into a dedicated module to prevent circular imports.
(main.py imports routes; routes import dependencies; dependencies must NOT import main.)

Per story dev notes (4.5): get_reference_data_service is a singleton accessor,
not a per-request factory — ReferenceDataService lives on app.state.
"""
from fastapi import HTTPException, Request

from .services.reference_data import ReferenceDataService


def get_reference_data_service(request: Request) -> ReferenceDataService:
    """FastAPI dependency: return the singleton ReferenceDataService from app.state.

    The service is created once at lifespan startup and stored on app.state.reference_data.
    Route handlers inject it via: Depends(get_reference_data_service)

    Per project-context.md: NEVER pass ReferenceDataService as a per-request
    Depends() constructor — it is a singleton, not created per request.

    [Medium-2] Uses getattr with None fallback to avoid AttributeError if lifespan
    did not complete (e.g. startup failed). Returns HTTP 503 with diagnostic message.
    """
    svc = getattr(request.app.state, "reference_data", None)
    if svc is None:
        raise HTTPException(
            status_code=503,
            detail={
                "detail": "Reference data service unavailable — startup may have failed.",
                "error_code": "SERVICE_UNAVAILABLE",
            },
        )
    return svc
