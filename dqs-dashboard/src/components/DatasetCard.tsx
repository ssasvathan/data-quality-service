/**
 * DatasetCard (LOB Card)
 *
 * Displays a LOB-level health summary card with DQS score, trend sparkline,
 * and pass/warn/fail status count chips.
 *
 * AC: 1, 2, 3, 4, 5
 */

import React from 'react'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import LinearProgress from '@mui/material/LinearProgress'
import Chip from '@mui/material/Chip'
import Box from '@mui/material/Box'
import { useTheme } from '@mui/material/styles'
import { DqsScoreChip } from './DqsScoreChip'
import { TrendSparkline } from './TrendSparkline'
import { getDqsColor } from '../theme'

// ---------------------------------------------------------------------------
// Props interface — defined locally, NOT in api/types.ts (UI-only props)
// ---------------------------------------------------------------------------

export interface DatasetCardProps {
  lobName: string
  datasetCount: number
  dqsScore: number
  previousScore?: number
  trendData: number[]
  statusCounts: { pass: number; warn: number; fail: number }
  onClick: () => void
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function DatasetCard({
  lobName,
  datasetCount,
  dqsScore,
  previousScore,
  trendData,
  statusCounts,
  onClick,
}: DatasetCardProps): React.ReactElement {
  const theme = useTheme()

  // Keyboard handler — activate on Enter or Space, prevent scroll on Space
  const handleKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onClick()
    }
  }

  // Aria-label construction per AC 5
  const ariaLabel = `${lobName}, DQS Score ${dqsScore}, ${datasetCount} datasets, ${statusCounts.pass} passing, ${statusCounts.warn} warning, ${statusCounts.fail} failing`

  // Status chip color mapping — use theme semantic palette, NEVER hardcoded hex
  const chipColors = {
    pass: {
      bgcolor: theme.palette.success.light,
      color: theme.palette.success.main,
    },
    warn: {
      bgcolor: theme.palette.warning.light,
      color: theme.palette.warning.main,
    },
    fail: {
      bgcolor: theme.palette.error.light,
      color: theme.palette.error.main,
    },
  }

  return (
    <Card
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      aria-label={ariaLabel}
      sx={{
        border: `1px solid ${theme.palette.neutral[200]}`,
        borderRadius: '8px',
        boxShadow: 'none',
        bgcolor: 'background.paper',
        cursor: 'pointer',
        transition: 'border-color 0.2s ease',
        '&:hover': {
          borderColor: theme.palette.primary.main,
        },
        '&:focus-visible': {
          outline: `2px solid ${theme.palette.primary.main}`,
          outlineOffset: '2px',
        },
      }}
    >
      <CardContent>
        {/* LOB name — h3 typography */}
        <Typography variant="h3" component="h3">
          {lobName}
        </Typography>

        {/* Dataset count — body2/caption */}
        <Typography variant="body2" color="text.secondary">
          {datasetCount} datasets
        </Typography>

        {/* DqsScoreChip — large size with optional trend */}
        <Box mt={1}>
          <DqsScoreChip
            score={dqsScore}
            previousScore={previousScore}
            size="lg"
          />
        </Box>

        {/* LinearProgress — colored via getDqsColor */}
        <Box mt={1}>
          <LinearProgress
            variant="determinate"
            value={dqsScore}
            sx={{
              height: 6,
              borderRadius: 3,
              bgcolor: theme.palette.neutral[200],
              '& .MuiLinearProgress-bar': {
                bgcolor: getDqsColor(dqsScore),
                borderRadius: 3,
              },
            }}
          />
        </Box>

        {/* TrendSparkline — medium size */}
        <Box mt={1}>
          <TrendSparkline data={trendData} size="md" />
        </Box>

        {/* Status count chips — pass / warn / fail */}
        <Box mt={1} display="flex" gap={1} flexWrap="wrap">
          <Chip
            label={`${statusCounts.pass} pass`}
            size="small"
            sx={{
              bgcolor: chipColors.pass.bgcolor,
              color: chipColors.pass.color,
            }}
          />
          <Chip
            label={`${statusCounts.warn} warn`}
            size="small"
            sx={{
              bgcolor: chipColors.warn.bgcolor,
              color: chipColors.warn.color,
            }}
          />
          <Chip
            label={`${statusCounts.fail} fail`}
            size="small"
            sx={{
              bgcolor: chipColors.fail.bgcolor,
              color: chipColors.fail.color,
            }}
          />
        </Box>
      </CardContent>
    </Card>
  )
}
