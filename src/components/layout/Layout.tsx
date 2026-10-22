import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { useI18n } from '../../i18n/I18n'
import { Masthead } from './Masthead'
import { Header } from './Header'
import { Footer } from './Footer'

export function Layout() {
  const { t } = useI18n()
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [pathname])

  return (
    <div className="app" id="top">
      <a className="skip-link" href="#main">
        {t('skipToContent')}
      </a>
      <Masthead />
      <Header />
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
