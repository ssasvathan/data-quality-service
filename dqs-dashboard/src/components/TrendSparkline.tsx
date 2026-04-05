/**
 * TrendSparkline
 *
 * Renders a Recharts sparkline for DQS score trends.
 * Uses LineChart only — no BarChart or AreaChart.
 * Animations disabled for performance (inline list/table usage).
 *
 * AC: 5, 6, 7
 */

import React from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import { useTheme } from '@mui/material/styles'
import {
  ResponsiveContainer,
  LineChart,
  Line,
  Tooltip,
  ReferenceLine,
} from 'recharts'
import type { ValueType } from 'recharts/types/component/DefaultTooltipContent'
import { getDqsColor } from '../theme'

// ---------------------------------------------------------------------------
// Props interface
// ---------------------------------------------------------------------------

export interface TrendSparklineProps {
  data: number[]
  size?: 'lg' | 'md' | 'mini'
  color?: string
  showBaseline?: boolean
}

// ---------------------------------------------------------------------------
// Size constants
// ---------------------------------------------------------------------------

const SIZE_HEIGHT: Record<'lg' | 'md' | 'mini', number> = { lg: 64, md: 32, mini: 24 }
const SIZE_WIDTH: Record<'lg' | 'md' | 'mini', number | string> = {
  lg: '100%',
  md: '100%',
  mini: 80,
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function TrendSparkline({
  data,
  size = 'md',
  color,
  showBaseline = false,
}: TrendSparklineProps): React.ReactElement {
  // Med-2: use useTheme() hook so component responds to dynamic theme changes
  const theme = useTheme()

  const height = SIZE_HEIGHT[size]
  const width = SIZE_WIDTH[size]

  // Derive line color
  const lineColor: string =
    color !== undefined
      ? color
      : data.length > 0
        ? getDqsColor(data[data.length - 1])
        : theme.palette.neutral[500]

  // Low-3: latestScore is undefined for empty data; aria-label handles both cases
  const latestScore: number | undefined = data.length > 0 ? data[data.length - 1] : undefined

  // Low-9: use a clear aria-label when data is empty
  const ariaLabel =
    data.length > 1
      ? `Trend over ${data.length} data points, current score ${latestScore}`
      : data.length === 0
        ? 'No trend data available'
        : `Trend over ${data.length} data points`

  // Single data point (or empty) fallback
  if (data.length <= 1) {
    return (
      <Box
        role="img"
        aria-label={ariaLabel}
        sx={{
          height,
          width,
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
        }}
      >
        <Box
          sx={{
            width: 8,
            height: 8,
            borderRadius: '50%',
            bgcolor: lineColor,
          }}
        />
        <Typography variant="caption" color="text.secondary">
          First run
        </Typography>
      </Box>
    )
  }

  // Low-5: use 'x' instead of 'index' to avoid naming collision with Recharts internals
  const chartData = data.map((score, x) => ({ x, score }))
  const showTooltip = size !== 'mini'

  return (
    <Box
      role="img"
      aria-label={ariaLabel}
      sx={{ height, width }}
    >
      <ResponsiveContainer width="100%" height={height}>
        <LineChart data={chartData} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
          <Line
            type="monotone"
            dataKey="score"
            stroke={lineColor}
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
          {showTooltip && (
            <Tooltip
              // Low-4: explicit cast inside the body rather than implicit parameter type
              formatter={(value: ValueType) => [value as number, 'Score']}
              labelFormatter={(x: number) => `Point ${x + 1}`}
            />
          )}
          {showBaseline && (
            <ReferenceLine
              y={80}
              stroke={theme.palette.neutral[300]}
              strokeDasharray="4 2"
            />
          )}
        </LineChart>
      </ResponsiveContainer>
    </Box>
  )
}
