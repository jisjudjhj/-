<template>
  <div class="page-stack analytics-ui-page dashboard-cockpit">
    <section class="dashboard-cockpit__header">
      <div>
        <h1 class="dashboard-cockpit__title">平台运营工作台</h1>
      </div>
      <div class="dashboard-cockpit__summary">
        <div class="dashboard-cockpit__summary-label">当前判断</div>
        <div class="dashboard-cockpit__summary-value">{{ dashboardHeadline }}</div>
        <div class="dashboard-cockpit__summary-meta">
          最近统计日 {{ dashboardLatestDate }} · 推荐曝光 {{ formatNumber(performanceSummary.exposureCount) }} · 系统检查 {{ formatDateTime(systemHealth.timestamp) }}
        </div>
      </div>
    </section>

    <section class="dashboard-cockpit__metric-strip">
      <article v-for="item in dashboardMetrics" :key="item.label" class="dashboard-cockpit__metric">
        <span class="dashboard-cockpit__metric-label">{{ item.label }}</span>
        <strong class="dashboard-cockpit__metric-value">{{ item.value }}</strong>
        <span class="dashboard-cockpit__metric-sub">{{ item.sub }}</span>
      </article>
    </section>

    <section class="dashboard-cockpit__board dashboard-cockpit__board--split">
      <div class="dashboard-cockpit__main">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">经营趋势</div>
            <h2 class="dashboard-cockpit__section-title">营收与订单趋势</h2>
          </div>
          <div class="dashboard-cockpit__section-aside">最近 {{ recentStats.length }} 个统计日</div>
        </div>
        <div v-if="hasRecentStats" class="dashboard-cockpit__chart-wrap">
          <div ref="revenueChartRef" class="h-80 w-full"></div>
        </div>
        <div v-else class="dashboard-cockpit__empty">
          <div class="dashboard-cockpit__empty-title">暂无经营趋势</div>
          <p>还没有可用于绘制趋势的统计日。数据同步后这里会显示营收走势。</p>
        </div>
      </div>

      <aside class="dashboard-cockpit__side">
        <div class="dashboard-cockpit__section-eyebrow">当前判断</div>
        <h2 class="dashboard-cockpit__section-title">关键结论</h2>
        <div class="dashboard-cockpit__rail-list">
          <article v-for="item in dashboardInsights" :key="item.title" class="dashboard-cockpit__rail-item">
            <div class="dashboard-cockpit__rail-label">{{ item.title }}</div>
            <div class="dashboard-cockpit__rail-title">{{ item.value }}</div>
            <p class="dashboard-cockpit__rail-text">{{ item.detail }}</p>
          </article>
        </div>
      </aside>
    </section>

    <section class="dashboard-cockpit__grid dashboard-cockpit__grid--triple">
      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">用户结构</div>
            <h2 class="dashboard-cockpit__section-title">用户分群概览</h2>
          </div>
          <el-button text @click="goTo('/admin/analytics/user-clusters')">查看完整分群</el-button>
        </div>
        <div v-if="hasKmeansData" class="space-y-4">
          <div class="dashboard-cockpit__fact-list">
            <div v-for="item in kmeansFacts" :key="item.label" class="dashboard-cockpit__fact">
              <span class="dashboard-cockpit__fact-label">{{ item.label }}</span>
              <strong class="dashboard-cockpit__fact-value">{{ item.value }}</strong>
              <span class="dashboard-cockpit__fact-sub">{{ item.sub }}</span>
            </div>
          </div>
          <div class="dashboard-cockpit__note">
            最佳分群 {{ bestSegmentLabel }} · 数据来源 {{ kmeansDataSource }} · 最近任务 {{ formatDateTime(kmeansFreshness.lastTaskTime || kmeansTask.endTime || kmeansTask.startTime) }}
          </div>
        </div>
        <el-empty v-else description="暂无分群结果，请先执行一次分群任务。" />
      </article>

      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">推荐与转化</div>
            <h2 class="dashboard-cockpit__section-title">推荐核心指标</h2>
          </div>
          <el-button text @click="goTo('/admin/analytics/recommend')">进入推荐分析</el-button>
        </div>
        <div class="dashboard-cockpit__mini-stats">
          <div class="dashboard-cockpit__mini-stat">
            <span>推荐曝光</span>
            <strong>{{ formatNumber(performanceSummary.exposureCount) }}</strong>
          </div>
          <div class="dashboard-cockpit__mini-stat">
            <span>推荐转化率</span>
            <strong>{{ formatKpiPercent(performanceSummary.conversionRate) }}</strong>
          </div>
        </div>
        <div class="dashboard-cockpit__fact-list dashboard-cockpit__fact-list--kpi dashboard-cockpit__fact-list--recommend">
          <div v-for="item in recommendKpiCards" :key="item.label" class="dashboard-cockpit__fact">
            <span class="dashboard-cockpit__fact-label">{{ item.label }}</span>
            <strong class="dashboard-cockpit__fact-value">{{ item.value }}</strong>
          </div>
        </div>
      </article>

      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">系统与实时链路</div>
            <h2 class="dashboard-cockpit__section-title">系统与链路状态</h2>
          </div>
          <el-button text @click="goTo('/admin/analytics/realtime-stream')">查看实时流</el-button>
        </div>
        <div class="dashboard-cockpit__status-list">
          <div v-for="item in healthItems" :key="item.key" class="dashboard-cockpit__status-row">
            <div>
              <div class="dashboard-cockpit__status-title">{{ item.title }}</div>
              <div class="dashboard-cockpit__status-text">{{ item.message }}</div>
            </div>
            <el-tag :type="statusTagType(item.status)" effect="light">{{ statusLabel(item.status) }}</el-tag>
          </div>
        </div>
      </article>
    </section>

    <section class="dashboard-cockpit__board">
      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">订单分布</div>
            <h2 class="dashboard-cockpit__section-title">最近订单结构</h2>
          </div>
          <div class="dashboard-cockpit__section-aside">按最近日期切片</div>
        </div>
        <div v-if="hasOrderDistribution" ref="categoryChartRef" class="h-80 w-full"></div>
        <div v-else class="dashboard-cockpit__empty dashboard-cockpit__empty--compact">
          <div class="dashboard-cockpit__empty-title">暂无订单结构</div>
          <p>最近统计日还没有订单分布数据。</p>
        </div>
      </article>
    </section>

    <section class="dashboard-cockpit__grid dashboard-cockpit__grid--double">
      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">实时推荐指标</div>
            <h2 class="dashboard-cockpit__section-title">核心指标</h2>
          </div>
          <div class="dashboard-cockpit__section-aside">{{ formatDateTime(streamKpiLastUpdate) }}</div>
        </div>
        <div class="dashboard-cockpit__fact-list dashboard-cockpit__fact-list--kpi">
          <div v-for="item in realtimeKpiCards" :key="item.label" class="dashboard-cockpit__fact">
            <span class="dashboard-cockpit__fact-label">{{ item.label }}</span>
            <strong class="dashboard-cockpit__fact-value">{{ item.value }}</strong>
          </div>
        </div>
      </article>

      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">实时热榜</div>
            <h2 class="dashboard-cockpit__section-title">前五（1 分钟）</h2>
          </div>
          <el-button text @click="goTo('/admin/analytics/realtime-stream')">查看完整热榜</el-button>
        </div>
        <div v-if="hotProductsPreview.length" class="dashboard-cockpit__rank-list">
          <div v-for="(item, index) in hotProductsPreview" :key="`${item.productId || item.name}-${index}`" class="dashboard-cockpit__rank-row">
            <div class="dashboard-cockpit__rank-main">
              <span class="dashboard-cockpit__rank-index">{{ index + 1 }}</span>
              <div class="dashboard-cockpit__rank-title">{{ item.name }}</div>
            </div>
            <div class="dashboard-cockpit__rank-side">
              <span class="dashboard-cockpit__rank-score">{{ item.score }}</span>
              <span class="dashboard-cockpit__rank-price">{{ item.price }}</span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无热榜数据" :image-size="80" />
      </article>
    </section>

    <section class="dashboard-cockpit__grid dashboard-cockpit__grid--double">
      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">日统计明细</div>
            <h2 class="dashboard-cockpit__section-title">最近 7 日经营数据</h2>
          </div>
          <div class="dashboard-cockpit__section-aside">{{ recentDailyRows.length }} 条</div>
        </div>
        <div v-if="recentDailyRows.length" class="dashboard-cockpit__table">
          <div class="dashboard-cockpit__table-row dashboard-cockpit__table-row--head">
            <span>日期</span>
            <span>营收</span>
            <span>订单</span>
            <span>客单价</span>
          </div>
          <div v-for="item in recentDailyRows" :key="item.key" class="dashboard-cockpit__table-row">
            <span>{{ item.date }}</span>
            <span>{{ item.revenue }}</span>
            <span>{{ item.orders }}</span>
            <span>{{ item.aov }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无日统计数据" :image-size="80" />
      </article>

      <article class="dashboard-cockpit__section">
        <div class="dashboard-cockpit__section-head">
          <div>
            <div class="dashboard-cockpit__section-eyebrow">链路监控</div>
            <h2 class="dashboard-cockpit__section-title">实时链路排行</h2>
          </div>
          <div class="dashboard-cockpit__section-aside">{{ formatDateTime(streamMonitor.updatedAt || streamKpiLastUpdate) }}</div>
        </div>
        <div class="dashboard-cockpit__fact-list dashboard-cockpit__fact-list--window">
          <div v-for="item in streamWindowFacts" :key="item.label" class="dashboard-cockpit__fact">
            <span class="dashboard-cockpit__fact-label">{{ item.label }}</span>
            <strong class="dashboard-cockpit__fact-value">{{ item.value }}</strong>
          </div>
        </div>
        <div class="dashboard-cockpit__stream-grid">
          <div class="dashboard-cockpit__rank-card">
            <div class="dashboard-cockpit__rank-card-title">积压前五</div>
            <div v-if="lagTopicRows.length" class="dashboard-cockpit__rank-card-list">
              <div v-for="item in lagTopicRows" :key="item.key" class="dashboard-cockpit__rank-card-row">
                <span class="dashboard-cockpit__rank-card-topic">{{ item.topic }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div v-else class="dashboard-cockpit__rank-card-empty">暂无积压数据</div>
          </div>
          <div class="dashboard-cockpit__rank-card">
            <div class="dashboard-cockpit__rank-card-title">死信前五</div>
            <div v-if="deadLetterTopicRows.length" class="dashboard-cockpit__rank-card-list">
              <div v-for="item in deadLetterTopicRows" :key="item.key" class="dashboard-cockpit__rank-card-row">
                <span class="dashboard-cockpit__rank-card-topic">{{ item.topic }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
            <div v-else class="dashboard-cockpit__rank-card-empty">暂无死信数据</div>
          </div>
        </div>
      </article>
    </section>

  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAdminDashboard,
  getAdminKmeansLatestTask,
  getAdminKmeansSummary,
  getAdminRecommendAnalytics,
  getAdminStreamOverview,
  getAdminSystemHealth,
} from '../../api/admin'

const router = useRouter()

const dashData = ref({})
const kmeansTask = ref({})
const kmeansSummary = ref({})
const recommendAnalytics = ref({})
const streamOverview = ref({})
const systemHealth = ref({})
const revenueChartRef = ref(null)
const categoryChartRef = ref(null)

const recommendPerformance = computed(() => {
  const data = recommendAnalytics.value || {}
  return (
    data.performanceSummary ||
    data.recommendationSummary ||
    data.performance?.summary ||
    data.performance ||
    data.summary ||
    {}
  )
})

let revenueChart = null
let categoryChart = null
let themeObserver = null
let echartsLoadPromise = null

function loadEcharts() {
  if (!echartsLoadPromise) {
    echartsLoadPromise = import('echarts')
  }
  return echartsLoadPromise
}

const performanceSummary = computed(() => {
  const data = dashData.value || {}
  const source =
    data.performanceSummary ||
    data.recommendationSummary ||
    data.performance ||
    data.recommendationPerformance ||
    recommendPerformance.value ||
    {}

  return {
    exposureCount:
      source.exposureCount ??
      source.impressionCount ??
      0,
    conversionRate:
      source.conversionRate ??
      source.cvr ??
      source.orderRate ??
      source.purchaseRate ??
      0,
    clickCount:
      source.clickCount ??
      source.clickedCount ??
      0,
    purchaseCount:
      source.purchaseCount ??
      source.orderCount ??
      source.convertedCount ??
      0,
    clickThroughRate:
      source.clickThroughRate ??
      source.clickRate ??
      source.ctr ??
      null,
    postClickConversionRate:
      source.postClickConversionRate ??
      source.clickToPurchaseRate ??
      null,
    gmv:
      source.gmv ??
      source.gmvAmount ??
      source.totalGmv ??
      0,
    aov:
      source.aov ??
      source.avgOrderValue ??
      source.averageOrderValue ??
      0,
  }
})

const recentStats = computed(() => (Array.isArray(dashData.value?.recentStats) ? dashData.value.recentStats : []))
const hasRecentStats = computed(() => recentStats.value.some(item =>
  Number(item?.amount || item?.revenue || item?.totalRevenue || item?.count || item?.orders || item?.orderCount || 0) > 0
))
const orderDistributionRows = computed(() => recentStats.value.slice(0, 5).map((item, index) => ({
  value: Number(item?.count || item?.orders || item?.orderCount || 0),
  name: item?.date || item?.orderDate || `最近${index + 1}日`,
})).filter(item => item.value > 0))
const hasOrderDistribution = computed(() => orderDistributionRows.value.length > 0)
const latestStat = computed(() => recentStats.value[recentStats.value.length - 1] || {})
const previousStat = computed(() => recentStats.value[recentStats.value.length - 2] || {})
const latestRevenue = computed(() =>
  Number(latestStat.value.amount || latestStat.value.revenue || latestStat.value.totalRevenue || 0)
)
const previousRevenue = computed(() =>
  Number(previousStat.value.amount || previousStat.value.revenue || previousStat.value.totalRevenue || 0)
)
const latestOrderCount = computed(() =>
  Number(latestStat.value.count || latestStat.value.orders || latestStat.value.orderCount || 0)
)
const latestPaidUsers = computed(() =>
  Number(dashData.value?.paidUserCount || dashData.value?.activeUsers || kmeansMetrics.value.clusteredUserCount || 0)
)
const latestConversionRate = computed(() => Number(performanceSummary.value.conversionRate || 0))
const latestRecommendCtr = computed(() => {
  const raw = Number(performanceSummary.value.clickThroughRate)
  if (Number.isFinite(raw)) return raw
  const exposure = Number(performanceSummary.value.exposureCount || 0)
  const click = Number(performanceSummary.value.clickCount || 0)
  return exposure > 0 ? (click / exposure) * 100 : 0
})
const latestPostClickConversionRate = computed(() => {
  const raw = Number(performanceSummary.value.postClickConversionRate)
  if (Number.isFinite(raw)) return raw
  const click = Number(performanceSummary.value.clickCount || 0)
  const purchase = Number(performanceSummary.value.purchaseCount || 0)
  return click > 0 ? (purchase / click) * 100 : 0
})
const latestRecommendGmv = computed(() => Number(performanceSummary.value.gmv || 0))
const latestRecommendAov = computed(() => Number(performanceSummary.value.aov || 0))
const revenueDelta = computed(() => {
  if (!previousRevenue.value) return null
  return ((latestRevenue.value - previousRevenue.value) / previousRevenue.value) * 100
})
const warningCount = computed(() =>
  healthItems.value.filter(item => item.status === 'WARN' || item.status === 'DOWN' || item.status === 'MISSING').length
)
const dashboardLatestDate = computed(() => latestStat.value.date || latestStat.value.orderDate || '待同步')
const streamMetrics = computed(() => streamOverview.value?.metrics || {})
const streamMonitor = computed(() => streamOverview.value?.monitor || {})
const streamPipeline = computed(() => (Array.isArray(streamOverview.value?.pipeline) ? streamOverview.value.pipeline : []))
const streamRecommendationKpi = computed(() => streamOverview.value?.recommendationKpi?.metrics || {})
const streamKpiLastUpdate = computed(() =>
  streamOverview.value?.recommendationKpi?.lastUpdate ||
  streamOverview.value?.recommendationKpi?.ts ||
  streamOverview.value?.status?.redisHotLastUpdate ||
  null
)
const dashboardHeadline = computed(() => {
  if (warningCount.value > 0) {
    return `待处理风险 ${warningCount.value} 项`
  }
  if (latestConversionRate.value >= 10) {
    return '推荐转化稳定'
  }
  if (latestRevenue.value > 0) {
    return '经营数据持续更新'
  }
  return '等待经营数据'
})
const dashboardMetrics = computed(() => [
  {
    label: '最近统计日营收',
    value: formatMoney(latestRevenue.value),
    sub: revenueDelta.value == null ? '当前基线' : `较上日 ${revenueDelta.value >= 0 ? '+' : ''}${formatDecimal(revenueDelta.value, 1)}%`,
  },
  {
    label: '最近统计日订单',
    value: formatNumber(latestOrderCount.value),
    sub: '日订单量',
  },
  {
    label: '推荐曝光量',
    value: formatNumber(performanceSummary.value.exposureCount),
    sub: '核心流量指标',
  },
  {
    label: '推荐转化率',
    value: formatPercent(latestConversionRate.value),
    sub: '推荐效果指标',
  },
  {
    label: '推荐 CTR',
    value: formatKpiPercent(latestRecommendCtr.value),
    sub: '点击率',
  },
  {
    label: '推荐 GMV',
    value: formatMoney(latestRecommendGmv.value),
    sub: '推荐成交额',
  },
  {
    label: '活跃 / 付费用户',
    value: formatNumber(latestPaidUsers.value),
    sub: '活跃用户规模',
  },
  {
    label: '待关注风险数',
    value: formatNumber(warningCount.value),
    sub: warningCount.value ? '优先处理' : '当前正常',
  },
  {
    label: '消费总积压',
    value: formatNumber(streamMonitor.value?.consumerLag?.totalLag || 0),
    sub: '队列积压',
  },
])
const dashboardInsights = computed(() => [
  {
    title: '业务规模',
    value: `${formatMoney(latestRevenue.value)} / ${formatNumber(latestOrderCount.value)} 单`,
    detail: '今日成交规模',
  },
  {
    title: '用户结构',
    value: `${bestSegmentLabel.value} 是当前重点分群`,
    detail: hasKmeansData.value
      ? '分群结果可用'
      : '待补齐分群数据',
  },
  {
    title: '链路状态',
    value: warningCount.value ? `${warningCount.value} 项告警` : '核心链路稳定',
    detail: warningCount.value
      ? '存在待处理项'
      : '可继续观察经营趋势',
  },
])
const recommendKpiCards = computed(() => ([
  { label: '曝光', value: formatNumber(performanceSummary.value.exposureCount) },
  { label: '点击', value: formatNumber(performanceSummary.value.clickCount) },
  { label: '购买', value: formatNumber(performanceSummary.value.purchaseCount) },
  { label: 'CTR', value: formatKpiPercent(latestRecommendCtr.value) },
  { label: 'CVR', value: formatKpiPercent(latestConversionRate.value) },
  { label: '点击后转化', value: formatKpiPercent(latestPostClickConversionRate.value) },
  { label: '推荐GMV', value: formatMoney(latestRecommendGmv.value) },
  { label: '推荐客单价', value: formatMoney(latestRecommendAov.value) },
]))

const realtimeKpiCards = computed(() => ([
  { label: 'DAU', value: formatNumber(streamRecommendationKpi.value?.DAU || 0) },
  { label: 'CTR', value: formatKpiPercent(streamRecommendationKpi.value?.CTR) },
  { label: 'CVR', value: formatKpiPercent(streamRecommendationKpi.value?.CVR) },
  { label: 'GMV', value: formatMoney(streamRecommendationKpi.value?.GMV || 0) },
  { label: '客单价', value: formatMoney(streamRecommendationKpi.value?.AOV || 0) },
  { label: '复购率', value: formatKpiPercent(streamRecommendationKpi.value?.RepurchaseRate) },
  { label: '7日留存', value: formatKpiPercent(streamRecommendationKpi.value?.Retention7d) },
  { label: '退款率', value: formatKpiPercent(streamRecommendationKpi.value?.RefundRate) },
]))

const hotProductsPreview = computed(() => {
  const rows = Array.isArray(streamOverview.value?.hotProducts1m) ? streamOverview.value.hotProducts1m : []
  return rows.slice(0, 5).map((row, index) => {
    const score = pickNumber(row, ['score', 'hotScore', 'count', 'heat', 'value'])
    const price = pickNumber(row, ['price'])
    return {
      productId: row?.productId || row?.id || `hot-${index + 1}`,
      name: row?.productName || row?.name || `商品 ${index + 1}`,
      score: formatNumber(score),
      price: formatMoney(price),
    }
  })
})

const recentDailyRows = computed(() => {
  return recentStats.value.slice(-7).reverse().map((item, index) => {
    const revenue = Number(item.amount || item.revenue || item.totalRevenue || 0)
    const orders = Number(item.count || item.orders || item.orderCount || 0)
    return {
      key: `${item.date || item.orderDate || index}`,
      date: item.date || item.orderDate || `最近${index + 1}日`,
      revenue: formatMoney(revenue),
      orders: formatNumber(orders),
      aov: formatMoney(orders > 0 ? revenue / orders : 0),
    }
  })
})

const lagTopicRows = computed(() => {
  const rows = Array.isArray(streamMonitor.value?.consumerLag?.topics) ? streamMonitor.value.consumerLag.topics : []
  return rows.slice(0, 5).map((item, index) => ({
    key: `${item?.topic || 'lag'}-${index}`,
    topic: item?.topic || `topic-${index + 1}`,
    value: formatNumber(item?.lag || 0),
  }))
})

const deadLetterTopicRows = computed(() => {
  const rows = Array.isArray(streamMonitor.value?.deadLetter?.topics) ? streamMonitor.value.deadLetter.topics : []
  return rows.slice(0, 5).map((item, index) => ({
    key: `${item?.topic || 'dlt'}-${index}`,
    topic: item?.topic || `dlt-${index + 1}`,
    value: formatNumber(item?.messages || 0),
  }))
})

const pipelineStepSummary = computed(() => {
  const steps = streamPipeline.value
  return {
    total: steps.length,
    active: steps.filter(item => item?.status === 'active').length,
    ready: steps.filter(item => item?.status === 'ready').length,
  }
})

const streamWindowFacts = computed(() => ([
  { label: '1m 热榜', value: formatNumber(streamMetrics.value.hotProducts1m || 0) },
  { label: '1h 热榜', value: formatNumber(streamMetrics.value.hotProducts1h || 0) },
  { label: '1d 热榜', value: formatNumber(streamMetrics.value.hotProducts1d || 0) },
  { label: '运行中步骤', value: `${formatNumber(pipelineStepSummary.value.active)}/${formatNumber(pipelineStepSummary.value.total)}` },
]))

const kmeansMetrics = computed(() => kmeansSummary.value.summary || {})
const kmeansFreshness = computed(() => kmeansSummary.value.freshness || kmeansTask.value.freshness || {})
const kmeansDataSource = computed(() => kmeansSummary.value.dataSource || kmeansTask.value.dataSource || '待同步')
const hasKmeansData = computed(() =>
  Object.keys(kmeansTask.value || {}).length > 0 || Number(kmeansMetrics.value.clusteredUserCount || 0) > 0
)
const bestSegmentLabel = computed(() => kmeansMetrics.value.bestSegmentCode || '待生成')
const kmeansFacts = computed(() => [
  {
    label: '分群数量',
    value: formatNumber(kmeansSummary.value.segmentCount || kmeansTask.value.clusterCount || 0),
    sub: '当前批次生成的用户簇数量',
  },
  {
    label: '聚类用户',
    value: formatNumber(kmeansMetrics.value.clusteredUserCount || kmeansTask.value.clusteredUserCount || 0),
    sub: '进入模型分析的用户数',
  },
  {
    label: '冷启动用户',
    value: formatNumber(kmeansMetrics.value.coldStartUserCount || kmeansTask.value.coldStartUserCount || 0),
    sub: '后续适合解释冷启动推荐策略',
  },
  {
    label: '轮廓系数',
    value: formatDecimal(kmeansTask.value.silhouetteScore, 4),
    sub: '分群质量参考',
  },
])

const healthItems = computed(() => {
  const mysql = systemHealth.value.mysql || {}
  const redis = systemHealth.value.redis || {}
  const pythonAnalytics = systemHealth.value.pythonAnalytics || {}
  const sqlInit = systemHealth.value.sqlInit || {}
  const modules = systemHealth.value.modules || {}

  return [
    {
      key: 'mysql',
      title: 'MySQL',
      status: mysql.status || 'UNKNOWN',
      message: mysql.message || '检查数据库连接与数据源配置',
      extra: mysql.target || '待配置',
    },
    {
      key: 'redis',
      title: 'Redis',
      status: redis.status || 'UNKNOWN',
      message: redis.message || '检查缓存连接状态',
      extra: redis.target || '待配置',
    },
    {
      key: 'python',
      title: 'Python 分析',
      status: pythonAnalytics.status || 'UNKNOWN',
      message: pythonAnalytics.message || '检查离线分析脚本',
      extra: pythonAnalytics.target || '待配置',
    },
    {
      key: 'sql',
      title: '初始化 SQL',
      status: sqlInit.status || 'UNKNOWN',
      message: sqlInit.message || '检查数据库初始化脚本',
      extra: sqlInit.target || '待配置',
    },
    {
      key: 'modules',
      title: '模块开关',
      status: modules.status || 'UNKNOWN',
      message: modules.message || '检查功能开关状态',
      extra: `${modules.enabled || 0}/${modules.total || 0} 已启用`,
    },
  ]
})

function formatNumber(value) {
  const number = Number(value || 0)
  return Number.isNaN(number) ? '0' : number.toLocaleString('zh-CN')
}

function formatMoney(value) {
  const number = Number(value || 0)
  if (Number.isNaN(number)) return '¥0.00'
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatDecimal(value, digits = 2) {
  if (value == null || value === '') return '待计算'
  const number = Number(value)
  return Number.isNaN(number) ? '待计算' : number.toFixed(digits)
}

function formatPercent(value) {
  if (value == null || value === '') return '0%'
  const number = Number(value)
  if (Number.isNaN(number)) return '0%'
  return `${number.toFixed(2).replace(/\.00$/, '')}%`
}

function formatKpiPercent(value) {
  if (value == null || value === '') return '待同步'
  const number = Number(value)
  if (Number.isNaN(number)) return '待同步'
  const normalized = Math.abs(number) <= 1 ? number * 100 : number
  return `${normalized.toFixed(2).replace(/\.00$/, '')}%`
}

function pickNumber(obj, keys = []) {
  for (const key of keys) {
    const value = obj?.[key]
    const number = Number(value)
    if (Number.isFinite(number)) {
      return number
    }
  }
  return 0
}

function formatDateTime(value) {
  if (!value) return '待同步'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

function statusTagType(status) {
  if (status === 'UP') return 'success'
  if (status === 'WARN') return 'warning'
  if (status === 'DOWN' || status === 'MISSING') return 'danger'
  return 'info'
}

function statusLabel(status) {
  if (status === 'UP') return '正常'
  if (status === 'WARN') return '关注'
  if (status === 'DOWN') return '异常'
  if (status === 'MISSING') return '缺失'
  return '未知'
}

function goTo(path) {
  if (router.currentRoute.value.path !== path) {
    router.push(path)
  }
}

function disposeCharts() {
  revenueChart?.dispose()
  categoryChart?.dispose()
  revenueChart = null
  categoryChart = null
}

async function renderCharts() {
  if (!hasRecentStats.value && !hasOrderDistribution.value) {
    disposeCharts()
    return
  }
  const echarts = await loadEcharts()
  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#cbd5e1' : '#475569'
  const splitLineColor = isDark ? '#334155' : '#e2e8f0'

  disposeCharts()

  if (revenueChartRef.value && hasRecentStats.value) {
    revenueChart = echarts.init(revenueChartRef.value)
    revenueChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '4%', right: '4%', bottom: 40, top: 18, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: recentStats.value.map(item => item.date || item.orderDate || ''),
        axisLabel: { color: textColor, margin: 12 },
        axisLine: { lineStyle: { color: splitLineColor } },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
        axisLabel: { color: textColor },
      },
      series: [
        {
          name: '营收',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          lineStyle: { width: 3, color: '#2563eb' },
          itemStyle: { color: '#2563eb' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(37, 99, 235, 0.24)' },
              { offset: 1, color: 'rgba(37, 99, 235, 0)' },
            ]),
          },
          data: recentStats.value.map(item => item.amount || item.revenue || item.totalRevenue || 0),
        },
      ],
    })
  }

  if (categoryChartRef.value && hasOrderDistribution.value) {
    categoryChart = echarts.init(categoryChartRef.value)
    categoryChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll', textStyle: { color: textColor } },
      series: [
        {
          name: '订单分布',
          type: 'pie',
          radius: ['46%', '70%'],
          center: ['50%', '42%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 8,
            borderColor: isDark ? '#0f172a' : '#ffffff',
            borderWidth: 2,
          },
          label: { show: false },
          labelLine: { show: false },
          data: orderDistributionRows.value,
        },
      ],
    })
  }
}

