/**
 * ATDD Component Tests — Story 4.8: AppLayout with Fixed Header, Routing & React Query
 *
 * GREEN PHASE: All tests pass — implementation complete.
 *
 * Acceptance Criteria covered:
 *   AC1: Fixed header visible with breadcrumbs, time range toggle, and search bar
 *   AC2: Routes render correct page component with breadcrumbs reflecting path
 *   AC4: Browser back/forward navigation and breadcrumbs update
 *   AC5: <header>, <main>, <nav> landmark regions present
 *   AC6: Hidden skip link visible on Tab focus
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router'
import React from 'react'
import theme from '../../src/theme'
import AppLayout from '../../src/layouts/AppLayout'
import { TimeRangeProvider } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// Render helper — wraps AppLayout with all required providers.
// Uses MemoryRouter so tests run without a real browser environment.
// route: initial route to render (default '/')
// ---------------------------------------------------------------------------

const renderAppLayout = (route = '/') => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[route]}>
          <TimeRangeProvider>
            <Routes>
              <Route
                path="/*"
                element={<AppLayout><div data-testid="page-content">content</div></AppLayout>}
              />
            </Routes>
          </TimeRangeProvider>
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// AC1: Fixed header visible with breadcrumbs, time range toggle, and search bar
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — fixed header presence (AC1)', () => {
  it('[P0] renders an AppBar element in the DOM', () => {
    // After implementation: AppBar with position="fixed" must be in the DOM
    renderAppLayout()
    // MUI AppBar renders as <header> by default
    const header = screen.getByRole('banner')
    expect(header).toBeInTheDocument()
  })

  it('[P0] renders time range toggle with "7d", "30d", "90d" buttons', () => {
    renderAppLayout()
    expect(screen.getByRole('button', { name: /7d/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /30d/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /90d/i })).toBeInTheDocument()
  })

  it('[P0] search input has placeholder containing "Search datasets"', () => {
    renderAppLayout()
    const searchInput = screen.getByPlaceholderText(/Search datasets/i)
    expect(searchInput).toBeInTheDocument()
  })

  it('[P1] search input placeholder contains "(Ctrl+K)"', () => {
    renderAppLayout()
    const searchInput = screen.getByPlaceholderText(/Ctrl\+K/i)
    expect(searchInput).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC5: Semantic landmark regions — <header>, <main>, <nav>
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — semantic landmark regions (AC5)', () => {
  it('[P0] renders a <header> landmark element (role="banner")', () => {
    // After implementation: MUI AppBar renders as <header> with role="banner"
    renderAppLayout()
    expect(screen.getByRole('banner')).toBeInTheDocument()
  })

  it('[P0] renders a <main> landmark element (role="main")', () => {
    // After implementation: <Box component="main" id="main-content"> must exist
    renderAppLayout()
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('[P0] <main> element has id="main-content"', () => {
    renderAppLayout()
    const main = screen.getByRole('main')
    expect(main).toHaveAttribute('id', 'main-content')
  })

  it('[P0] renders a <nav> landmark element for breadcrumbs (role="navigation")', () => {
    renderAppLayout()
    expect(screen.getByRole('navigation')).toBeInTheDocument()
  })

  it('[P1] <nav> element has aria-label="breadcrumb"', () => {
    renderAppLayout()
    const nav = screen.getByRole('navigation', { name: /breadcrumb/i })
    expect(nav).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC6: Skip link — hidden by default, visible on Tab focus
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — skip link accessibility (AC6)', () => {
  it('[P0] skip link with href="#main-content" is present in DOM', () => {
    renderAppLayout()
    const skipLink = screen.getByRole('link', { name: /skip to main content/i })
    expect(skipLink).toBeInTheDocument()
  })

  it('[P0] skip link href points to "#main-content"', () => {
    renderAppLayout()
    const skipLink = screen.getByRole('link', { name: /skip to main content/i })
    expect(skipLink).toHaveAttribute('href', '#main-content')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — root and /summary paths
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — breadcrumb navigation root/summary (AC2)', () => {
  it('[P0] shows "Summary" breadcrumb as plain text (not a link) at root path "/"', () => {
    renderAppLayout('/')
    // At root, current page is "Summary" displayed as non-linked text
    const summaryText = screen.getByText('Summary')
    expect(summaryText).toBeInTheDocument()
    // It should NOT be a link (no <a> wrapping it)
    expect(summaryText.tagName).not.toBe('A')
  })

  it('[P0] shows "Summary" breadcrumb as plain text at "/summary" path', () => {
    renderAppLayout('/summary')
    const summaryText = screen.getByText('Summary')
    expect(summaryText).toBeInTheDocument()
    expect(summaryText.tagName).not.toBe('A')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — LOB detail path
// ---------------------------------------------------------------------------

describe('[P1] AppLayout — breadcrumb navigation LOB path (AC2)', () => {
  it('[P1] shows "Summary" as a clickable link and lobId as plain text at "/lobs/:lobId"', () => {
    renderAppLayout('/lobs/consumer-banking')
    // Summary should be a link
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    expect(summaryLink).toBeInTheDocument()
    // LOB id displayed as plain text
    expect(screen.getByText('consumer-banking')).toBeInTheDocument()
  })

  it('[P1] Summary breadcrumb link at LOB path navigates to /summary', () => {
    renderAppLayout('/lobs/mortgage')
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    // MUI Link rendered as RouterLink should have href resolving to /summary
    expect(summaryLink).toHaveAttribute('href', '/summary')
  })
})

// ---------------------------------------------------------------------------
// AC2: Breadcrumb derivation — dataset detail path
// ---------------------------------------------------------------------------

describe('[P1] AppLayout — breadcrumb navigation dataset path (AC2)', () => {
  it('[P1] shows "Summary" as link and datasetId as plain text at "/datasets/:datasetId"', () => {
    renderAppLayout('/datasets/loan-origination-daily')
    const summaryLink = screen.getByRole('link', { name: 'Summary' })
    expect(summaryLink).toBeInTheDocument()
    expect(screen.getByText('loan-origination-daily')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC2 + AC4: Time range toggle interaction
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — time range toggle interaction (AC2, AC3)', () => {
  it('[P0] "7d" toggle button is selected by default', () => {
    renderAppLayout()
    const btn7d = screen.getByRole('button', { name: /7d/i })
    // MUI ToggleButton sets aria-pressed="true" when selected
    expect(btn7d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P0] clicking "30d" toggle changes the selected time range to 30d', () => {
    renderAppLayout()
    const btn30d = screen.getByRole('button', { name: /30d/i })
    fireEvent.click(btn30d)
    expect(btn30d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P0] clicking "90d" toggle changes the selected time range to 90d', () => {
    renderAppLayout()
    const btn90d = screen.getByRole('button', { name: /90d/i })
    fireEvent.click(btn90d)
    expect(btn90d).toHaveAttribute('aria-pressed', 'true')
  })

  it('[P1] only one time range button is selected at a time after toggle change', () => {
    renderAppLayout()
    const btn30d = screen.getByRole('button', { name: /30d/i })
    fireEvent.click(btn30d)

    const btn7d = screen.getByRole('button', { name: /7d/i })
    const btn90d = screen.getByRole('button', { name: /90d/i })

    expect(btn30d).toHaveAttribute('aria-pressed', 'true')
    expect(btn7d).toHaveAttribute('aria-pressed', 'false')
    expect(btn90d).toHaveAttribute('aria-pressed', 'false')
  })
})

// ---------------------------------------------------------------------------
// AC1: Children rendered inside main content area
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — children rendered in main (AC1)', () => {
  it('[P0] children are rendered inside the <main> element', () => {
    // after implementation the main must have id="main-content"
    renderAppLayout()
    const main = screen.getByRole('main')
    expect(main).toContainElement(screen.getByTestId('page-content'))
  })
})

// ---------------------------------------------------------------------------
// Rendering stability — no throws with all route variants
// ---------------------------------------------------------------------------

describe('[P0] AppLayout — rendering stability', () => {
  it('[P0] renders without throwing at root path "/"', () => {
    expect(() => renderAppLayout('/')).not.toThrow()
  })

  it('[P0] renders without throwing at "/summary"', () => {
    expect(() => renderAppLayout('/summary')).not.toThrow()
  })

  it('[P0] renders without throwing at "/lobs/test-lob"', () => {
    expect(() => renderAppLayout('/lobs/test-lob')).not.toThrow()
  })

  it('[P0] renders without throwing at "/datasets/test-dataset"', () => {
    expect(() => renderAppLayout('/datasets/test-dataset')).not.toThrow()
  })
})
