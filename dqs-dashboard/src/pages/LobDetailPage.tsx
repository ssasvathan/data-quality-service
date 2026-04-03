import { Typography } from '@mui/material'
import { useParams } from 'react-router'

/**
 * LobDetailPage — drill-down view for a single Line of Business.
 * Placeholder; full implementation in story 2-x.
 */
export default function LobDetailPage() {
  const { lobId } = useParams<{ lobId: string }>()

  return (
    <div>
      <Typography variant="h4" gutterBottom>
        LOB Detail: {lobId}
      </Typography>
      <Typography variant="body1" color="text.secondary">
        LOB detail view — placeholder, implemented in story 2-x.
      </Typography>
    </div>
  )
}