async function loadDashboardData() {
  const [dashboardResult, taskResult, summaryResult, streamResult, healthResult, recommendResult] = await Promise.allSettled([
    getAdminDashboard(),
    getAdminKmeansLatestTask(),
    getAdminKmeansSummary(),
    getAdminStreamOverview({ hotLimit: 6, userLimit: 6 }),
    getAdminSystemHealth(),
    getAdminRecommendAnalytics(30),
  ])

  dashData.value = dashboardResult.status === 'fulfilled' ? dashboardResult.value || {} : {}
  kmeansTask.value = taskResult.status === 'fulfilled' ? taskResult.value || {} : {}
  kmeansSummary.value = summaryResult.status === 'fulfilled' ? summaryResult.value || {} : {}
  streamOverview.value = streamResult.status === 'fulfilled' ? streamResult.value || {} : {}
  systemHealth.value = healthResult.status === 'fulfilled' ? healthResult.value || {} : {}
  recommendAnalytics.value = recommendResult.status === 'fulfilled' ? recommendResult.value || {} : {}

  await nextTick()
  renderCharts()
}

onMounted(async () => {
  await loadDashboardData()
  window.addEventListener('resize', renderCharts)

  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(mutation => mutation.attributeName === 'class')) {
      nextTick(() => renderCharts())
    }
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', renderCharts)
  themeObserver?.disconnect()
  disposeCharts()
})
</script>

