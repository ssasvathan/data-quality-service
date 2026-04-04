/**
 * AppLayout — fixed header with breadcrumbs, time range toggle, search bar,
 * and main content area with proper landmark regions for accessibility.
 *
 * AC1: Fixed header visible with breadcrumbs, time range toggle, and search bar
 * AC2: Routes render correct page component with breadcrumbs reflecting path
 * AC4: Browser back/forward navigation works with breadcrumb updates
 * AC5: <header>, <main>, <nav> landmark regions present
 * AC6: Hidden skip link visible on Tab focus
 */

import React from 'react'
import type { ReactNode } from 'react'
import {
  AppBar,
  Toolbar,
  Box,
  Breadcrumbs,
  Link,
  Typography,
  ToggleButtonGroup,
  ToggleButton,
  TextField,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useLocation, useParams, useSearchParams, Link as RouterLink } from 'react-router'
import { useTimeRange } from '../context/TimeRangeContext'
import type { TimeRange } from '../context/TimeRangeContext'

interface AppLayoutProps {
  children: ReactNode
}

/**
 * Renders breadcrumb navigation based on the current route.
 * MUI Breadcrumbs renders as <nav aria-label="breadcrumb"> providing the
 * navigation landmark required by AC5.
 *
 * Uses both useParams() (when matched by a typed route) and pathname parsing
 * (fallback for wildcard routes in tests) to derive IDs.
 */
function AppBreadcrumbs() {
  const location = useLocation()
  const { lobId: paramLobId, datasetId: paramDatasetId } = useParams<{
    lobId?: string
    datasetId?: string
  }>()
  const [searchParams] = useSearchParams()

  const { pathname } = location

  // Derive IDs from pathname segments as a fallback when route params aren't populated
  // e.g. /lobs/consumer-banking -> lobId = 'consumer-banking'
  const lobMatch = pathname.match(/^\/lobs\/(.+)$/)
  const datasetMatch = pathname.match(/^\/datasets\/([^?]+)/)
  const lobId = paramLobId ?? lobMatch?.[1]
  const datasetId = paramDatasetId ?? datasetMatch?.[1]

  // lobId search param — passed by LobDetailPage when navigating to dataset detail
  const lobIdParam = searchParams.get('lobId')

  // Summary (root or /summary) — plain text, not clickable
  if (pathname === '/' || pathname === '/summary') {
    return (
      <Breadcrumbs aria-label="breadcrumb">
        <Typography color="text.primary">Summary</Typography>
      </Breadcrumbs>
    )
  }

  // LOB detail — Summary link > LOB ID as plain text
  if (lobId) {
    return (
      <Breadcrumbs aria-label="breadcrumb">
        <Link component={RouterLink} to="/summary" underline="hover" color="inherit">
          Summary
        </Link>
        <Typography color="text.primary">{lobId}</Typography>
      </Breadcrumbs>
    )
  }

  // Dataset detail — Summary link > LOB link (when lobIdParam present) > Dataset ID
  if (datasetId) {
    return (
      <Breadcrumbs aria-label="breadcrumb">
        <Link component={RouterLink} to="/summary" underline="hover" color="inherit">
          Summary
        </Link>
        {lobIdParam && (
          <Link
            component={RouterLink}
            to={`/lobs/${lobIdParam}`}
            underline="hover"
            color="inherit"
          >
            {lobIdParam}
          </Link>
        )}
        <Typography color="text.primary">{datasetId}</Typography>
      </Breadcrumbs>
    )
  }

  // Fallback — show Summary as plain text
  return (
    <Breadcrumbs aria-label="breadcrumb">
      <Typography color="text.primary">Summary</Typography>
    </Breadcrumbs>
  )
}

export default function AppLayout({ children }: AppLayoutProps) {
  const theme = useTheme()
  const { timeRange, setTimeRange } = useTimeRange()

  const handleTimeRangeChange = (
    _: React.MouseEvent<HTMLElement>,
    value: TimeRange | null
  ): void => {
    if (value) {
      setTimeRange(value)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* Skip to main content link — visible only on keyboard focus (AC6) */}
      <Box
        component="a"
        href="#main-content"
        sx={{
          position: 'absolute',
          left: '-9999px',
          top: 'auto',
          width: '1px',
          height: '1px',
          overflow: 'hidden',
          '&:focus': {
            left: 0,
            top: 0,
            width: 'auto',
            height: 'auto',
            overflow: 'visible',
            zIndex: 9999,
            p: 1,
            bgcolor: 'primary.main',
            color: 'white',
          },
        }}
      >
        Skip to main content
      </Box>

      {/* Fixed header (AC1, AC5: <header> landmark via MUI AppBar default) */}
      <AppBar
        position="fixed"
        sx={{
          bgcolor: 'background.paper',
          borderBottom: `1px solid ${theme.palette.neutral[200]}`,
          boxShadow: 'none',
          color: 'text.primary',
        }}
      >
        <Toolbar>
          {/* AppBreadcrumbs renders MUI Breadcrumbs as <nav aria-label="breadcrumb"> (AC5) */}
          <Box sx={{ flexGrow: 1 }}>
            <AppBreadcrumbs />
          </Box>

          {/* Time range toggle (AC1, AC3) */}
          <ToggleButtonGroup
            value={timeRange}
            exclusive
            onChange={handleTimeRangeChange}
            size="small"
            aria-label="time range"
          >
            <ToggleButton value="7d">7d</ToggleButton>
            <ToggleButton value="30d">30d</ToggleButton>
            <ToggleButton value="90d">90d</ToggleButton>
          </ToggleButtonGroup>

          {/* Search bar placeholder (AC1) — full implementation in Story 4.13 */}
          <TextField
            size="small"
            placeholder="Search datasets... (Ctrl+K)"
            sx={{ ml: 2, width: 240 }}
          />
        </Toolbar>
      </AppBar>

      {/* Toolbar spacer: pushes content below fixed AppBar.
          Responsive: 56px base, 48px landscape, 64px at >=600px — matches theme.mixins.toolbar exactly.
          Per MUI docs, a sibling <Toolbar /> is the recommended approach for fixed AppBar offset. */}
      <Toolbar aria-hidden="true" />

      {/* Main content area (AC5: <main> landmark) */}
      <Box
        component="main"
        id="main-content"
        sx={{ flexGrow: 1, p: 3 }}
      >
        {children}
      </Box>
    </Box>
  )
}
