# Architecture — dqs-dashboard

_React 19 / TypeScript 5 / Vite 8 / MUI 7 — Data quality visualization SPA._

---

## Overview

dqs-dashboard is a single-page application providing a 3-level drill-down (Summary → LOB → Dataset) plus executive reporting. It uses TanStack Query for API data fetching, MUI 7 with a custom design system, skeleton loading (never spinners), and accessibility-first design.

**Entry point:** `src/main.tsx`

---

## Routing

| Route | Component | Purpose |
|-------|-----------|---------|
| `/` or `/summary` | SummaryPage | Landing — LOB card grid with aggregate stats |
| `/lobs/:lobId` | LobDetailPage | LOB drill-down — MUI X DataGrid per dataset |
| `/datasets/:datasetId` | DatasetDetailPage | Dataset detail — master-detail split layout |
| `/exec` | ExecReportPage | Executive report — scorecard, improvement, source systems |

All routes nested under `AppLayout` (fixed header + breadcrumbs + search + time range toggle).

---

## Component Architecture

```
App.tsx
├── QueryClientProvider (TanStack Query)
├── ThemeProvider (MUI 7 custom theme)
├── TimeRangeProvider (7d/30d/90d global state)
└── BrowserRouter
    └── AppLayout
        ├── Skip-to-main link
        ├── AppBreadcrumbs (derived from route)
        ├── LastUpdatedIndicator (amber if >= 24h)
        ├── RunFailedBanner (dismissible)
        ├── GlobalSearch (Autocomplete, Ctrl+K)
        ├── TimeRange ToggleButtonGroup
        └── <Routes>
            ├── SummaryPage → DatasetCard[] (3-col grid)
            ├── LobDetailPage → MUI X DataGrid
            ├── DatasetDetailPage → master-detail split
            └── ExecReportPage → 3 data tables
```

---

## API Layer

**`api/client.ts`** — Minimal `apiFetch<T>()` wrapper. All requests proxied through Vite (`/api` → `http://localhost:8000`).

**TanStack Query hooks** (`api/queries.ts`):

| Hook | Endpoint | Time-Parameterized |
|------|----------|--------------------|
| `useSummary` | GET /api/summary | No (fixed 7d server-side) |
| `useLobs` | GET /api/lobs?time_range | Yes |
| `useLobDatasets` | GET /api/lobs/{id}/datasets?time_range | Yes |
| `useDatasetDetail` | GET /api/datasets/{id} | No (point-in-time) |
| `useDatasetMetrics` | GET /api/datasets/{id}/metrics | No |
| `useDatasetTrend` | GET /api/datasets/{id}/trend?time_range | Yes |
| `useSearch` | GET /api/search?q= | No (staleTime: 30s, min 2 chars) |
| `useExecutiveReport` | GET /api/executive/report | No (fixed 3-month window) |

**Cache invalidation:** Changing the global time range calls `queryClient.invalidateQueries()` — all time-parameterized queries refetch.

---

## State Management

- **Global:** TimeRangeContext (`7d` | `30d` | `90d`) — changes invalidate all TanStack Query caches
- **Component-level:** UI ephemera only (search input, banner dismiss, copy feedback)
- **No Redux/Zustand** — Context + React Query is sufficient

---

## Theme and Design System

**MUI 7 custom theme** (`theme.ts`):

**Semantic colors for DQS scores:**
- Score >= 80: Success green (`#2E7D32` / `#E8F5E9`)
- Score 60-79: Warning amber (`#ED6C02` / `#FFF3E0`)
- Score < 60: Error red (`#D32F2F` / `#FFEBEE`)

**Custom typography variants:**
- `score`: 28px/700 — large DQS score display
- `scoreSm`: 18px/700 — medium DQS score
- `mono`: 13px/400 + monospace — dataset names, system IDs

**Component overrides:**
- MuiCard: elevation=0, 1px border, 8px radius, no shadow (muted palette per UX spec)

**Helper functions:** `getDqsColor(score)`, `getDqsColorLight(score)` for threshold-based coloring.

---

## Reusable Components

| Component | Props | Purpose |
|-----------|-------|---------|
| **DqsScoreChip** | score, previousScore, size (lg/md/sm), showTrend | Score badge with semantic color + optional trend arrow |
| **TrendSparkline** | data[], size (lg/md/mini), color, showBaseline | Recharts LineChart with optional y=80 baseline |
| **DatasetCard** | lobName, datasetCount, dqsScore, trendData, statusCounts, onClick | LOB summary card (keyboard accessible) |
| **DatasetInfoPanel** | dataset: DatasetDetail | Metadata panel with dl/dt/dd, copyable HDFS path |

---

## Accessibility

- Skip-to-main-content link (visible on Tab focus)
- `aria-live` region for data update announcements
- `role="button"` + `tabIndex=0` on DatasetCard (Enter/Space activation)
- `role="img"` + `aria-label` on TrendSparkline
- Semantic `<nav aria-label="breadcrumb">`, `<dl>/<dt>/<dd>` for data
- axe-core accessibility auditing in development mode

---

## Loading and Error States

- **Loading:** Skeleton elements matching target layout (never spinning loaders)
- **Stale refetch:** `isFetching` opacity transition (0.5 during refetch)
- **Errors:** Component-level "Failed to load" with Retry button — never full-page crashes
- **Partial failures:** Dataset detail page handles trend/metrics fetch failure independently

---

## Test Strategy

- **Vitest** + React Testing Library + jest-dom
- Fresh QueryClient per test (staleTime: 0)
- Render helpers with ThemeProvider + QueryClientProvider + MemoryRouter
- `vi.mock` for API calls and component isolation
- 14 test files covering all pages, components, layouts, and context
- Tests in `tests/` directory mirroring `src/` structure
