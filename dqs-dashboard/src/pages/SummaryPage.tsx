/**
 * SummaryPage — landing page showing LOB-level DQ score summary.
 * Story 4.9: Summary Card Grid View (Level 1)
 *
 * Renders a stats bar with aggregate health counts and a 3-column grid
 * of DatasetCard components, one per LOB. Uses skeleton loading (no spinners),
 * component-level error handling, and MUI theme tokens throughout.
 */

import React from 'react'
import Box from '@mui/material/Box'
import Grid from '@mui/material/Grid'
import Typography from '@mui/material/Typography'
import Skeleton from '@mui/material/Skeleton'
import { useNavigate } from 'react-router'
import { useSummary } from '../api/queries'
import { DatasetCard } from '../components'

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function SummaryPage(): React.ReactElement {
  const navigate = useNavigate()
  const { data, isLoading, isError, refetch } = useSummary()

  // -------------------------------------------------------------------------
  // Loading state — 6 skeleton cards, no stats bar
  // -------------------------------------------------------------------------
  if (isLoading) {
    return (
      <Box>
        <Grid container spacing={3}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={i}>
              <Skeleton variant="rectangular" height={220} sx={{ borderRadius: '8px' }} />
            </Grid>
          ))}
        </Grid>
      </Box>
    )
  }

  // -------------------------------------------------------------------------
  // Error state — component-level error message with retry (no full-page crash)
  // -------------------------------------------------------------------------
  if (isError) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography color="error.main">Failed to load summary data.</Typography>
        <Typography
          component="button"
          type="button"
          onClick={() => refetch()}
          sx={{
            mt: 1,
            color: 'primary.main',
            cursor: 'pointer',
            textDecoration: 'underline',
            background: 'none',
            border: 'none',
          }}
        >
          Retry
        </Typography>
      </Box>
    )
  }

  // -------------------------------------------------------------------------
  // Loaded state — stats bar + LOB card grid
  // -------------------------------------------------------------------------
  return (
    <Box>
      {/* Stats bar */}
      {data && (
        <Box
          aria-label="Data quality summary statistics"
          role="region"
          sx={{
            bgcolor: 'background.paper',
            border: '1px solid',
            borderColor: 'neutral.200',
            borderRadius: '8px',
            p: 2,
            mb: 3,
            display: 'flex',
            justifyContent: 'space-around',
            gap: 2,
          }}
        >
          {/* Total Datasets */}
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" color="neutral.500">
              Total Datasets
            </Typography>
            <Typography variant="h2" component="p" fontWeight="bold" color="neutral.900">
              {data.total_datasets}
            </Typography>
          </Box>

          {/* Healthy */}
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" color="neutral.500">
              Healthy
            </Typography>
            <Typography variant="h2" component="p" fontWeight="bold" color="success.main">
              {data.healthy_count}
            </Typography>
          </Box>

          {/* Degraded */}
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" color="neutral.500">
              Degraded
            </Typography>
            <Typography variant="h2" component="p" fontWeight="bold" color="warning.main">
              {data.degraded_count}
            </Typography>
          </Box>

          {/* Critical */}
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="caption" color="neutral.500">
              Critical
            </Typography>
            <Typography variant="h2" component="p" fontWeight="bold" color="error.main">
              {data.critical_count}
            </Typography>
          </Box>
        </Box>
      )}

      {/* Empty state */}
      {data && data.lobs.length === 0 && (
        <Box sx={{ textAlign: 'center', py: 8 }}>
          <Typography color="text.secondary">
            No data quality results yet. Results will appear after the first DQS orchestration run
            completes.
          </Typography>
        </Box>
      )}

      {/* LOB card grid */}
      {data && data.lobs.length > 0 && (
        <Grid container spacing={3}>
          {data.lobs.map((lob) => (
            <Grid size={{ xs: 12, sm: 6, md: 4 }} key={lob.lob_id}>
              <DatasetCard
                lobName={lob.lob_id}
                datasetCount={lob.dataset_count}
                dqsScore={lob.aggregate_score ?? 0}
                trendData={lob.trend}
                statusCounts={{
                  pass: lob.healthy_count,
                  warn: lob.degraded_count,
                  fail: lob.critical_count,
                }}
                onClick={() => navigate(`/lobs/${lob.lob_id}`)}
              />
            </Grid>
          ))}
        </Grid>
      )}
    </Box>
  )
}
