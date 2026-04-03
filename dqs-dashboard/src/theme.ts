import { createTheme } from '@mui/material/styles'

/**
 * MUI 7 custom theme — muted palette and semantic colors per UX spec.
 */
const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1565C0',
    },
    secondary: {
      main: '#455A64',
    },
    error: {
      main: '#C62828',
    },
    warning: {
      main: '#E65100',
    },
    success: {
      main: '#2E7D32',
    },
    background: {
      default: '#F5F6F8',
      paper: '#FFFFFF',
    },
  },
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
  },
})

export default theme
