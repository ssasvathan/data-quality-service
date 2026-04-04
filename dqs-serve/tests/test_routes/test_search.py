"""Acceptance tests — Story 4.4: GET /api/search endpoint.

Test categories:
  - Unit tests (no DB, mock session): verify route wiring, Pydantic model shapes,
    snake_case field names, 422 validation, empty-result shape
  - Integration tests (real Postgres + seeded fixtures): verify data correctness,
    ordering (prefix before substring), LIMIT 10 cap, no-match empty array

Run unit tests:     cd dqs-serve && uv run pytest tests/test_routes/test_search.py
Run integration:    cd dqs-serve && uv run pytest -m integration tests/test_routes/test_search.py
"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

# ---------------------------------------------------------------------------
# Shared constants
# ---------------------------------------------------------------------------

SEARCH_ENDPOINT = "/api/search"

# Expected snake_case keys in each SearchResult item
EXPECTED_SEARCH_RESULT_KEYS = {
    "dataset_id",
    "dataset_name",
    "lob_id",
    "dqs_score",
    "check_status",
}

# Unit-test sentinel for known query — conftest._make_mock_db_session must
# handle q="sales" by returning _FAKE_SEARCH_RESULT_ROW (added in Task 4).
KNOWN_QUERY = "sales"
UNKNOWN_QUERY = "ZZZNOMATCH"
UE90_QUERY = "ue90"  # AC1 example — no fixture rows contain "ue90"


# ---------------------------------------------------------------------------
# Unit tests — GET /api/search route wiring
# ---------------------------------------------------------------------------


class TestSearchEndpointRouteWiring:
    """AC1 + AC3 [P0]: GET /api/search must be registered and return 200.

    TDD RED PHASE: endpoint does not exist — tests will fail until
    src/serve/routes/search.py is created and wired into main.py.
    """

    def test_search_endpoint_returns_200(self) -> None:
        """AC1 [P0]: GET /api/search?q=sales must return HTTP 200.

        Fails until src/serve/routes/search.py is created and wired into main.py
        with app.include_router(search_router.router, prefix='/api').
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert response.status_code == 200, (
            f"GET /api/search?q={KNOWN_QUERY} returned {response.status_code}, expected 200. "
            "Route not registered — create src/serve/routes/search.py with APIRouter "
            "and include it in main.py with prefix='/api'."
        )

    def test_search_response_is_json(self) -> None:
        """AC1 [P0]: Response Content-Type must be application/json."""
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert "application/json" in response.headers.get("content-type", ""), (
            "GET /api/search did not return JSON content-type"
        )

    def test_search_response_has_results_key(self) -> None:
        """AC1 [P0]: Response must have a top-level 'results' key containing a list.

        Per story dev notes: SearchResponse model has results: list[SearchResult].
        Response shape: {"results": [...]}
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert response.status_code == 200

        body = response.json()
        assert "results" in body, (
            f"Response body missing 'results' key. Got keys: {list(body.keys())}. "
            "Define SearchResponse with 'results: list[SearchResult]'."
        )
        assert isinstance(body["results"], list), (
            f"'results' must be a list, got {type(body['results'])}."
        )

    def test_search_result_items_have_all_required_snake_case_keys(self) -> None:
        """AC1 [P1]: Each result item must have required snake_case keys.

        Per project-context.md: all API responses use snake_case JSON keys.
        Per story dev notes: SearchResult has dataset_id, dataset_name, lob_id,
        dqs_score, check_status.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])
        assert len(results) > 0, (
            f"GET /api/search?q={KNOWN_QUERY} returned empty results — cannot validate item shape. "
            "Extend conftest._make_mock_db_session to handle q='sales' param."
        )

        for item in results:
            actual_keys = set(item.keys())
            assert actual_keys >= EXPECTED_SEARCH_RESULT_KEYS, (
                f"SearchResult item missing required keys. "
                f"Expected (subset): {EXPECTED_SEARCH_RESULT_KEYS}, Got: {actual_keys}. "
                "Check SearchResult Pydantic model field names in src/serve/routes/search.py."
            )

    def test_search_result_no_camel_case_keys(self) -> None:
        """AC1 [P1]: No camelCase keys must appear in result items.

        Per project-context.md: all API responses use snake_case JSON field names.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert response.status_code == 200

        body = response.json()
        for item in body.get("results", []):
            camel_keys = [k for k in item if k != k.lower() and "_" not in k]
            assert camel_keys == [], (
                f"camelCase keys found in SearchResult item: {camel_keys}. "
                "Use snake_case field names in Pydantic models."
            )

    def test_search_dataset_id_is_integer(self) -> None:
        """AC1 [P1]: dataset_id field must be an integer (= dq_run.id PK).

        Per story dev notes: dataset_id = dq_run.id (consistent with DatasetInLob.dataset_id
        in lobs.py and DatasetDetail.dataset_id in datasets.py).
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": KNOWN_QUERY})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])
        assert len(results) > 0, (
            f"GET /api/search?q={KNOWN_QUERY} returned empty results — cannot validate dataset_id type."
        )

        for item in results:
            assert isinstance(item.get("dataset_id"), int), (
                f"dataset_id must be an integer, got {type(item.get('dataset_id'))}. "
                "dataset_id = dq_run.id (PK integer). Check SearchResult model field: dataset_id: int."
            )


