/**
 * ExecReportPage — Executive Reporting Suite
 * Story 7.4: Strategic dashboards for executive-level quality reporting.
 *
 * Three sections:
 *  1. LOB Monthly Scorecard — cross-LOB DQS scores for the last 3 months
 *  2. Improvement Summary — current month vs. 3-months-ago baseline delta
 *  3. Source System Accountability — avg DQS score per src_sys_nm
 *
 * Loading: Skeleton elements (no spinners per project-context.md)
 * Error: component-level message + Retry button (never a full-page crash)
 */

import React from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Skeleton from '@mui/material/Skeleton'
import Button from '@mui/material/Button'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'
import { useExecutiveReport } from '../api/queries'
import type { LobMonthlyScore, SourceSystemScore, LobImprovementSummary } from '../api/types'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a 'YYYY-MM' month string to a human-readable label e.g. 'Feb 2026'.
 * Returns the original string if format is unexpected (defensive guard).
 */
function formatMonth(month: string): string {
  const parts = month.split('-')
  if (parts.length !== 2) return month
  const year = Number(parts[0])
  const mm = Number(parts[1])
  if (Number.isNaN(year) || Number.isNaN(mm) || mm < 1 || mm > 12) return month
  const date = new Date(year, mm - 1, 1)
  return date.toLocaleString('en-US', { month: 'short', year: 'numeric' })
}

/**
 * Get MUI color token for a DQS score value.
 */
