import React from 'react'
import { useParams, useNavigate } from 'react-router'
import { Box, Typography, Skeleton, Chip, Button } from '@mui/material'
import { DataGrid } from '@mui/x-data-grid'
import type { GridColDef, GridRowParams } from '@mui/x-data-grid'
import { useLobDatasets } from '../api/queries'
import { useTimeRange } from '../context/TimeRangeContext'
import { DqsScoreChip, TrendSparkline } from '../components'
import type { DatasetInLob } from '../api/types'

// ---------------------------------------------------------------------------
// StatusChip helper — renders colored chip for PASS/WARN/FAIL, or dash for null
// Used for the overall check_status column.
// ---------------------------------------------------------------------------

function StatusChip({ status }: { status: 'PASS' | 'WARN' | 'FAIL' | null }) {
  if (status === null) {
    return (
      <Typography variant="caption" color="text.disabled">
        —
      </Typography>
    )
  }
  if (status === 'PASS') {
    return (
      <Chip
        label="PASS"
        size="small"
        sx={{ bgcolor: 'success.light', color: 'success.main' }}
      />
    )
  }
  if (status === 'WARN') {
    return (
      <Chip
        label="WARN"
        size="small"
        sx={{ bgcolor: 'warning.light', color: 'warning.main' }}
      />
    )
  }
  if (status === 'FAIL') {
    return (
      <Chip
        label="FAIL"
        size="small"
        sx={{ bgcolor: 'error.light', color: 'error.main' }}
      />
    )
  }
  // Default — unknown status, gray chip
  return <Chip label={status} size="small" />
}

// ---------------------------------------------------------------------------
// StatusDot helper — compact colored indicator for individual check columns
// (freshness, volume, schema). Uses a colored dot + aria-label instead of
// visible text to avoid duplicate PASS/WARN/FAIL text in the DOM.
// Null still renders "—" typography per UX spec.
// ---------------------------------------------------------------------------

function StatusDot({ status }: { status: 'PASS' | 'WARN' | 'FAIL' | null }) {
  if (status === null) {
    return (
      <Typography variant="caption" color="text.disabled">
        —
      </Typography>
    )
  }
  const color =
    status === 'PASS'
      ? 'success.main'
      : status === 'WARN'
        ? 'warning.main'
        : status === 'FAIL'
          ? 'error.main'
          : 'text.disabled'

  const ariaLabel =
    status === 'PASS' || status === 'WARN' || status === 'FAIL'
      ? status
      : `Unknown status: ${status}`

  return (
    <Box
      component="span"
      aria-label={ariaLabel}
      sx={{
        display: 'inline-block',
        width: 10,
        height: 10,
        borderRadius: '50%',
        bgcolor: color,
      }}
    />
  )
}

// ---------------------------------------------------------------------------
// LobDetailPage — drill-down view for a single Line of Business (Level 2)
// ---------------------------------------------------------------------------

