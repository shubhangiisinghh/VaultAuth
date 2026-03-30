import { useSearchParams } from 'react-router-dom'
import type { AuthStep } from '../types'

/**
 * Reads the ?step= param the backend puts in the Location header URL.
 * The backend sets Location: /login?step=totp (or challenge, change-password).
 * When the SPA navigates there, this hook tells LoginPage which form to show.
 *
 * Default is 'password' — the start of the flow.
 */
export function useAuthChallenge(): AuthStep | 'password' {
  const [params] = useSearchParams()
  const step = params.get('step')

  switch (step) {
    case 'totp':            return 'TOTP_PENDING'
    case 'challenge':       return 'CHALLENGE_PENDING'
    case 'change-password': return 'PASSWORD_CHANGE'
    default:                return 'password'
  }
}
