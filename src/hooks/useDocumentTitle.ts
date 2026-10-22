import { useEffect } from 'react'
import { useI18n } from '../i18n/I18n'

export function useDocumentTitle(title?: string) {
  const { t } = useI18n()
  useEffect(() => {
    document.title = title ? `${title} | ${t('siteName')}` : `${t('siteName')} | ${t('orgName')}`
  }, [title, t])
}
