import type React from 'react'
import { createTheme } from '@mui/material/styles'

// ---------------------------------------------------------------------------
// TypeScript module augmentation — required for custom typography variants
// and custom palette keys
// ---------------------------------------------------------------------------

declare module '@mui/material/styles' {
  interface TypographyVariants {
    score: React.CSSProperties
    scoreSm: React.CSSProperties
    mono: React.CSSProperties
  }
  interface TypographyVariantsOptions {
    score?: React.CSSProperties
    scoreSm?: React.CSSProperties
    mono?: React.CSSProperties
  }
  interface Palette {
    neutral: {
      50: string
      100: string
      200: string
      300: string
      500: string
      700: string
      900: string
    }
  }
  interface PaletteOptions {
    neutral?: {
      50?: string
      100?: string
      200?: string
      300?: string
      500?: string
      700?: string
      900?: string
    }
  }
}

declare module '@mui/material/Typography' {
  interface TypographyPropsVariantOverrides {
    score: true
    scoreSm: true
    mono: true
  }
}

// ---------------------------------------------------------------------------
// Font stacks
// ---------------------------------------------------------------------------

const fontFamilySans =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif'

const fontFamilyMono =
  '"SF Mono", "Fira Code", "Fira Mono", Menlo, monospace'

// ---------------------------------------------------------------------------
// Neutral palette tokens (documented here, exposed via theme.palette.neutral)
//
//   50  → #FAFAFA  (page background)
//   100 → #F5F5F5  (card background)
//   200 → #EEEEEE  (borders, dividers)
//   300 → #E0E0E0  (disabled states)
//   500 → #9E9E9E  (secondary text, icons)
//   700 → #616161  (body text)
//   900 → #212121  (headings, primary text)
// ---------------------------------------------------------------------------

const neutral = {
  50: '#FAFAFA',
  100: '#F5F5F5',
  200: '#EEEEEE',
  300: '#E0E0E0',
  500: '#9E9E9E',
  700: '#616161',
  900: '#212121',
} as const

// ---------------------------------------------------------------------------
// getDqsColor — returns semantic color for a DQS Score
// Thresholds: success >= 80, warning 60-79, error < 60
// ---------------------------------------------------------------------------

export function getDqsColor(score: number): string {
  if (score >= 80) return '#2E7D32' // success
  if (score >= 60) return '#ED6C02' // warning
  return '#D32F2F'                   // error
}

// ---------------------------------------------------------------------------
// getDqsColorLight — returns light tint variant for a DQS Score
// ---------------------------------------------------------------------------

export function getDqsColorLight(score: number): string {
  if (score >= 80) return '#E8F5E9' // success-light
  if (score >= 60) return '#FFF3E0' // warning-light
  return '#FFEBEE'                   // error-light
}

// ---------------------------------------------------------------------------
// MUI 7 custom theme — DQS Design System Foundation (Story 4.1)
// ---------------------------------------------------------------------------
//
// Spacing reference (8px base — default MUI spacing, not overridden):
//   xs  = theme.spacing(0.5) →  4px
//   sm  = theme.spacing(1)   →  8px
//   md  = theme.spacing(2)   → 16px
//   lg  = theme.spacing(3)   → 24px
//   xl  = theme.spacing(4)   → 32px
//   2xl = theme.spacing(6)   → 48px

const theme = createTheme({
  palette: {
    mode: 'light',

    // Neutral palette tokens — accessible via theme.palette.neutral[50..900]
    neutral,

    // Primary accent — links, active states, focus
    primary: {
      main: '#1565C0',
      light: '#E3F2FD', // primary-light for selected/active backgrounds
    },

    // Secondary — muted blue-grey
    secondary: {
      main: '#455A64',
    },

    // Semantic — DQS Score thresholds
    success: {
      main: '#2E7D32',   // DQS Score >= 80
      light: '#E8F5E9',  // success-light tint
    },
    warning: {
      main: '#ED6C02',   // DQS Score 60-79
      light: '#FFF3E0',  // warning-light tint
    },
    error: {
      main: '#D32F2F',   // DQS Score < 60
      light: '#FFEBEE',  // error-light tint
    },

    // Backgrounds — aligned to neutral tokens
    background: {
      default: neutral[50],   // #FAFAFA — page background
      paper: '#FFFFFF',       // card/panel surfaces
    },

    // Text — neutral scale
    text: {
      primary: neutral[900],   // #212121 — headings, primary text
      secondary: neutral[700], // #616161 — body text
      disabled: neutral[500],  // #9E9E9E — disabled/secondary icons
    },

    // Divider — neutral-200
    divider: neutral[200],
  },

  typography: {
    fontFamily: fontFamilySans,

    // Type scale
    h1: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.3 },    // 24px
    h2: { fontSize: '1.25rem', fontWeight: 600, lineHeight: 1.3 },   // 20px
    h3: { fontSize: '1rem', fontWeight: 600, lineHeight: 1.4 },      // 16px
    body1: { fontSize: '0.875rem', fontWeight: 400, lineHeight: 1.5 },   // 14px
    body2: { fontSize: '0.8125rem', fontWeight: 400, lineHeight: 1.5 },  // 13px
    caption: { fontSize: '0.75rem', fontWeight: 400, lineHeight: 1.4 },  // 12px

    // Custom variants — DQS-specific
    score: { fontSize: '1.75rem', fontWeight: 700, lineHeight: 1.0 },     // 28px — DQS Score large
    scoreSm: { fontSize: '1.125rem', fontWeight: 700, lineHeight: 1.0 },  // 18px — DQS Score compact
    mono: {
      fontSize: '0.8125rem',     // 13px
      fontWeight: 400,
      lineHeight: 1.4,
      fontFamily: fontFamilyMono,
    },
  },

  components: {
    // Card — flat design: 1px neutral-200 border, 8px radius, no shadow
    MuiCard: {
      defaultProps: {
        elevation: 0,
      },
      styleOverrides: {
        root: {
          border: `1px solid ${neutral[200]}`,
          borderRadius: '8px',
          boxShadow: 'none',
        },
      },
    },

    // CardContent — consistent 16px (md) padding
    MuiCardContent: {
      styleOverrides: {
        root: {
          padding: '16px',
          '&:last-child': { paddingBottom: '16px' },
        },
      },
    },
  },
})

export default theme
