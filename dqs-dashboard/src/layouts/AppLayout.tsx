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

import React, { useState, useRef, useEffect, useDeferredValue } from 'react'
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
  Autocomplete,
  InputAdornment,
  Button,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useLocation, useParams, useSearchParams, Link as RouterLink, useNavigate } from 'react-router'
import SearchIcon from '@mui/icons-material/Search'
import AccessTimeIcon from '@mui/icons-material/AccessTime'
import { useTimeRange } from '../context/TimeRangeContext'
import type { TimeRange } from '../context/TimeRangeContext'
import { useSearch, useSummary } from '../api/queries'
import type { SearchResult } from '../api/types'
import { DqsScoreChip } from '../components'

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

/**
 * GlobalSearch — Autocomplete search bar in the AppBar header.
 * AC1: Shows dropdown after 2+ characters with DqsScoreChip + dataset name + LOB.
 * AC2: Escape closes the dropdown (MUI Autocomplete handles natively).
 * AC3: Selecting a result navigates to /datasets/{dataset_id}.
 * AC4: Ctrl+K (or Cmd+K on Mac) focuses the search input.
 * AC5: Shows "No datasets matching '{query}'" when no results.
 */
function GlobalSearch() {
  const [inputValue, setInputValue] = useState('')
  const [open, setOpen] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  const deferredQuery = useDeferredValue(inputValue)
  // Only pass a non-empty query when 2+ chars — hook's enabled guard also enforces this,
  // but passing empty string when < 2 chars keeps the call to useSearch consistent.
  const searchQuery = deferredQuery.length >= 2 ? deferredQuery : ''
  const { data, isError } = useSearch(searchQuery)
  const options: SearchResult[] = isError ? [] : (data?.results ?? [])

  // Ctrl+K / Cmd+K keyboard shortcut to focus search input (AC4)
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault()
        // Focus the input — the dropdown opens naturally when the user types 2+ chars.
        // Avoid calling setOpen(true) here: the handler closes over the initial empty
        // inputValue (stale closure with [] dep array) and opening an empty dropdown
        // shows "Type to search" which is not useful.
        inputRef.current?.focus()
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  return (
    <Autocomplete<SearchResult, false, false, false>
      disableClearable
      open={open}
      onOpen={() => inputValue.length >= 2 && setOpen(true)}
      onClose={() => setOpen(false)}
      options={options}
      getOptionLabel={(option) => option.dataset_name}
      filterOptions={(x) => x}
      inputValue={inputValue}
      onInputChange={(_, value, reason) => {
        // Ignore 'reset' reason — fired after onChange when option is selected.
        // We handle input clearing in onChange to keep state consistent.
        if (reason === 'reset') return
        setInputValue(value)
        setOpen(value.length >= 2)
      }}
      onChange={(_, value) => {
        if (value) {
          navigate(`/datasets/${value.dataset_id}`)
          setInputValue('')
          setOpen(false)
        }
      }}
      noOptionsText={
        isError
          ? 'Search unavailable — please try again'
          : inputValue.length >= 2
          ? `No datasets matching '${inputValue}'`
          : 'Type to search'
      }
      renderInput={(params) => (
        <TextField
          {...params}
          size="small"
          placeholder="Search datasets... (Ctrl+K)"
          inputRef={inputRef}
          InputProps={{
            ...params.InputProps,
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
          }}
          sx={{ ml: 2, width: 280 }}
        />
      )}
      renderOption={(props, option) => {
        const { key, ...optionProps } = props
        return (
          <Box
            component="li"
            key={key}
            {...optionProps}
            sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 1 }}
          >
            <DqsScoreChip score={option.dqs_score ?? undefined} size="sm" showTrend={false} />
            <Typography variant="mono" sx={{ flex: 1 }} noWrap>
              {option.dataset_name}
            </Typography>
            {option.lob_id && (
              <Typography variant="caption" color="text.secondary" noWrap>
                {option.lob_id}
              </Typography>
            )}
          </Box>
        )
      }}
    />
  )
}

/**
 * LastUpdatedIndicator — shows relative time since last DQS run in the header.
 * AC5: amber (warning.main) when stale (>=24h), text.secondary when fresh (<24h).
 * Renders nothing if last_run_at is absent or null (graceful degradation).
 */
function LastUpdatedIndicator() {
  const { data } = useSummary()
  const lastRunAt = data?.last_run_at

  if (!lastRunAt) return null

  const lastRunDate = new Date(lastRunAt)
  const now = new Date()
  const diffMs = now.getTime() - lastRunDate.getTime()
  const diffHours = diffMs / (1000 * 60 * 60)
  const isStale = diffHours >= 24

  // Format relative time
  let relativeTime: string
  if (diffHours < 1) {
    const diffMinutes = Math.round(diffMs / (1000 * 60))
    relativeTime = `${diffMinutes} minute${diffMinutes !== 1 ? 's' : ''} ago`
  } else if (diffHours < 24) {
    const hours = Math.round(diffHours)
    relativeTime = `${hours} hour${hours !== 1 ? 's' : ''} ago`
  } else {
    const days = Math.floor(diffHours / 24)
    const remainingHours = Math.round(diffHours % 24)
    if (remainingHours > 0) {
      relativeTime = `${Math.round(diffHours)} hours ago`
    } else {
      relativeTime = `${days} day${days !== 1 ? 's' : ''} ago`
    }
  }

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        mx: 2,
        color: isStale ? 'warning.main' : 'text.secondary',
        whiteSpace: 'nowrap',
      }}
    >
      <AccessTimeIcon fontSize="inherit" />
      <Typography variant="caption" sx={{ color: 'inherit' }}>
        Last updated: {relativeTime}
      </Typography>
    </Box>
  )
}

