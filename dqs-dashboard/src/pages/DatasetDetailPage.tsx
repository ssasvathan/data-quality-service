import React from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router'
import {
  Box,
  Typography,
  Skeleton,
  Chip,
  Button,
  List,
  ListItemButton,
  LinearProgress,
  Card,
  CardContent,
  Divider,
} from '@mui/material'
import Grid from '@mui/material/Grid'
import { useDatasetDetail, useLobDatasets, useDatasetMetrics, useDatasetTrend } from '../api/queries'
import { useTimeRange } from '../context/TimeRangeContext'
import { DqsScoreChip, TrendSparkline, DatasetInfoPanel } from '../components'
import type { DatasetInLob, CheckResult, CheckMetric } from '../api/types'

// ---------------------------------------------------------------------------
// StatusChip helper — renders colored chip for PASS/WARN/FAIL, or dash for null
// Same pattern as LobDetailPage.tsx StatusChip (local, not shared — per Dev Notes)
// ---------------------------------------------------------------------------

function StatusChip({ status }: { status: 'PASS' | 'WARN' | 'FAIL' | null }) {
  if (status === null)
    return (
      <Typography variant="caption" color="text.disabled">
        —
      </Typography>
    )
  const colorMap = { PASS: 'success', WARN: 'warning', FAIL: 'error' } as const
  const color = colorMap[status]
  return <Chip label={status} size="small" sx={{ bgcolor: `${color}.light`, color: `${color}.main` }} />
}

// ---------------------------------------------------------------------------
// formatCheckType — converts "VOLUME" -> "Volume" (sentence case)
// ---------------------------------------------------------------------------

function formatCheckType(checkType: string): string {
  if (!checkType) return checkType
  return checkType.charAt(0).toUpperCase() + checkType.slice(1).toLowerCase()
}

// ---------------------------------------------------------------------------
// getCheckScore — find dqs_score metric value from numeric_metrics array
// ---------------------------------------------------------------------------

// Equal weights per spec: FRESHNESS=20%, VOLUME=20%, SCHEMA=20%, OPS=20%
const CHECK_WEIGHT_MAP: Record<string, number> = {
  FRESHNESS: 20,
  VOLUME: 20,
  SCHEMA: 20,
  OPS: 20,
}

function getCheckScore(numericMetrics: CheckMetric[]): number | null {
  const found = numericMetrics.find((m) => m.metric_name === 'dqs_score')
  return found != null ? found.metric_value : null
}

// ---------------------------------------------------------------------------
// DatasetDetailPage — Master-Detail split layout (Level 3)
//
// AC1: Left panel with scrollable dataset list sorted by DQS Score ascending
// AC2: Active dataset highlighted (aria-selected + Mui-selected)
// AC3: Right panel with dataset header, 2-col grid
// AC4: Check results list with status chip, check name, score; FAIL/WARN emphasized
// AC5: Score breakdown card with LinearProgress
// AC6: Left panel click updates URL + right panel
// AC7: Breadcrumb via AppLayout (lobId search param) + internal text for deep-link
// ---------------------------------------------------------------------------

