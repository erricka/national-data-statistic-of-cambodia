import type { Catalog, Indicator, Province, ProvinceIndicator, StatTable } from './types'

export const config = {
  mode: (import.meta.env.VITE_DATA_MODE as string) === 'api' ? 'api' : 'static',
  apiBase: ((import.meta.env.VITE_API_BASE_URL as string) || '').replace(/\/$/, ''),
  adminUrl: (import.meta.env.VITE_ADMIN_URL as string) || '',
  dataBase: `${import.meta.env.BASE_URL}data`,
} as const

const cache = new Map<string, Promise<unknown>>()

function once<T>(key: string, load: () => Promise<T>): Promise<T> {
  if (!cache.has(key)) {
    const p = load().catch((e) => {
      cache.delete(key) // allow a retry after a failure
      throw e
    })
    cache.set(key, p)
  }
  return cache.get(key) as Promise<T>
}

async function getJson<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} — ${url}`)
  return res.json() as Promise<T>
}

export const loadCatalog = () => once('catalog', () => getJson<Catalog>(`${config.dataBase}/catalog.json`))

export const loadIndicators = () =>
  once('indicators', async () => (await getJson<{ indicators: Indicator[] }>(`${config.dataBase}/indicators.json`)).indicators)

export const loadProvinces = () =>
  once('provinces', () => getJson<{ provinces: Province[]; indicators: ProvinceIndicator[] }>(`${config.dataBase}/provinces.json`))

interface ApiEnvelope<T> {
  code: string
  message: string | { messageBody?: string }
  data?: T
}

/** Loads a statistical table: live from the NIS server (DTSI003) in api mode, otherwise from the snapshot. */
export const loadTable = (id: string) =>
  once(`table:${id}`, async (): Promise<StatTable> => {
    if (config.mode === 'api' && config.apiBase) {
      try {
        const body = await getJson<ApiEnvelope<{ header: { title: string }[][]; list: StatTable['rows'] }>>(
          `${config.apiBase}/DTSI003`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ sourceId: id, upload_id: '', page: 1, size: 0 }),
          },
        )
        if (body.code === '000000' && body.data) {
          return {
            id,
            header: (body.data.header || []).map((row) => row.map((h) => h?.title ?? '')),
            rows: body.data.list || [],
          }
        }
      } catch {
        // fall through to the snapshot
      }
    }
    return getJson<StatTable>(`${config.dataBase}/tables/${id}.json`)
  })

/** Official Excel export from the NIS server (api mode only). */
export const officialDownloadUrl = (id: string) =>
  config.mode === 'api' && config.apiBase ? `${config.apiBase}/files/${encodeURIComponent(id)}/download` : null
