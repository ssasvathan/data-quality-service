"""Acceptance tests — Story 4.5: ReferenceDataService unit + integration tests.

TDD RED PHASE: All tests are marked @pytest.mark.skip because the implementation
does not exist yet. Tests assert EXPECTED behavior per acceptance criteria.
Remove skip markers after implementation to enter the green phase.

Test categories:
  - Unit tests (no DB, mock session): verify cache logic, resolve behavior,
    TTL-based refresh, N/A fallback, thread-safe operations.
  - Integration tests (real Postgres + seeded fixtures): verify that
    refresh() reads from v_lob_lookup_active, and the dataset detail endpoint
    includes resolved lob_name/owner/classification fields.

Acceptance Criteria mapped to tests:
  AC1: Cache populated on startup → test_refresh_populates_cache_from_db,
       test_refresh_reads_from_lob_lookup_view (integration)
  AC2: Lookup code resolved in API response → test_resolve_returns_cached_mapping,
       test_dataset_detail_includes_resolved_names (integration)
  AC3: Cache refreshed after 12 hours → test_maybe_refresh_triggers_on_stale_cache,
       test_maybe_refresh_skips_on_fresh_cache
  AC4: Unknown code returns N/A → test_resolve_returns_na_for_none_code,
       test_resolve_returns_na_for_unknown_code

Run unit tests:      cd dqs-serve && uv run pytest tests/test_services/test_reference_data.py
Run integration:     cd dqs-serve && uv run pytest -m integration tests/test_services/test_reference_data.py
Run all:             cd dqs-serve && uv run pytest tests/
"""

from __future__ import annotations

import datetime
from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import pytest

if TYPE_CHECKING:
    import psycopg2.extensions

# ---------------------------------------------------------------------------
# Shared constants
# ---------------------------------------------------------------------------

KNOWN_LOB_CODE = "LOB_RETAIL"
UNKNOWN_LOB_CODE = "UNKNOWN_CODE_XYZ"
CACHE_TTL_HOURS = 12


# ---------------------------------------------------------------------------
# Unit tests — ReferenceDataService.resolve()
# ---------------------------------------------------------------------------


