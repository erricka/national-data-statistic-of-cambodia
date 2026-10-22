import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { SearchBox } from '../components/SearchBox'
import { Icon } from '../components/Icon'

export function NotFoundPage() {
  const { t } = useI18n()
  useDocumentTitle(t('notFoundTitle'))
  return (
    <section className="not-found container">
      <p className="not-found-code">404</p>
      <h1>{t('notFoundTitle')}</h1>
      <p className="page-lead">{t('notFoundLead')}</p>
      <div className="not-found-search">
        <SearchBox />
      </div>
      <Link to="/" className="btn btn-outline">
        <Icon name="arrowRight" size={16} className="flip" /> {t('backHome')}
      </Link>
    </section>
  )
}
