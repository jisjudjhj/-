export const competitionMode = import.meta.env.VITE_COMPETITION_MODE !== 'false'

export const demoToken = 'competition-demo-token'

export const demoAdminUser = {
  id: 900001,
  username: 'ops-sample',
  nickname: '运营体验账号',
  avatar: '',
  role: 'admin',
  status: 1,
}

const now = new Date()
const dayMs = 24 * 60 * 60 * 1000
const pad = value => String(value).padStart(2, '0')
const dateText = date => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
const dateTimeText = date => `${dateText(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
const daysAgo = days => new Date(now.getTime() - days * dayMs)
const daysAfter = days => new Date(now.getTime() + days * dayMs)

const cloneData = data => {
  if (typeof data === 'function') {
    return cloneData(data())
  }
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

const hasContent = value => {
  if (value == null) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value !== 'object') return true
  if (Array.isArray(value.records)) return value.records.length > 0 || Number(value.total || 0) > 0
  if (value.available === false) return false
  return Object.keys(value).length > 0
}

export const isCompetitionDemoToken = () => {
  if (typeof window === 'undefined') return false
  return String(window.localStorage.getItem('token') || '').startsWith(demoToken)
}

export const competitionRequestConfig = (config = {}) => ({
  ...config,
  skipErrorNotify: competitionMode ? true : config.skipErrorNotify,
})

export const withCompetitionFallback = (task, fallback) => {
  if (!competitionMode) {
    return task
  }
  if (isCompetitionDemoToken()) {
    return Promise.resolve(cloneData(fallback))
  }
  return Promise.resolve(task)
    .then(result => (hasContent(result) ? result : cloneData(fallback)))
    .catch(() => cloneData(fallback))
}

export const demoAnalysisSummary = {
  totalUsers: 12860,
  activeUsers: 8430,
  paidUserCount: 2168,
  orderCount: 3842,
  refundCount: 96,
  gmv: 935680.5,
  recommendationConversionRate: 6.12,
  bestSegmentCode: 'S1',
}

const demoRecommendProducts = [
  { id: 401, name: '降噪蓝牙耳机 Pro', price: 399, salesCount: 2380, mainImage: '' },
  { id: 402, name: '护眼学习台灯 Max', price: 259, salesCount: 1842, mainImage: '' },
  { id: 403, name: '便携充电宝 20000mAh', price: 169, salesCount: 3120, mainImage: '' },
  { id: 404, name: '机械键盘 87 键', price: 329, salesCount: 968, mainImage: '' },
  { id: 405, name: '智能手环 S', price: 219, salesCount: 2764, mainImage: '' },
  { id: 406, name: '运动水杯 1L', price: 59, salesCount: 4206, mainImage: '' },
]

export const demoRecommendProfile = (userId = 4) => ({
  userId,
  username: `demo_user_${userId}`,
  nickname: `演示用户${userId}`,
  experimentGroup: 'hybrid',
  experimentGroupDesc: '标准混合组',
  interactedProducts: 27,
  vectorDimension: 5,
  userTags: ['数码偏好', '高意向', '耳机', '夜间下单', '价格敏感'],
  categoryWeights: { 数码: 0.92, 家居: 0.66, 配件: 0.48, 学习: 0.34 },
  behaviorStats: [
    { behaviorType: 'view', count: 18 },
    { behaviorType: 'search', count: 7 },
    { behaviorType: 'cart', count: 5 },
    { behaviorType: 'favorite', count: 4 },
    { behaviorType: 'purchase', count: 2 },
  ],
})

export const demoRecommendPreview = (userId = 4) => ({
  userId,
  experimentGroup: 'hybrid',
  algorithmWeights: { collaborative: 0.4, content: 0.3, popularity: 0.3 },
  products: demoRecommendProducts.slice(0, 5),
  explanations: [
    { primaryReason: 'COLLABORATIVE', reasonText: 'score = 0.40CF + 0.30CB + 0.30Hot；同类高转化。' },
    { primaryReason: 'CONTENT_TAG', reasonText: '标签命中：耳机 / 数码 / 通勤。' },
    { primaryReason: 'CONTENT_CATEGORY', reasonText: '品类权重高：数码 > 家居 > 配件。' },
    { primaryReason: 'HOT_SELLING', reasonText: '热榜补位；销量与点击率稳定。' },
    { primaryReason: 'COLD_START', reasonText: '探索补位；控制重复曝光。' },
  ],
})

export const demoRecommendRealtime = (userId = 4) => ({
  generatedAt: dateTimeText(now),
  profile: demoRecommendProfile(userId),
  segment: {
    segmentCode: 'S2',
    segmentName: '高意向待转化用户',
    snapshotDate: dateText(daysAgo(1)),
    confidenceScore: 0.86,
    personaSummary: '高行为、低消费；先承接高意向，再给券。 ',
    operationSuggestion: '限时券 + 加购提醒',
    featureHighlights: ['search 高', 'cart 高', '数码偏好'],
    topCategories: ['数码', '配件'],
    topTags: ['耳机', '蓝牙', '通勤'],
  },
  recommendation: demoRecommendPreview(userId),
})

export const demoRecommendCompare = (userId = 4) => ({
  userId,
  experimentGroup: 'hybrid',
  weights: { collaborative: 0.4, content: 0.3, popularity: 0.3 },
  online: demoRecommendProducts.slice(0, 5),
  hybrid: demoRecommendProducts.slice(0, 5),
  cf: [demoRecommendProducts[0], demoRecommendProducts[3], demoRecommendProducts[4], demoRecommendProducts[1]],
  cb: [demoRecommendProducts[1], demoRecommendProducts[0], demoRecommendProducts[2], demoRecommendProducts[4]],
  hot: [demoRecommendProducts[2], demoRecommendProducts[5], demoRecommendProducts[0], demoRecommendProducts[4]],
  quality: {
    topCategoryHitRate: '82.50',
    topPreferenceCategories: ['数码', '配件'],
    status: 'PASS',
    tagComparisons: [
      { rank: 1, productId: 401, productName: '降噪蓝牙耳机 Pro', matchedTags: ['耳机', '蓝牙'], basis: 'CF + CB 同时命中。' },
      { rank: 2, productId: 402, productName: '护眼学习台灯 Max', matchedTags: ['学习'], basis: 'CB 品类命中。' },
    ],
  },
  portraitLayers: ['短期意图', '长期偏好', '分群标签'],
  explainableFormula: { name: '混合推荐', expression: '0.40CF + 0.30CB + 0.30Hot' },
  beforeAfterQuality: {
    baseline: { topCategoryHitRate: '66.20' },
    optimized: { topCategoryHitRate: '82.50' },
  },
})

const demoStreamUsers = [
  { id: 4, nickname: '演示用户4', username: 'demo_user_4', tags: ['耳机', '高意向', '数码'], topCategory: '数码', behaviorEventCount: 36 },
  { id: 7, nickname: '演示用户7', username: 'demo_user_7', tags: ['台灯', '学习', '夜间'], topCategory: '学习', behaviorEventCount: 24 },
  { id: 12, nickname: '演示用户12', username: 'demo_user_12', tags: ['手环', '运动', '健康'], topCategory: '穿戴', behaviorEventCount: 18 },
]

export const demoStreamOverview = {
  status: {
    realtimeEnabled: true,
    kafkaConsumerEnabled: false,
    consumerGroupId: 'demo-stream-group',
    redisHotLastUpdate: dateTimeText(now),
  },
  metrics: {
    hotProducts1m: 6,
    hotProducts1h: 8,
    sampleUsers: demoStreamUsers.length,
  },
  hotProducts1m: demoRecommendProducts.slice(0, 5).map((item, index) => ({ ...item, rank: index + 1, categoryName: index < 3 ? '数码' : '配件', score: 96 - index * 4 })),
  hotProducts1h: demoRecommendProducts.slice(0, 5).map((item, index) => ({ ...item, rank: index + 1, categoryName: index < 3 ? '数码' : '配件', score: 90 - index * 3 })),
  hotProducts1d: demoRecommendProducts.slice(0, 5).map((item, index) => ({ ...item, rank: index + 1, categoryName: index < 3 ? '数码' : '配件', score: 84 - index * 2 })),
  pipeline: [
    { name: 'event', status: 'active', summary: '行为事件接收中', target: 'behavior_event' },
    { name: 'profile', status: 'ready', summary: '画像快照可读', target: 'redis:profile' },
    { name: 'hot', status: 'active', summary: '热榜已聚合', target: 'redis:hot' },
    { name: 'alert', status: 'ready', summary: '监控在线', target: 'stream-monitor' },
  ],
  sampleUsers: demoStreamUsers.map(item => ({
    user: { id: item.id, nickname: item.nickname, username: item.username },
    lastUpdate: dateTimeText(now),
    behaviorEventCount: item.behaviorEventCount,
    topCategory: item.topCategory,
    tags: item.tags,
  })),
  monitor: {
    available: true,
    updatedAt: dateTimeText(now),
    consumerLag: { totalLag: 0, topics: [{ topic: 'behavior_event', lag: 0 }] },
    deadLetter: { totalMessages: 0, topics: [] },
    alerts: [],
  },
}

export const demoStreamUserSnapshot = (userId = 4) => {
  const user = demoStreamUsers.find(item => item.id === Number(userId)) || demoStreamUsers[0]
  return {
    user: { id: user.id, nickname: user.nickname, username: user.username },
    lastUpdate: dateTimeText(now),
    tags: user.tags,
    behaviorDistribution: [
      { behaviorType: 'view', count: 18 },
      { behaviorType: 'search', count: 8 },
      { behaviorType: 'cart', count: 5 },
      { behaviorType: 'favorite', count: 3 },
      { behaviorType: 'purchase', count: 2 },
    ],
    categoryWeights: [
      { categoryName: user.topCategory, weight: 0.92 },
      { categoryName: '配件', weight: 0.61 },
      { categoryName: '家居', weight: 0.34 },
    ],
  }
}

export const demoDashboard = {
  orderCount: 3842,
  paidOrderCount: 3219,
  refundCount: 96,
  totalUsers: 12860,
  activeUsers: 8430,
  totalProducts: 2480,
  todayOrders: 186,
  todaySales: 52680.8,
  pendingRefunds: 12,
  pendingProfileChanges: 5,
  riskAlerts: 9,
}

export const demoRecommendAnalytics = (days = 30) => {
  const start = daysAgo(Number(days || 30) - 1)
  const dailyTrend = Array.from({ length: Math.min(Number(days || 30), 14) }, (_, index) => {
    const exposureCount = 10800 + index * 560
    const clickCount = Math.round(exposureCount * (0.158 + index * 0.002))
    const purchaseCount = Math.round(clickCount * (0.31 + index * 0.004))
    return {
      statDate: dateText(new Date(start.getTime() + index * dayMs)),
      exposureCount,
      clickCount,
      purchaseCount,
      conversionRate: Number(((purchaseCount / exposureCount) * 100).toFixed(2)),
      clickThroughRate: Number(((clickCount / exposureCount) * 100).toFixed(2)),
    }
  })

  return {
    totalUsers: 12860,
    totalProducts: 2480,
    performance: {
      days,
      startDate: dateText(start),
      endDate: dateText(now),
      summary: {
        exposureCount: 186420,
        userCount: 8430,
        clickCount: 34296,
        purchaseCount: 11403,
        successCount: 138260,
        clickThroughRate: 18.4,
        conversionRate: 6.12,
        recommendationSuccessRate: 74.16,
        postClickConversionRate: 33.23,
      },
      sceneMetrics: [
        { scene: 'HOME', exposureCount: 64200, clickCount: 12840, purchaseCount: 4301, conversionRate: 6.7, clickThroughRate: 20, successRate: 76.2 },
        { scene: 'DETAIL', exposureCount: 48360, clickCount: 8124, purchaseCount: 2892, conversionRate: 5.98, clickThroughRate: 16.8, successRate: 73.6 },
        { scene: 'CART', exposureCount: 39210, clickCount: 7449, purchaseCount: 2818, conversionRate: 7.19, clickThroughRate: 19, successRate: 78.5 },
        { scene: 'SEARCH', exposureCount: 34650, clickCount: 5883, purchaseCount: 1392, conversionRate: 4.02, clickThroughRate: 16.98, successRate: 67.9 },
      ],
      algorithmMetrics: [
        { algorithm: 'hybrid', exposureCount: 72800, clickCount: 15033, purchaseCount: 5229, conversionRate: 7.18, clickThroughRate: 20.65, successRate: 79.2 },
        { algorithm: 'collaborative', exposureCount: 51280, clickCount: 9041, purchaseCount: 3015, conversionRate: 5.88, clickThroughRate: 17.63, successRate: 72.4 },
        { algorithm: 'content', exposureCount: 38860, clickCount: 6620, purchaseCount: 2157, conversionRate: 5.55, clickThroughRate: 17.04, successRate: 70.8 },
        { algorithm: 'hot', exposureCount: 23480, clickCount: 3602, purchaseCount: 1002, conversionRate: 4.27, clickThroughRate: 15.34, successRate: 66.1 },
      ],
      dailyTrend,
      comparisonHighlights: [
        {
          dimension: 'scene',
          title: '信号源对比',
          winnerLabel: '高意向信号源承接最稳',
          baselineLabel: '检索信号源',
          summary: '高意向信号源转化更强。',
          conversionLiftRatio: 78.9,
          successLiftRatio: 15.6,
        },
        {
          dimension: 'algorithm',
          title: '算法对比',
          winnerLabel: 'Hybrid 综合排序',
          baselineLabel: '热榜兜底',
          summary: 'Hybrid 点击后购买更强。',
          conversionLiftRatio: 68.1,
          successLiftRatio: 19.8,
        },
      ],
      sceneAlgorithmLeaders: [
        { sceneLabel: '主信号源', algorithmLabel: 'Hybrid 综合排序', shareInsideScene: 0.43, conversionRate: 0.0718 },
        { sceneLabel: '内容信号源', algorithmLabel: '内容相似', shareInsideScene: 0.37, conversionRate: 0.0635 },
        { sceneLabel: '高意向信号源', algorithmLabel: '协同过滤', shareInsideScene: 0.31, conversionRate: 0.0812 },
      ],
      bestAlgorithmSegment: {
        available: true,
        segmentLabel: '高价值活跃用户',
        algorithmLabel: 'Hybrid 综合排序',
        summary: '高价值活跃用户更适合 Hybrid。',
        conversionRate: 0.0826,
        successRate: 0.812,
        exposureCount: 28640,
      },
      optimizationStages: [
        {
          key: 'recall',
          title: '多路召回',
          focus: '覆盖',
          techStack: '协同过滤 / 内容相似 / 热榜 / 搜索意图',
          evidence: 'token 追踪曝光来源。',
          result: '降低热榜同质化。',
          note: '先看召回，再看权重。',
        },
        {
          key: 'rank',
          title: '画像排序',
          focus: '转化',
          techStack: '用户偏好 / 分群标签 / 商品质量 / 库存',
          evidence: '不同分群使用不同权重。',
          result: '结果可解释。',
          note: '可联动解释面板。',
        },
      ],
      diagnosticCards: [
        { title: '数据新鲜度', value: '5 分钟', summary: 'Redis 快照保持近期可读', detail: 'MySQL 失败时仍可读取最近一批结果。' },
        { title: '归因字段', value: 'Token', summary: '曝光到成交可追踪', detail: '用于串联推荐曝光、点击和订单。' },
        { title: '降级策略', value: '热榜', summary: '异常时回退热销商品', detail: '保证推荐结果持续可用。' },
        { title: '实验口径', value: '分层', summary: '按人群与信号源比较', detail: '避免只看总量平均数。' },
      ],
      distributionQuality: {
        available: true,
        summary: '推荐曝光覆盖主信号源，高意向信号源贡献主要转化。',
      },
      sevenDayRepurchase: {
        current: 0.214,
        baseline: 0.176,
        lift: 21.6,
      },
      defenseNarrative: [
        '先看曝光、点击、成交。',
        '再看分群、特征、动作。',
        '再看向量、权重、解释。',
        '最后看交易链路是否稳定。',
      ],
    },
  }
}

const demoFeatureColumns = [
  'order_count_90d',
  'order_amount_90d',
  'avg_order_amount_90d',
  'distinct_category_count_90d',
  'behavior_count_30d',
  'view_count_30d',
  'cart_count_30d',
  'favorite_count_30d',
  'purchase_behavior_count_30d',
  'active_days_30d',
  'avg_duration_30d',
  'recency_order_days',
  'recency_behavior_days',
  'tenure_days',
]

export const demoKmeansLatestTask = {
  id: 2026042901,
  batchNo: 'KM-20260429-DEMO',
  snapshotDate: dateText(daysAgo(1)),
  status: 'SUCCESS',
  finalStatus: 'SUCCESS',
  clusterCount: 4,
  sampleUserCount: 8430,
  clusteredUserCount: 7526,
  coldStartUserCount: 904,
  silhouetteScore: 0.6127,
  inertiaScore: 184.392615,
  featureColumns: demoFeatureColumns,
  startTime: dateTimeText(daysAgo(1)),
  endTime: dateTimeText(new Date(daysAgo(1).getTime() + 3 * 60 * 1000)),
  dataSource: 'redis + mysql',
  freshness: {
    lastTaskTime: dateTimeText(daysAgo(1)),
    snapshotDate: dateText(daysAgo(1)),
    source: 'redis',
  },
  storageBoundary: {
    mysqlTables: ['analytics_kmeans_task', 'analytics_kmeans_segment', 'analytics_kmeans_user_result'],
    redisKeys: ['analytics:kmeans:latest:task', 'analytics:kmeans:latest:summary'],
  },
}

export const demoKmeansSegments = [
  {
    segmentCode: 'S1',
    segmentName: '高价值活跃用户',
    userCount: 1860,
    userRatio: 0.247,
    avgOrderAmount: 682.4,
    avgOrderCount: 4.8,
    avgBehaviorCount: 38.6,
    avgRecencyDays: 9,
    topCategories: ['数码', '家电', '运动'],
    segmentDescription: '近期消费高、复购强、活跃稳定，是新品首发和会员权益的优先触达人群。',
    operationSuggestion: '优先推会员券、新品首发、组合购，并保持推荐理由可解释。',
    llmSummary: '适合做高客单价商品与会员权益承接。',
  },
  {
    segmentCode: 'S2',
    segmentName: '高意向待转化用户',
    userCount: 2140,
    userRatio: 0.284,
    avgOrderAmount: 188.2,
    avgOrderCount: 1.2,
    avgBehaviorCount: 45.1,
    avgRecencyDays: 18,
    topCategories: ['美妆', '食品', '服饰'],
    segmentDescription: '浏览、收藏和加购较多，但成交偏弱，需要优惠和提醒承接。',
    operationSuggestion: '使用限时券、加购提醒和客服触达推动首单转化。',
    llmSummary: '适合做限时券和强理由推荐。',
  },
  {
    segmentCode: 'S3',
    segmentName: '稳定消费用户',
    userCount: 2688,
    userRatio: 0.357,
    avgOrderAmount: 312.7,
    avgOrderCount: 2.1,
    avgBehaviorCount: 24.4,
    avgRecencyDays: 31,
    topCategories: ['日用', '食品', '母婴'],
    segmentDescription: '消费和活跃处于中间水平，适合做常规个性化推荐和满减组合。',
    operationSuggestion: '用跨品类组合、满减券和热销榜单提升复购。',
    llmSummary: '适合做持续促活。',
  },
  {
    segmentCode: 'S4',
    segmentName: '沉睡低活跃用户',
    userCount: 838,
    userRatio: 0.111,
    avgOrderAmount: 96.5,
    avgOrderCount: 0.7,
    avgBehaviorCount: 5.4,
    avgRecencyDays: 86,
    topCategories: ['服饰', '家清'],
    segmentDescription: '近期访问和购买弱，需要召回，不适合强依赖个性化排序。',
    operationSuggestion: '先用召回券和热门商品唤醒，再逐步恢复个性化推荐。',
    llmSummary: '适合做召回运营。',
  },
  {
    segmentCode: 'COLD_START',
    segmentName: '冷启动观察用户',
    userCount: 904,
    userRatio: 0.107,
    avgOrderAmount: 0,
    avgOrderCount: 0,
    avgBehaviorCount: 1.8,
    avgRecencyDays: 0,
    topCategories: ['待识别'],
    segmentDescription: '行为样本不足，先独立观察，避免进入 KMeans 后拉偏聚类中心。',
    operationSuggestion: '优先使用热榜、新人券和类目探索策略。',
    llmSummary: '适合做冷启动推荐。',
  },
]

export const demoKmeansSummary = {
  segmentCount: 5,
  dataSource: 'redis + mysql',
  summary: {
    requestedClusterCount: 4,
    actualClusterCount: 4,
    sampleUserCount: 8430,
    clusteredUserCount: 7526,
    coldStartUserCount: 904,
    bestSegmentCode: 'S1',
    segmentDistribution: demoKmeansSegments.map(item => ({
      segmentCode: item.segmentCode,
      segmentName: item.segmentName,
      userCount: item.userCount,
      userRatio: item.userRatio,
    })),
    clusterSelection: {
      mode: 'auto_silhouette',
      candidateScores: [
        { k: 2, silhouetteScore: 0.4921 },
        { k: 3, silhouetteScore: 0.5719 },
        { k: 4, silhouetteScore: 0.6127 },
        { k: 5, silhouetteScore: 0.5982 },
      ],
    },
  },
  featureColumns: demoFeatureColumns,
  freshness: demoKmeansLatestTask.freshness,
  storageBoundary: demoKmeansLatestTask.storageBoundary,
  task: demoKmeansLatestTask,
  llmOverview: {
    overallSummary: '本批次形成高价值、待转化、稳定消费、沉睡召回和冷启动观察五类人群，可直接联动推荐与优惠券策略。',
  },
}

export const demoKmeansTaskHistory = {
  records: [
    demoKmeansLatestTask,
    {
      ...demoKmeansLatestTask,
      id: 2026042801,
      batchNo: 'KM-20260428-DEMO',
      snapshotDate: dateText(daysAgo(2)),
      startTime: dateTimeText(daysAgo(2)),
      endTime: dateTimeText(new Date(daysAgo(2).getTime() + 4 * 60 * 1000)),
      silhouetteScore: 0.6041,
    },
  ],
  total: 2,
  runtime: {
    runningInDatabase: false,
    triggerInProgress: false,
    latestStatus: 'SUCCESS',
  },
}

export const demoKmeansUsers = {
  records: [
    { userId: 1012, nickname: '张同学', username: 'user1012', segmentCode: 'S1', segmentName: '高价值活跃用户', confidenceScore: 0.91, distanceToCenter: 0.083, totalAmount: 2860.5, orderCount: 8 },
    { userId: 1048, nickname: '李同学', username: 'user1048', segmentCode: 'S2', segmentName: '高意向待转化用户', confidenceScore: 0.84, distanceToCenter: 0.126, totalAmount: 198.9, orderCount: 1 },
    { userId: 1086, nickname: '王同学', username: 'user1086', segmentCode: 'S3', segmentName: '稳定消费用户', confidenceScore: 0.79, distanceToCenter: 0.169, totalAmount: 836.2, orderCount: 3 },
  ],
  total: 3,
}

export const demoKmeansUserDetail = {
  userId: 1012,
  nickname: '张同学',
  segmentCode: 'S1',
  segmentName: '高价值活跃用户',
  confidenceScore: 0.91,
  distanceToCenter: 0.083,
  rawFeatures: {
    order_count_90d: 8,
    order_amount_90d: 2860.5,
    avg_order_amount_90d: 357.56,
    behavior_count_30d: 42,
    cart_count_30d: 9,
    favorite_count_30d: 7,
    recency_order_days: 6,
  },
  normalizedFeatures: {
    order_count_90d: 1.62,
    order_amount_90d: 1.88,
    avg_order_amount_90d: 1.31,
    behavior_count_30d: 1.44,
    recency_order_days: -0.86,
  },
  segment: demoKmeansSegments[0],
}

export const demoSeckillDiagnostics = {
  moduleEnabled: true,
  diagnosis: '秒杀链路可用，活动、报名、库存和压测链路已就绪。',
  activityCount: 3,
  publishedActivityCount: 2,
  applicationCount: 18,
  approvedApplicationCount: 12,
  visibleProductCount: 8,
}

export const demoSeckillActivities = {
  records: [
    {
      id: 301,
      name: '数码尖货限时秒杀',
      description: '库存小于并发用户数，用于验证防超卖和限购。',
      coverImage: '',
      startTime: dateTimeText(daysAfter(1)),
      endTime: dateTimeText(daysAfter(2)),
      sortOrder: 1,
      publishStatus: 1,
      runtimeStatus: 0,
    },
    {
      id: 302,
      name: '新人专享低价场',
      description: '承接冷启动用户，配合新人券和热榜推荐。',
      coverImage: '',
      startTime: dateTimeText(daysAfter(3)),
      endTime: dateTimeText(daysAfter(4)),
      sortOrder: 2,
      publishStatus: 1,
      runtimeStatus: 0,
    },
  ],
  total: 2,
}

export const demoSeckillApplications = {
  records: [
    { id: 701, activityId: 301, activityName: '数码尖货限时秒杀', productName: '旗舰影像手机 Pro', merchantName: '官方自营店', status: 0, seckillStock: 10, seckillPrice: 1999, activityStartTime: dateTimeText(daysAfter(1)), activityEndTime: dateTimeText(daysAfter(2)) },
    { id: 702, activityId: 301, activityName: '数码尖货限时秒杀', productName: '智能手表 S', merchantName: '潮品数码馆', status: 1, seckillStock: 20, seckillPrice: 499, activityStartTime: dateTimeText(daysAfter(1)), activityEndTime: dateTimeText(daysAfter(2)) },
  ],
  total: 2,
}

export const demoSeckillStressResult = {
  success: true,
  reportPath: 'docs/competition/load-test-report.md',
  report: {
    summary: {
      requests: 200,
      success: 10,
      failed: 190,
      duration_seconds: 4.82,
      throughput_per_second: 41.49,
      latency_ms: { min: 42, p50: 186, p90: 518, p95: 736, p99: 1088, max: 1260 },
      messages: {
        '秒杀成功': 10,
        '库存不足': 121,
        '限购拦截': 38,
        '请求过快': 31,
      },
    },
    preparation: {
      rechargeAmount: 50000,
      accounts: Array.from({ length: 20 }, (_, index) => ({ username: `stress_user_${index + 1}` })),
      seckillData: {
        productName: '旗舰影像手机 Pro',
        productId: 88001,
        applyId: 99001,
        seckillStock: 10,
        plannedUsers: 20,
        limitPerUser: 1,
        seckillPrice: 1999,
        competitionNote: '验证库存扣减、限购、限流和失败兜底。',
      },
    },
  },
}