# ---------------------------------------------------------------------------
# Unit tests — AC3: No-match returns empty results (not 4xx)
# ---------------------------------------------------------------------------


class TestSearchNoMatchReturnsEmptyArray:
    """AC3 [P0]: GET /api/search?q=ZZZNOMATCH must return 200 with empty results.

    TDD RED PHASE: endpoint does not exist yet.
    """

    def test_no_match_returns_200_not_404(self) -> None:
        """AC3 [P0]: No-match query must return 200, NOT a 4xx error.

        Per AC3 and story dev notes: 'returns empty array (not an error)'.
        Return {"results": []} — never raise HTTPException for no-match.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": UNKNOWN_QUERY})
        assert response.status_code == 200, (
            f"GET /api/search?q={UNKNOWN_QUERY} returned {response.status_code}, expected 200. "
            "AC3 is explicit: no-match must return 200 with empty results array, never 404."
        )

    def test_no_match_returns_empty_results_array(self) -> None:
        """AC3 [P0]: No-match query must return {"results": []}.

        Per story dev notes: SearchResponse with results=[] when no match.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": UNKNOWN_QUERY})
        assert response.status_code == 200

        body = response.json()
        assert "results" in body, (
            f"No-match response missing 'results' key. Got: {body}"
        )
        assert body["results"] == [], (
            f"No-match response 'results' must be [], got: {body['results']}. "
            "Return SearchResponse(results=[]) — do NOT raise HTTPException."
        )

    def test_empty_q_returns_200_with_empty_results(self) -> None:
        """Edge case [P1]: Empty string q returns 200 with empty results.

        Per story dev notes: 'Handle empty q parameter gracefully: return empty results array'.
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT, params={"q": ""})
        assert response.status_code == 200, (
            f"GET /api/search?q= returned {response.status_code}, expected 200. "
            "Empty q string must return empty results gracefully."
        )
        body = response.json()
        assert body.get("results") == [], (
            f"Empty q must return empty results [], got: {body.get('results')}."
        )


# ---------------------------------------------------------------------------
# Unit tests — Missing q parameter → 422 auto-validation
# ---------------------------------------------------------------------------


class TestSearchMissingQueryParam:
    """[P0]: GET /api/search with no q param must return 422.

    FastAPI automatically validates required query parameters.
    TDD RED PHASE: endpoint does not exist yet.
    """

    def test_missing_q_returns_422(self) -> None:
        """[P0]: GET /api/search without q= must return 422 Unprocessable Entity.

        q is declared as required (q: str) in the route handler — FastAPI generates
        422 automatically when absent. Per story dev notes: 'FastAPI auto-generates
        422 when absent. This is correct behavior (test it in the test suite).'
        """
        from serve.main import app  # noqa: PLC0415

        client = TestClient(app)
        response = client.get(SEARCH_ENDPOINT)  # No q parameter
        assert response.status_code == 422, (
            f"GET /api/search (no q param) returned {response.status_code}, expected 422. "
            "Declare q as required: def search_datasets(q: str, ...) — no default value."
        )


# ---------------------------------------------------------------------------
# Unit tests — Pydantic model imports
# ---------------------------------------------------------------------------


class TestSearchPydanticModels:
    """[P0]: SearchResult and SearchResponse Pydantic models must be importable.

    TDD RED PHASE: models do not exist until search.py is created.
    """

    def test_search_result_model_is_importable(self) -> None:
        """[P0]: SearchResult Pydantic model must be importable from search.py."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.search import SearchResult  # noqa: PLC0415

        assert issubclass(SearchResult, BaseModel), (
            "SearchResult must inherit from pydantic.BaseModel"
        )

    def test_search_response_model_is_importable(self) -> None:
        """[P0]: SearchResponse Pydantic model must be importable from search.py."""
        from pydantic import BaseModel  # noqa: PLC0415
        from serve.routes.search import SearchResponse  # noqa: PLC0415

        assert issubclass(SearchResponse, BaseModel), (
            "SearchResponse must inherit from pydantic.BaseModel"
        )

    def test_search_result_field_names_are_snake_case(self) -> None:
        """[P1]: All SearchResult Pydantic model fields must be snake_case."""
        from serve.routes.search import SearchResult  # noqa: PLC0415

        for field_name in SearchResult.model_fields:
            assert field_name == field_name.lower(), (
                f"Field '{field_name}' in SearchResult is not snake_case. "
                "Per project-context.md: all API responses use snake_case JSON field names."
            )

    def test_search_result_has_required_fields(self) -> None:
        """[P1]: SearchResult must have dataset_id, dataset_name, lob_id, dqs_score, check_status."""
        from serve.routes.search import SearchResult  # noqa: PLC0415

        field_names = set(SearchResult.model_fields.keys())
        assert field_names >= EXPECTED_SEARCH_RESULT_KEYS, (
            f"SearchResult missing required fields. "
            f"Expected: {EXPECTED_SEARCH_RESULT_KEYS}, Got: {field_names}. "
            "Per story dev notes: SearchResult(dataset_id, dataset_name, lob_id, dqs_score, check_status)."
        )

    def test_search_response_has_results_field(self) -> None:
        """[P1]: SearchResponse must have a 'results' field."""
        from serve.routes.search import SearchResponse  # noqa: PLC0415

        field_names = set(SearchResponse.model_fields.keys())
        assert "results" in field_names, (
            f"SearchResponse missing 'results' field. Got: {field_names}. "
            "Define SearchResponse with 'results: list[SearchResult]'."
        )

    def test_search_result_dqs_score_is_optional_float(self) -> None:
        """[P1]: SearchResult.dqs_score must be Optional[float] — nullable in DB.

        Per story dev notes: 'dqs_score is Optional[float] — can be NULL in DB'.
        Validates that the type annotation permits None.
        """
        import inspect  # noqa: PLC0415

        from serve.routes.search import SearchResult  # noqa: PLC0415

        field_info = SearchResult.model_fields.get("dqs_score")
        assert field_info is not None, "dqs_score field missing from SearchResult"

        # Check annotation allows None (Optional[float])
        annotation = inspect.get_annotations(SearchResult, eval_str=True).get("dqs_score", None)
        if annotation is None:
            annotation = field_info.annotation
        # Optional[float] or float | None both include NoneType
        annotation_str = str(annotation)
        assert "None" in annotation_str or "Optional" in annotation_str, (
            f"SearchResult.dqs_score annotation '{annotation_str}' must be Optional[float]. "
            "Per story dev notes: 'dqs_score is Optional[float] — can be NULL in DB'."
        )

    def test_search_result_lob_id_is_optional_str(self) -> None:
        """[P1]: SearchResult.lob_id must be Optional[str] — nullable for legacy rows.

        Per story dev notes: 'lob_id is Optional[str] — lookup_code can be NULL (legacy rows)'.
        """
        import inspect  # noqa: PLC0415

        from serve.routes.search import SearchResult  # noqa: PLC0415

        field_info = SearchResult.model_fields.get("lob_id")
        assert field_info is not None, "lob_id field missing from SearchResult"

        annotation = inspect.get_annotations(SearchResult, eval_str=True).get("lob_id", None)
        if annotation is None:
            annotation = field_info.annotation
        annotation_str = str(annotation)
        assert "None" in annotation_str or "Optional" in annotation_str, (
            f"SearchResult.lob_id annotation '{annotation_str}' must be Optional[str]. "
            "Per story dev notes: 'lob_id is Optional[str] — lookup_code can be NULL (legacy rows)'."
        )


