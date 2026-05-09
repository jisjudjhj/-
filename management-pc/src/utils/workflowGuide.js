const STORAGE_KEY = 'admin:workflow-guide:v1'

export const DEFENSE_DEMO_STEPS = [
  {
    key: 'showcase',
    title: '运营工作台',
    path: '/admin/dashboard',
    spotlightTitle: '运营状态总览',
    spotlightDescription: '查看交易、用户、推荐和风险链路的当前状态。',
  },
  {
    key: 'recommend',
    title: '推荐效果归因',
    path: '/admin/analytics/recommend',
    spotlightTitle: '推荐效果可验证',
    spotlightDescription: '查看曝光、点击、成交、推荐 token 和分群联动状态。',
  },
  {
    key: 'clusters',
    title: '用户画像分群',
    path: '/admin/analytics/user-clusters',
    spotlightTitle: 'KMeans 分群落地',
    spotlightDescription: '查看特征工程、冷启动剔除、聚类任务和运营动作。',
  },
  {
    key: 'tech',
    title: '链路与缓存',
    path: '/admin/analytics/tech-overview',
    spotlightTitle: '数据链路可信',
    spotlightDescription: '查看前端、后端、Redis、MySQL 和推荐服务之间的数据流转与降级边界。',
  },
  {
    key: 'seckill',
    title: '秒杀与风控',
    path: '/admin/seckill',
    spotlightTitle: '高并发防护',
    spotlightDescription: '查看秒杀压测、注册限流和库存保护状态。',
  },
  {
    key: 'stream',
    title: '实时链路巡检',
    path: '/admin/analytics/realtime-stream',
    spotlightTitle: '链路在线状态',
    spotlightDescription: '查看 CDC -> DWD -> DWS -> Redis 在线状态。',
  },
  {
    key: 'preview',
    title: '负反馈快路径',
    path: '/admin/recommend/preview',
    spotlightTitle: '快速反馈状态',
    spotlightDescription: '触发不感兴趣/短停留后刷新推荐，查看同类商品降权与结果多样化恢复。',
  },
  {
    key: 'abtest',
    title: '分层对照与回滚',
    path: '/admin/recommend/abtest',
    spotlightTitle: '提升与风控状态',
    spotlightDescription: '查看新老客/高低客单分层对照提升和阈值回滚状态。',
  },
]

const nowIso = () => new Date().toISOString()

const defaultState = () => ({
  active: false,
  stepKey: '',
  updatedAt: nowIso(),
})

const normalizeStepKey = (stepKey) => {
  if (!stepKey) {
    return DEFENSE_DEMO_STEPS[0].key
  }
  const target = String(stepKey).trim()
  return DEFENSE_DEMO_STEPS.some(step => step.key === target) ? target : DEFENSE_DEMO_STEPS[0].key
}

const writeState = (state) => {
  if (typeof window === 'undefined') {
    return state
  }
  const payload = {
    active: Boolean(state?.active),
    stepKey: state?.stepKey || '',
    updatedAt: nowIso(),
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
  return payload
}

export const readDefenseDemoState = () => {
  if (typeof window === 'undefined') {
    return defaultState()
  }
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return defaultState()
  }
  try {
    const parsed = JSON.parse(raw)
    return {
      active: Boolean(parsed?.active),
      stepKey: String(parsed?.stepKey || ''),
      updatedAt: parsed?.updatedAt || nowIso(),
    }
  } catch {
    return defaultState()
  }
}

export const startDefenseDemo = (stepKey = DEFENSE_DEMO_STEPS[0].key) =>
  writeState({
    active: true,
    stepKey: normalizeStepKey(stepKey),
  })

export const stopDefenseDemo = () => writeState(defaultState())

export const markDefenseDemoStep = (stepKey) => {
  const prev = readDefenseDemoState()
  return writeState({
    active: true,
    stepKey: normalizeStepKey(stepKey || prev.stepKey),
  })
}

export const getDefenseStepByKey = (stepKey) =>
  DEFENSE_DEMO_STEPS.find(step => step.key === stepKey) || null

export const getDefenseStepByPath = (path) =>
  DEFENSE_DEMO_STEPS.find(step => step.path === path) || null

export const getDefenseDemoContext = (path) => {
  const step = getDefenseStepByPath(path)
  if (!step) {
    return null
  }
  const index = DEFENSE_DEMO_STEPS.findIndex(item => item.key === step.key)
  return {
    step,
    index,
    total: DEFENSE_DEMO_STEPS.length,
    previous: index > 0 ? DEFENSE_DEMO_STEPS[index - 1] : null,
    next: index < DEFENSE_DEMO_STEPS.length - 1 ? DEFENSE_DEMO_STEPS[index + 1] : null,
  }
}

export const buildDefenseDemoRoute = (step, extraQuery = {}) => ({
  path: step.path,
  query: {
    ...extraQuery,
    workflowGuide: '1',
    workflowStep: step.key,
  },
})