<style scoped>
.dashboard-cockpit {
  color: #0f172a;
}

.dashboard-cockpit__header {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 1.18fr) minmax(320px, 0.82fr);
  padding: 6px 0 10px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.dashboard-cockpit__eyebrow,
.dashboard-cockpit__section-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: #0f766e;
}

.dashboard-cockpit__title {
  margin-top: 12px;
  font-size: 38px;
  line-height: 1.08;
  font-weight: 800;
  letter-spacing: -0.04em;
}

.dashboard-cockpit__desc,
.dashboard-cockpit__body-text,
.dashboard-cockpit__rail-text,
.dashboard-cockpit__status-text,
.dashboard-cockpit__route-desc {
  color: #475569;
  line-height: 1.8;
}

.dashboard-cockpit__desc {
  max-width: 760px;
  margin-top: 16px;
  font-size: 15px;
}

.dashboard-cockpit__summary {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 24px 26px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(236, 253, 245, 0.92), rgba(239, 246, 255, 0.88)),
    rgba(255, 255, 255, 0.9);
}

.dashboard-cockpit__summary-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #0f766e;
}

.dashboard-cockpit__summary-value {
  margin-top: 12px;
  font-size: 24px;
  line-height: 1.35;
  font-weight: 800;
}

.dashboard-cockpit__summary-meta,
.dashboard-cockpit__section-aside,
.dashboard-cockpit__route-meta,
.dashboard-cockpit__fact-sub,
.dashboard-cockpit__metric-sub {
  color: #64748b;
}

