import { Typography } from '@mui/material'
import { useParams } from 'react-router'

/**
 * DatasetDetailPage — drill-down view for a single dataset.
 * Placeholder; full implementation in story 2-x.
 */
export default function DatasetDetailPage() {
  const { datasetId } = useParams<{ datasetId: string }>()

  return (
    <div>
      <Typography variant="h4" gutterBottom>
        Dataset Detail: {datasetId}
      </Typography>
      <Typography variant="body1" color="text.secondary">
        Dataset detail view — placeholder, implemented in story 2-x.
      </Typography>
    </div>
  )
}
