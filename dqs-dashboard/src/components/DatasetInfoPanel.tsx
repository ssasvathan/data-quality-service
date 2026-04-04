/**
 * DatasetInfoPanel — Story 4.11
 *
 * Pure display component for dataset metadata. Accepts DatasetDetail as props.
 * No API calls, no React Query hooks, no routing logic.
 * Parent page (Story 4.12) handles data fetching and passes props.
 *
 * Semantic dl/dt/dd structure for screen reader accessibility (AC5).
 */

import React from 'react'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import Alert from '@mui/material/Alert'
import ContentCopy from '@mui/icons-material/ContentCopy'
import type { DatasetDetail } from '../api/types'

interface DatasetInfoPanelProps {
  dataset: DatasetDetail
}

const isNA = (value: string) => value === 'N/A'

export function DatasetInfoPanel({ dataset }: DatasetInfoPanelProps) {
  const [copied, setCopied] = React.useState<boolean>(false)

  React.useEffect(() => {
    if (!copied) return
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const handleCopy = () => {
    void navigator.clipboard.writeText(dataset.hdfs_path)
    setCopied(true)
  }

  // Row count display
  const rowCountText = dataset.row_count !== null ? dataset.row_count.toLocaleString() : '—'

  const hasDelta =
    dataset.row_count !== null &&
    dataset.previous_row_count !== null &&
    dataset.row_count !== dataset.previous_row_count

  const deltaText = hasDelta ? `(was ${dataset.previous_row_count!.toLocaleString()})` : null

  return (
    <Box>
      <Box
        component="dl"
        sx={{ m: 0, display: 'grid', gridTemplateColumns: '140px 1fr', gap: '4px 16px' }}
      >
        {/* Source System */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Source System
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">{dataset.source_system}</Typography>
        </Box>

        {/* LOB */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            LOB
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography
            variant="body2"
            sx={isNA(dataset.lob_id) ? { color: 'text.secondary', fontStyle: 'italic' } : {}}
          >
            {dataset.lob_id}
          </Typography>
        </Box>

        {/* Format */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Format
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">{dataset.format}</Typography>
        </Box>

        {/* HDFS Path */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            HDFS Path
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 0.5 }}>
            <Typography variant="mono" sx={{ wordBreak: 'break-all', flex: 1 }}>
              {dataset.hdfs_path}
            </Typography>
            <Tooltip title={copied ? 'Copied!' : 'Copy HDFS path'}>
              <IconButton
                size="small"
                onClick={handleCopy}
                aria-label={copied ? 'Copied! Copy HDFS path to clipboard' : 'Copy HDFS path to clipboard'}
              >
                <ContentCopy fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>

        {/* Parent Path */}
        {dataset.parent_path !== null && (
          <>
            <Box component="dt">
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                Parent Path
              </Typography>
            </Box>
            <Box component="dd" sx={{ m: 0 }}>
              <Typography variant="mono" sx={{ wordBreak: 'break-all' }}>
                {dataset.parent_path}
              </Typography>
            </Box>
          </>
        )}

        {/* Partition Date */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Partition Date
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">{dataset.partition_date}</Typography>
        </Box>

        {/* Row Count */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Row Count
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">
            {rowCountText}
            {deltaText && (
              <Typography
                component="span"
                variant="caption"
                color="text.secondary"
                sx={{ ml: 0.5 }}
              >
                {deltaText}
              </Typography>
            )}
          </Typography>
        </Box>

        {/* Last Updated */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Last Updated
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">{dataset.last_updated}</Typography>
        </Box>

        {/* Run ID */}
        <Box component="dt">
          <Typography variant="caption" color="text.secondary" fontWeight={600}>
            Run ID
          </Typography>
        </Box>
        <Box component="dd" sx={{ m: 0 }}>
          <Typography variant="body2">{dataset.run_id}</Typography>
        </Box>

        {/* Rerun # — only shown when rerun_number > 0 */}
        {dataset.rerun_number > 0 && (
          <>
            <Box component="dt">
              <Typography variant="caption" color="text.secondary" fontWeight={600}>
                Rerun #
              </Typography>
            </Box>
            <Box component="dd" sx={{ m: 0 }}>
              <Typography variant="body2">{dataset.rerun_number}</Typography>
            </Box>
          </>
        )}
      </Box>

      {/* Error message — AC3 */}
      {dataset.error_message && (
        <Alert severity="error" variant="outlined" sx={{ mt: 2 }}>
          {dataset.error_message}
        </Alert>
      )}
    </Box>
  )
}
