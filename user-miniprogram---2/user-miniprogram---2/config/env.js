const { getSystemInfoCompat } = require('../utils/system-info')

let privateConfig = {}
try {
  privateConfig = require('../project.private.config.json') || {}
} catch (err) {
  privateConfig = {}
}

const trimEndSlash = (value) => String(value || '').trim().replace(/\/+$/, '')
const withApiSuffix = (value) => {
  const normalized = trimEndSlash(value)
  if (!normalized) {
    return ''
  }
  return normalized.endsWith('/api') ? normalized : `${normalized}/api`
}

const API_BASES = {
  localhost: withApiSuffix(privateConfig.apiBaseUrlDev || 'http://localhost:8080'),
  lan: withApiSuffix(privateConfig.apiBaseUrlLan || 'http://192.168.0.10:8080'),
  server: withApiSuffix(privateConfig.apiBaseUrlDevice || privateConfig.apiBaseUrl || 'https://api.example.com'),
  trial: withApiSuffix(privateConfig.apiBaseUrlTrial || 'https://staging.example.com'),
  release: withApiSuffix(privateConfig.apiBaseUrlRelease || 'https://api.example.com')
}

const FALLBACK_BASE = API_BASES.server

const ENV_CONFIG = {
  develop: {
    apiBaseUrl: API_BASES.server,
    apiBaseUrlDevtools: privateConfig.useLocalDevApi === true ? API_BASES.localhost : API_BASES.server,
    apiBaseUrlDevice: API_BASES.server
  },
  trial: {
    apiBaseUrl: API_BASES.trial
  },
  release: {
    apiBaseUrl: API_BASES.release
  }
}

const normalizeEnvName = (env) => {
  if (!env) {
    return 'develop'
  }
  return String(env).toLowerCase()
}

const detectRuntimePlatform = () => {
  if (typeof wx !== 'object') {
    return 'unknown'
  }

  try {
    const systemInfo = getSystemInfoCompat()
    const platform = String(systemInfo.platform || '').toLowerCase()
    if (platform === 'devtools') {
      return 'devtools'
    }
    return 'device'
  } catch (err) {
    console.warn('[config/env] get runtime platform failed:', err)
  }

  return 'unknown'
}

const detectEnv版本 = () => {
  if (typeof wx !== 'object' || typeof wx.getAccountInfoSync !== 'function') {
    return 'develop'
  }

  try {
    const info = wx.getAccountInfoSync()
    if (info && info.miniProgram && (info.miniProgram.envVersion || info.miniProgram.env版本)) {
      return normalizeEnvName(info.miniProgram.envVersion || info.miniProgram.env版本)
    }
  } catch (err) {
    console.warn('[config/env] getAccountInfoSync failed:', err)
  }

  return 'develop'
}

const getApiBaseUrl = () => {
  const env = detectEnv版本()
  const config = ENV_CONFIG[env] || ENV_CONFIG.develop
  const runtimePlatform = detectRuntimePlatform()

  if (runtimePlatform === 'devtools' && config.apiBaseUrlDevtools) {
    return config.apiBaseUrlDevtools
  }

  if (runtimePlatform === 'device' && config.apiBaseUrlDevice) {
    return config.apiBaseUrlDevice
  }

  return config.apiBaseUrl || FALLBACK_BASE
}

const getRuntimeAllowedHosts = () => {
  const env = detectEnv版本()
  const serverHost = getHostFromUrl(API_BASES.server)
  const lanHost = getHostFromUrl(API_BASES.lan)
  const commonHosts = [
    'api.example.com',
    'staging.example.com',
    'example.com'
  ]

  if (env === 'develop') {
    return [
      'localhost',
      '127.0.0.1',
      lanHost,
      serverHost,
      ...commonHosts
    ].filter(Boolean)
  }

  return commonHosts
}

const getHostFromUrl = (url) => {
  const text = String(url || '')
  const match = text.match(/^https?:\/\/([^/:?#]+)/i)
  return match ? match[1] : ''
}

module.exports = {
  getApiBaseUrl,
  detectEnv版本,
  detectRuntimePlatform,
  getRuntimeAllowedHosts,
  API_BASES
}

