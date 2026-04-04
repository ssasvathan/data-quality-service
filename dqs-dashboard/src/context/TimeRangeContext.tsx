/**
 * TimeRangeContext — global time range state for the dashboard.
 * TimeRangeProvider MUST be rendered inside QueryClientProvider so that
 * useQueryClient() can access the query cache for invalidation on range change.
 */

import React, { createContext, useContext, useState } from 'react'
import type { ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'

export type TimeRange = '7d' | '30d' | '90d'

interface TimeRangeContextValue {
  timeRange: TimeRange
  setTimeRange: (range: TimeRange) => void
}

const TimeRangeContext = createContext<TimeRangeContextValue | null>(null)

export function TimeRangeProvider({ children }: { children: ReactNode }) {
  const [timeRange, setTimeRangeState] = useState<TimeRange>('7d')
  const queryClient = useQueryClient()

  const setTimeRange = (range: TimeRange): void => {
    setTimeRangeState(range)
    queryClient.invalidateQueries()
  }

  return (
    <TimeRangeContext.Provider value={{ timeRange, setTimeRange }}>
      {children}
    </TimeRangeContext.Provider>
  )
}

export function useTimeRange(): TimeRangeContextValue {
  const ctx = useContext(TimeRangeContext)
  if (ctx === null) {
    throw new Error('useTimeRange must be used within a TimeRangeProvider')
  }
  return ctx
}
