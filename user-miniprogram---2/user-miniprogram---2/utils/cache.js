const CACHE_PREFIX = 'mp_cache:'
const memoryCache = {}

function stableStringify(value) {
  if (value === null || value === undefined) return ''
  if (typeof value !== 'object') return String(value)
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`
  }
  return `{${Object.keys(value).sort().map(key => `${key}:${stableStringify(value[key])}`).join('|')}}`
}

function hashString(text) {
  const value = String(text || '')
  let hash = 0
  for (let i = 0; i < value.length; i += 1) {
    hash = ((hash << 5) - hash) + value.charCodeAt(i)
    hash |= 0
  }
  return Math.abs(hash).toString(36)
}

function createKey(namespace, url, data, scope) {
  const scopeKey = hashString(scope || 'public')
  const dataKey = hashString(`${url}:${stableStringify(data)}`)
  return `${CACHE_PREFIX}${namespace}:${scopeKey}:${dataKey}`
}

function get(key, maxAge, options = {}) {
  if (!key) return null
  const now = Date.now()
  const cached = memoryCache[key] || wx.getStorageSync(key)
  if (!cached || !cached.timestamp) return null
  const expired = maxAge > 0 && now - cached.timestamp > maxAge
  if (expired && !options.allowExpired) {
    try {
      wx.removeStorageSync(key)
      delete memoryCache[key]
    } catch (error) {}
    return null
  }
  return cached.data
}

function set(key, data) {
  if (!key || data === undefined) return
  const payload = {
    timestamp: Date.now(),
    data
  }
  memoryCache[key] = payload
  try {
    wx.setStorageSync(key, payload)
  } catch (error) {
    delete memoryCache[key]
  }
}

function remove(key) {
  if (!key) return
  delete memoryCache[key]
  try {
    wx.removeStorageSync(key)
  } catch (error) {}
}

function clearAll() {
  Object.keys(memoryCache).forEach(key => {
    delete memoryCache[key]
  })
  try {
    const info = wx.getStorageInfoSync()
    ;(info.keys || []).forEach(key => {
      if (key.indexOf(CACHE_PREFIX) === 0 || key.indexOf('cache_') === 0) {
        wx.removeStorageSync(key)
      }
    })
  } catch (error) {}
}

module.exports = {
  createKey,
  get,
  set,
  remove,
  clearAll
}
