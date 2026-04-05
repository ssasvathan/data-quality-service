/**
 * DqsScoreChip
 *
 * Displays a DQS score with optional trend arrow and delta.
 * Composed from Box + Typography — NOT using MUI Chip component.
 *
 * AC: 1, 2, 3, 4, 7
 */

import React from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { getDqsColor } from '../theme'

// ---------------------------------------------------------------------------
// Props interface
// ---------------------------------------------------------------------------

export interface DqsScoreChipProps {
  score?: number
  previousScore?: number
  size?: 'lg' | 'md' | 'sm'
  showTrend?: boolean
}

// ---------------------------------------------------------------------------
// Size → Typography variant mapping
//
// Convention: returns undefined for 'sm' to signal that inline sx is used
// instead of a named theme variant. Callers must check for undefined and
// apply the sm-specific sx object (`{ fontSize: '0.875rem', fontWeight: 600 }`).
// ---------------------------------------------------------------------------

type TypographyVariant = 'score' | 'scoreSm'

function getTypographyVariant(size: 'lg' | 'md' | 'sm'): TypographyVariant | undefined {
  if (size === 'lg') return 'score'
  if (size === 'md') return 'scoreSm'
  // sm has no dedicated theme variant — caller uses inline sx
  return undefined
}

// ---------------------------------------------------------------------------
// Aria-label helpers
// ---------------------------------------------------------------------------

function getStatusLabel(score: number): string {
  if (score >= 80) return 'healthy'
  if (score >= 60) return 'degraded'
  return 'critical'
}

function getTrendLabel(delta: number): string {
  if (delta > 0) return `improving by ${delta} points`
  if (delta < 0) return `declining by ${Math.abs(delta)} points`
  return 'stable'
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function DqsScoreChip({
  score,
  previousScore,
  size = 'md',
  showTrend = true,
}: DqsScoreChipProps): React.ReactElement {
  // No-data state — gap={0.5} intentionally omitted: single child, no flex gap needed
  if (score === undefined) {
    return (
      <Box
        display="flex"
        alignItems="center"
        gap={0.5}
        aria-label="DQS Score unavailable"
      >
        <Typography variant="scoreSm" color="text.disabled">
          —
        </Typography>
      </Box>
    )
  }

  const color = getDqsColor(score)
  const statusLabel = getStatusLabel(score)

  // Compute trend — only when hasTrend is true
  const hasTrend = showTrend && previousScore !== undefined

  let delta = 0
  let arrow = '→'
  let deltaText = '0'

  if (hasTrend) {
    // previousScore is narrowed to number by the hasTrend guard above
    const prev = previousScore as number
    delta = score - prev
    arrow = delta > 0 ? '▲' : delta < 0 ? '▼' : '→'
    deltaText = delta > 0 ? `+${delta}` : `${delta}`
  }

  // Build aria-label
  let ariaLabel = `DQS Score ${score}, ${statusLabel}`
  if (hasTrend) {
    ariaLabel += `, ${getTrendLabel(delta)}`
  }

  // Determine typography variant / sx for size
  const variant = getTypographyVariant(size)

  return (
    <Box
      display="flex"
      alignItems="center"
      gap={0.5}
      aria-label={ariaLabel}
    >
      {variant ? (
        <Typography variant={variant} sx={{ color }}>
          {score}
        </Typography>
      ) : (
        // sm size: no theme variant exists — use inline sx for 14px/600
        <Typography sx={{ fontSize: '0.875rem', fontWeight: 600, color }}>
          {score}
        </Typography>
      )}
      {hasTrend && (
        <>
          <Typography variant="caption" sx={{ color }}>
            {arrow}
          </Typography>
          <Typography variant="caption" sx={{ color }}>
            {deltaText}
          </Typography>
        </>
      )}
    </Box>
  )
}
