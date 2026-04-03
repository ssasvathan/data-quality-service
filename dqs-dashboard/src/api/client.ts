/**
 * API client — thin wrapper around fetch for dqs-serve endpoints.
 * All requests go through the /api proxy configured in vite.config.ts.
 */

const BASE_URL = '/api'

export async function apiFetch<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error(`API request failed: ${response.status} ${response.statusText}`)
  }
  return response.json() as Promise<T>
}