.dashboard-cockpit__summary-meta {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
}

.dashboard-cockpit__metric-strip {
  display: grid;
  gap: 0 20px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  padding: 4px 0 8px;
}

.dashboard-cockpit__metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
  padding: 20px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
}

.dashboard-cockpit__metric-label,
.dashboard-cockpit__fact-label {
  font-size: 12px;
  color: #64748b;
}

.dashboard-cockpit__metric-value {
  font-size: 28px;
  line-height: 1.1;
  font-weight: 800;
}

.dashboard-cockpit__board,
.dashboard-cockpit__section {
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  padding-top: 24px;
}

.dashboard-cockpit__board--split {
  display: grid;
  gap: 0 32px;
  grid-template-columns: minmax(0, 1.18fr) minmax(300px, 0.82fr);
}

.dashboard-cockpit__side {
  padding-left: 30px;
  border-left: 1px solid rgba(148, 163, 184, 0.18);
}

.dashboard-cockpit__section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-cockpit__section-title {
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.28;
  font-weight: 800;
}

.dashboard-cockpit__chart-wrap {
  margin-top: 20px;
}

.dashboard-cockpit__empty {
  min-height: 320px;
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed rgba(148, 163, 184, 0.42);
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.72);
  color: #64748b;
  text-align: center;
  padding: 28px;
}

