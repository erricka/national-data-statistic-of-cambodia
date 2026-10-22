import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Frequency, Lang, Localized, ValueFormat } from '../data/types'
import { strings, type StringKey } from './strings'

const MONTHS: Record<Lang, string[]> = {
  en: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'],
  km: ['មករា', 'កុម្ភៈ', 'មីនា', 'មេសា', 'ឧសភា', 'មិថុនា', 'កក្កដា', 'សីហា', 'កញ្ញា', 'តុលា', 'វិច្ឆិកា', 'ធ្នូ'],
}
const MONTHS_LONG_EN = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']

export interface I18n {
  lang: Lang
  setLang: (l: Lang) => void
  t: (key: StringKey, vars?: Record<string, string | number>) => string
  /** Picks the current language from a { en, km } pair */
  l: (v: Localized | null | undefined) => string
  /** The other language, for secondary labels */
  lOther: (v: Localized | null | undefined) => string
  num: (n: number, opts?: Intl.NumberFormatOptions) => string
  fmt: (n: number, format: ValueFormat) => string
  date: (iso: string | null | undefined, style?: 'short' | 'long') => string
  period: (p: string) => string
  monthName: (m: number, long?: boolean) => string
  freq: (f: Frequency) => string
}

const Ctx = createContext<I18n | null>(null)

function initialLang(): Lang {
  try {
    const saved = localStorage.getItem('nis.lang')
    if (saved === 'en' || saved === 'km') return saved
  } catch {
    /* storage unavailable */
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('km') ? 'km' : 'en'
}

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>(initialLang)

  useEffect(() => {
    document.documentElement.lang = lang
    document.documentElement.dataset.lang = lang
  }, [lang])

  const setLang = useCallback((l: Lang) => {
    setLangState(l)
    try {
      localStorage.setItem('nis.lang', l)
    } catch {
      /* storage unavailable */
    }
  }, [])

  const value = useMemo<I18n>(() => {
    const dict = strings[lang]
    const t: I18n['t'] = (key, vars) => {
      let s = dict[key] ?? strings.en[key] ?? key
      if (vars) for (const [k, v] of Object.entries(vars)) s = s.replaceAll(`{${k}}`, String(v))
      return s
    }
    const num: I18n['num'] = (n, opts) => n.toLocaleString('en-US', opts)
    const monthName = (m: number, long = false) => (lang === 'en' && long ? MONTHS_LONG_EN[m - 1] : MONTHS[lang][m - 1])
    return {
      lang,
      setLang,
      t,
      l: (v) => (v ? v[lang] || v.en || v.km : ''),
      lOther: (v) => (v ? v[lang === 'en' ? 'km' : 'en'] || '' : ''),
      num,
      fmt: (n, format) => {
        switch (format) {
          case 'percent1':
            return `${num(n, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`
          case 'integer':
            return num(Math.round(n))
          case 'decimal1':
            return num(n, { minimumFractionDigits: 1, maximumFractionDigits: 1 })
          case 'compact': {
            const abs = Math.abs(n)
            if (lang === 'km') {
              if (abs >= 1e6) return `${num(n / 1e6, { maximumFractionDigits: 2 })} លាន`
              return num(n)
            }
            if (abs >= 1e9) return `${num(n / 1e9, { maximumFractionDigits: 2 })}B`
            if (abs >= 1e6) return `${num(n / 1e6, { maximumFractionDigits: 2 })}M`
            if (abs >= 1e4) return `${num(n / 1e3, { maximumFractionDigits: 1 })}K`
            return num(n)
          }
        }
      },
      date: (iso, style = 'long') => {
        if (!iso) return '–'
        const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
        if (!y || !m) return iso
        if (lang === 'km') return `${d ? d + ' ' : ''}${MONTHS.km[m - 1]} ${y}`
        return style === 'short' ? `${d ? d + ' ' : ''}${MONTHS.en[m - 1]} ${y}` : `${d ? d + ' ' : ''}${MONTHS_LONG_EN[m - 1]} ${y}`
      },
      period: (p) => {
        const m = p.match(/^(\d{4})-(\d{2})$/)
        return m ? `${monthName(Number(m[2]))} ${m[1]}` : p
      },
      monthName,
      freq: (f) => t(f ? (`freq${f}` as StringKey) : 'freqNone'),
    }
  }, [lang, setLang])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useI18n(): I18n {
  const ctx = useContext(Ctx)
  if (!ctx) throw new Error('useI18n must be used inside <I18nProvider>')
  return ctx
}