function scoreColor(score: number | null): string {
  if (score === null) return 'text.secondary'
  if (score >= 90) return 'success.main'
  if (score >= 70) return 'warning.main'
  return 'error.main'
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

interface LobScorecardTableProps {
  rows: LobMonthlyScore[]
}

function LobScorecardTable({ rows }: LobScorecardTableProps): React.ReactElement {
  // Collect unique LOBs and months (preserving insertion order) using Sets for O(1) lookup
  const lobIdSet = new Set<string>()
  const monthSet = new Set<string>()
  const scoreMap = new Map<string, Map<string, number | null>>()

  for (const item of rows) {
    lobIdSet.add(item.lob_id)
    monthSet.add(item.month)

    if (!scoreMap.has(item.lob_id)) scoreMap.set(item.lob_id, new Map())
    scoreMap.get(item.lob_id)!.set(item.month, item.avg_score)
  }

  const lobIds = Array.from(lobIdSet)
  const months = Array.from(monthSet)

  return (
    <TableContainer component={Paper} sx={{ bgcolor: 'background.paper', borderColor: 'neutral.200' }}>
      <Table size="small" aria-label="LOB Monthly Scorecard">
        <TableHead>
          <TableRow>
            <TableCell>
              <Typography variant="caption" fontWeight="bold">LOB</Typography>
            </TableCell>
            {months.map((m) => (
              <TableCell key={m} align="center">
                <Typography variant="caption" fontWeight="bold">{formatMonth(m)}</Typography>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {lobIds.map((lob) => (
            <TableRow key={lob}>
              <TableCell>
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{lob}</Typography>
              </TableCell>
              {months.map((m) => {
                const score = scoreMap.get(lob)?.get(m) ?? null
                return (
                  <TableCell key={m} align="center">
                    {score !== null ? (
                      <Typography variant="body2" sx={{ color: scoreColor(score), fontWeight: 'bold' }}>
                        {score.toFixed(1)}
                      </Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">—</Typography>
                    )}
                  </TableCell>
                )
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

interface ImprovementSummaryTableProps {
  rows: LobImprovementSummary[]
}

function ImprovementSummaryTable({ rows }: ImprovementSummaryTableProps): React.ReactElement {
  return (
    <TableContainer component={Paper} sx={{ bgcolor: 'background.paper', borderColor: 'neutral.200' }}>
      <Table size="small" aria-label="Improvement Summary">
        <TableHead>
          <TableRow>
            <TableCell><Typography variant="caption" fontWeight="bold">LOB</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Baseline</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Current</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Delta (Δ)</Typography></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((item) => {
            const deltaDisplay = (): React.ReactElement => {
              if (item.delta === null) {
                return <Typography variant="body2" color="text.secondary">N/A</Typography>
              }
              if (item.delta > 0) {
                return (
                  <Typography variant="body2" sx={{ color: 'success.main', fontWeight: 'bold' }}>
                    ▲ {item.delta.toFixed(1)}
                  </Typography>
                )
              }
              if (item.delta < 0) {
                return (
                  <Typography variant="body2" sx={{ color: 'error.main', fontWeight: 'bold' }}>
                    ▼ {Math.abs(item.delta).toFixed(1)}
                  </Typography>
                )
              }
              return <Typography variant="body2" color="text.secondary">—</Typography>
            }

            return (
              <TableRow key={item.lob_id}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.lob_id}</Typography>
                </TableCell>
                <TableCell align="center">
                  <Typography variant="body2" color={scoreColor(item.baseline_score)}>
                    {item.baseline_score !== null ? item.baseline_score.toFixed(1) : '—'}
                  </Typography>
                </TableCell>
                <TableCell align="center">
                  <Typography variant="body2" color={scoreColor(item.current_score)}>
                    {item.current_score !== null ? item.current_score.toFixed(1) : '—'}
                  </Typography>
                </TableCell>
                <TableCell align="center">
                  {deltaDisplay()}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

interface SourceSystemTableProps {
  rows: SourceSystemScore[]
}

function SourceSystemTable({ rows }: SourceSystemTableProps): React.ReactElement {
  return (
    <TableContainer component={Paper} sx={{ bgcolor: 'background.paper', borderColor: 'neutral.200' }}>
      <Table size="small" aria-label="Source System Accountability">
        <TableHead>
          <TableRow>
            <TableCell><Typography variant="caption" fontWeight="bold">Source System</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Datasets</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Avg Score</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Healthy</Typography></TableCell>
            <TableCell align="center"><Typography variant="caption" fontWeight="bold">Critical</Typography></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((item) => (
            <TableRow key={item.src_sys_nm}>
              <TableCell>
                <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>{item.src_sys_nm}</Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2">{item.dataset_count}</Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" sx={{ color: scoreColor(item.avg_score), fontWeight: 'bold' }}>
                  {item.avg_score !== null ? item.avg_score.toFixed(1) : '—'}
                </Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" color="success.main">{item.healthy_count}</Typography>
              </TableCell>
              <TableCell align="center">
                <Typography variant="body2" color="error.main">{item.critical_count}</Typography>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

// ---------------------------------------------------------------------------
// Main page component
// ---------------------------------------------------------------------------

export default function ExecReportPage(): React.ReactElement {
  const { data, isLoading, isError, isFetching, refetch } = useExecutiveReport()

  // -------------------------------------------------------------------------
  // Loading state — Skeleton elements (no spinners per project-context.md)
  // -------------------------------------------------------------------------
  if (isLoading) {
    return (
      <Box>
        <Skeleton variant="text" width={260} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 2, mb: 4 }} />
        <Skeleton variant="text" width={220} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 2, mb: 4 }} />
        <Skeleton variant="text" width={280} height={40} sx={{ mb: 2 }} />
        <Skeleton variant="rectangular" height={120} sx={{ borderRadius: 2 }} />
      </Box>
    )
  }

  // -------------------------------------------------------------------------
  // Error state — component-level error + Retry button (never full-page crash)
  // -------------------------------------------------------------------------
  if (isError) {
    return (
      <Box sx={{ textAlign: 'center', py: 4 }}>
        <Typography sx={{ color: 'error.main' }}>Failed to load executive report</Typography>
        <Button
          variant="outlined"
          size="small"
          onClick={() => void refetch()}
          sx={{ mt: 2 }}
        >
          Retry
        </Button>
      </Box>
    )
  }

  // -------------------------------------------------------------------------
  // Loaded state — three reporting sections
  // -------------------------------------------------------------------------
  const lobMonthlyScores = data?.lob_monthly_scores ?? []
  const sourceSystemScores = data?.source_system_scores ?? []
  const improvementSummary = data?.improvement_summary ?? []

  return (
    <Box style={{ opacity: isFetching ? 0.6 : 1 }} sx={{ transition: 'opacity 0.2s' }}>
      {/* Section 1: LOB Monthly Scorecard */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          LOB Monthly Scorecard
        </Typography>
        {lobMonthlyScores.length === 0 ? (
          <Typography color="text.secondary" variant="body2">
            No monthly LOB data available for the last 3 months.
          </Typography>
        ) : (
          <LobScorecardTable rows={lobMonthlyScores} />
        )}
      </Box>

      {/* Section 2: Improvement Summary */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Improvement Summary
        </Typography>
        {improvementSummary.length === 0 ? (
          <Typography color="text.secondary" variant="body2">
            No improvement data available.
          </Typography>
        ) : (
          <ImprovementSummaryTable rows={improvementSummary} />
        )}
      </Box>

      {/* Section 3: Source System Accountability */}
      <Box>
        <Typography variant="h6" component="h2" gutterBottom>
          Source System Accountability
        </Typography>
        {sourceSystemScores.length === 0 ? (
          <Typography color="text.secondary" variant="body2">
            No source system data available.
          </Typography>
        ) : (
          <SourceSystemTable rows={sourceSystemScores} />
        )}
      </Box>
    </Box>
  )
}
