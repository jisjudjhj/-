#!/usr/bin/env node

const http = require('http')
const https = require('https')
const { URL } = require('url')

const DEFAULTS = {
  url: 'http://127.0.0.1:8080/api/auth/register',
  rps: 1000,
  total: 0,
  seconds: 1,
  timeoutMs: 10000,
  password: 'Test123456',
  smsCode: '123456',
  captchaKey: 'load-test',
  captchaCode: '0000',
  phonePrefix: '19',
  concurrency: 1000
}

function parseArgs(argv) {
  const args = { ...DEFAULTS }
  for (let i = 0; i < argv.length; i += 1) {
    const raw = argv[i]
    if (!raw.startsWith('--')) continue

    const [keyPart, inlineValue] = raw.slice(2).split('=')
    const value = inlineValue !== undefined ? inlineValue : argv[i + 1]
    if (inlineValue === undefined) i += 1

    switch (keyPart) {
      case 'url':
        args.url = value
        break
      case 'rps':
        args.rps = Math.max(1, Number(value || DEFAULTS.rps))
        break
      case 'total':
        args.total = Math.max(1, Number(value || DEFAULTS.total))
        break
      case 'seconds':
        args.seconds = Math.max(1, Number(value || DEFAULTS.seconds))
        break
      case 'timeout-ms':
        args.timeoutMs = Math.max(1000, Number(value || DEFAULTS.timeoutMs))
        break
      case 'password':
        args.password = value || DEFAULTS.password
        break
      case 'sms-code':
        args.smsCode = value || DEFAULTS.smsCode
        break
      case 'captcha-key':
        args.captchaKey = value || DEFAULTS.captchaKey
        break
      case 'captcha-code':
        args.captchaCode = value || DEFAULTS.captchaCode
        break
      case 'phone-prefix':
        args.phonePrefix = value || DEFAULTS.phonePrefix
        break
      case 'concurrency':
        args.concurrency = Math.max(1, Number(value || DEFAULTS.concurrency))
        break
      case 'help':
        printHelp()
        process.exit(0)
        break
      default:
        throw new Error(`未知参数: --${keyPart}`)
    }
  }
  return args
}

function printHelp() {
  console.log(`
注册接口限流压测

默认：1 秒内发起 1000 个 POST /api/auth/register 请求，并输出成功、失败、耗时、限流数量。

用法：
  node scripts/register_load_test.js
  node scripts/register_load_test.js --url http://127.0.0.1:8081/api/auth/register --rps 1000 --seconds 1

参数：
  --url            注册接口完整地址，默认 ${DEFAULTS.url}
  --rps            每秒请求数，默认 1000
  --total          请求总数；传入后会覆盖 rps * seconds
  --seconds        持续秒数，默认 1
  --concurrency    最大同时在途请求数，默认 1000
  --timeout-ms     单请求超时，默认 10000
  --sms-code       短信验证码，默认 123456
  --captcha-key    图形验证码 key，默认 load-test
  --captcha-code   图形验证码值，默认 0000
  --phone-prefix   测试手机号前缀，默认 19

说明：
  当前后端注册接口有 IP 限流和图形验证码保护。未提供真实 captchaKey/captchaCode 时，
  预期大多数请求会被验证码或限流拦截，这正好可以验证服务是否稳定、是否被限流保护。
`)
}

function createPhone(index, prefix) {
  const suffix = String((Date.now() % 100000000) + index).padStart(9, '0').slice(-9)
  return `${prefix}${suffix}`.slice(0, 11)
}

function classifyFailure(statusCode, bodyText, errorCode) {
  const text = `${bodyText || ''}`
  if (errorCode) return `NETWORK_${errorCode}`
  if (statusCode === 429) return 'RATE_LIMIT'
  if (/频繁|限流|too many|rate/i.test(text)) return 'RATE_LIMIT'
  if (/图形验证码|captcha|验证码错误|验证码已过期/i.test(text)) return 'CAPTCHA_REJECTED'
  if (/短信|验证码/i.test(text)) return 'SMS_CODE_REJECTED'
  if (statusCode >= 500) return 'SERVER_5XX'
  if (statusCode >= 400) return `HTTP_${statusCode}`
  return 'BUSINESS_FAILED'
}

function isBusinessSuccess(statusCode, payload) {
  return statusCode >= 200 &&
    statusCode < 300 &&
    payload &&
    (payload.code === 200 || payload.code === 0)
}

function parseJson(text) {
  try {
    return JSON.parse(text)
  } catch (error) {
    return null
  }
}

function postJson(targetUrl, payload, timeoutMs, agent) {
  return new Promise((resolve) => {
    const url = new URL(targetUrl)
    const body = JSON.stringify(payload)
    const transport = url.protocol === 'https:' ? https : http
    const startedAt = process.hrtime.bigint()

    const req = transport.request({
      protocol: url.protocol,
      hostname: url.hostname,
      port: url.port || (url.protocol === 'https:' ? 443 : 80),
      path: `${url.pathname}${url.search}`,
      method: 'POST',
      agent,
      timeout: timeoutMs,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        'User-Agent': 'ecommerce-register-load-test/1.0'
      }
    }, (res) => {
      const chunks = []
      res.on('data', (chunk) => chunks.push(chunk))
      res.on('end', () => {
        const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6
        const text = Buffer.concat(chunks).toString('utf8')
        const json = parseJson(text)
        const success = isBusinessSuccess(res.statusCode, json)
        resolve({
          success,
          statusCode: res.statusCode,
          elapsedMs,
          bodyText: text,
          message: json && json.message ? json.message : ''
        })
      })
    })

    req.on('timeout', () => {
      req.destroy(new Error('ETIMEDOUT'))
    })

    req.on('error', (error) => {
      const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6
      resolve({
        success: false,
        statusCode: 0,
        elapsedMs,
        errorCode: error && error.message === 'ETIMEDOUT' ? 'ETIMEDOUT' : (error.code || 'REQUEST_ERROR'),
        message: error.message || 'request error',
        bodyText: ''
      })
    })

    req.write(body)
    req.end()
  })
}

