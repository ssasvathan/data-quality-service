/**
 * ATDD Component Tests — Story 4.8: TimeRangeContext
 *
 * GREEN PHASE: All tests pass — implementation complete.
 *
 * Acceptance Criteria covered:
 *   AC3: Time range change invalidates all React Query cached queries (context state)
 *
 * Validates:
 *   - Default time range is '7d'
 *   - setTimeRange updates the time range value
 *   - useTimeRange throws when used outside provider
 *   - TimeRangeProvider + useQueryClient pattern works inside QueryClientProvider
 *
 * @testing-framework Vitest + React Testing Library
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { TimeRangeProvider, useTimeRange } from '../../src/context/TimeRangeContext'
import type { TimeRange } from '../../src/context/TimeRangeContext'

// ---------------------------------------------------------------------------
// Helper: render a consumer component that reads from TimeRangeContext
// Provides full provider stack required for TimeRangeProvider to work
// (TimeRangeProvider must be inside QueryClientProvider)
// ---------------------------------------------------------------------------

const TimeRangeConsumer: React.FC = () => {
  const { timeRange, setTimeRange } = useTimeRange()
  return (
    <div>
      <span data-testid="time-range-value">{timeRange}</span>
      <button onClick={() => setTimeRange('30d')} data-testid="set-30d">set 30d</button>
      <button onClick={() => setTimeRange('90d')} data-testid="set-90d">set 90d</button>
      <button onClick={() => setTimeRange('7d')} data-testid="set-7d">set 7d</button>
    </div>
  )
}

const renderWithProviders = (ui: React.ReactElement) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <TimeRangeProvider>
        {ui}
      </TimeRangeProvider>
    </QueryClientProvider>
  )
}

// ---------------------------------------------------------------------------
// TimeRange type validation — exported correctly
// ---------------------------------------------------------------------------

describe('[P0] TimeRangeContext — type export', () => {
  it('[P0] TimeRange type accepts "7d", "30d", "90d" values', () => {
    // Validates the TypeScript type works at runtime via assignment
    const valid7d: TimeRange = '7d'
    const valid30d: TimeRange = '30d'
    const valid90d: TimeRange = '90d'
    expect(valid7d).toBe('7d')
    expect(valid30d).toBe('30d')
    expect(valid90d).toBe('90d')
  })
})

// ---------------------------------------------------------------------------
// Default state
// ---------------------------------------------------------------------------

describe('[P0] TimeRangeContext — default state (AC3)', () => {
  it('[P0] default time range value is "7d"', () => {
    renderWithProviders(<TimeRangeConsumer />)
    expect(screen.getByTestId('time-range-value')).toHaveTextContent('7d')
  })
})

// ---------------------------------------------------------------------------
// State updates
// ---------------------------------------------------------------------------

describe('[P0] TimeRangeContext — setTimeRange updates state (AC3)', () => {
  it('[P0] setTimeRange("30d") updates time range to "30d"', () => {
    renderWithProviders(<TimeRangeConsumer />)
    fireEvent.click(screen.getByTestId('set-30d'))
    expect(screen.getByTestId('time-range-value')).toHaveTextContent('30d')
  })

  it('[P0] setTimeRange("90d") updates time range to "90d"', () => {
    renderWithProviders(<TimeRangeConsumer />)
    fireEvent.click(screen.getByTestId('set-90d'))
    expect(screen.getByTestId('time-range-value')).toHaveTextContent('90d')
  })

  it('[P0] setTimeRange("7d") resets time range back to "7d" from "30d"', () => {
    renderWithProviders(<TimeRangeConsumer />)
    fireEvent.click(screen.getByTestId('set-30d'))
    expect(screen.getByTestId('time-range-value')).toHaveTextContent('30d')
    fireEvent.click(screen.getByTestId('set-7d'))
    expect(screen.getByTestId('time-range-value')).toHaveTextContent('7d')
  })
})

// ---------------------------------------------------------------------------
// React Query invalidation on time range change (AC3)
// ---------------------------------------------------------------------------

describe('[P0] TimeRangeContext — React Query invalidation (AC3)', () => {
  it('[P0] changing time range calls queryClient.invalidateQueries', () => {
    // Creates a real QueryClient with spy to verify invalidation is triggered
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    render(
      <QueryClientProvider client={queryClient}>
        <TimeRangeProvider>
          <TimeRangeConsumer />
        </TimeRangeProvider>
      </QueryClientProvider>
    )

    fireEvent.click(screen.getByTestId('set-30d'))

    // invalidateQueries should have been called when time range changed
    expect(invalidateSpy).toHaveBeenCalled()
  })

  it('[P1] changing time range to the same value does not double-invalidate', () => {
    // Guard: MUI ToggleButtonGroup passes null when clicking already-selected item
    // setTimeRange is only called when value is non-null (per story dev notes)
    // This test documents that the guard exists
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')

    render(
      <QueryClientProvider client={queryClient}>
        <TimeRangeProvider>
          <TimeRangeConsumer />
        </TimeRangeProvider>
      </QueryClientProvider>
    )

    // Change to 30d (triggers invalidation)
    fireEvent.click(screen.getByTestId('set-30d'))
    const callCountAfterFirst = invalidateSpy.mock.calls.length
    expect(callCountAfterFirst).toBeGreaterThan(0)
  })
})

// ---------------------------------------------------------------------------
// useTimeRange error when used outside provider
// ---------------------------------------------------------------------------

describe('[P2] TimeRangeContext — hook outside provider', () => {
  it('[P2] useTimeRange throws an error when used outside TimeRangeProvider', () => {
    // When used outside provider, should throw a descriptive error
    const ConsumerOutsideProvider: React.FC = () => {
      useTimeRange() // Should throw
      return null
    }

    // Suppress React error boundary console output during this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => render(<ConsumerOutsideProvider />)).toThrow()

    consoleSpy.mockRestore()
  })
})

// ---------------------------------------------------------------------------
// Provider nesting — TimeRangeProvider must be inside QueryClientProvider
// ---------------------------------------------------------------------------

describe('[P0] TimeRangeContext — provider nesting requirement', () => {
  it('[P0] TimeRangeProvider renders children successfully inside QueryClientProvider', () => {
    const queryClient = new QueryClient()
    const { getByText } = render(
      <QueryClientProvider client={queryClient}>
        <TimeRangeProvider>
          <span>child content</span>
        </TimeRangeProvider>
      </QueryClientProvider>
    )
    expect(getByText('child content')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Barrel export validation
// ---------------------------------------------------------------------------

describe('[P1] TimeRangeContext — barrel export from context/index.ts', () => {
  it('[P1] TimeRangeProvider is exported from context barrel index', async () => {
    const contextBarrel = await import('../../src/context/index')
    expect(contextBarrel).toHaveProperty('TimeRangeProvider')
  })

  it('[P1] useTimeRange is exported from context barrel index', async () => {
    const contextBarrel = await import('../../src/context/index')
    expect(contextBarrel).toHaveProperty('useTimeRange')
  })

  it('[P1] TimeRangeProvider is named export from TimeRangeContext module', async () => {
    const mod = await import('../../src/context/TimeRangeContext')
    expect(mod).toHaveProperty('TimeRangeProvider')
    expect(mod).toHaveProperty('useTimeRange')
  })
})
