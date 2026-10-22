import { useState } from 'react'
import { useI18n } from '../../i18n/I18n'
import { Icon } from '../Icon'

/** Government identity strip, in the style of official government websites. */
export function Masthead() {
  const { t } = useI18n()
  const [open, setOpen] = useState(false)
  return (
    <div className="masthead">
      <div className="container masthead-bar">
        <span className="masthead-flag" aria-hidden="true">
          <i />
          <i />
          <i />
        </span>
        <span className="masthead-text">{t('mastheadText')}</span>
        <button type="button" className="masthead-toggle" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
          {t('howToIdentify')}
          <Icon name="chevronDown" size={14} className={open ? 'rot-180' : ''} />
        </button>
      </div>
      {open && (
        <div className="masthead-panel">
          <div className="container masthead-grid">
            <div className="masthead-item">
              <Icon name="building" size={28} />
              <div>
                <strong>{t('mastheadOfficialTitle')}</strong>
                <p>{t('mastheadOfficialBody')}</p>
              </div>
            </div>
            <div className="masthead-item">
              <Icon name="lock" size={28} />
              <div>
                <strong>{t('mastheadSecureTitle')}</strong>
                <p>{t('mastheadSecureBody')}</p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
