import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// BASE_PATH lets the site be served from a sub-path, e.g. GitHub Pages:
//   BASE_PATH=/national-data-statistic-of-cambodia/ npm run build
export default defineConfig({
  base: process.env.BASE_PATH || '/',
  plugins: [react()],
  build: {
    chunkSizeWarningLimit: 900,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [{ name: 'echarts', test: /node_modules[\\/](echarts|zrender)/ }],
        },
      },
    },
  },
})
