/**
 * Vitest global test setup
 * Extends Vitest matchers with @testing-library/jest-dom assertions.
 * Required for: toBeInTheDocument, toHaveTextContent, toHaveStyle, etc.
 */
import '@testing-library/jest-dom'