function percentile(values, p) {
  if (!values.length) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)
  return sorted[index]
}

async function run() {
  const options = parseArgs(process.argv.slice(2))
  const total = options.total > 0
    ? Math.max(1, Math.round(options.total))
    : Math.max(1, Math.round(options.rps * options.seconds))
  const effectiveRps = total / options.seconds
  const durationMs = Math.max(1, options.seconds * 1000)
  const target = new URL(options.url)
  const agentOptions = {
    keepAlive: true,
    maxSockets: options.concurrency,
    maxFreeSockets: options.concurrency
  }
  const agent = target.protocol === 'https:' ? new https.Agent(agentOptions) : new http.Agent(agentOptions)

  const stats = {
    total,
    success: 0,
    failed: 0,
    rateLimited: 0,
    status: {},
    reasons: {},
    latency: []
  }

  let launched = 0
  let inFlight = 0
  let completed = 0
  const tasks = []
  const startWall = Date.now()
  const start = process.hrtime.bigint()

  console.log(`开始压测: ${options.url}`)
  console.log(`计划请求: ${total}，目标频率: ${effectiveRps.toFixed(2)}/s，持续: ${options.seconds}s，最大在途: ${options.concurrency}`)

  await new Promise((resolve) => {
    const tryLaunch = () => {
      const elapsed = Date.now() - startWall
      const shouldHaveLaunched = elapsed >= durationMs
        ? total
        : Math.min(total, Math.floor((elapsed / durationMs) * total))

      while (launched < shouldHaveLaunched && launched < total && inFlight < options.concurrency) {
        const index = launched
        launched += 1
        inFlight += 1

        const payload = {
          phone: createPhone(index, options.phonePrefix),
          password: options.password,
          code: options.smsCode,
          nickname: `压测用户${index + 1}`,
          captchaKey: options.captchaKey,
          captchaCode: options.captchaCode
        }

        const task = postJson(options.url, payload, options.timeoutMs, agent)
          .then((result) => {
            completed += 1
            inFlight -= 1
            stats.latency.push(result.elapsedMs)
            stats.status[result.statusCode] = (stats.status[result.statusCode] || 0) + 1

            if (result.success) {
              stats.success += 1
            } else {
              stats.failed += 1
              const reason = classifyFailure(result.statusCode, result.bodyText, result.errorCode)
              if (reason === 'RATE_LIMIT') {
                stats.rateLimited += 1
              }
              stats.reasons[reason] = (stats.reasons[reason] || 0) + 1
            }
          })
        tasks.push(task)
      }

      if (completed >= total) {
        resolve()
        return
      }

      setTimeout(tryLaunch, 1)
    }

    tryLaunch()
  })

  await Promise.all(tasks)
  agent.destroy()

  const elapsedMs = Number(process.hrtime.bigint() - start) / 1e6
  const latency = stats.latency
  const summary = {
    target: options.url,
    plannedRequests: total,
    success: stats.success,
    failed: stats.failed,
    rateLimited: stats.rateLimited,
    elapsedMs: Math.round(elapsedMs),
    elapsedSeconds: Number((elapsedMs / 1000).toFixed(3)),
    throughputPerSecond: Number((total / (elapsedMs / 1000)).toFixed(2)),
    latencyMs: {
      min: latency.length ? Number(Math.min(...latency).toFixed(2)) : 0,
      p50: Number(percentile(latency, 50).toFixed(2)),
      p90: Number(percentile(latency, 90).toFixed(2)),
      p95: Number(percentile(latency, 95).toFixed(2)),
      p99: Number(percentile(latency, 99).toFixed(2)),
      max: latency.length ? Number(Math.max(...latency).toFixed(2)) : 0
    },
    status: stats.status,
    failureReasons: stats.reasons
  }

  console.log('\n压测结果')
  console.log(`成功: ${summary.success}`)
  console.log(`失败: ${summary.failed}`)
  console.log(`其中限流: ${summary.rateLimited}`)
  console.log(`总耗时: ${summary.elapsedSeconds}s`)
  console.log(`实际吞吐: ${summary.throughputPerSecond}/s`)
  console.log(`延迟(ms): min=${summary.latencyMs.min}, p50=${summary.latencyMs.p50}, p90=${summary.latencyMs.p90}, p95=${summary.latencyMs.p95}, p99=${summary.latencyMs.p99}, max=${summary.latencyMs.max}`)
  console.log(`HTTP状态分布: ${JSON.stringify(summary.status)}`)
  console.log(`失败原因分布: ${JSON.stringify(summary.failureReasons)}`)
  console.log('\nJSON:')
  console.log(JSON.stringify(summary, null, 2))
}

run().catch((error) => {
  console.error('压测脚本异常:', error.message || error)
  process.exit(1)
})