export default function LobDetailPage(): React.ReactElement {
  const { lobId } = useParams<{ lobId: string }>()
  const navigate = useNavigate()
  const { timeRange } = useTimeRange()

  // ---------------------------------------------------------------------------
  // Column definitions for MUI X DataGrid — defined inside component after
  // useNavigate() so they can reference component-local functions cleanly.
  // ---------------------------------------------------------------------------

  const columns: GridColDef<DatasetInLob>[] = [
    {
      field: 'dataset_name',
      headerName: 'Dataset',
      width: 240,
      renderCell: (params) => (
        <Typography variant="mono">{params.row.dataset_name}</Typography>
      ),
    },
    {
      field: 'dqs_score',
      headerName: 'DQS Score',
      width: 120,
      renderCell: (params) => (
        <DqsScoreChip score={params.row.dqs_score ?? undefined} size="sm" showTrend={false} />
      ),
    },
    {
      field: 'trend',
      headerName: 'Trend',
      width: 100,
      sortable: false,
      renderCell: (params) => (
        <TrendSparkline data={params.row.trend} size="mini" />
      ),
    },
    {
      field: 'freshness_status',
      headerName: 'Freshness',
      width: 100,
      renderCell: (params) => <StatusDot status={params.row.freshness_status} />,
    },
    {
      field: 'volume_status',
      headerName: 'Volume',
      width: 100,
      renderCell: (params) => <StatusDot status={params.row.volume_status} />,
    },
    {
      field: 'schema_status',
      headerName: 'Schema',
      width: 100,
      renderCell: (params) => <StatusDot status={params.row.schema_status} />,
    },
    {
      field: 'check_status',
      headerName: 'Status',
      width: 100,
      renderCell: (params) => <StatusChip status={params.row.check_status} />,
    },
  ]

  // Hook called unconditionally (rules of hooks). The enabled: !!lobId guard
  // in useLobDatasets prevents a fetch when lobId is undefined.
  const { data, isLoading, isError, refetch } = useLobDatasets(lobId ?? '', timeRange)

  // Guard: if lobId is missing from URL params, render a not-found message.
  if (!lobId) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography color="text.secondary">LOB not found.</Typography>
      </Box>
    )
  }

  // ----- Loading state -----
  if (isLoading) {
    return (
      <Box>
        {/* Stats header skeleton */}
        <Skeleton variant="rectangular" height={80} sx={{ borderRadius: '8px', mb: 3 }} />
        {/* Table skeleton rows — 8 rows at 52px matching DataGrid default */}
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={`skeleton-row-${i}`} variant="rectangular" height={52} sx={{ mb: 0.5, borderRadius: '4px' }} />
        ))}
      </Box>
    )
  }

  // ----- Error state -----
  if (isError) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography color="error.main">Failed to load LOB data.</Typography>
        <Button variant="text" onClick={() => refetch()} sx={{ mt: 1 }}>
          Retry
        </Button>
      </Box>
    )
  }

  // ----- Stats header computation -----
  const datasets = data?.datasets ?? []
  const datasetCount = datasets.length

  const scores = datasets.map((d) => d.dqs_score).filter((s): s is number => s !== null)
  const avgScore = scores.length > 0 ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length) : null

  const passingCount = datasets.filter((d) => d.check_status === 'PASS').length
  const passingRate = datasetCount > 0 ? Math.round((passingCount / datasetCount) * 100) : null

  const lastRun =
    datasets.length > 0
      ? datasets.reduce(
          (max, d) => (d.partition_date > max ? d.partition_date : max),
          datasets[0]?.partition_date ?? ''
        )
      : null

  // ----- Row click handler -----
  const handleRowClick = (params: GridRowParams<DatasetInLob>): void => {
    navigate(`/datasets/${params.row.dataset_id}?lobId=${lobId ?? ''}`)
  }

  return (
    <Box>
      {/* Stats header */}
      <Box
        sx={{
          display: 'flex',
          gap: 2,
          bgcolor: 'background.paper',
          border: 1,
          borderColor: 'divider',
          borderRadius: '8px',
          p: 2,
          mb: 3,
          flexWrap: 'wrap',
        }}
      >
        {/* LOB Name */}
        <Box sx={{ flex: 1, minWidth: 120 }}>
          <Typography variant="caption" color="text.secondary">
            LOB
          </Typography>
          <Typography variant="h6" fontWeight="bold">
            {lobId ?? '—'}
          </Typography>
        </Box>

        {/* Dataset Count */}
        <Box sx={{ flex: 1, minWidth: 100 }}>
          <Typography variant="caption" color="text.secondary">
            Datasets
          </Typography>
          <Typography variant="h6" fontWeight="bold">
            {datasetCount}
          </Typography>
        </Box>

        {/* Avg DQS Score */}
        <Box sx={{ flex: 1, minWidth: 100 }}>
          <Typography variant="caption" color="text.secondary">
            Avg DQS Score
          </Typography>
          <Typography variant="h6" fontWeight="bold">
            {avgScore !== null ? avgScore : '—'}
          </Typography>
        </Box>

        {/* Checks Passing Rate */}
        <Box sx={{ flex: 1, minWidth: 100 }}>
          <Typography variant="caption" color="text.secondary">
            Checks Passing
          </Typography>
          <Typography variant="h6" fontWeight="bold">
            {passingRate !== null ? `${passingRate}%` : '—'}
          </Typography>
        </Box>

        {/* Last Run */}
        <Box sx={{ flex: 1, minWidth: 120 }}>
          <Typography variant="caption" color="text.secondary">
            Last Run
          </Typography>
          <Typography variant="h6" fontWeight="bold">
            {lastRun ?? '—'}
          </Typography>
        </Box>
      </Box>

      {/* Empty state */}
      {data && datasets.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography color="text.secondary">
            No datasets monitored in {lobId}
          </Typography>
        </Box>
      )}

      {/* DataGrid — only when datasets exist */}
      {data && datasets.length > 0 && (
        <DataGrid
          rows={datasets}
          columns={columns}
          getRowId={(row) => row.dataset_id}
          onRowClick={handleRowClick}
          initialState={{
            sorting: {
              sortModel: [{ field: 'dqs_score', sort: 'asc' }],
            },
          }}
          sx={{ cursor: 'pointer' }}
          autoHeight
          disableRowSelectionOnClick
          hideFooter
        />
      )}
    </Box>
  )
}
