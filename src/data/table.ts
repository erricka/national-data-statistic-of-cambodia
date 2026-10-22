import type { Cell, StatTable } from './types'
import { buildHeaderGrid, leafLabels, normalizeHeader, type HeaderCell } from './header'

/** Parses numbers published as text ("1,234", " 5.6 ", "12%"); returns null for anything else. */
export function toNumber(v: Cell): number | null {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null
  if (typeof v !== 'string') return null
  const s = v.replace(/[,\s \u200B%]/g, '')
  if (!/^[-+]?(\d+\.?\d*|\.\d+)(e[-+]?\d+)?$/i.test(s)) return null
  const n = Number(s)
  return Number.isFinite(n) ? n : null
}

export type ColumnKind = 'label' | 'code' | 'measure'

export interface PreparedTable {
  id: string
  width: number
  header: string[][]
  cells: HeaderCell[]
  labels: string[]
  kinds: ColumnKind[]
  rows: Cell[][]
  /** Index of the column that best names each row (e.g. province) */
  labelColumn: number
  /** A second column that tells rows with the same label apart (e.g. year), or -1 */
  detailColumn: number
}

const CODE_HINT = /(^|\s)(ល\.?រ|លរ|no\.?|n°|#|code|កូដ|id|pro_?code|metric code)(\s|$)/i
const YEARISH = /^(19|20)\d{2}$/
const YEAR_LABEL = /ឆ្នាំ|year|period/i
/** Machine identifiers such as "27_its_a_apr" or "18A3_MUSEUM_1" */
const IDENTIFIER = /^[A-Za-z0-9]+([_.][A-Za-z0-9]+)+$/

export function prepareTable(t: StatTable): PreparedTable {
  const width = Math.max(t.header.at(-1)?.length ?? 0, t.header[0]?.length ?? 0, ...t.rows.slice(0, 200).map((r) => r.length))
  const header = normalizeHeader(t.header, width)
  const cells = buildHeaderGrid(header)
  const labels = leafLabels(header, cells)
  const rows = t.rows.map((r) => (r.length >= width ? r : [...r, ...Array(width - r.length).fill(null)]))

  const kinds: ColumnKind[] = labels.map((label, c) => {
    let filled = 0, numeric = 0, yearish = 0, ids = 0
    for (const r of rows) {
      const v = r[c]
      if (v === null || v === '' || v === '-') continue
      filled++
      if (typeof v === 'string' && IDENTIFIER.test(v.trim())) ids++
      const n = toNumber(v)
      if (n !== null) {
        numeric++
        if (Number.isInteger(n) && YEARISH.test(String(n))) yearish++
      }
    }
    if (!filled) return 'measure'
    if (ids / filled > 0.6) return 'code'
    if (numeric / filled < 0.6) return 'label'
    if (CODE_HINT.test(label.trim())) return 'code'
    if (yearish === numeric && (c < 2 || YEAR_LABEL.test(label))) return 'code'
    return 'measure'
  })

  // Row label: the first text column that isn't a code, preferring ones with varied values.
  let labelColumn = kinds.findIndex((k, c) => k === 'label' && !CODE_HINT.test(labels[c]) && new Set(rows.map((r) => r[c])).size > 1)
  if (labelColumn < 0) labelColumn = kinds.findIndex((k) => k === 'label')
  if (labelColumn < 0) labelColumn = kinds.findIndex((k) => k === 'code')
  if (labelColumn < 0) labelColumn = 0

  // When labels repeat (long-format tables), find the column that separates them
  let detailColumn = -1
  const labelsSeen = new Set(rows.map((r) => r[labelColumn]))
  if (labelsSeen.size < rows.length) {
    detailColumn = kinds.findIndex((k, c) => {
      if (c === labelColumn || k === 'measure' || CODE_HINT.test(labels[c])) return false
      const vals = rows.map((r) => r[c])
      if (vals.some((v) => typeof v === 'string' && IDENTIFIER.test(v.trim()))) return false
      return new Set(rows.map((r, i) => `${r[labelColumn]}|${vals[i]}`)).size > labelsSeen.size
    })
  }

  return { id: t.id, width, header, cells, labels, kinds, rows, labelColumn, detailColumn }
}

/** Rows that sum up the others (national totals, sub-totals) */
export const TOTAL_LABEL = /^(សរុប|ទូទាំងប្រទេស|ទូទាំងព្រះរាជាណាចក្រ)|^(total|grand total|sub-?total|nationwide|cambodia)\b/i

export function formatCell(v: Cell, kind: ColumnKind): string {
  if (v === null || v === undefined || v === '') return '–'
  if (kind !== 'measure') return String(v)
  const n = toNumber(v)
  if (n === null) return String(v)
  if (typeof v === 'string' && /%\s*$/.test(v)) return v.trim()
  return n.toLocaleString('en-US', { maximumFractionDigits: 3 })
}

// ---------------------------------------------------------------------------
// Downloads
// ---------------------------------------------------------------------------

const csvEscape = (v: Cell) => {
  if (v === null || v === undefined) return ''
  const s = String(v)
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function tableToCsv(t: PreparedTable, rows: Cell[][] = t.rows): string {
  // One header line per level so the published structure survives the round trip.
  const headerLines = t.header.map((level) => level.map((h) => csvEscape(h.replace(/\u200B/g, ''))).join(','))
  const body = rows.map((r) => r.map(csvEscape).join(','))
  return [...headerLines, ...body].join('\r\n')
}

export function downloadBlob(content: BlobPart, filename: string, type: string) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function downloadCsv(t: PreparedTable, filename: string, rows?: Cell[][]) {
  // BOM so Excel opens UTF-8 Khmer text correctly
  downloadBlob('\uFEFF' + tableToCsv(t, rows), filename, 'text/csv;charset=utf-8')
}

export function downloadJson(t: PreparedTable, filename: string) {
  const records = t.rows.map((r) => Object.fromEntries(t.labels.map((l, i) => [l, r[i]])))
  downloadBlob(JSON.stringify({ id: t.id, columns: t.labels, records }, null, 2), filename, 'application/json')
}
