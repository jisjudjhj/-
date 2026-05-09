const trimEndSlash = value => String(value || '').replace(/\/+$/, '')

const API_BASE = trimEndSlash(import.meta.env.VITE_API_BASE || '/api') || '/api'
const WS_BASE = trimEndSlash(import.meta.env.VITE_WS_BASE || '/ws') || '/ws'

export const runtimeConfig = {
  apiBase: API_BASE,
  wsBase: WS_BASE,
}