class TestReferenceDataServiceResolveNone:
    """AC4 [P0]: resolve(None) must return LobMapping('N/A', 'N/A', 'N/A').

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_resolve_returns_na_for_none_code(self) -> None:
        """AC4 [P0]: resolve(None) returns LobMapping with all fields = 'N/A'.

        The service must never return null/None fields — unknown or missing
        lookup codes must gracefully degrade to 'N/A' strings.
        """
        from serve.services.reference_data import LobMapping, ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        svc = ReferenceDataService(db_factory=mock_db_factory)

        result = svc.resolve(None)

        assert isinstance(result, LobMapping), (
            f"resolve(None) must return a LobMapping, got {type(result)}"
        )
        assert result.lob_name == "N/A", (
            f"Expected lob_name='N/A' for None code, got {result.lob_name!r}"
        )
        assert result.owner == "N/A", (
            f"Expected owner='N/A' for None code, got {result.owner!r}"
        )
        assert result.classification == "N/A", (
            f"Expected classification='N/A' for None code, got {result.classification!r}"
        )


class TestReferenceDataServiceResolveUnknown:
    """AC4 [P0]: resolve() with unknown code returns N/A mapping.

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_resolve_returns_na_for_unknown_code(self) -> None:
        """AC4 [P0]: resolve('UNKNOWN_CODE') with empty cache returns N/A LobMapping.

        When a lookup_code has no mapping in the cache, all three resolved
        fields must return 'N/A' — not None, not an empty string, not an error.
        Per story dev notes: 'The service never raises HTTPException'.
        """
        from serve.services.reference_data import LobMapping, ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        # Simulate empty DB result so cache is empty after refresh
        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory.return_value = mock_db

        svc = ReferenceDataService(db_factory=mock_db_factory)
        # Force an empty cache state (no refresh needed — datetime.min triggers refresh)
        # After refresh returns empty rows, cache is empty
        svc.refresh()

        result = svc.resolve(UNKNOWN_LOB_CODE)

        assert isinstance(result, LobMapping), (
            f"resolve() must always return a LobMapping, got {type(result)}"
        )
        assert result.lob_name == "N/A", (
            f"Unknown code: expected lob_name='N/A', got {result.lob_name!r}"
        )
        assert result.owner == "N/A", (
            f"Unknown code: expected owner='N/A', got {result.owner!r}"
        )
        assert result.classification == "N/A", (
            f"Unknown code: expected classification='N/A', got {result.classification!r}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_resolve_does_not_raise_for_unknown_code(self) -> None:
        """AC4 [P0]: resolve() must never raise an exception for unknown codes.

        Per story dev notes: 'NEVER raise HTTPException from ReferenceDataService'.
        The service handles missing codes with graceful N/A fallback.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        svc.refresh()

        # Must not raise KeyError, HTTPException, AttributeError, or any other exception
        try:
            svc.resolve("COMPLETELY_MADE_UP_CODE_99999")
        except Exception as exc:  # noqa: BLE001
            pytest.fail(
                f"resolve() raised {type(exc).__name__}: {exc} — "
                "service must never raise for unknown codes"
            )


class TestReferenceDataServiceResolveKnown:
    """AC2 [P0]: resolve() with known code returns correct cached LobMapping.

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_resolve_returns_cached_mapping(self) -> None:
        """AC2 [P0]: resolve('LOB_RETAIL') returns the mapping pre-populated in _cache.

        Tests that the cache dict is consulted correctly and returns the
        mapped LobMapping with all three fields (lob_name, owner, classification).
        """
        from serve.services.reference_data import LobMapping, ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        svc = ReferenceDataService(db_factory=mock_db_factory)

        # Pre-populate cache directly (bypasses DB call)
        expected_mapping = LobMapping(
            lob_name="Retail Banking",
            owner="Jane Doe",
            classification="Tier 1 Critical",
        )
        svc._cache[KNOWN_LOB_CODE] = expected_mapping
        # Set last_refresh to now so _maybe_refresh won't trigger
        svc._last_refresh = datetime.datetime.now()

        result = svc.resolve(KNOWN_LOB_CODE)

        assert result == expected_mapping, (
            f"resolve({KNOWN_LOB_CODE!r}) returned {result!r}, "
            f"expected {expected_mapping!r}"
        )
        assert result.lob_name == "Retail Banking", (
            f"Expected lob_name='Retail Banking', got {result.lob_name!r}"
        )
        assert result.owner == "Jane Doe", (
            f"Expected owner='Jane Doe', got {result.owner!r}"
        )
        assert result.classification == "Tier 1 Critical", (
            f"Expected classification='Tier 1 Critical', got {result.classification!r}"
        )


# ---------------------------------------------------------------------------
# Unit tests — ReferenceDataService._maybe_refresh() TTL logic
# ---------------------------------------------------------------------------


class TestReferenceDataServiceCacheTTL:
    """AC3 [P0]: Cache refresh triggered when _last_refresh is > 12 hours ago.

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_maybe_refresh_triggers_on_stale_cache(self) -> None:
        """AC3 [P0]: _maybe_refresh() calls refresh() when cache is > 12 hours old.

        Sets _last_refresh to 13 hours ago and confirms refresh() is called.
        Per story dev notes: TTL = 12 hours, checked inside resolve().
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        # Make cache stale: 13 hours ago (> 12h TTL)
        svc._last_refresh = datetime.datetime.now() - datetime.timedelta(hours=13)

        with patch.object(svc, "refresh", wraps=svc.refresh) as mock_refresh:
            svc._maybe_refresh()

        mock_refresh.assert_called_once_with(), (
            "_maybe_refresh() must call refresh() when cache is older than 12 hours. "
            "Last refresh was 13 hours ago but refresh() was NOT called."
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_maybe_refresh_skips_on_fresh_cache(self) -> None:
        """AC3 [P0]: _maybe_refresh() does NOT call refresh() when cache is < 12 hours old.

        Sets _last_refresh to 1 hour ago and confirms refresh() is NOT called.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        svc = ReferenceDataService(db_factory=mock_db_factory)
        # Cache is fresh: 1 hour ago (< 12h TTL)
        svc._last_refresh = datetime.datetime.now() - datetime.timedelta(hours=1)

        with patch.object(svc, "refresh") as mock_refresh:
            svc._maybe_refresh()

        mock_refresh.assert_not_called(), (
            "_maybe_refresh() must NOT call refresh() when cache is only 1 hour old. "
            "TTL is 12 hours — refresh was incorrectly triggered."
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_maybe_refresh_skips_on_exactly_12h_boundary(self) -> None:
        """AC3 [P1]: _maybe_refresh() does NOT refresh when cache is exactly 12 hours old.

        Boundary condition: age.total_seconds() == 12 * 3600 should NOT trigger refresh.
        Only strictly greater than 12h triggers a refresh.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        svc = ReferenceDataService(db_factory=mock_db_factory)
        # Exactly 12 hours ago — boundary: should NOT trigger (> not >=)
        svc._last_refresh = datetime.datetime.now() - datetime.timedelta(hours=12)

        with patch.object(svc, "refresh") as mock_refresh:
            svc._maybe_refresh()

        mock_refresh.assert_not_called(), (
            "_maybe_refresh() must use strictly-greater-than comparison (> 12h). "
            "At exactly 12 hours, refresh should NOT be triggered."
        )


# ---------------------------------------------------------------------------
# Unit tests — ReferenceDataService.refresh()
# ---------------------------------------------------------------------------


class TestReferenceDataServiceRefresh:
    """AC1 [P0]: refresh() populates _cache from v_lob_lookup_active view rows.

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_refresh_populates_cache_from_db(self) -> None:
        """AC1 [P0]: refresh() calls db_factory(), queries v_lob_lookup_active, populates _cache.

        Mock db_factory returns fake rows. After refresh(), _cache must contain
        the correct LobMapping entries keyed by lookup_code.
        """
        from serve.services.reference_data import LobMapping, ReferenceDataService  # noqa: PLC0415

        # Mock DB rows that would come from v_lob_lookup_active
        fake_rows = [
            {
                "lookup_code": "LOB_RETAIL",
                "lob_name": "Retail Banking",
                "owner": "Jane Doe",
                "classification": "Tier 1 Critical",
            },
            {
                "lookup_code": "LOB_COMMERCIAL",
                "lob_name": "Commercial Banking",
                "owner": "John Smith",
                "classification": "Tier 1 Critical",
            },
            {
                "lookup_code": "LOB_LEGACY",
                "lob_name": "Legacy Systems",
                "owner": "Alice Brown",
                "classification": "Tier 2 Standard",
            },
        ]

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = fake_rows
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        svc.refresh()

        # Cache must contain all three LOB codes
        assert "LOB_RETAIL" in svc._cache, (
            "After refresh(), 'LOB_RETAIL' must be in _cache. "
            "Check that refresh() iterates all rows from v_lob_lookup_active."
        )
        assert "LOB_COMMERCIAL" in svc._cache, (
            "After refresh(), 'LOB_COMMERCIAL' must be in _cache."
        )
        assert "LOB_LEGACY" in svc._cache, (
            "After refresh(), 'LOB_LEGACY' must be in _cache."
        )

        retail = svc._cache["LOB_RETAIL"]
        assert isinstance(retail, LobMapping), (
            f"Cache values must be LobMapping instances, got {type(retail)}"
        )
        assert retail.lob_name == "Retail Banking", (
            f"Expected lob_name='Retail Banking', got {retail.lob_name!r}"
        )
        assert retail.owner == "Jane Doe", (
            f"Expected owner='Jane Doe', got {retail.owner!r}"
        )
        assert retail.classification == "Tier 1 Critical", (
            f"Expected classification='Tier 1 Critical', got {retail.classification!r}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_refresh_updates_last_refresh_timestamp(self) -> None:
        """AC1 [P1]: refresh() updates _last_refresh to approximately now.

        After a successful refresh, _last_refresh must be set to a recent
        datetime so _maybe_refresh() knows the cache is fresh.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        before = datetime.datetime.now()
        svc.refresh()
        after = datetime.datetime.now()

        assert svc._last_refresh >= before, (
            f"_last_refresh ({svc._last_refresh!r}) must be >= time before refresh call ({before!r})"
        )
        assert svc._last_refresh <= after, (
            f"_last_refresh ({svc._last_refresh!r}) must be <= time after refresh call ({after!r})"
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_refresh_closes_db_session(self) -> None:
        """AC1 [P1]: refresh() must close the DB session it opens.

        Per story dev notes: 'refresh() opens its own session using db_factory(),
        closes it when done'. The session close() must be called even on success.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        svc.refresh()

        mock_db.close.assert_called_once_with(), (
            "refresh() must call db.close() after querying v_lob_lookup_active. "
            "DB session leak detected — db.close() was not called."
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_refresh_closes_db_session_on_query_error(self) -> None:
        """AC1 [P1]: refresh() must close DB session even when query raises an exception.

        Per project-context.md: use try-finally pattern for resource cleanup.
        DB session must be closed regardless of success or failure.
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.side_effect = RuntimeError("DB connection lost")
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)

        with pytest.raises(RuntimeError, match="DB connection lost"):
            svc.refresh()

        mock_db.close.assert_called_once_with(), (
            "refresh() must call db.close() in a finally block — "
            "session was not closed after DB error."
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_refresh_queries_active_view_not_raw_table(self) -> None:
        """AC1 [P0]: refresh() must query v_lob_lookup_active, never lob_lookup directly.

        Per project-context.md anti-pattern: 'NEVER query raw tables in the
        serve layer — always use v_*_active views'.
        Per story dev notes: SQL must be
        'SELECT lookup_code, lob_name, owner, classification FROM v_lob_lookup_active'
        """
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_db = MagicMock()
        mock_db.execute.return_value.mappings.return_value.all.return_value = []
        mock_db_factory = MagicMock(return_value=mock_db)

        svc = ReferenceDataService(db_factory=mock_db_factory)
        svc.refresh()

        # Inspect the SQL text passed to db.execute()
        assert mock_db.execute.called, "refresh() must call db.execute() to query the view"
        call_args = mock_db.execute.call_args
        sql_arg = call_args[0][0]  # First positional arg to db.execute()

        # SQLAlchemy text() objects have a .text attribute or can be cast to str
        sql_text = str(sql_arg)
        assert "v_lob_lookup_active" in sql_text, (
            f"refresh() SQL must reference v_lob_lookup_active, not raw lob_lookup table. "
            f"Actual SQL: {sql_text!r}"
        )
        assert "lob_lookup" not in sql_text.replace("v_lob_lookup_active", ""), (
            "refresh() SQL must not reference the raw lob_lookup table directly. "
            "Use the active-record view v_lob_lookup_active."
        )


# ---------------------------------------------------------------------------
# Unit tests — LobMapping dataclass
# ---------------------------------------------------------------------------


class TestLobMappingDataclass:
    """AC2 [P0]: LobMapping dataclass must be importable and frozen.

    TDD RED PHASE: fails until services/reference_data.py is created.
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_lob_mapping_is_importable(self) -> None:
        """AC2 [P0]: LobMapping dataclass must be importable from the service module."""
        from serve.services.reference_data import LobMapping  # noqa: PLC0415
        from dataclasses import fields  # noqa: PLC0415

        field_names = {f.name for f in fields(LobMapping)}
        assert "lob_name" in field_names, (
            "LobMapping must have a 'lob_name' field"
        )
        assert "owner" in field_names, (
            "LobMapping must have an 'owner' field"
        )
        assert "classification" in field_names, (
            "LobMapping must have a 'classification' field"
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_lob_mapping_is_frozen(self) -> None:
        """AC2 [P1]: LobMapping must be a frozen dataclass (immutable).

        Per story dev notes: '@dataclass(frozen=True)' — ensures cache entries
        are not accidentally mutated.
        """
        from serve.services.reference_data import LobMapping  # noqa: PLC0415

        mapping = LobMapping(
            lob_name="Retail Banking",
            owner="Jane Doe",
            classification="Tier 1 Critical",
        )

        with pytest.raises((TypeError, AttributeError)):
            mapping.lob_name = "Mutated"  # type: ignore[misc]

    @pytest.mark.skip(
        reason="RED PHASE: serve/services/reference_data.py does not exist yet. "
        "Remove skip after implementing ReferenceDataService."
    )
    def test_reference_data_service_is_importable(self) -> None:
        """AC1 [P0]: ReferenceDataService class must be importable."""
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        assert ReferenceDataService is not None, (
            "ReferenceDataService must be defined in serve/services/reference_data.py"
        )


# ---------------------------------------------------------------------------
# Integration tests — real Postgres + seeded fixtures
# ---------------------------------------------------------------------------


class TestReferenceDataServiceIntegration:
    """AC1+AC2 [P0]: Integration tests with real Postgres and seeded lob_lookup data.

    TDD RED PHASE: fails until:
      1. lob_lookup table added to ddl.sql
      2. v_lob_lookup_active view added to views.sql
      3. lob_lookup fixture rows added to fixtures.sql
      4. ReferenceDataService implemented in services/reference_data.py

    These tests require @pytest.mark.integration (real Postgres).
    Run: cd dqs-serve && uv run pytest -m integration tests/test_services/test_reference_data.py
    """

    @pytest.mark.skip(
        reason="RED PHASE: lob_lookup table, view, fixtures, and ReferenceDataService "
        "do not exist yet. Remove skip after completing Tasks 1-3 and Task 3 (service)."
    )
    @pytest.mark.integration
    def test_refresh_reads_from_lob_lookup_view(
        self, seeded_client: "TestClient"  # noqa: F821
    ) -> None:
        """AC1 [P0]: ReferenceDataService.refresh() reads from v_lob_lookup_active.

        With seeded fixture data (LOB_RETAIL, LOB_COMMERCIAL, LOB_LEGACY),
        refresh() must populate the cache with all three LOB codes.
        Verifies: DDL table exists, view works, data is queryable.
        """
        from serve.db.engine import SessionLocal  # noqa: PLC0415
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        svc = ReferenceDataService(db_factory=SessionLocal)
        svc.refresh()

        assert "LOB_RETAIL" in svc._cache, (
            "After refresh() with seeded data, 'LOB_RETAIL' must be in cache. "
            "Check: lob_lookup table exists, v_lob_lookup_active view exists, "
            "fixture rows have lookup_code='LOB_RETAIL'."
        )
        assert "LOB_COMMERCIAL" in svc._cache, (
            "After refresh() with seeded data, 'LOB_COMMERCIAL' must be in cache."
        )
        assert "LOB_LEGACY" in svc._cache, (
            "After refresh() with seeded data, 'LOB_LEGACY' must be in cache."
        )

        retail = svc._cache["LOB_RETAIL"]
        assert retail.lob_name == "Retail Banking", (
            f"LOB_RETAIL fixture must map to lob_name='Retail Banking', got {retail.lob_name!r}"
        )
        assert retail.owner == "Jane Doe", (
            f"LOB_RETAIL fixture must have owner='Jane Doe', got {retail.owner!r}"
        )
        assert retail.classification == "Tier 1 Critical", (
            f"LOB_RETAIL fixture must have classification='Tier 1 Critical', "
            f"got {retail.classification!r}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet, and ReferenceDataService is not wired into the lifespan. "
        "Remove skip after completing Tasks 3-8."
    )
    @pytest.mark.integration
    def test_dataset_detail_includes_resolved_names(
        self, seeded_client: "TestClient"  # noqa: F821
    ) -> None:
        """AC2 [P0]: GET /api/datasets/{dataset_id} includes lob_name, owner, classification.

        With seeded data (dq_run rows with lookup_code='LOB_RETAIL' and
        lob_lookup rows mapping LOB_RETAIL → Retail Banking / Jane Doe / Tier 1 Critical),
        the DatasetDetail response must include all three resolved fields.
        Verifies: route injection of ReferenceDataService, resolve() called correctly.
        """
        # Use dataset_id from seeded fixtures — must match a dq_run row with
        # lookup_code='LOB_RETAIL' in fixtures.sql
        # Fixture dataset IDs start at 1 (SERIAL primary key)
        response = seeded_client.get("/api/datasets/1")
        assert response.status_code == 200, (
            f"GET /api/datasets/1 returned {response.status_code}, expected 200. "
            "Check that dataset ID 1 exists in seeded fixtures."
        )

        body = response.json()
        assert "lob_name" in body, (
            f"DatasetDetail response missing 'lob_name' field. "
            f"Got keys: {list(body.keys())}. "
            "Add lob_name field to DatasetDetail Pydantic model."
        )
        assert "owner" in body, (
            f"DatasetDetail response missing 'owner' field. "
            f"Got keys: {list(body.keys())}. "
            "Add owner field to DatasetDetail Pydantic model."
        )
        assert "classification" in body, (
            f"DatasetDetail response missing 'classification' field. "
            f"Got keys: {list(body.keys())}. "
            "Add classification field to DatasetDetail Pydantic model."
        )

        assert body["lob_name"] != "N/A", (
            f"lob_name should be resolved for known LOB_RETAIL, got 'N/A'. "
            "Check that ReferenceDataService is wired in lifespan and seeded data is correct."
        )
        assert body["lob_name"] == "Retail Banking", (
            f"Expected lob_name='Retail Banking' for LOB_RETAIL fixture, "
            f"got {body['lob_name']!r}"
        )
        assert body["owner"] == "Jane Doe", (
            f"Expected owner='Jane Doe' for LOB_RETAIL fixture, got {body['owner']!r}"
        )
        assert body["classification"] == "Tier 1 Critical", (
            f"Expected classification='Tier 1 Critical' for LOB_RETAIL fixture, "
            f"got {body['classification']!r}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet. Remove skip after completing Tasks 3-8."
    )
    @pytest.mark.integration
    def test_dataset_detail_returns_na_for_null_lookup_code(
        self, seeded_client: "TestClient"  # noqa: F821
    ) -> None:
        """AC4 [P1]: GET /api/datasets/{id} returns 'N/A' for datasets with NULL lookup_code.

        Datasets with no lookup_code (lookup_code IS NULL) must return 'N/A'
        for all three resolved fields — not an error, not null.
        """
        # This test requires a seeded dq_run row with lookup_code=NULL
        # If no such row exists in fixtures, this test documents the expected behavior
        # and serves as a reminder to add a fixture row with null lookup_code.
        # For now, assert the shape of N/A response pattern.
        from serve.services.reference_data import LobMapping, ReferenceDataService  # noqa: PLC0415

        mock_db_factory = MagicMock()
        svc = ReferenceDataService(db_factory=mock_db_factory)
        svc._last_refresh = datetime.datetime.now()  # prevent auto-refresh

        result = svc.resolve(None)  # NULL lookup_code mapped to None
        assert result.lob_name == "N/A"
        assert result.owner == "N/A"
        assert result.classification == "N/A"


# ---------------------------------------------------------------------------
# Unit tests — DatasetDetail response shape (route tests, mocked service)
# ---------------------------------------------------------------------------


class TestDatasetDetailWithResolvedFields:
    """AC2 [P0]: GET /api/datasets/{dataset_id} must include lob_name, owner, classification.

    TDD RED PHASE: fails until DatasetDetail Pydantic model and route are updated (Tasks 6-8).
    These are unit-level route tests using the mock DB session from conftest.py.
    """

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet, and ReferenceDataService is not wired. "
        "Remove skip after completing Tasks 3-8 and updating conftest.py."
    )
    def test_dataset_detail_has_lob_name_field(self, client: "TestClient") -> None:  # noqa: F821
        """AC2 [P0]: GET /api/datasets/{dataset_id} response must include 'lob_name' key.

        After story 4.5 implementation, DatasetDetail adds lob_name (str).
        The field must always be present and must be a string (never None or absent).
        """
        response = client.get("/api/datasets/9")
        assert response.status_code == 200

        body = response.json()
        assert "lob_name" in body, (
            f"DatasetDetail response missing 'lob_name' field. "
            f"Got keys: {set(body.keys())}. "
            "Add 'lob_name: str' to DatasetDetail model and populate via ReferenceDataService."
        )
        assert isinstance(body["lob_name"], str), (
            f"'lob_name' must be a str, got {type(body['lob_name'])} value={body['lob_name']!r}. "
            "resolve() always returns a str — never None."
        )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet. Remove skip after completing Tasks 3-8."
    )
    def test_dataset_detail_has_owner_field(self, client: "TestClient") -> None:  # noqa: F821
        """AC2 [P0]: GET /api/datasets/{dataset_id} response must include 'owner' key.

        The 'owner' field comes from ReferenceDataService.resolve(lookup_code).owner.
        Must always be a string — N/A for unknown codes.
        """
        response = client.get("/api/datasets/9")
        assert response.status_code == 200

        body = response.json()
        assert "owner" in body, (
            f"DatasetDetail response missing 'owner' field. "
            f"Got keys: {set(body.keys())}. "
            "Add 'owner: str' to DatasetDetail model."
        )
        assert isinstance(body["owner"], str), (
            f"'owner' must be a str, got {type(body['owner'])} value={body['owner']!r}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet. Remove skip after completing Tasks 3-8."
    )
    def test_dataset_detail_has_classification_field(self, client: "TestClient") -> None:  # noqa: F821
        """AC2 [P0]: GET /api/datasets/{dataset_id} response must include 'classification' key.

        The 'classification' field comes from ReferenceDataService.resolve(lookup_code).classification.
        Must always be a string — N/A for unknown codes.
        """
        response = client.get("/api/datasets/9")
        assert response.status_code == 200

        body = response.json()
        assert "classification" in body, (
            f"DatasetDetail response missing 'classification' field. "
            f"Got keys: {set(body.keys())}. "
            "Add 'classification: str' to DatasetDetail model."
        )
        assert isinstance(body["classification"], str), (
            f"'classification' must be a str, got {type(body['classification'])}"
        )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet. Remove skip after completing Tasks 3-8."
    )
    def test_dataset_detail_resolved_fields_are_snake_case(self, client: "TestClient") -> None:  # noqa: F821
        """AC2 [P1]: New resolved fields must be snake_case per project-context.md.

        Per project-context.md: 'All API responses use snake_case JSON keys'.
        lob_name, owner, classification are all snake_case.
        """
        response = client.get("/api/datasets/9")
        assert response.status_code == 200

        body = response.json()
        new_fields = {"lob_name", "owner", "classification"}
        for field in new_fields:
            assert field in body, (
                f"Field '{field}' missing from DatasetDetail response. "
                "All three resolved fields must be present."
            )
            assert field == field.lower(), (
                f"Field '{field}' is not snake_case — violates project-context.md rule."
            )

    @pytest.mark.skip(
        reason="RED PHASE: DatasetDetail does not have lob_name/owner/classification "
        "fields yet. Remove skip after completing Tasks 3-8."
    )
    def test_dataset_detail_resolved_fields_never_null(self, client: "TestClient") -> None:  # noqa: F821
        """AC4 [P0]: lob_name, owner, classification must never be null in the response.

        resolve() always returns LobMapping with string values — never None.
        DatasetDetail model must declare these as 'str', not 'Optional[str]'.
        """
        response = client.get("/api/datasets/9")
        assert response.status_code == 200

        body = response.json()
        for field in ("lob_name", "owner", "classification"):
            assert field in body, f"Field '{field}' missing from response"
            assert body[field] is not None, (
                f"Field '{field}' is None in response — must be a str. "
                "ReferenceDataService.resolve() returns 'N/A' for unknown codes, never None."
            )


# ---------------------------------------------------------------------------
# Unit tests — dependencies.py module
# ---------------------------------------------------------------------------


class TestGetReferenceDataServiceDependency:
    """AC2 [P1]: get_reference_data_service dependency must be importable and work correctly.

    TDD RED PHASE: fails until dependencies.py is created (Task 5).
    """

    @pytest.mark.skip(
        reason="RED PHASE: serve/dependencies.py does not exist yet. "
        "Remove skip after completing Task 5."
    )
    def test_dependencies_module_is_importable(self) -> None:
        """AC2 [P1]: serve.dependencies module must be importable."""
        from serve import dependencies  # noqa: PLC0415

        assert hasattr(dependencies, "get_reference_data_service"), (
            "serve.dependencies must define get_reference_data_service function. "
            "Per story dev notes: create dependencies.py to avoid circular imports."
        )

    @pytest.mark.skip(
        reason="RED PHASE: serve/dependencies.py does not exist yet. "
        "Remove skip after completing Task 5."
    )
    def test_get_reference_data_service_returns_service_from_app_state(self) -> None:
        """AC2 [P1]: get_reference_data_service returns the ReferenceDataService from app.state.

        The dependency must retrieve app.state.reference_data via the Request object.
        This is a singleton pattern — not a per-request instantiation.
        """
        from fastapi import Request  # noqa: PLC0415
        from serve.dependencies import get_reference_data_service  # noqa: PLC0415
        from serve.services.reference_data import ReferenceDataService  # noqa: PLC0415

        mock_svc = MagicMock(spec=ReferenceDataService)

        # Create a mock Request with app.state.reference_data set
        mock_app = MagicMock()
        mock_app.state.reference_data = mock_svc
        mock_request = MagicMock(spec=Request)
        mock_request.app = mock_app

        result = get_reference_data_service(mock_request)

        assert result is mock_svc, (
            "get_reference_data_service() must return request.app.state.reference_data. "
            f"Got {result!r} instead of the mock service."
        )
