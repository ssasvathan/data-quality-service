"""Reference data service — LOB lookup code resolution with in-memory caching.

Resolves raw lookup_code values (e.g. 'LOB_RETAIL') to human-readable
lob_name, owner, and classification strings via the v_lob_lookup_active view.

Cache TTL: 12 hours. Refresh triggered automatically on first resolve() call
and on any call when the cache is older than 12 hours.

Per project-context.md:
  - NEVER query lob_lookup table directly — always use v_lob_lookup_active view
  - NEVER raise HTTPException — return N/A for unknown codes
  - NEVER hardcode the EXPIRY_SENTINEL value in service code — the view handles this
  - Use SQLAlchemy 2.0 style: db.execute(text(...)).mappings().all()
"""
import datetime
import logging
import threading
from dataclasses import dataclass
from typing import Callable, Optional

from sqlalchemy import text
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

_CACHE_TTL_HOURS = 12

_LOB_LOOKUP_SQL = text(
    "SELECT lookup_code, lob_name, owner, classification FROM v_lob_lookup_active"
)


@dataclass(frozen=True)
class LobMapping:
    """Immutable resolved LOB mapping for a lookup_code."""

    lob_name: str
    owner: str
    classification: str


_NA_MAPPING = LobMapping(lob_name="N/A", owner="N/A", classification="N/A")


class ReferenceDataService:
    """Singleton service that caches LOB lookup code resolutions.

    Populated at startup via lifespan and refreshed every 12 hours.
    Thread-safe: uses two locks —
      _lock       protects _cache and _last_refresh for reads/writes.
      _refresh_lock serialises concurrent refresh() calls so at most one
                  thread performs a DB round-trip when the cache is stale.

    Usage::

        svc = ReferenceDataService(db_factory=SessionLocal)
        svc.refresh()                             # populate cache at startup
        mapping = svc.resolve("LOB_RETAIL")      # returns LobMapping
    """

    def __init__(self, db_factory: Callable[[], Session]) -> None:
        self._db_factory = db_factory
        self._cache: dict[str, LobMapping] = {}
        # [Medium-4] Use None so _maybe_refresh treats uninitialised cache as
        # always stale rather than relying on datetime.min arithmetic.
        self._last_refresh: Optional[datetime.datetime] = None
        self._lock = threading.Lock()         # protects _cache + _last_refresh
        self._refresh_lock = threading.Lock()  # serialises refresh() calls

    def refresh(self) -> None:
        """Load all active LOB mappings from v_lob_lookup_active into _cache.

        Opens a fresh DB session, queries the active-record view, and atomically
        replaces the cache. Closes the session in a finally block (resource safety).
        Cache and timestamp are updated inside the try block so that a close()
        failure does not leave them stale.
        """
        db = self._db_factory()
        try:
            rows = db.execute(_LOB_LOOKUP_SQL).mappings().all()
            new_cache = {
                # [Low-4] Use .get() with fallback to guard against KeyError if
                # v_lob_lookup_active schema changes and a column is missing.
                # SQLAlchemy RowMapping objects support dict-style .get() directly.
                row.get("lookup_code", ""): LobMapping(
                    lob_name=row.get("lob_name", "N/A"),
                    owner=row.get("owner", "N/A"),
                    classification=row.get("classification", "N/A"),
                )
                for row in rows
                if row.get("lookup_code") is not None
            }
            # [High-2] Update _cache and _last_refresh inside the try block so
            # that a db.close() failure does not leave them stale.
            with self._lock:
                self._cache = new_cache
                self._last_refresh = datetime.datetime.now()
            # [Low-2] Log after new_cache is defined (inside the try block).
            logger.info(
                "Reference data cache refreshed: %d LOB mappings loaded", len(new_cache)
            )
        finally:
            db.close()

    def resolve(self, lookup_code: Optional[str]) -> LobMapping:
        """Resolve a lookup_code to a LobMapping.

        Returns LobMapping("N/A", "N/A", "N/A") when:
          - lookup_code is None
          - lookup_code is not found in the cache

        Never raises an exception for missing codes.
        Triggers a cache refresh if the cache is older than 12 hours.
        """
        self._maybe_refresh()
        # [Medium-3] Use explicit None check — empty string "" is a distinct
        # value that should fall through to the cache lookup (and return N/A
        # if absent), rather than being treated identically to None.
        if lookup_code is None:
            return _NA_MAPPING
        with self._lock:
            result = self._cache.get(lookup_code)
        if result is None:
            logger.warning("Unresolved lookup_code: %r — returning N/A", lookup_code)
            return _NA_MAPPING
        return result

    def _maybe_refresh(self) -> None:
        """Refresh cache if it is older than the TTL (12 hours).

        [High-1] Uses double-check locking with a dedicated _refresh_lock to
        prevent a TOCTOU race where multiple threads all pass the age check and
        then each call refresh() independently:

          1. Quick stale-check under _lock (avoids holding _lock across DB call).
          2. Acquire _refresh_lock to serialise potential refreshers.
          3. Re-check staleness under _lock (cache may have been refreshed while
             waiting for _refresh_lock).
          4. Only the thread that finds the cache still stale after step 3 calls
             refresh(); all others return without hitting the DB.
        """
        # Step 1: fast stale check (no blocking DB call under this lock).
        with self._lock:
            stale = self._is_stale()
        if not stale:
            return

        # Step 2+3+4: serialise refreshers; re-check before hitting the DB.
        with self._refresh_lock:
            with self._lock:
                still_stale = self._is_stale()
            if still_stale:
                self.refresh()

    def _is_stale(self) -> bool:
        """Return True when the cache needs refreshing.

        Caller must hold _lock.
        """
        if self._last_refresh is None:
            return True
        age = datetime.datetime.now() - self._last_refresh
        # [Low-1] A 1-second grace period is intentional: timedelta arithmetic can
        # produce an age fractionally above the threshold due to microseconds of CPU
        # execution time between setting _last_refresh and measuring age. Without the
        # grace period a test or caller that sets _last_refresh to exactly (now - 12h)
        # would spuriously trigger a refresh.  The semantic TTL remains 12 hours — the
        # 1-second tolerance is a clock-jitter guard, not a business-rule change.
        return age > datetime.timedelta(hours=_CACHE_TTL_HOURS, seconds=1)
