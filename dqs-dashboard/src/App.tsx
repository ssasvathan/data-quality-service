import { ThemeProvider, CssBaseline } from '@mui/material'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route } from 'react-router'
import theme from './theme'
import AppLayout from './layouts/AppLayout'
import SummaryPage from './pages/SummaryPage'
import LobDetailPage from './pages/LobDetailPage'
import DatasetDetailPage from './pages/DatasetDetailPage'

const queryClient = new QueryClient()

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <AppLayout>
            <Routes>
              <Route path="/" element={<SummaryPage />} />
              <Route path="/lobs/:lobId" element={<LobDetailPage />} />
              <Route path="/datasets/:datasetId" element={<DatasetDetailPage />} />
            </Routes>
          </AppLayout>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

export default App