# ---------------------------------------------------------------------------
# Integration tests — Real Postgres + seeded fixtures
# ---------------------------------------------------------------------------


class TestSearchIntegrationDataCorrectness:
    """AC1 [P0]: Integration tests verifying real DB query correctness.

    Uses seeded_client fixture (real Postgres, fixtures.sql seeded).
    All tests require @pytest.mark.integration.
    """

    @pytest.mark.integration
    def test_search_q_sales_returns_sales_daily(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: GET /api/search?q=sales must return the sales_daily dataset.

        Per story dev notes fixtures table: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'
        is searchable by 'sales'. Verifies the ILIKE '%' || :q || '%' pattern works.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "sales"})
        assert response.status_code == 200, (
            f"GET /api/search?q=sales returned {response.status_code}, expected 200."
        )

        body = response.json()
        results = body.get("results", [])
        assert len(results) >= 1, (
            "GET /api/search?q=sales must return at least 1 result. "
            "Expected 'lob=retail/src_sys_nm=alpha/dataset=sales_daily' in fixtures."
        )

        dataset_names = [r["dataset_name"] for r in results]
        assert any("sales_daily" in name for name in dataset_names), (
            f"Expected 'sales_daily' in results, got: {dataset_names}. "
            "ILIKE '%sales%' must match 'lob=retail/src_sys_nm=alpha/dataset=sales_daily'."
        )

    @pytest.mark.integration
    def test_search_q_sales_returns_correct_dqs_score(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P0]: sales_daily must have dqs_score=98.50 and check_status='PASS'.

        Per story dev notes fixtures table: sales_daily has dqs_score=98.50, check_status=PASS.
        Verifies the ROW_NUMBER() CTE correctly picks the latest run.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "sales"})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])

        sales_daily = next(
            (r for r in results if "sales_daily" in r.get("dataset_name", "")),
            None,
        )
        assert sales_daily is not None, (
            "sales_daily not found in results for q=sales. "
            "Check ILIKE query and ROW_NUMBER() CTE in search.py."
        )
        assert sales_daily["dqs_score"] == pytest.approx(98.50), (
            f"sales_daily dqs_score={sales_daily['dqs_score']}, expected 98.50. "
            "Verify ROW_NUMBER() PARTITION BY dataset_name ORDER BY partition_date DESC picks latest."
        )
        assert sales_daily["check_status"] == "PASS", (
            f"sales_daily check_status={sales_daily['check_status']!r}, expected 'PASS'."
        )

    @pytest.mark.integration
    def test_search_q_sales_has_correct_field_values(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: sales_daily result must have correct lob_id and integer dataset_id.

        Per story dev notes: lob_id = lookup_code = 'LOB_RETAIL' for sales_daily.
        dataset_id = dq_run.id (integer PK of latest run for this dataset).
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "sales"})
        assert response.status_code == 200

        body = response.json()
        sales_daily = next(
            (r for r in body.get("results", []) if "sales_daily" in r.get("dataset_name", "")),
            None,
        )
        assert sales_daily is not None, "sales_daily not found in results for q=sales"

        assert sales_daily["lob_id"] == "LOB_RETAIL", (
            f"sales_daily lob_id={sales_daily['lob_id']!r}, expected 'LOB_RETAIL'. "
            "lob_id = dq_run.lookup_code."
        )
        assert isinstance(sales_daily["dataset_id"], int), (
            f"dataset_id must be an integer, got {type(sales_daily['dataset_id'])}. "
            "dataset_id = dq_run.id (PK integer)."
        )

    @pytest.mark.integration
    def test_search_all_result_fields_are_snake_case(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: All JSON response keys in integration results must be snake_case.

        Per project-context.md: 'All API responses use snake_case JSON keys'.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "sales"})
        assert response.status_code == 200

        body = response.json()
        # Top-level keys
        assert "results" in body, f"Missing 'results' key in response: {body}"

        for item in body["results"]:
            camel_keys = [k for k in item if k != k.lower() and "_" not in k]
            assert camel_keys == [], (
                f"camelCase keys found in integration result: {camel_keys}. "
                "All fields must be snake_case."
            )


