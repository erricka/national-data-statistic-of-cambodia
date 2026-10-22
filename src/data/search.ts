import type { Catalog, Dataset } from './types'

// Everyday words people search for → words used in table titles.
const SYNONYMS: Record<string, string[]> = {
  inflation: ['consumer price', 'cpi', 'price'],
  cpi: ['consumer price'],
  prices: ['price'],
  population: ['citizens', 'registered', 'civil registration', 'population'],
  people: ['citizens', 'population'],
  births: ['civil registration', 'vital'],
  deaths: ['vital', 'fatalities'],
  tourism: ['tourist', 'visitors', 'arrivals', 'tourism'],
  tourists: ['tourist', 'visitors', 'arrivals'],
  travel: ['tourist', 'air passengers', 'arrivals'],
  school: ['schools', 'students', 'education', 'enrolment'],
  schools: ['school', 'students'],
  education: ['school', 'students', 'teachers', 'enrolment'],
  university: ['higher education'],
  jobs: ['labour', 'employment', 'workers'],
  employment: ['labour', 'employment', 'workers'],
  unemployment: ['labour force', 'employment'],
  wages: ['wages', 'salary'],
  hospital: ['hospital', 'health', 'beds'],
  hospitals: ['hospital', 'health facilities'],
  health: ['health', 'hospital', 'vaccinat', 'malaria', 'hiv'],
  vaccine: ['vaccinat'],
  crime: ['criminal', 'offences'],
  police: ['criminal', 'offences'],
  fdi: ['investment'],
  investment: ['investment', 'capital'],
  trade: ['trade', 'export', 'import'],
  exports: ['export'],
  imports: ['import'],
  money: ['monetary', 'bank', 'exchange'],
  currency: ['exchange rate'],
  riel: ['exchange rate'],
  dollar: ['exchange rate'],
  bank: ['bank', 'depository', 'financial'],
  budget: ['budget', 'revenue', 'expenditure'],
  tax: ['revenue', 'tax'],
  electricity: ['electricity', 'power'],
  energy: ['electricity', 'petroleum', 'fuel', 'power'],
  oil: ['petroleum', 'fuel'],
  rice: ['rice', 'paddy'],
  farming: ['crop', 'livestock', 'agricultur'],
  agriculture: ['crop', 'livestock', 'rice', 'agricultur'],
  fish: ['fisheries', 'aquaculture'],
  forest: ['forest', 'protected'],
  weather: ['rainfall', 'temperature'],
  climate: ['rainfall', 'temperature', 'hydrology'],
  rain: ['rainfall'],
  roads: ['road', 'roads'],
  traffic: ['traffic', 'accident'],
  accidents: ['accident'],
  cars: ['vehicles'],
  vehicles: ['vehicle'],
  port: ['port', 'container', 'cargo'],
  flights: ['air', 'airport', 'airlines'],
  airport: ['airport', 'air'],
  internet: ['internet', 'telecom', 'fibre'],
  phone: ['telephone', 'telecom'],
  women: ['women', 'gender', 'female'],
  gender: ['women', 'gender'],
  religion: ['buddhist', 'pagodas', 'religion'],
  culture: ['cultural', 'museum', 'heritage'],
  land: ['land'],
  housing: ['residential', 'construction'],
  construction: ['construction'],
  poverty: ['idpoor', 'equity', 'cash transfer'],
}

const norm = (s: string) => s.toLowerCase().normalize('NFC').replace(/[\u200B-\u200D]/g, '')
const escape = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')

/** Latin terms must start a word ("rice" ≠ "price"); Khmer has no spaces, so substring. */
function matches(text: string, term: string) {
  if (/[a-z0-9]/.test(term)) return new RegExp(`(^|[^a-z0-9])${escape(term)}`).test(text)
  return text.includes(term)
}

export interface SearchHit {
  dataset: Dataset
  score: number
}

export function searchDatasets(catalog: Catalog, query: string): SearchHit[] {
  const q = norm(query).trim()
  if (!q) return catalog.datasets.map((d) => ({ dataset: d, score: 0 }))
  const categories = new Map(catalog.categories.map((c) => [c.id, c]))
  const themes = new Map(catalog.themes.map((t) => [t.id, t]))
  const tokens = q.split(/[\s,;/]+/).filter(Boolean)

  const hits: SearchHit[] = []
  for (const d of catalog.datasets) {
    const cat = categories.get(d.categoryId)
    const theme = d.themeId ? themes.get(d.themeId) : undefined
    const titleEn = norm(d.title.en)
    const titleKm = norm(d.title.km)
    const code = norm(d.code)
    const agency = norm(`${cat?.name.en ?? ''} ${cat?.name.km ?? ''} ${cat?.abbr ?? ''}`)
    const themeText = norm(`${theme?.name.en ?? ''} ${theme?.name.km ?? ''}`)

    let score = 0
    let all = true
    for (const tok of tokens) {
      const alts = [tok, ...(SYNONYMS[tok] ?? [])]
      // Strongest match in the code or title, plus smaller boosts when the agency or
      // theme also matches (so the responsible ministry's tables rank first).
      let best = 0
      let boost = 0
      for (const a of alts) {
        const weight = a === tok ? 1 : 0.6
        if (code === a || code.replace(/_/g, '') === a.replace(/_/g, '')) best = Math.max(best, 12 * weight)
        else if (code.startsWith(a)) best = Math.max(best, 6 * weight)
        if (matches(titleEn, a) || matches(titleKm, a)) best = Math.max(best, 5 * weight)
        else if (a.length > 3 && titleEn.includes(a)) best = Math.max(best, 2 * weight)
        if (matches(agency, a)) boost = Math.max(boost, 2 * weight)
        if (matches(themeText, a)) boost = Math.max(boost, 1 * weight)
      }
      if (best || boost) best += boost * (best ? 0.75 : 1)
      if (!best) {
        all = false
        break
      }
      score += best
    }
    if (!all) continue
    if (titleEn.startsWith(q) || titleKm.startsWith(q)) score += 4
    score += Math.min(d.records, 5000) / 5000 // mild preference for richer tables
    if (!d.records) score *= 0.5 // published but still empty
    hits.push({ dataset: d, score })
  }
  return hits.sort((a, b) => b.score - a.score)
}
