import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import '@fontsource-variable/inter/wght.css'
import '@fontsource/noto-sans-khmer/khmer-400.css'
import '@fontsource/noto-sans-khmer/khmer-500.css'
import '@fontsource/noto-sans-khmer/khmer-600.css'
import '@fontsource/noto-sans-khmer/khmer-700.css'
import './styles/app.css'
import { I18nProvider } from './i18n/I18n'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter basename={import.meta.env.BASE_URL.replace(/\/$/, '')}>
      <I18nProvider>
        <App />
      </I18nProvider>
    </BrowserRouter>
  </StrictMode>,
)
