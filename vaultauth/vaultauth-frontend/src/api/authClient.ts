/**
 * authClient wraps fetch() with one key behaviour:
 * on a 401 response, it reads the Location header and redirects the SPA there.
 *
 * This is exactly the contract a server needs to honour to work with an SPA —
 * instead of a 302 redirect the browser follows automatically (which would deliver
 * HTML to a fetch() call), the server returns 401 + Location so the SPA can
 * choose how and when to navigate.
 */

const BASE = '/api'

interface FetchOptions extends RequestInit {
  skipAuthRedirect?: boolean
}

export async function authFetch(path: string, options: FetchOptions = {}): Promise<Response> {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    credentials: 'include', // always send cookies so the session travels with the request
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  })

  if (res.status === 401 && !options.skipAuthRedirect) {
    const location = res.headers.get('Location')
    if (location) {
      // the backend told us exactly where the user needs to go next
      window.location.href = location
      // return a never-resolving promise so callers don't try to parse the 401 body
      return new Promise(() => {})
    }
  }

  return res
}

export async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await authFetch(path, {
    method: 'POST',
    body: JSON.stringify(body),
  })
  return res.json()
}

export async function get<T>(path: string): Promise<T> {
  const res = await authFetch(path, { method: 'GET' })
  return res.json()
}
