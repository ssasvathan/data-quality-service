"""SQLAlchemy session dependency for FastAPI route injection.

Provides get_db() generator for use with FastAPI's Depends().
Uses SQLAlchemy 2.0 style — never legacy session.query().
"""
from typing import Generator

from sqlalchemy.orm import Session

from .engine import SessionLocal


def get_db() -> Generator[Session, None, None]:
    """Yield a SQLAlchemy Session and ensure it is closed after the request."""
    db = SessionLocal()
    try:
        yield db
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()
