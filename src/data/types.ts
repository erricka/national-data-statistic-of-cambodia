export type Lang = 'en' | 'km'
export type Localized = { en: string; km: string }

export type Frequency = 'M' | 'Q' | 'S' | 'A' | null

export interface Theme {
  id: string
  name: Localized
  description: Localized
  color: string
  icon: string
  categoryIds: number[]
  datasetCount: number
}

export interface Category {
  id: number
  code: string
  name: Localized
  abbr: string | null
  themeId: string | null
  datasetCount: number
  lastUpdated: string | null
}

export interface Release {
  uploadId: string
  /** Approval (publication) date, YYYY-MM-DD */
  date: string
  submitted: string | null
  label: string | null
}

export interface Dataset {
  id: string
  code: string
  title: Localized
  categoryId: number
  themeId: string | null
  sortOrder: number
  records: number
  frequency: Frequency
  coverage: { from: number; to: number } | null
  releases: Release[]
  lastUpdated: string
  table?: { rows: number; columns: number; source: string }
}

export interface CatalogRelease {
  date: string
  datasetId: string
  uploadId: string
}

export interface Catalog {
  generatedAt: string
  stats: { datasets: number; agencies: number; records: number; releases: number; lastRelease: string | null }
  themes: Theme[]
  categories: Category[]
  datasets: Dataset[]
  releases: CatalogRelease[]
}

export interface SeriesPoint {
  /** "2025" for annual or "2025-03" for monthly */
  period: string
  value: number
}

export type ValueFormat = 'percent1' | 'integer' | 'decimal1' | 'compact'

export interface Indicator {
  id: string
  datasetId: string
  themeId: string
  title: Localized
  unit: Localized
  frequency: 'M' | 'A'
  format: ValueFormat
  latest: SeriesPoint
  /** The immediately preceding comparable period, when the source has it */
  previous: SeriesPoint | null
  change: { value: number | null; type: 'pct' | 'pp' }
  series: SeriesPoint[]
}

export interface Province {
  no: number
  id: string
  name: Localized
}

export interface ProvinceIndicator {
  id: string
  datasetId: string
  title: Localized
  unit: Localized
  years: number[]
  values: Record<string, Record<string, number>>
}

export type Cell = string | number | null

export interface StatTable {
  id: string
  /** Header levels, top to bottom. "" continues the cell to the left (or above). */
  header: string[][]
  rows: Cell[][]
}
