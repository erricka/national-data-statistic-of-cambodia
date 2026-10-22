import { useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useI18n } from '../i18n/I18n'
import { useCatalog } from '../hooks/useData'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { config } from '../data/repository'
import { PageHeader } from '../components/ui'
import { Icon } from '../components/Icon'

const logo = `${import.meta.env.BASE_URL}img/nis-logo.webp`

const CONTENT = {
  en: {
    lead: 'The National Institute of Statistics (NIS) is the official statistics agency of the Kingdom of Cambodia, under the Ministry of Planning.',
    whoTitle: 'Who we are',
    who: [
      'NIS collects, compiles, analyses and disseminates official statistics on Cambodia’s population, economy, society and environment, and coordinates the national statistical system.',
      'This portal brings together administrative statistics compiled by ministries and agencies across government, so that anyone — policymakers, researchers, journalists, businesses and the public — can find, compare and reuse them in one place.',
    ],
    stepsTitle: 'How statistics reach this portal',
    steps: [
      ['Compile', 'Each ministry or agency compiles its tables using a standard NIS template.'],
      ['Submit', 'Officers upload the table to the NIS data platform.'],
      ['Review', 'NIS reviews the submission and approves it for release.'],
      ['Publish', 'Approved tables appear here immediately, with their release date.'],
    ],
    accessTitle: 'Data access',
    access: [
      'Every table can be downloaded as Excel (.xlsx), CSV or JSON from its page. Tables keep the headings published by the source agency.',
      'Developers can read the same machine-readable files the portal uses:',
    ],
    useTitle: 'Using and citing our data',
    use: 'Statistics on this portal may be reused freely, provided the source is acknowledged. Please cite the National Institute of Statistics and the source ministry or agency, the table ID and the date you accessed it. Each table’s “About this data” tab provides a ready-made citation.',
    notesTitle: 'Notes on the data',
    notes: [
      'Tables show only data from uploads approved for release.',
      'Table headings are shown in Khmer as published by the source agency; English titles are provided for every table.',
      'Annual headline indicators show the latest complete year.',
    ],
  },
  km: {
    lead: 'វិទ្យាស្ថានជាតិស្ថិតិ គឺជាស្ថាប័នស្ថិតិផ្លូវការរបស់ព្រះរាជាណាចក្រកម្ពុជា ក្រោមឱវាទក្រសួងផែនការ។',
    whoTitle: 'អំពីយើង',
    who: [
      'វិទ្យាស្ថានជាតិស្ថិតិ ប្រមូល ចងក្រង វិភាគ និងផ្សព្វផ្សាយស្ថិតិផ្លូវការស្តីពីប្រជាជន សេដ្ឋកិច្ច សង្គម និងបរិស្ថាននៃប្រទេសកម្ពុជា ព្រមទាំងសម្របសម្រួលប្រព័ន្ធស្ថិតិជាតិ។',
      'គេហទំព័រនេះប្រមូលផ្តុំស្ថិតិរដ្ឋបាលដែលចងក្រងដោយក្រសួង និងស្ថាប័ននានា ដើម្បីឱ្យអ្នកធ្វើគោលនយោបាយ អ្នកស្រាវជ្រាវ អ្នកសារព័ត៌មាន អាជីវកម្ម និងសាធារណជន អាចស្វែងរក ប្រៀបធៀប និងប្រើប្រាស់ទិន្នន័យនៅកន្លែងតែមួយ។',
    ],
    stepsTitle: 'ដំណើរការផ្សព្វផ្សាយស្ថិតិ',
    steps: [
      ['ចងក្រង', 'ក្រសួង ឬស្ថាប័ននីមួយៗចងក្រងតារាងតាមគំរូស្តង់ដាររបស់វិទ្យាស្ថានជាតិស្ថិតិ។'],
      ['ដាក់ស្នើ', 'មន្ត្រីផ្ទុកឡើងតារាងទៅប្រព័ន្ធទិន្នន័យរបស់វិទ្យាស្ថានជាតិស្ថិតិ។'],
      ['ពិនិត្យ', 'វិទ្យាស្ថានជាតិស្ថិតិពិនិត្យ និងអនុម័តឱ្យផ្សព្វផ្សាយ។'],
      ['ផ្សព្វផ្សាយ', 'តារាងដែលបានអនុម័តបង្ហាញនៅទីនេះភ្លាមៗ ព្រមទាំងកាលបរិច្ឆេទផ្សព្វផ្សាយ។'],
    ],
    accessTitle: 'ការចូលប្រើទិន្នន័យ',
    access: [
      'តារាងនីមួយៗអាចទាញយកជា Excel (.xlsx), CSV ឬ JSON ពីទំព័ររបស់វា។ តារាងរក្សាចំណងជើងជួរឈរដូចដែលស្ថាប័នប្រភពបានផ្សព្វផ្សាយ។',
      'អ្នកអភិវឌ្ឍន៍អាចប្រើឯកសារដែលម៉ាស៊ីនអានបាន ដូចគ្នានឹងគេហទំព័រនេះ៖',
    ],
    useTitle: 'ការប្រើប្រាស់ និងការដកស្រង់ទិន្នន័យ',
    use: 'ស្ថិតិនៅលើគេហទំព័រនេះអាចប្រើប្រាស់ឡើងវិញដោយសេរី ដោយត្រូវបញ្ជាក់ប្រភព។ សូមដកស្រង់វិទ្យាស្ថានជាតិស្ថិតិ និងក្រសួង ឬស្ថាប័នប្រភព លេខកូដតារាង និងកាលបរិច្ឆេទចូលមើល។ ផ្ទាំង «អំពីទិន្នន័យ» នៃតារាងនីមួយៗមានការដកស្រង់ដែលរៀបចំរួចជាស្រេច។',
    notesTitle: 'កំណត់សម្គាល់អំពីទិន្នន័យ',
    notes: [
      'តារាងបង្ហាញតែទិន្នន័យពីការផ្ទុកឡើងដែលបានអនុម័តឱ្យផ្សព្វផ្សាយប៉ុណ្ណោះ។',
      'ចំណងជើងជួរឈរបង្ហាញជាភាសាខ្មែរ តាមការផ្សព្វផ្សាយរបស់ស្ថាប័នប្រភព។ តារាងនីមួយៗមានចំណងជើងជាភាសាអង់គ្លេស។',
      'សូចនាករប្រចាំឆ្នាំបង្ហាញឆ្នាំពេញលេញចុងក្រោយ។',
    ],
  },
}

