import { useSyncExternalStore } from 'react'

function subscribe(callback: () => void) {
  window.addEventListener('hashchange', callback)
  return () => window.removeEventListener('hashchange', callback)
}
function getRoute() { return window.location.hash.slice(1) || '/dashboard' }
export function useRoute() { return useSyncExternalStore(subscribe, getRoute) }
