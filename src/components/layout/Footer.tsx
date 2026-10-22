import { Link } from 'react-router-dom'
import { useI18n } from '../../i18n/I18n'
import { useCatalog } from '../../hooks/useData'
import { config } from '../../data/repository'
import { Icon } from '../Icon'

const logoSrc = `${import.meta.env.BASE_URL}img/nis-mark.png`

export function Footer() {
  const { t, l, date } = useI18n()
  const catalog = useCatalog()
  const themes = catalog.status === 'ready' ? catalog.data.themes : []
  const asOf = catalog.status === 'ready' ? catalog.data.stats.lastRelease : null

  return (
    <footer className="site-footer">
      <div className="footer-stripe" aria-hidden="true" />
      <div className="container footer-grid">
        <div className="footer-brand">
          <div className="footer-logo">
            <img src={logoSrc} alt="" width={56} height={38} />
            <div>
              <strong>{t('orgName')}</strong>
              <span>{t('ministry')}</span>
            </div>
          </div>
          <p>{t('footerAbout')}</p>
        </div>

        <nav className="footer-col" aria-label={t('footerData')}>
          <h2>{t('footerData')}</h2>
          <ul>
            {themes.map((th) => (
              <li key={th.id}>
                <Link to={`/themes/${th.id}`}>{l(th.name)}</Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav className="footer-col" aria-label={t('footerPortal')}>
          <h2>{t('footerPortal')}</h2>
          <ul>
            <li><Link to="/find-data">{t('navFindData')}</Link></li>
            <li><Link to="/agencies">{t('navAgencies')}</Link></li>
            <li><Link to="/indicators">{t('navIndicators')}</Link></li>
            <li><Link to="/provinces">{t('navProvinces')}</Link></li>
            <li><Link to="/release-calendar">{t('navCalendar')}</Link></li>
            <li><Link to="/about">{t('navAbout')}</Link></li>
          </ul>
        </nav>

        <div className="footer-col">
          <h2>{t('footerContact')}</h2>
          <p className="footer-contact">
            <Icon name="pin" size={16} />
            <span>{t('footerAddress')}</span>
          </p>
          <p className="footer-contact">
            <Icon name="globe" size={16} />
            <a href="https://www.nis.gov.kh" target="_blank" rel="noreferrer">www.nis.gov.kh</a>
          </p>
          {config.adminUrl && (
            <a className="btn btn-outline-light btn-sm" href={config.adminUrl}>
              <Icon name="user" size={16} /> {t('staffSignIn')}
            </a>
          )}
        </div>
      </div>
      <div className="footer-bottom">
        <div className="container footer-bottom-bar">
          <span>{t('copyright', { year: new Date().getFullYear() })}</span>
          {asOf && <span>{t('dataAsOf', { date: date(asOf) })}</span>}
          <span className="footer-attrib">{t('mapAttribution')}</span>
          <a href="#top" className="footer-top">
            {t('backToTop')} <Icon name="arrowUp" size={14} />
          </a>
        </div>
      </div>
    </footer>
  )
}