export function AboutPage() {
  const { t, lang, num } = useI18n()
  const catalog = useCatalog()
  const { hash } = useLocation()
  useDocumentTitle(t('aboutTitle'))
  const c = CONTENT[lang]
  const base = `${window.location.origin}${config.dataBase}`

  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView({ behavior: 'smooth' })
  }, [hash])

  return (
    <>
      <PageHeader crumbs={[{ label: t('aboutTitle') }]} title={t('aboutTitle')} lead={c.lead} />
      <div className="container section-sm about">
        <div className="about-intro">
          <div className="prose">
            <h2>{c.whoTitle}</h2>
            {c.who.map((p) => (
              <p key={p}>{p}</p>
            ))}
          </div>
          <figure className="about-logo">
            <img src={logo} alt={t('orgName')} width={260} height={257} />
          </figure>
        </div>

        {catalog.data && (
          <dl className="about-stats">
            <div><dd>{num(catalog.data.stats.datasets)}</dd><dt>{t('statDatasets')}</dt></div>
            <div><dd>{num(catalog.data.stats.agencies)}</dd><dt>{t('statAgencies')}</dt></div>
            <div><dd>{num(catalog.data.stats.records)}</dd><dt>{t('statRecords')}</dt></div>
            <div><dd>{num(catalog.data.themes.length)}</dd><dt>{t('navThemes')}</dt></div>
          </dl>
        )}

        <section className="about-section">
          <h2>{c.stepsTitle}</h2>
          <ol className="steps">
            {c.steps.map(([title, body], i) => (
              <li key={title}>
                <span className="step-no">{i + 1}</span>
                <strong>{title}</strong>
                <p>{body}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="about-section" id="data-access">
          <h2>{c.accessTitle}</h2>
          <div className="prose">
            {c.access.map((p) => (
              <p key={p}>{p}</p>
            ))}
          </div>
          <pre className="code-block"><code>{`${base}/catalog.json        # themes, agencies, datasets, releases
${base}/indicators.json     # headline indicators with time series
${base}/provinces.json      # province-level indicators
${base}/tables/{id}.json    # one statistical table, e.g. nis_05a1`}</code></pre>
        </section>

        <div className="about-columns">
          <section className="card">
            <h2 className="card-title">{c.useTitle}</h2>
            <p>{c.use}</p>
          </section>
          <section className="card">
            <h2 className="card-title">{c.notesTitle}</h2>
            <ul className="bullets">
              {c.notes.map((n) => (
                <li key={n}>{n}</li>
              ))}
            </ul>
          </section>
          <section className="card">
            <h2 className="card-title">{t('footerContact')}</h2>
            <p className="footer-contact dark">
              <Icon name="pin" size={16} /> <span>{t('footerAddress')}</span>
            </p>
            <p className="footer-contact dark">
              <Icon name="globe" size={16} /> <a href="https://www.nis.gov.kh" target="_blank" rel="noreferrer">www.nis.gov.kh</a>
            </p>
            <Link to="/find-data" className="link-arrow">
              {t('navFindData')} <Icon name="arrowRight" size={16} />
            </Link>
          </section>
        </div>
      </div>
    </>
  )
}
