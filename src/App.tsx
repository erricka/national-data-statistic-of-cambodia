import { lazy, Suspense, type ReactNode } from 'react'
import { Navigate, Route, Routes, useParams, useSearchParams } from 'react-router-dom'
import { Layout } from './components/layout/Layout'
import { Loading } from './components/ui'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'

// Everything except the home page loads on demand
const FindDataPage = lazy(() => import('./pages/FindDataPage').then((m) => ({ default: m.FindDataPage })))
const DatasetPage = lazy(() => import('./pages/DatasetPage').then((m) => ({ default: m.DatasetPage })))
const ThemesPage = lazy(() => import('./pages/CatalogPages').then((m) => ({ default: m.ThemesPage })))
const ThemePage = lazy(() => import('./pages/CatalogPages').then((m) => ({ default: m.ThemePage })))
const AgenciesPage = lazy(() => import('./pages/CatalogPages').then((m) => ({ default: m.AgenciesPage })))
const AgencyPage = lazy(() => import('./pages/CatalogPages').then((m) => ({ default: m.AgencyPage })))
const IndicatorsPage = lazy(() => import('./pages/IndicatorPages').then((m) => ({ default: m.IndicatorsPage })))
const ProvincesPage = lazy(() => import('./pages/IndicatorPages').then((m) => ({ default: m.ProvincesPage })))
const ReleaseCalendarPage = lazy(() => import('./pages/ReleaseCalendarPage').then((m) => ({ default: m.ReleaseCalendarPage })))
const AboutPage = lazy(() => import('./pages/AboutPage').then((m) => ({ default: m.AboutPage })))

const page = (el: ReactNode) => <Suspense fallback={<Loading block />}>{el}</Suspense>

// URLs used by the previous NIS public portal keep working.
function LegacyDataset() {
  const { sourceId = '' } = useParams()
  return <Navigate to={`/datasets/${sourceId}`} replace />
}
function LegacyCategory() {
  const [params] = useSearchParams()
  const id = params.get('id')
  return <Navigate to={id ? `/agencies/${id}` : '/agencies'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="find-data" element={page(<FindDataPage />)} />
        <Route path="datasets/:id" element={page(<DatasetPage />)} />
        <Route path="themes" element={page(<ThemesPage />)} />
        <Route path="themes/:id" element={page(<ThemePage />)} />
        <Route path="agencies" element={page(<AgenciesPage />)} />
        <Route path="agencies/:id" element={page(<AgencyPage />)} />
        <Route path="indicators" element={page(<IndicatorsPage />)} />
        <Route path="provinces" element={page(<ProvincesPage />)} />
        <Route path="release-calendar" element={page(<ReleaseCalendarPage />)} />
        <Route path="about" element={page(<AboutPage />)} />

        <Route path="detail/:sourceId" element={<LegacyDataset />} />
        <Route path="categories" element={<Navigate to="/agencies" replace />} />
        <Route path="categories/detail" element={<LegacyCategory />} />
        <Route path="data-explore" element={<Navigate to="/find-data" replace />} />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
