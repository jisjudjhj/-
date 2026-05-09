import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import router from '../router'
import { runtimeConfig } from '../config/runtime'
import { disconnectRealtime } from './realtime'
import { competitionMode, isCompetitionDemoToken } from './competitionDemoData'

const MODULE_DISABLED_CODE = 423
const DEDUPE_WINDOW_MS = 1500
const DEFAULT_GET_CACHE_TTL = 12000
const LONG_GET_CACHE_TTL = 60000

const getCache = new Map()
const pendingGets = new Map()

let lastNotice = {
  message: '',
  at: 0
}

const defineMeta = (target, key, value) => {
  if (!target || typeof target !== 'object') {
    return
  }

  if (Object.prototype.hasOwnProperty.call(target, key)) {
    return
  }

  Object.defineProperty(target, key, {
    value,
    writable: false,
    enumerable: false,
  })
}

const wrapSuccessPayload = (payload, response) => {
  if (payload && typeof payload === 'object') {
    defineMeta(payload, 'message', response.message)
    defineMeta(payload, 'code', response.code)
    defineMeta(payload, '__rawResponse', response)
    return payload
  }

  return {
    code: response.code,
    message: response.message,
    data: payload,
  }
}

const request = axios.create({
  baseURL: runtimeConfig.apiBase,
  timeout: 10000
})

const defaultAdapter = axios.getAdapter(axios.defaults.adapter)

const isPlainObject = value => Object.prototype.toString.call(value) === '[object Object]'

const stableStringify = value => {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(',')}]`
  }
  if (isPlainObject(value)) {
    return `{${Object.keys(value)
      .sort()
      .map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(',')}}`
  }
  return JSON.stringify(value ?? null)
}

const normalizeUrl = config => {
  const base = config.baseURL || runtimeConfig.apiBase || ''
  const url = config.url || ''
  return url.startsWith('http') ? url : `${base}${url}`
}

const getAuthBucket = config => String(config.headers?.Authorization || '').slice(0, 36)

const buildGetCacheKey = config => [
  'GET',
  normalizeUrl(config),
  stableStringify(config.params || {}),
  getAuthBucket(config)
].join('|')

const shouldSkipGetCache = config => {
  if (config.skipCache || config.noCache || config.responseType === 'blob') {
    return true
  }

  const url = String(config.url || '')
  return [
    '/captcha',
    '/auth/me',
    '/im/conversations/',
    '/messages/unread-count',
    '/workbench/badge-counts',
    '/stream/',
  ].some(pattern => url.includes(pattern))
}

const resolveGetCacheTtl = config => {
  if (Number.isFinite(config.cacheTtl)) {
    return Math.max(0, Number(config.cacheTtl))
  }

  const url = String(config.url || '')
  if (
    url.includes('/module-switches') ||
    url.includes('/role-permissions') ||
    url.includes('/products/categories') ||
    url.includes('/risk/rules')
  ) {
    return LONG_GET_CACHE_TTL
  }

  return DEFAULT_GET_CACHE_TTL
}

const cloneData = data => {
  if (data == null) {
    return data
  }
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(data)
    } catch {
      return data
    }
  }
  try {
    return JSON.parse(JSON.stringify(data))
  } catch {
    return data
  }
}

export function clearRequestCache() {
  getCache.clear()
  pendingGets.clear()
}

function notify(message, type = 'error') {
  const text = message || '请求失败'
  const now = Date.now()

  if (lastNotice.message === text && now - lastNotice.at < DEDUPE_WINDOW_MS) {
    return
  }

  lastNotice = {
    message: text,
    at: now
  }

  ElMessage({
    message: text,
    type,
    duration: 2500
  })
}

request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }

    const method = String(config.method || 'get').toLowerCase()
    if (method === 'get' && !shouldSkipGetCache(config)) {
      const cacheKey = buildGetCacheKey(config)
      const now = Date.now()
      const cached = getCache.get(cacheKey)
      if (cached && cached.expiresAt > now) {
        config.adapter = () => Promise.resolve({
          data: cloneData(cached.data),
          status: 200,
          statusText: 'OK',
          headers: cached.headers || {},
          config,
          request: null,
        })
        return config
      }

      if (pendingGets.has(cacheKey)) {
        config.adapter = () => pendingGets.get(cacheKey).then(response => ({
          ...response,
          data: cloneData(response.data),
          config,
          request: null,
        }))
        return config
      }

      config.__cacheKey = cacheKey
      config.__cacheTtl = resolveGetCacheTtl(config)
      config.adapter = adapterConfig => {
        const task = defaultAdapter(adapterConfig)
          .then(response => {
            if (adapterConfig.__cacheTtl > 0 && response.status >= 200 && response.status < 300) {
              getCache.set(cacheKey, {
                data: cloneData(response.data),
                headers: response.headers,
                expiresAt: Date.now() + adapterConfig.__cacheTtl,
              })
            }
            return response
          })
          .finally(() => {
            pendingGets.delete(cacheKey)
          })
        pendingGets.set(cacheKey, task)
        return task
      }
      return config
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const method = String(response?.config?.method || 'get').toLowerCase()
    if (method !== 'get') {
      clearRequestCache()
    }

    const res = response.data
    const skipErrorNotify = !!response?.config?.skipErrorNotify || (competitionMode && isCompetitionDemoToken())
    if (res.code !== 200) {
      const message = res.message || '请求失败'
      if (res.code === 401) {
        if (competitionMode && isCompetitionDemoToken()) {
          const error = new Error(message)
          error.code = res.code
          return Promise.reject(error)
        }
        if (!skipErrorNotify) {
          notify(message)
        }
        const userStore = useUserStore()
        clearRequestCache()
        disconnectRealtime()
        userStore.logout()
        if (router.currentRoute.value.path !== '/login') {
          router.push('/login')
        }
      } else if (res.code === MODULE_DISABLED_CODE) {
        if (!skipErrorNotify) {
          notify(message, 'warning')
        }
      } else {
        if (!skipErrorNotify) {
          notify(message)
        }
      }

      const error = new Error(message)
      error.code = res.code
      return Promise.reject(error)
    }
    const payload = res.data !== undefined ? res.data : null
    return wrapSuccessPayload(payload, res)
  },
  error => {
    const status = error?.response?.status
    const code = error?.response?.data?.code
    const message = error?.response?.data?.message || error.message || '网络请求失败'
    const skipErrorNotify = !!error?.config?.skipErrorNotify || (competitionMode && isCompetitionDemoToken())

    if (status === 401) {
      if (competitionMode && isCompetitionDemoToken()) {
        return Promise.reject(error)
      }
      const userStore = useUserStore()
      clearRequestCache()
      disconnectRealtime()
      userStore.logout()
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }

    if (!skipErrorNotify) {
      if (code === MODULE_DISABLED_CODE) {
        notify(message, 'warning')
      } else if (status === 403) {
        notify('当前账号没有操作权限')
      } else {
        notify(message)
      }
    }
    return Promise.reject(error)
  }
)

export default request
