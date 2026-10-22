import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog } from '../hooks/useData'
import { searchDatasets } from '../data/search'
import { Icon } from './Icon'

interface Props {
  size?: 'lg' | 'md'
  autoFocus?: boolean
  initial?: string
  onDone?: () => void
  /** Called on submit instead of navigating to the Find Data page */
  onSubmit?: (q: string) => void
}

export function SearchBox({ size = 'md', autoFocus, initial = '', onDone, onSubmit }: Props) {
  const { t, l } = useI18n()
  const navigate = useNavigate()
  const catalog = useCatalog()
  const [q, setQ] = useState(initial)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const listId = useId()
  const wrap = useRef<HTMLFormElement>(null)

  useEffect(() => setQ(initial), [initial])

  const suggestions = useMemo(() => {
    if (catalog.status !== 'ready' || q.trim().length < 2) return []
    return searchDatasets(catalog.data, q).slice(0, 6)
  }, [catalog, q])

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (wrap.current && !wrap.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const go = (query: string) => {
    setOpen(false)
    if (onSubmit) onSubmit(query.trim())
    else navigate(query.trim() ? `/find-data?q=${encodeURIComponent(query.trim())}` : '/find-data')
    onDone?.()
  }

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (active >= 0 && suggestions[active]) {
      setOpen(false)
      navigate(`/datasets/${suggestions[active].dataset.id}`)
      onDone?.()
      return
    }
    go(q)
  }

  const onKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (!suggestions.length) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setOpen(true)
      setActive((a) => Math.min(a + 1, suggestions.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, -1))
    } else if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
    }
  }

  const showList = open && suggestions.length > 0
  return (
    <form ref={wrap} className={`searchbox searchbox-${size}`} role="search" onSubmit={submit}>
      <label className="sr-only" htmlFor={`${listId}-input`}>
        {t('searchLabel')}
      </label>
      <Icon name="search" size={size === 'lg' ? 22 : 18} className="searchbox-icon" />
      <input
        id={`${listId}-input`}
        type="search"
        value={q}
        autoFocus={autoFocus}
        autoComplete="off"
        placeholder={t('searchPlaceholder')}
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={active >= 0 ? `${listId}-${active}` : undefined}
        onChange={(e) => {
          setQ(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
        onFocus={() => setOpen(true)}
        onKeyDown={onKey}
      />
      <button type="submit" className="btn btn-primary searchbox-submit">
        {t('searchButton')}
      </button>
      {showList && (
        <div className="searchbox-panel">
          <div className="searchbox-panel-title">{t('suggestions')}</div>
          <ul id={listId} role="listbox">
            {suggestions.map((s, i) => (
              <li
                key={s.dataset.id}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === active}
                className={i === active ? 'active' : ''}
                onMouseDown={(e) => {
                  e.preventDefault()
                  setOpen(false)
                  navigate(`/datasets/${s.dataset.id}`)
                  onDone?.()
                }}
                onMouseEnter={() => setActive(i)}
              >
                <span className="code-chip">{s.dataset.code}</span>
                <span className="searchbox-option-title">{l(s.dataset.title)}</span>
              </li>
            ))}
          </ul>
          <button type="button" className="searchbox-all" onMouseDown={(e) => { e.preventDefault(); go(q) }}>
            {t('seeAllResults', { q: q.trim() })}
            <Icon name="arrowRight" size={16} />
          </button>
        </div>
      )}
    </form>
  )
}