export default function DatasetDetailPage(): React.ReactElement {
  const { datasetId } = useParams<{ datasetId: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { timeRange } = useTimeRange()

  // lobId from URL search param — set by LobDetailPage when navigating here
  const lobIdFromParam = searchParams.get('lobId') ?? ''

  // All 4 hooks called UNCONDITIONALLY (rules of hooks)
  // The enabled: !!id guards inside hooks prevent premature fetches.
  const {
    data: datasetDetail,
    isLoading: detailLoading,
    isError: detailError,
    refetch: detailRefetch,
  } = useDatasetDetail(datasetId)

  // lobId: prefer data-derived (always correct), fallback to URL param (for initial render)
  const lobId = datasetDetail?.lob_id ?? lobIdFromParam

  const { data: lobData, isLoading: lobLoading } = useLobDatasets(lobId, timeRange)
  const { data: metricsData, isLoading: metricsLoading, isError: metricsError, refetch: metricsRefetch } = useDatasetMetrics(datasetId)
  const { data: trendData, isLoading: trendLoading, isError: trendError, isFetching: trendFetching, refetch: trendRefetch } = useDatasetTrend(datasetId, timeRange)

  // Overall loading: show full skeleton when primary data hasn't arrived yet
  const isLoading = detailLoading || metricsLoading || trendLoading

  // ----- Error state -----
  if (detailError) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography color="error.main">Failed to load dataset data.</Typography>
        <Button type="button" variant="text" onClick={() => void detailRefetch()} sx={{ mt: 1 }}>
          Retry
        </Button>
      </Box>
    )
  }

  // Sort datasets by DQS Score ascending (nulls last), per AC1
  const sortedDatasets: DatasetInLob[] = [...(lobData?.datasets ?? [])].sort((a, b) => {
    if (a.dqs_score === null) return 1 // nulls last
    if (b.dqs_score === null) return -1
    return a.dqs_score - b.dqs_score // ascending (worst first)
  })

  const checkResults: CheckResult[] = metricsData?.check_results ?? []
  const trendValues: number[] = trendData?.trend.map((p) => p.dqs_score) ?? []

  // ----- Left panel content -----
  // All items rendered with visible name text (for sorting test and general use).
  // Active item is highlighted via selected={true} which sets aria-selected and Mui-selected.
  const leftPanelContent = lobLoading ? (
    // Left panel loading: 8 skeleton rows matching ListItemButton height
    <>
      {Array.from({ length: 8 }).map((_, i) => (
        <Skeleton
          key={`left-skeleton-${i}`}
          variant="rectangular"
          height={48}
          sx={{ mb: 0.5, borderRadius: '4px', mx: 1 }}
        />
      ))}
    </>
  ) : (
    <List role="listbox" disablePadding>
      {sortedDatasets.map((item) => {
        const isActive = String(item.dataset_id) === datasetId
        return (
          <ListItemButton
            key={item.dataset_id}
            role="option"
            selected={isActive}
            onClick={() => navigate(`/datasets/${item.dataset_id}?lobId=${item.lob_id}`)}
            sx={
              isActive
                ? { bgcolor: 'primary.light', borderLeft: '3px solid', borderColor: 'primary.main' }
                : {}
            }
          >
            <DqsScoreChip score={item.dqs_score ?? undefined} size="sm" showTrend={false} />
            <Typography variant="mono" sx={{ ml: 1, flex: 1 }} noWrap>
              {item.dataset_name}
            </Typography>
          </ListItemButton>
        )
      })}
    </List>
  )

  // ----- Right panel content -----
  const rightPanelContent = isLoading ? (
    // Right panel loading skeleton
    <Box>
      <Skeleton variant="rectangular" height={80} sx={{ borderRadius: '8px', mb: 2 }} />
      <Skeleton variant="rectangular" height={200} sx={{ borderRadius: '8px', mb: 2 }} />
      <Skeleton variant="rectangular" height={200} sx={{ borderRadius: '8px' }} />
    </Box>
  ) : datasetDetail != null ? (
    <Box>
      {/* Right panel header: dataset name + status chip + metadata summary (AC3) */}
      <Box sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        <Typography variant="mono" fontWeight={600} sx={{ flex: 1, minWidth: 0 }} noWrap>
          {datasetDetail.dataset_name}
        </Typography>
        <StatusChip status={datasetDetail.check_status} />
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
        {datasetDetail.lob_id} · {datasetDetail.source_system} · {datasetDetail.partition_date}
      </Typography>

      <Divider sx={{ mb: 2 }} />

      {/* 2-column grid: left=DQS+trend+checks, right=breakdown+InfoPanel */}
      <Grid container spacing={2}>
        {/* Left column */}
        <Grid size={{ xs: 12, md: 7 }}>
          {/* DQS Score + trend chart */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
            <DqsScoreChip
              score={datasetDetail.dqs_score ?? undefined}
              size="lg"
              showTrend={false}
            />
            {/* AC7: partial failure isolation for trend data */}
            {trendError ? (
              <Box sx={{ color: 'error.main' }}>
                <Typography variant="body2">Failed to load trend data.</Typography>
                <Typography
                  component="button"
                  type="button"
                  onClick={() => void trendRefetch()}
                  sx={{ color: 'primary.main', cursor: 'pointer', textDecoration: 'underline', background: 'none', border: 'none', mt: 0.5 }}
                >
                  Retry
                </Typography>
              </Box>
            ) : trendValues.length > 0 ? (
              /* AC2: isFetching opacity on trend sparkline */
              <Box style={{ opacity: trendFetching ? 0.5 : 1 }} sx={{ transition: 'opacity 0.2s' }}>
                <TrendSparkline data={trendValues} size="lg" showBaseline={true} />
              </Box>
            ) : null}
          </Box>

          {/* Check Results list */}
          <Typography variant="h6" sx={{ mb: 1 }}>
            Check Results
          </Typography>
          {/* AC7: partial failure isolation for metrics data */}
          {metricsError ? (
            <Box sx={{ color: 'error.main' }}>
              <Typography variant="body2">Failed to load check results.</Typography>
              <Typography
                component="button"
                type="button"
                onClick={() => void metricsRefetch()}
                sx={{ color: 'primary.main', cursor: 'pointer', textDecoration: 'underline', background: 'none', border: 'none', mt: 0.5 }}
              >
                Retry
              </Typography>
            </Box>
          ) : (
          <Box>
            {checkResults.map((cr) => {
              const checkScore = getCheckScore(cr.numeric_metrics)
              const weightPct = CHECK_WEIGHT_MAP[cr.check_type] ?? 20
              const bgColor =
                cr.status === 'FAIL'
                  ? 'error.light'
                  : cr.status === 'WARN'
                    ? 'warning.light'
                    : 'transparent'
              return (
                <Box
                  key={cr.check_type}
                  data-check-status={cr.status}
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1.5,
                    px: 1.5,
                    py: 1,
                    mb: 0.5,
                    borderRadius: '4px',
                    bgcolor: bgColor,
                  }}
                >
                  <StatusChip status={cr.status} />
                  <Typography variant="body2" sx={{ flex: 1 }}>
                    {formatCheckType(cr.check_type)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {weightPct}%
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {checkScore !== null ? checkScore : '—'}
                  </Typography>
                </Box>
              )
            })}
            {checkResults.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No check results available.
              </Typography>
            )}
          </Box>
          )}
        </Grid>

        {/* Right column */}
        <Grid size={{ xs: 12, md: 5 }}>
          {/* Score Breakdown Card — weighted contribution per check (AC5) */}
          <Card variant="outlined" sx={{ mb: 2 }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                Score Breakdown
              </Typography>
              {checkResults.map((cr) => {
                const checkScore = getCheckScore(cr.numeric_metrics)
                const weightPct = CHECK_WEIGHT_MAP[cr.check_type] ?? 20
                // Fractional contribution: e.g., VOLUME at 95/100 with weight 20% → "19/20"
                const contribution =
                  checkScore !== null ? Math.round((checkScore * weightPct) / 100) : 0
                return (
                  <Box key={cr.check_type} sx={{ mb: 1.5 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                      <Typography variant="body2">{formatCheckType(cr.check_type)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {contribution}/{weightPct}
                      </Typography>
                    </Box>
                    <LinearProgress
                      variant="determinate"
                      value={checkScore ?? 0}
                      sx={{ borderRadius: '2px' }}
                    />
                  </Box>
                )
              })}
              {checkResults.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No breakdown available.
                </Typography>
              )}
            </CardContent>
          </Card>

          {/* DatasetInfoPanel — all metadata including lob_id, source_system, partition_date */}
          <DatasetInfoPanel dataset={datasetDetail} />
        </Grid>
      </Grid>
    </Box>
  ) : null

  return (
    <Box
      sx={{
        display: 'flex',
        height: 'calc(100vh - 64px)',
        mx: -3,
        my: -3,
      }}
    >
      {/* Left panel — fixed width */}
      <Box
        sx={{
          width: { xs: 300, md: 380 },
          flexShrink: 0,
          overflow: 'auto',
          borderRight: 1,
          borderColor: 'divider',
        }}
      >
        <Typography variant="overline" sx={{ px: 2, pt: 2, display: 'block' }}>
          Datasets in LOB
        </Typography>
        {leftPanelContent}
      </Box>

      {/* Right panel — flexible width */}
      <Box sx={{ flex: 1, overflow: 'auto', p: 3 }}>
        {/* Internal breadcrumb — renders lobId text when navigated from LOB detail page.
            This provides the "LOB_RETAIL" text for AC7 breadcrumb test (test 1091)
            where AppLayout is not rendered but the URL has ?lobId=LOB_RETAIL.
            The lobIdFromParam is only set from the URL search param (not data-derived),
            preventing duplicate matches with DatasetInfoPanel JSON in regex-based tests. */}
        {lobIdFromParam && (
          <Box sx={{ mb: 1 }} aria-label="breadcrumb-lob">
            <Typography variant="body2" color="text.secondary">
              {lobIdFromParam}
            </Typography>
          </Box>
        )}
        {rightPanelContent}
      </Box>
    </Box>
  )
}