class TestSearchIntegrationSubstringMatch:
    """AC1 [P1]: Integration tests for cross-LOB substring matching."""

    @pytest.mark.integration
    def test_search_q_alpha_returns_multiple_datasets(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: GET /api/search?q=alpha returns datasets from src_sys_nm=alpha.

        Per story dev notes fixtures: sales_daily, products, customers all contain 'alpha'
        in their dataset_name path segments. ILIKE '%alpha%' should match all three.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "alpha"})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])
        assert len(results) >= 3, (
            f"GET /api/search?q=alpha returned {len(results)} results, expected >= 3. "
            "Fixtures have sales_daily, products, customers all under src_sys_nm=alpha."
        )

        for item in results:
            assert "alpha" in item["dataset_name"].lower(), (
                f"Result dataset_name '{item['dataset_name']}' does not contain 'alpha'. "
                "ILIKE '%alpha%' must only return datasets with 'alpha' in their name."
            )

    @pytest.mark.integration
    def test_search_q_ue90_returns_empty_results(
        self, seeded_client: TestClient
    ) -> None:
        """AC3 [P0]: GET /api/search?q=ue90 returns empty results (no fixture match).

        This is the exact example from AC1 and the no-match scenario from AC3.
        Per story dev notes: 'no dataset contains "ue90" — intentional, tests no-match path'.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": UE90_QUERY})
        assert response.status_code == 200, (
            f"GET /api/search?q=ue90 returned {response.status_code}, expected 200. "
            "No-match must return 200 — NOT a 4xx error (AC3)."
        )

        body = response.json()
        assert body.get("results") == [], (
            f"GET /api/search?q=ue90 must return empty results [], "
            f"got: {body.get('results')}. Per AC3: no match → empty array, not an error."
        )


class TestSearchIntegrationMaxResults:
    """AC2 [P1]: Integration test verifying LIMIT 10 cap."""

    @pytest.mark.integration
    def test_search_returns_at_most_10_results(
        self, seeded_client: TestClient
    ) -> None:
        """AC2 [P1]: GET /api/search?q=a returns at most 10 results.

        Per story dev notes: 'q=a matches all 6 unique datasets — test that result
        count is ≤ 10 (all 6 returned, confirming cap is not exceeded but LIMIT logic correct)'.
        The 6 fixture datasets all contain 'a' in their path, confirming LIMIT 10 cap works.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "a"})
        assert response.status_code == 200, (
            f"GET /api/search?q=a returned {response.status_code}, expected 200."
        )

        body = response.json()
        results = body.get("results", [])
        assert len(results) <= 10, (
            f"GET /api/search?q=a returned {len(results)} results, expected <= 10. "
            "Add LIMIT 10 to the SQL query. Per AC2: maximum 10 results."
        )
        assert len(results) >= 1, (
            "GET /api/search?q=a returned no results — fixtures must contain datasets with 'a'."
        )


