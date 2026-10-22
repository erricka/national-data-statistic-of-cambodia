import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useI18n } from '../../i18n/I18n'
import { useCatalog, useIndicators } from '../../hooks/useData'
import { config } from '../../data/repository'
import { Icon } from '../Icon'
import { SearchBox } from '../SearchBox'

const logoSrc = `${import.meta.env.BASE_URL}img/nis-mark.png`

export function Header() {
  const { t, l, lang, setLang, fmt, period } = useI18n()
  const catalog = useCatalog()
  const indicators = useIndicators()
  const location = useLocation()
  const [mega, setMega] = useState(false)
  const [search, setSearch] = useState(false)
  const [drawer, setDrawer] = useState(false)
  const megaRef = useRef<HTMLDivElement>(null)
  const hoverTimer = useRef<number | undefined>(undefined)
  const hoverOpenedAt = useRef(0)

  // Close everything on navigation
  useEffect(() => {
    setMega(false)
    setSearch(false)
    setDrawer(false)
  }, [location.pathname, location.search])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setMega(false)
        setSearch(false)
        setDrawer(false)
      }
    }
    const onClick = (e: MouseEvent) => {
      if (megaRef.current && !megaRef.current.contains(e.target as Node)) setMega(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onClick)
    }
  }, [])

  useEffect(() => {
    document.body.classList.toggle('no-scroll', drawer)
  }, [drawer])

  const findDataActive = ['/find-data', '/themes', '/agencies', '/datasets'].some((p) => location.pathname.startsWith(p))
  const themes = catalog.status === 'ready' ? catalog.data.themes : []
  const featured = indicators.status === 'ready' ? indicators.data[0] : null

  const openMega = () => {
    window.clearTimeout(hoverTimer.current)
    if (!mega) hoverOpenedAt.current = Date.now()
    setMega(true)
  }
  // A click right after hover-open must not immediately close the panel again
  const toggleMega = () => {
    if (mega && Date.now() - hoverOpenedAt.current < 500) return
    setMega((m) => !m)
  }
  const closeMegaSoon = () => {
    window.clearTimeout(hoverTimer.current)
    hoverTimer.current = window.setTimeout(() => setMega(false), 180)
  }

  return (
    <header className="site-header">
      <div className="container header-bar">
        <Link to="/" className="brand" aria-label={`${t('orgName')} — ${t('navHome')}`}>
          <img src={logoSrc} alt="" width={64} height={43} />
          <span className="brand-text">
            <span className="brand-org">{t('orgName')}</span>
            <span className="brand-site">{t('siteName')}</span>
          </span>
        </Link>

        <nav className="main-nav" aria-label="Main">
          <div className="nav-mega" ref={megaRef} onMouseEnter={openMega} onMouseLeave={closeMegaSoon}>
            <button
              type="button"
              className={`nav-link${findDataActive ? ' active' : ''}`}
              aria-expanded={mega}
              aria-haspopup="true"
              onClick={toggleMega}
            >
              {t('navFindData')}
              <Icon name="chevronDown" size={16} className={mega ? 'rot-180' : ''} />
            </button>
            {mega && (
              <div className="mega-panel" role="region" aria-label={t('navFindData')}>
                <div className="container mega-grid">
                  <div className="mega-col mega-col-wide">
                    <h2 className="mega-title">{t('megaThemesTitle')}</h2>
                    <ul className="mega-themes">
                      {themes.map((th) => (
                        <li key={th.id}>
                          <Link to={`/themes/${th.id}`} className="mega-theme">
                            <span className="theme-badge" style={{ ['--theme' as string]: th.color }}>
                              <Icon name={th.icon} size={20} />
                            </span>
                            <span>
                              <strong>{l(th.name)}</strong>
                              <small>{t('datasetsCount', { n: th.datasetCount })}</small>
                            </span>
                          </Link>
                        </li>
                      ))}
                    </ul>
                  </div>
                  <div className="mega-col">
                    <h2 className="mega-title">{t('megaAgenciesTitle')}</h2>
                    <ul className="mega-links">
                      <li><Link to="/find-data"><Icon name="search" size={16} />{t('navFindData')}</Link></li>
                      <li><Link to="/agencies"><Icon name="building" size={16} />{t('navAgencies')}</Link></li>
                      <li><Link to="/find-data?view=az"><Icon name="table" size={16} />{t('navAllDatasets')}</Link></li>
                      <li><Link to="/themes"><Icon name="layers" size={16} />{t('navThemes')}</Link></li>
                      <li><Link to="/indicators"><Icon name="chart" size={16} />{t('navIndicators')}</Link></li>
                      <li><Link to="/provinces"><Icon name="map" size={16} />{t('navProvinces')}</Link></li>
                    </ul>
                    <p className="mega-note">
                      <strong>{t('megaTableBuilder')}</strong>
                      <br />
                      {t('megaTableBuilderSub')}
                    </p>
                  </div>
                  {featured && (
                    <div className="mega-col">
                      <h2 className="mega-title">{t('megaFeatured')}</h2>
                      <Link to={`/datasets/${featured.datasetId}`} className="mega-feature">
                        <span className="mega-feature-label">{l(featured.title)}</span>
                        <span className="mega-feature-value">{fmt(featured.latest.value, featured.format)}</span>
                        <span className="mega-feature-period">{period(featured.latest.period)}</span>
                        <span className="link-arrow">
                          {t('viewData')} <Icon name="arrowRight" size={16} />
                        </span>
                      </Link>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
          <NavLink to="/indicators" className="nav-link">{t('navIndicators')}</NavLink>
          <NavLink to="/provinces" className="nav-link">{t('navProvinces')}</NavLink>
          <NavLink to="/release-calendar" className="nav-link">{t('navCalendar')}</NavLink>
          <NavLink to="/about" className="nav-link">{t('navAbout')}</NavLink>
        </nav>

        <div className="header-actions">
          <button
            type="button"
            className="lang-toggle"
            onClick={() => setLang(lang === 'en' ? 'km' : 'en')}
            lang={lang === 'en' ? 'km' : 'en'}
            aria-label={t('switchLang')}
          >
            <Icon name="globe" size={16} />
            <span>{t('switchLangShort')}</span>
          </button>
          <button
            type="button"
            className="icon-btn"
            aria-label={t('searchLabel')}
            aria-expanded={search}
            onClick={() => setSearch((s) => !s)}
          >
            <Icon name={search ? 'close' : 'search'} />
          </button>
          <button type="button" className="icon-btn menu-btn" aria-label={t('navMenu')} aria-expanded={drawer} onClick={() => setDrawer(true)}>
            <Icon name="menu" />
          </button>
        </div>
      </div>

      {search && (
        <div className="header-search">
          <div className="container">
            <SearchBox autoFocus onDone={() => setSearch(false)} />
          </div>
        </div>
      )}

      {drawer && createPortal(
        <div className="drawer-backdrop" onClick={() => setDrawer(false)}>
          <aside className="drawer" aria-label={t('navMenu')} onClick={(e) => e.stopPropagation()}>
            <div className="drawer-head">
              <span className="brand-org">{t('orgName')}</span>
              <button type="button" className="icon-btn" aria-label={t('navClose')} onClick={() => setDrawer(false)}>
                <Icon name="close" />
              </button>
            </div>
            <SearchBox />
            <nav className="drawer-nav" aria-label="Mobile">
              <NavLink to="/" end>{t('navHome')}</NavLink>
              <NavLink to="/find-data">{t('navFindData')}</NavLink>
              <div className="drawer-sub">
                {themes.map((th) => (
                  <NavLink key={th.id} to={`/themes/${th.id}`}>
                    <Icon name={th.icon} size={16} /> {l(th.name)}
                  </NavLink>
                ))}
                <NavLink to="/agencies"><Icon name="building" size={16} /> {t('navAgencies')}</NavLink>
              </div>
              <NavLink to="/indicators">{t('navIndicators')}</NavLink>
              <NavLink to="/provinces">{t('navProvinces')}</NavLink>
              <NavLink to="/release-calendar">{t('navCalendar')}</NavLink>
              <NavLink to="/about">{t('navAbout')}</NavLink>
            </nav>
            <div className="drawer-foot">
              <button type="button" className="btn btn-outline" onClick={() => setLang(lang === 'en' ? 'km' : 'en')}>
                <Icon name="globe" size={16} /> {t('switchLang')}
              </button>
              {config.adminUrl && (
                <a className="btn btn-ghost" href={config.adminUrl}>
                  <Icon name="user" size={16} /> {t('staffSignIn')}
                </a>
              )}
            </div>
          </aside>
        </div>,
        document.body,
      )}
    </header>
  )
}