.dashboard-cockpit__empty--compact {
  min-height: 240px;
}

.dashboard-cockpit__empty-title {
  font-size: 16px;
  font-weight: 800;
  color: #334155;
}

.dashboard-cockpit__rail-list,
.dashboard-cockpit__status-list,
.dashboard-cockpit__route-list {
  display: flex;
  flex-direction: column;
}

.dashboard-cockpit__rail-list {
  margin-top: 18px;
}

.dashboard-cockpit__rail-item,
.dashboard-cockpit__status-row,
.dashboard-cockpit__route-item {
  padding: 16px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.dashboard-cockpit__rail-item:first-child,
.dashboard-cockpit__status-row:first-child,
.dashboard-cockpit__route-item:first-child {
  border-top: none;
}

.dashboard-cockpit__rail-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.dashboard-cockpit__rail-title,
.dashboard-cockpit__status-title,
.dashboard-cockpit__route-title {
  margin-top: 6px;
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.dashboard-cockpit__grid {
  display: grid;
  gap: 28px;
}

.dashboard-cockpit__grid--triple {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.dashboard-cockpit__grid--double {
  grid-template-columns: minmax(0, 0.88fr) minmax(0, 1.12fr);
}

.dashboard-cockpit__fact-list {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-cockpit__fact-list--kpi {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-cockpit__fact-list--recommend {
  margin-top: 10px;
}

.dashboard-cockpit__fact-list--window {
  margin-top: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.dashboard-cockpit__fact {
  padding: 12px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.dashboard-cockpit__fact-value,
.dashboard-cockpit__mini-stat strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  line-height: 1.1;
  font-weight: 800;
  color: #0f172a;
}

.dashboard-cockpit__note,
.dashboard-cockpit__note {
  margin-top: 8px;
  padding-left: 14px;
  border-left: 2px solid rgba(20, 184, 166, 0.45);
  font-size: 13px;
  line-height: 1.8;
  color: #0f766e;
}

.dashboard-cockpit__mini-stats {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 18px;
}

.dashboard-cockpit__mini-stat {
  padding: 16px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.dashboard-cockpit__mini-stat span {
  font-size: 12px;
  color: #64748b;
}

.dashboard-cockpit__bullet-list {
  margin: 18px 0 0;
  padding-left: 18px;
  color: #475569;
}

.dashboard-cockpit__bullet-list li + li {
  margin-top: 10px;
}

.dashboard-cockpit__table {
  margin-top: 16px;
}

.dashboard-cockpit__table-row {
  display: grid;
  grid-template-columns: minmax(112px, 1.1fr) minmax(128px, 1fr) minmax(88px, 0.9fr) minmax(128px, 1fr);
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 13px;
}

.dashboard-cockpit__table-row > span:last-child,
.dashboard-cockpit__table-row > span:nth-child(2),
.dashboard-cockpit__table-row > span:nth-child(3) {
  text-align: right;
}

.dashboard-cockpit__table-row--head {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
}

.dashboard-cockpit__status-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.dashboard-cockpit__stream-grid {
  margin-top: 16px;
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.dashboard-cockpit__rank-card {
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  padding-top: 12px;
}

.dashboard-cockpit__rank-card-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.dashboard-cockpit__rank-card-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
}

.dashboard-cockpit__rank-card-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 13px;
}

.dashboard-cockpit__rank-card-row:first-child {
  border-top: none;
}

.dashboard-cockpit__rank-card-topic {
  color: #475569;
  word-break: break-all;
}

.dashboard-cockpit__rank-card-empty {
  margin-top: 12px;
  font-size: 12px;
  color: #94a3b8;
}

.dashboard-cockpit__rank-list {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
}

.dashboard-cockpit__rank-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.dashboard-cockpit__rank-row:first-child {
  border-top: none;
}

.dashboard-cockpit__rank-main {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.dashboard-cockpit__rank-index {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
  background: rgba(20, 184, 166, 0.12);
}

.dashboard-cockpit__rank-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.dashboard-cockpit__rank-side {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #64748b;
  font-size: 13px;
}

.dashboard-cockpit__rank-score {
  font-weight: 700;
  color: #0f172a;
}

.dashboard-cockpit__route-item {
  width: 100%;
  background: transparent;
  border-left: 3px solid transparent;
  text-align: left;
  transition: border-color 0.18s ease, transform 0.18s ease, color 0.18s ease;
}

.dashboard-cockpit__route-item:hover {
  border-left-color: #0ea5e9;
  transform: translateX(4px);
}

html.dark .dashboard-cockpit {
  color: #e2e8f0;
}

html.dark .dashboard-cockpit__summary {
  border-color: rgba(71, 85, 105, 0.56);
  background:
    linear-gradient(180deg, rgba(6, 78, 59, 0.28), rgba(8, 47, 73, 0.24)),
    rgba(15, 23, 42, 0.5);
}

html.dark .dashboard-cockpit__desc,
html.dark .dashboard-cockpit__body-text,
html.dark .dashboard-cockpit__rail-text,
html.dark .dashboard-cockpit__status-text,
html.dark .dashboard-cockpit__route-desc,
html.dark .dashboard-cockpit__summary-meta,
html.dark .dashboard-cockpit__section-aside,
html.dark .dashboard-cockpit__route-meta,
html.dark .dashboard-cockpit__fact-sub,
html.dark .dashboard-cockpit__metric-sub,
html.dark .dashboard-cockpit__metric-label,
html.dark .dashboard-cockpit__fact-label,
html.dark .dashboard-cockpit__mini-stat span,
html.dark .dashboard-cockpit__rail-label {
  color: #94a3b8;
}

html.dark .dashboard-cockpit__rail-title,
html.dark .dashboard-cockpit__status-title,
html.dark .dashboard-cockpit__route-title,
.dark .dashboard-cockpit__rank-title,
.dark .dashboard-cockpit__rank-score,
html.dark .dashboard-cockpit__fact-value,
html.dark .dashboard-cockpit__mini-stat strong {
  color: #f8fafc;
}

html.dark .dashboard-cockpit__table-row--head,
html.dark .dashboard-cockpit__rank-card-title {
  color: #94a3b8;
}

html.dark .dashboard-cockpit__rank-card-topic {
  color: #cbd5e1;
}

/* Cardless cockpit pass: summary and empty states use lines instead of boxes. */
.dashboard-cockpit__summary {
  padding: 24px 0 !important;
  border: 0 !important;
  border-top: 1px solid rgba(148, 163, 184, 0.18) !important;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18) !important;
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

.dashboard-cockpit__empty {
  border-radius: 0 !important;
  background: transparent !important;
  box-shadow: none !important;
}

html.dark .dashboard-cockpit__summary {
  background: transparent !important;
}

@media (max-width: 1279px) {
  .dashboard-cockpit__header,
  .dashboard-cockpit__board--split,
  .dashboard-cockpit__grid--triple,
  .dashboard-cockpit__grid--double {
    grid-template-columns: 1fr;
  }

  .dashboard-cockpit__side {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid rgba(148, 163, 184, 0.18);
    padding-top: 24px;
  }
}

@media (max-width: 767px) {
  .dashboard-cockpit__title {
    font-size: 30px;
  }

  .dashboard-cockpit__metric-strip,
  .dashboard-cockpit__fact-list,
  .dashboard-cockpit__mini-stats,
  .dashboard-cockpit__fact-list--window {
    grid-template-columns: 1fr 1fr;
  }

  .dashboard-cockpit__fact-list--kpi {
    grid-template-columns: 1fr 1fr;
  }

  .dashboard-cockpit__table {
    overflow-x: auto;
  }

  .dashboard-cockpit__table-row {
    min-width: 460px;
  }

  .dashboard-cockpit__stream-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-cockpit__section-title {
    font-size: 20px;
  }
}
</style>
