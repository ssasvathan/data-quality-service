/**
 * Component Tests — DatasetInfoPanel
 *
 * GREEN PHASE: All tests enabled — DatasetInfoPanel is fully implemented.
 *
 * @testing-framework Vitest + React Testing Library + MUI ThemeProvider
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { ThemeProvider } from '@mui/material/styles'
import React from 'react'
import theme from '../../src/theme'
import type { DatasetDetail } from '../../src/api/types'

// ---------------------------------------------------------------------------
// Mock navigator.clipboard — jsdom does not implement clipboard API
// ---------------------------------------------------------------------------

const mockClipboard = {
  writeText: vi.fn().mockResolvedValue(undefined),
}
Object.defineProperty(navigator, 'clipboard', {
  value: mockClipboard,
  writable: true,
})

import { DatasetInfoPanel } from '../../src/components/DatasetInfoPanel'

// ---------------------------------------------------------------------------
// Render helper: wraps component with MUI ThemeProvider so theme tokens work.
// Same pattern as DatasetCard.test.tsx.
// ---------------------------------------------------------------------------

const renderWithTheme = (ui: React.ReactElement) =>
  render(<ThemeProvider theme={theme}>{ui}</ThemeProvider>)

// ---------------------------------------------------------------------------
// Mock DatasetDetail fixture — matches GET /api/datasets/{dataset_id} response
// (AC1: full metadata with all fields present)
// ---------------------------------------------------------------------------

const mockDataset: DatasetDetail = {
  dataset_id: 9,
  dataset_name: 'lob=retail/src_sys_nm=alpha/dataset=sales_daily',
  lob_id: 'LOB_RETAIL',
  source_system: 'alpha',
  format: 'Parquet',
  hdfs_path: '/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402',
  parent_path: 'lob=retail/src_sys_nm=alpha',
  partition_date: '2026-04-02',
  row_count: 103876,
  previous_row_count: 96103,
  last_updated: '2026-04-02T06:45:00',
  run_id: 9,
  rerun_number: 0,
  dqs_score: 98.50,
  check_status: 'PASS',
  error_message: null,
}

// ---------------------------------------------------------------------------
// AC 1: Renders complete dataset metadata fields
// ---------------------------------------------------------------------------

describe('[P0] DatasetInfoPanel — metadata rendering (AC 1)', () => {
  it('[P0] renders source system field value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('alpha')).toBeInTheDocument()
  })

  it('[P0] renders LOB field value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('LOB_RETAIL')).toBeInTheDocument()
  })

  it('[P0] renders format field value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('Parquet')).toBeInTheDocument()
  })

  it('[P0] renders HDFS path value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(
      screen.getByText('/prod/datalake/lob=retail/src_sys_nm=alpha/dataset=sales_daily/partition_date=20260402')
    ).toBeInTheDocument()
  })

  it('[P0] renders partition date field value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('2026-04-02')).toBeInTheDocument()
  })

  it('[P0] renders row count with locale formatting', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    // row_count: 103876 → "103,876"
    expect(screen.getByText(/103,876/)).toBeInTheDocument()
  })

  it('[P0] renders run ID field value', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('9')).toBeInTheDocument()
  })

  it('[P1] renders last updated timestamp field', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('2026-04-02T06:45:00')).toBeInTheDocument()
  })

  it('[P1] renders parent path field', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.getByText('lob=retail/src_sys_nm=alpha')).toBeInTheDocument()
  })

  it('[P1] uses semantic dl element for metadata list (AC5)', () => {

    // Semantic dl/dt/dd structure is required for screen reader accessibility
    const { container } = renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(container.querySelector('dl')).toBeInTheDocument()
  })

  it('[P1] uses semantic dt elements for labels (AC5)', () => {

    const { container } = renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    const dtElements = container.querySelectorAll('dt')
    expect(dtElements.length).toBeGreaterThan(0)
  })

  it('[P1] uses semantic dd elements for values (AC5)', () => {

    const { container } = renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    const ddElements = container.querySelectorAll('dd')
    expect(ddElements.length).toBeGreaterThan(0)
  })
})

// ---------------------------------------------------------------------------
// AC 2 + AC 6: HDFS path copy button
// ---------------------------------------------------------------------------

describe('[P0] DatasetInfoPanel — copy button (AC 2, AC 6)', () => {
  it('[P0] copy button has aria-label="Copy HDFS path to clipboard" (AC6)', () => {

    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    const copyBtn = screen.getByRole('button', { name: 'Copy HDFS path to clipboard' })
    expect(copyBtn).toBeInTheDocument()
  })

  it('[P0] clicking copy button calls navigator.clipboard.writeText with HDFS path (AC2)', () => {

    mockClipboard.writeText.mockClear()
    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    const copyBtn = screen.getByRole('button', { name: 'Copy HDFS path to clipboard' })
    fireEvent.click(copyBtn)
    expect(mockClipboard.writeText).toHaveBeenCalledTimes(1)
    expect(mockClipboard.writeText).toHaveBeenCalledWith(mockDataset.hdfs_path)
  })

  it('[P1] copy button tooltip shows "Copied!" immediately after click (AC2)', async () => {

    // After click, `copied` state → true → Tooltip title becomes "Copied!"
    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    const copyBtn = screen.getByRole('button', { name: 'Copy HDFS path to clipboard' })
    fireEvent.click(copyBtn)
    // The Tooltip title prop changes to "Copied!" — check accessible name or tooltip element
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /copied/i })).toBeInTheDocument()
    })
  })
})

// ---------------------------------------------------------------------------
// AC 3: Error message display for Spark job failures
// ---------------------------------------------------------------------------

describe('[P0] DatasetInfoPanel — error message display (AC 3)', () => {
  it('[P0] renders error Alert when error_message is not null', () => {

    const datasetWithError: DatasetDetail = {
      ...mockDataset,
      error_message: 'SparkException: Job aborted due to stage failure: Task 0 in stage 1.0 failed',
      check_status: 'FAIL',
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetWithError} />)
    expect(
      screen.getByText('SparkException: Job aborted due to stage failure: Task 0 in stage 1.0 failed')
    ).toBeInTheDocument()
  })

  it('[P0] error Alert has role="alert" for accessibility (AC3)', () => {

    const datasetWithError: DatasetDetail = {
      ...mockDataset,
      error_message: 'SparkException: Task failed',
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetWithError} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
  })

  it('[P0] does NOT render error Alert when error_message is null', () => {

    // mockDataset has error_message: null
    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// AC 4: "N/A" field styling — gray italic when value is "N/A"
// ---------------------------------------------------------------------------

describe('[P1] DatasetInfoPanel — N/A field rendering (AC 4)', () => {
  it('[P1] renders "N/A" for lob_id when value is "N/A"', () => {

    const datasetWithNA: DatasetDetail = {
      ...mockDataset,
      lob_id: 'N/A',
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetWithNA} />)
    expect(screen.getByText('N/A')).toBeInTheDocument()
  })

  it('[P1] N/A lob_id value is styled with gray italic text (AC4)', () => {

    // When lob_id === "N/A", should render with color: text.secondary + fontStyle: italic
    const datasetWithNA: DatasetDetail = {
      ...mockDataset,
      lob_id: 'N/A',
    }
    const { container } = renderWithTheme(<DatasetInfoPanel dataset={datasetWithNA} />)
    // Find the element containing "N/A" and verify italic style
    const naText = screen.getByText('N/A')
    expect(naText).toHaveStyle({ fontStyle: 'italic' })
  })
})

// ---------------------------------------------------------------------------
// AC 1 + story spec: Row count delta display
// ---------------------------------------------------------------------------

describe('[P1] DatasetInfoPanel — row count delta display (AC 1)', () => {
  it('[P1] shows "(was X)" delta text when previous_row_count differs from row_count', () => {

    // mockDataset: row_count=103876, previous_row_count=96103
    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    // Delta text: "(was 96,103)"
    expect(screen.getByText(/was 96,103/i)).toBeInTheDocument()
  })

  it('[P1] does NOT show delta text when previous_row_count is null', () => {

    const datasetNoPrev: DatasetDetail = {
      ...mockDataset,
      previous_row_count: null,
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetNoPrev} />)
    expect(screen.queryByText(/was/i)).not.toBeInTheDocument()
  })

  it('[P1] does NOT show delta text when row_count equals previous_row_count', () => {

    const datasetSameCount: DatasetDetail = {
      ...mockDataset,
      row_count: 103876,
      previous_row_count: 103876,
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetSameCount} />)
    expect(screen.queryByText(/was/i)).not.toBeInTheDocument()
  })

  it('[P1] renders "—" (em dash) for row count when row_count is null', () => {

    const datasetNoCount: DatasetDetail = {
      ...mockDataset,
      row_count: null,
      previous_row_count: null,
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetNoCount} />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Story spec: Rerun number conditional display
// ---------------------------------------------------------------------------

describe('[P1] DatasetInfoPanel — rerun number display', () => {
  it('[P1] does NOT render "Rerun #" row when rerun_number is 0', () => {

    // mockDataset has rerun_number: 0 — row should NOT appear
    renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)
    expect(screen.queryByText(/rerun/i)).not.toBeInTheDocument()
  })

  it('[P1] renders "Rerun #" row when rerun_number is greater than 0', () => {

    const datasetWithRerun: DatasetDetail = {
      ...mockDataset,
      rerun_number: 2,
    }
    renderWithTheme(<DatasetInfoPanel dataset={datasetWithRerun} />)
    expect(screen.getByText('2')).toBeInTheDocument()
    // Also verify the label appears
    expect(screen.getByText(/rerun/i)).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// Barrel export — DatasetInfoPanel accessible via components index
// ---------------------------------------------------------------------------

describe('[P1] DatasetInfoPanel — barrel export', () => {
  it('[P1] DatasetInfoPanel is exported from the components barrel index', async () => {

    const components = await import('../../src/components/index')
    expect(components).toHaveProperty('DatasetInfoPanel')
  })
})

// ---------------------------------------------------------------------------
// Rendering stability
// ---------------------------------------------------------------------------

describe('[P0] DatasetInfoPanel — rendering stability', () => {
  it('[P0] renders without throwing with full valid dataset props', () => {

    expect(() => renderWithTheme(<DatasetInfoPanel dataset={mockDataset} />)).not.toThrow()
  })

  it('[P1] renders without throwing when parent_path is null', () => {

    const datasetNoParent: DatasetDetail = {
      ...mockDataset,
      parent_path: null,
    }
    expect(() => renderWithTheme(<DatasetInfoPanel dataset={datasetNoParent} />)).not.toThrow()
  })

  it('[P1] renders without throwing when dqs_score is null', () => {

    const datasetNoScore: DatasetDetail = {
      ...mockDataset,
      dqs_score: null,
    }
    expect(() => renderWithTheme(<DatasetInfoPanel dataset={datasetNoScore} />)).not.toThrow()
  })

  it('[P1] renders without throwing when check_status is null', () => {

    const datasetNoStatus: DatasetDetail = {
      ...mockDataset,
      check_status: null,
    }
    expect(() => renderWithTheme(<DatasetInfoPanel dataset={datasetNoStatus} />)).not.toThrow()
  })
})