/**
 * RunFailedBanner — yellow banner shown below header when latest DQS run failed.
 * AC6: dismissible via local state, reappears on reload.
 * Renders nothing if run_failed is absent/false.
 */
function RunFailedBanner() {
  const [dismissed, setDismissed] = useState(false)
  const { data } = useSummary()

  if (dismissed) return null
  if (data?.run_failed !== true) return null

  const lastRunAt = data.last_run_at
  const timeStr = lastRunAt ? new Date(lastRunAt).toLocaleString() : 'unknown time'

  return (
    <Box
      sx={{
        width: '100%',
        bgcolor: 'warning.light',
        color: 'warning.dark',
        borderBottom: '1px solid',
        borderColor: 'warning.main',
        px: 3,
        py: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 2,
      }}
    >
      <Typography variant="body2">
        Latest run failed at {timeStr}. Showing results from previous run.
      </Typography>
      <Button
        size="small"
        variant="text"
        onClick={() => setDismissed(true)}
        sx={{ color: 'warning.dark', minWidth: 'auto' }}
        aria-label="dismiss"
      >
        Dismiss
      </Button>
    </Box>
  )
}

export default function AppLayout({ children }: AppLayoutProps) {
  const theme = useTheme()
  const { timeRange, setTimeRange } = useTimeRange()
  const [liveMessage, setLiveMessage] = useState('')
  const { isFetching: summaryFetching } = useSummary()
  const prevFetchingRef = useRef(false)
  const timeRangeChangedRef = useRef(false)

  // Track time range changes so we only announce after an actual range change
  const handleTimeRangeChange = (
    _: React.MouseEvent<HTMLElement>,
    value: TimeRange | null
  ): void => {
    if (value) {
      timeRangeChangedRef.current = true
      setTimeRange(value)
    }
  }

  // AC3: Announce data update when isFetching transitions true → false after time range change
  useEffect(() => {
    if (prevFetchingRef.current && !summaryFetching && timeRangeChangedRef.current) {
      setLiveMessage(`Data updated for ${timeRange} range`)
      timeRangeChangedRef.current = false
      const timer = setTimeout(() => setLiveMessage(''), 3000)
      return () => clearTimeout(timer)
    }
    prevFetchingRef.current = summaryFetching
  }, [summaryFetching, timeRange])

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      {/* AC3: aria-live region for screen reader data-update announcements — off-screen technique */}
      <Box
        aria-live="polite"
        aria-atomic="true"
        sx={{
          position: 'absolute',
          width: '1px',
          height: '1px',
          padding: 0,
          margin: '-1px',
          overflow: 'hidden',
          clip: 'rect(0, 0, 0, 0)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      >
        {liveMessage}
      </Box>

      {/* Skip to main content link — visible only on keyboard focus (AC1) */}
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
          <Box sx={{ flexGrow: 1, display: 'flex', alignItems: 'center', gap: 2 }}>
            <AppBreadcrumbs />
            <Link
              component={RouterLink}
              to="/exec"
              underline="hover"
              color="inherit"
              sx={{ whiteSpace: 'nowrap', fontSize: '0.875rem' }}
            >
              Executive Report
            </Link>
          </Box>

          {/* AC5: LastUpdatedIndicator — amber when stale (>=24h), gray when fresh */}
          <LastUpdatedIndicator />

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

          {/* GlobalSearch — full Autocomplete implementation (Story 4.13) */}
          <GlobalSearch />
        </Toolbar>
      </AppBar>

      {/* Toolbar spacer: pushes content below fixed AppBar.
          Responsive: 56px base, 48px landscape, 64px at >=600px — matches theme.mixins.toolbar exactly.
          Per MUI docs, a sibling <Toolbar /> is the recommended approach for fixed AppBar offset. */}
      <Toolbar aria-hidden="true" />

      {/* AC6: RunFailedBanner — yellow banner below header when latest run failed */}
      <RunFailedBanner />

      {/* Main content area (AC5: <main> landmark) */}
      <Box
        component="main"
        id="main-content"
        sx={{ flexGrow: 1, p: 3 }}
      >
        {/* AC5: max-width centering — content centers at 1440px for wide viewports */}
        <Box style={{ maxWidth: '1440px', marginLeft: 'auto', marginRight: 'auto', width: '100%' }}>
          {children}
        </Box>
      </Box>
    </Box>
  )
}
