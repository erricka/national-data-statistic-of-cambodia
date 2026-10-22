import { useCallback, useEffect, useState } from 'react'
import { loadCatalog, loadIndicators, loadProvinces, loadTable } from '../data/repository'

export type AsyncState<T> =
  | { status: 'loading'; data?: undefined; error?: undefined; retry: () => void }
  | { status: 'error'; data?: undefined; error: Error; retry: () => void }
  | { status: 'ready'; data: T; error?: undefined; retry: () => void }

export function useAsync<T>(load: () => Promise<T>, deps: unknown[]): AsyncState<T> {
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'ready'; data?: T; error?: Error }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let alive = true
    setState((s) => (s.status === 'loading' ? s : { status: 'loading' }))
    load().then(
      (data) => alive && setState({ status: 'ready', data }),
      (error: Error) => alive && setState({ status: 'error', error }),
    )
    return () => {
      alive = false
    }
  }, [...deps, attempt])

  const retry = useCallback(() => setAttempt((a) => a + 1), [])
  return { ...state, retry } as AsyncState<T>
}

export const useCatalog = () => useAsync(loadCatalog, [])
export const useIndicators = () => useAsync(loadIndicators, [])
export const useProvinces = () => useAsync(loadProvinces, [])
export const useTable = (id: string) => useAsync(() => loadTable(id), [id])
