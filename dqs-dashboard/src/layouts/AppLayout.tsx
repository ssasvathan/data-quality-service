import { Box, AppBar, Toolbar, Typography } from '@mui/material'
import type { ReactNode } from 'react'

interface AppLayoutProps {
  children: ReactNode
}

/**
 * AppLayout — top nav bar + main content area.
 * Skeleton implementation; full layout defined in story 2-x UX spec.
 */
export default function AppLayout({ children }: AppLayoutProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Data Quality Service
          </Typography>
        </Toolbar>
      </AppBar>
      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        {children}
      </Box>
    </Box>
  )
}