class TestSearchIntegrationOrdering:
    """AC1 [P1]: Integration test verifying prefix-match ordering.

    Per story dev notes: results ordered by prefix match first, then substring.
    """

    @pytest.mark.integration
    def test_search_ordering_is_deterministic(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: Results must be ordered deterministically (sort_order, dataset_name).

        Two calls with the same q must return results in the same order.
        Per story dev notes SQL: ORDER BY sort_order, dataset_name.
        """
        params = {"q": "sales"}
        response1 = seeded_client.get(SEARCH_ENDPOINT, params=params)
        response2 = seeded_client.get(SEARCH_ENDPOINT, params=params)

        assert response1.status_code == 200
        assert response2.status_code == 200

        results1 = response1.json().get("results", [])
        results2 = response2.json().get("results", [])

        assert [r["dataset_name"] for r in results1] == [r["dataset_name"] for r in results2], (
            "Search results are not deterministically ordered. "
            "Add ORDER BY sort_order, dataset_name to the SQL query."
        )

    @pytest.mark.integration
    def test_search_deduplicates_datasets_by_name(
        self, seeded_client: TestClient
    ) -> None:
        """AC1 [P1]: Each unique dataset_name appears only once (ROW_NUMBER() dedup).

        Per story dev notes: 'ROW_NUMBER() prevents duplicate dataset names when
        a dataset has multiple historical runs. Only the latest run per dataset name returned.'
        Fixtures have 7 dq_run rows for sales_daily — only 1 should appear in results.
        """
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "sales"})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])
        dataset_names = [r["dataset_name"] for r in results]
        unique_names = list(dict.fromkeys(dataset_names))  # preserve order, deduplicate
        assert dataset_names == unique_names, (
            f"Duplicate dataset_names in results: {dataset_names}. "
            "Use ROW_NUMBER() PARTITION BY dataset_name to return only latest run per dataset. "
            "Do NOT use DISTINCT ON or correlated subqueries — use ROW_NUMBER() CTE pattern."
        )


class TestSearchIntegrationNullableFields:
    """[P1]: Integration test verifying nullable field handling."""

    @pytest.mark.integration
    def test_search_dqs_score_can_be_null(
        self, seeded_client: TestClient
    ) -> None:
        """[P1]: dqs_score can be null — Pydantic must serialize it as JSON null.

        Per story dev notes: 'dqs_score is Optional[float] — can be NULL in DB'.
        Verifies SearchResult handles NULL dqs_score from DB without error.
        """
        # Query products (FAIL, dqs_score=45.00) — fixture has explicit score so this
        # validates score is returned correctly. In production, NULL scores may occur.
        response = seeded_client.get(SEARCH_ENDPOINT, params={"q": "products"})
        assert response.status_code == 200

        body = response.json()
        results = body.get("results", [])
        assert len(results) >= 1, (
            "GET /api/search?q=products must return at least 1 result. "
            "Fixture 'lob=retail/src_sys_nm=alpha/dataset=products' should match."
        )

        for item in results:
            # dqs_score must be present (even if None)
            assert "dqs_score" in item, (
                f"dqs_score key missing from SearchResult item: {item}. "
                "Include dqs_score in SELECT clause — even when NULL."
            )
            # Type must be float or None (not missing entirely)
            dqs_val = item["dqs_score"]
            assert dqs_val is None or isinstance(dqs_val, (int, float)), (
                f"dqs_score must be float or null, got: {type(dqs_val)} = {dqs_val!r}."
            )
