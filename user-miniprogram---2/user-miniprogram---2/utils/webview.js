const {
  detectEnv版本,
  getRuntimeAllowedHosts
} = require('../config/env')

const getHostName = (rawUrl) => {
  if (!rawUrl) {
    return ''
  }

  try {
    return new URL(rawUrl).hostname || ''
  } catch (error) {
    const normalized = String(rawUrl).replace(/^https?:\/\//, '')
    return normalized.split(/[/:?#]/)[0] || ''
  }
}

const isAllowedHost = (host) => {
  if (!host) {
    return false
  }

  const allowedHosts = getRuntimeAllowedHosts()
  return allowedHosts.some(
    (allowed) => host === allowed || host.endsWith(`.${allowed}`)
  )
}

const buildSafeWebviewUrl = (rawUrl) => {
  if (!rawUrl) {
    return ''
  }

  const trimmed = String(rawUrl).trim()
  if (!trimmed) {
    return ''
  }

  let normalized = trimmed
  try {
    normalized = decodeURIComponent(trimmed)
  } catch (error) {}

  if (!/^https?:\/\//.test(normalized)) {
    return ''
  }

  const currentEnv = detectEnv版本()
  const isHttps = normalized.startsWith('https://')
  if (currentEnv !== 'develop' && !isHttps) {
    return ''
  }

  const host = getHostName(normalized)
  if (!isAllowedHost(host)) {
    return ''
  }

  return normalized
}

module.exports = {
  buildSafeWebviewUrl
}

