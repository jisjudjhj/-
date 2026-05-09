<template>
  <div v-loading="loading" class="space-y-6 analytics-ui-page defense-page">
    <section class="analysis-page-header">
      <h1 class="analysis-page-header__title">行为分析</h1>
      <div class="analysis-page-header__meta">行为权重 · 转化率 · 搜索指标</div>
    </section>

    <FeatureBrief
      kicker="用户行为"
      title="判断依据与组成"
      :items="behaviorFeatureBrief"
    />

    <section class="defense-evidence-list defense-evidence-list--two">
      <article
        v-for="item in behaviorEvidenceTracks"
        :key="item.title"
        class="defense-evidence-item"
      >
        <div class="defense-evidence-item__head">
          <div>
            <div class="defense-evidence-item__title">{{ item.title }}</div>
            <p class="mt-2 defense-evidence-item__text">{{ item.instance }}</p>
          </div>
          <span class="defense-inline-tag">{{ item.tag }}</span>
        </div>
        <div class="defense-evidence-item__note">{{ item.evidence }}</div>
      </article>
    </section>

    <div v-if="behaviorActiveTab === 'overview'" class="space-y-5">
      <section class="defense-metric-strip">
        <article v-for="item in behaviorSummaryCards" :key="item.label" class="defense-metric-strip__item">
          <span class="defense-metric-strip__label">{{ item.label }}</span>
          <strong class="defense-metric-strip__value">{{ item.value }}</strong>
          <span class="defense-metric-strip__sub">{{ item.sub }}</span>
        </article>
      </section>

      <section class="defense-surface defense-surface--split behavior-overview-board">
        <div class="defense-surface__main">
          <div class="defense-surface__eyebrow">行为结构</div>
          <h3 class="defense-surface__title">主行为、承接点和搜索信号</h3>
          <p class="defense-surface__text">{{ behaviorOverviewNarrative }}</p>

          <div class="behavior-overview-facts">
            <article class="behavior-overview-facts__item">
              <div class="behavior-overview-facts__label">主行为</div>
              <div class="behavior-overview-facts__value">{{ topBehaviorText }}</div>
              <p class="behavior-overview-facts__text">count 最大。</p>
            </article>
            <article class="behavior-overview-facts__item">
              <div class="behavior-overview-facts__label">购买占比</div>
              <div class="behavior-overview-facts__value">{{ formatPercent(purchaseShare) }}</div>
              <p class="behavior-overview-facts__text">purchase / all_behavior。</p>
            </article>
            <article class="behavior-overview-facts__item">
              <div class="behavior-overview-facts__label">搜索参与度</div>
              <div class="behavior-overview-facts__value">{{ formatPercent(searchShare) }}</div>
              <p class="behavior-overview-facts__text">query 进入召回。</p>
            </article>
          </div>
        </div>

        <aside class="defense-surface__side">
          <div class="defense-surface__eyebrow">运营摘要</div>
          <h3 class="defense-surface__title">判断</h3>
          <div class="defense-rail-list">
            <article v-for="(item, index) in behaviorDefenseLines" :key="index" class="defense-rail-list__item">
              <div class="defense-rail-list__label">判断 {{ index + 1 }}</div>
              <p class="defense-rail-list__text behavior-overview-rail__text">{{ item }}</p>
            </article>
          </div>
        </aside>
      </section>

      <PageSectionTabs
        v-model="behaviorActiveTab"
        primary-label="管理端"
        page-label="行为分析"
        title="专题切换"
        description=""
        :tabs="behaviorTabs"
        :active-label="behaviorActiveTabInfo.label"
      />
    </div>

    <template v-else-if="behaviorActiveTab === 'structure'">
      <PageSectionTabs
        v-model="behaviorActiveTab"
        primary-label="管理端"
        page-label="行为分析"
        title="专题切换"
        description=""
        :tabs="behaviorTabs"
        :active-label="behaviorActiveTabInfo.label"
      />
    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <section class="defense-surface p-6">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-6">用户行为分布</h3>
        <div ref="barChartRef" class="h-80 w-full"></div>
      </section>
      <section class="defense-surface p-6">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-6">行为占比</h3>
        <div ref="pieChartRef" class="h-80 w-full"></div>
      </section>
    </div>
    </template>

    <template v-else-if="behaviorActiveTab === 'search'">
      <PageSectionTabs
        v-model="behaviorActiveTab"
        primary-label="管理端"
        page-label="行为分析"
        title="专题切换"
        description=""
        :tabs="behaviorTabs"
        :active-label="behaviorActiveTabInfo.label"
      />
    <div class="space-y-6">
      <section class="defense-surface">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">搜索总览</h3>
            <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">query、纠错、点击、购买。</p>
          </div>
          <el-tag type="warning" effect="plain" round>近 {{ searchAnalytics.days || 14 }} 天</el-tag>
        </div>
        <div class="mt-5 defense-metric-strip">
          <article v-for="item in searchSummaryCards" :key="item.label" class="defense-metric-strip__item">
            <span class="defense-metric-strip__label">{{ item.label }}</span>
            <strong class="defense-metric-strip__value">{{ item.value }}</strong>
            <span class="defense-metric-strip__sub">{{ item.sub }}</span>
          </article>
        </div>
      </section>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <section class="defense-surface p-6">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-2">搜索总次数趋势</h3>
          <p class="mb-4 text-sm text-slate-500 dark:text-slate-400">query 与 correction_hit_rate。</p>
          <div v-if="searchTrendRows.length" ref="searchTrendChartRef" class="h-80 w-full"></div>
          <el-empty v-else description="暂无搜索趋势数据" :image-size="60" />
        </section>

        <section class="defense-surface p-6">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-2">Top 搜索词</h3>
          <p class="mb-4 text-sm text-slate-500 dark:text-slate-400">Top query = 显性意图。</p>
          <div v-if="searchTopKeywords.length" ref="searchKeywordChartRef" class="h-80 w-full"></div>
          <el-empty v-else description="暂无关键词排行" :image-size="60" />
        </section>
      </div>

      <section class="defense-surface p-6">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">搜索词明细</h3>
            <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">按 total_query desc 排序。</p>
          </div>
        </div>
        <el-table
          :data="searchTopKeywords"
          class="!mt-5 !bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
          <el-table-column type="index" label="排名" width="90" />
          <el-table-column prop="keyword" label="关键词" min-width="220" />
          <el-table-column prop="totalCount" label="搜索次数" width="160">
            <template #default="{ row }">{{ formatNumber(row.totalCount) }}</template>
          </el-table-column>
        </el-table>
      </section>
    </div>
    </template>

    <template v-else>
      <PageSectionTabs
        v-model="behaviorActiveTab"
        primary-label="管理端"
        page-label="行为分析"
        title="专题切换"
        description=""
        :tabs="behaviorTabs"
        :active-label="behaviorActiveTabInfo.label"
      />
    <section class="defense-surface p-6">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">行为明细</h3>
          <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">count、user_count、share 三个口径直接对比。</p>
        </div>
        <div class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-300">
          行为类型 {{ formatNumber(sortedBehaviorStats.length) }} 类
        </div>
      </div>

      <el-table
        :data="tableRows"
        class="!mt-5 !bg-transparent"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="label" label="行为类型" min-width="180" />
        <el-table-column prop="count" label="行为次数" width="160" />
        <el-table-column prop="userCount" label="参与用户数" width="160" />
        <el-table-column prop="share" label="行为占比" width="140" />
      </el-table>
    </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useRoute, useRouter } from 'vue-router'
import { getAdminBehaviorAnalytics, getAdminSearchBehaviorAnalytics } from '../../api/admin'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'

const route = useRoute()
const router = useRouter()
const behaviorFeatureBrief = [
  { label: '判断依据', value: 'view / search / cart / purchase', text: '用 count、user_count、share 判断主行为强弱。' },
  { label: '功能组成', value: 'structure + search', text: '行为分布、query、点击承接、购买承接。' },
  { label: '输出结果', value: '主行为判断 + 转化断点', text: '高意向行为进推荐权重，低转化位置进优化清单。' },
]

const loading = ref(false)
const behaviorStats = ref([])
const barChartRef = ref(null)
const pieChartRef = ref(null)
const searchTrendChartRef = ref(null)
const searchKeywordChartRef = ref(null)
const searchAnalytics = ref({})
let charts = []
let themeObserver = null

const behaviorTypeMap = {
  view: '浏览信号',
  cart: '加购信号',
  favorite: '收藏信号',
  purchase: '购买信号',
  search: '搜索信号',
}

const heroTags = ['行为强弱', '转化动作', '搜索意图', '推荐前置信号']
const behaviorTabs = [
  { key: 'overview', label: '实例总览', hint: '结论', description: '主行为、购买占比、搜索占比。' },
  { key: 'structure', label: '结构拆解', hint: '分布', description: '各行为 count / share。' },
  { key: 'search', label: '搜索分析', hint: 'query', description: 'query、correction_hit、click、purchase。' },
  { key: 'details', label: '证据明细', hint: '明细', description: 'count、user_count、share 表。' },
]
const behaviorTabKeySet = new Set(behaviorTabs.map(item => item.key))

function normalizeBehaviorTab(value) {
  const tab = String(value || '').trim()
  return behaviorTabKeySet.has(tab) ? tab : behaviorTabs[0].key
}

const behaviorActiveTab = ref(normalizeBehaviorTab(route.query.tab))
const behaviorActiveTabInfo = computed(() => behaviorTabs.find(item => item.key === behaviorActiveTab.value) || behaviorTabs[0])

const sortedBehaviorStats = computed(() =>
  [...behaviorStats.value].sort((left, right) => Number(right.count || 0) - Number(left.count || 0))
)
const totalBehaviorCount = computed(() => sortedBehaviorStats.value.reduce((sum, item) => sum + Number(item.count || 0), 0))
const totalUserCoverage = computed(() => Math.max(0, ...sortedBehaviorStats.value.map(item => Number(item.user_count || 0))))
const topBehavior = computed(() => sortedBehaviorStats.value[0] || null)
const purchaseBehavior = computed(() => sortedBehaviorStats.value.find(item => item.behaviorType === 'purchase') || null)
const cartBehavior = computed(() => sortedBehaviorStats.value.find(item => item.behaviorType === 'cart') || null)
const searchBehavior = computed(() => sortedBehaviorStats.value.find(item => item.behaviorType === 'search') || null)
const topBehaviorText = computed(() => topBehavior.value ? `${topBehavior.value.label || topBehavior.value.behaviorLabel}` : '等待行为数据')
const purchaseShare = computed(() => totalBehaviorCount.value ? Number(purchaseBehavior.value?.count || 0) / totalBehaviorCount.value : 0)
const cartToPurchaseRate = computed(() => {
  const cart = Number(cartBehavior.value?.count || 0)
  const purchase = Number(purchaseBehavior.value?.count || 0)
  return cart ? purchase / cart : 0
})
const searchShare = computed(() => totalBehaviorCount.value ? Number(searchBehavior.value?.count || 0) / totalBehaviorCount.value : 0)
const searchQuality = computed(() => searchAnalytics.value.quality || {})
const searchConversion = computed(() => searchAnalytics.value.conversion || {})
const searchTrendRows = computed(() => {
  const rows = Array.isArray(searchAnalytics.value.trend) ? searchAnalytics.value.trend : []
  return rows.map(item => ({
    date: item.date || '',
    totalQueries: Number(item.totalQueries || 0),
    correctedHits: Number(item.correctedHits || 0),
    correctionHitRate: Number(item.correctionHitRate || 0),
  }))
})
const searchTopKeywords = computed(() => {
  const rows = Array.isArray(searchAnalytics.value.topKeywords) ? searchAnalytics.value.topKeywords : []
  return rows
    .map(item => ({
      keyword: item.keyword || '',
      totalCount: Number(item.totalCount || 0),
    }))
    .filter(item => item.keyword)
    .sort((a, b) => b.totalCount - a.totalCount)
    .slice(0, 10)
})
const searchSummaryCards = computed(() => [
  {
    label: '搜索总次数',
    value: formatNumber(searchQuality.value.totalQueries),
    sub: 'sum(query_count)',
  },
  {
    label: '搜索纠错命中率',
    value: formatPercentValue(searchQuality.value.correctionHitRate),
    sub: 'correction_hit / correction_total',
  },
  {
    label: '搜索后点击率',
    value: formatPercentValue(searchConversion.value.searchToClickRate),
    sub: `click_user ${formatNumber(searchConversion.value.searchToClickUserCount)} / search_user ${formatNumber(searchConversion.value.searchUserCount)}`,
  },
  {
    label: '搜索后购买率',
    value: formatPercentValue(searchConversion.value.searchToPurchaseRate),
    sub: `purchase_user ${formatNumber(searchConversion.value.searchToPurchaseUserCount)} / search_user ${formatNumber(searchConversion.value.searchUserCount)}`,
  },
])

const behaviorEvidenceTracks = computed(() => [
  {
    title: '主行为实例',
    tag: '主行为',
    type: 'primary',
    instance: topBehavior.value
      ? `${topBehaviorText.value} = max(count)，当前 ${formatNumber(topBehavior.value.count)}。`
      : '等待行为数据回流后识别主行为。',
    evidence: topBehavior.value
      ? `主行为判断依据：count 与 share 同时最高。`
      : '用于识别当前主行为。',
  },
  {
    title: '转化动作实例',
    tag: '转化',
    type: 'success',
    instance: cartBehavior.value && purchaseBehavior.value
      ? `cart ${formatNumber(cartBehavior.value.count)}，purchase ${formatNumber(purchaseBehavior.value.count)}。`
      : '等待加购与购买数据回流。',
    evidence: `cart_to_purchase = ${formatPercent(cartToPurchaseRate.value)}。`,
  },
  {
    title: '搜索意图实例',
    tag: searchBehavior.value ? '搜索' : '待加载',
    type: searchBehavior.value ? 'warning' : 'info',
    instance: searchBehavior.value
      ? `search ${formatNumber(searchBehavior.value.count)}，share ${formatPercent(searchShare.value)}。`
      : '当前未采集到明显搜索行为。',
    evidence: 'query 直接进入关键词召回与类目偏好。',
  },
  {
    title: '经营判断实例',
    tag: '结论',
    type: 'danger',
    instance: totalBehaviorCount.value
      ? `count ${formatNumber(totalBehaviorCount.value)}，max_user ${formatNumber(totalUserCoverage.value)}。`
      : '等待行为总量生成经营结论。',
    evidence: behaviorOverviewNarrative.value,
  },
])
const behaviorIntroRows = computed(() => [
  {
    label: '传统做法',
    value: '只看 PV、UV 或总行为量，知道流量热不热，但不知道哪类动作最能代表真实意图。',
  },
  {
    label: '解释短板',
    value: `当前最强行为是 ${topBehaviorText.value}，如果不拆开行为链路，就很难解释推荐为什么这样排。`,
  },
  {
    label: '搜索断层',
    value: '只看搜索次数无法承接关键词、类目偏好和后续转化，运营动作很容易停在表层。',
  },
])
const behaviorSolutionRows = computed(() => [
  {
    label: '多行为建模',
    value: `统一统计浏览、搜索、加购、购买等行为，总行为 ${formatNumber(totalBehaviorCount.value)} 次。`,
  },
  {
    label: '转化承接',
    value: `购买占比 ${formatPercent(purchaseShare.value)}，加购到购买 ${formatPercent(cartToPurchaseRate.value)}。`,
  },
  {
    label: '推荐联动',
    value: '把行为强弱和搜索意图直接送进推荐召回与排序，不只停留在行为报表。',
  },
])

const behaviorSummaryCards = computed(() => [
  {
    label: '总行为量',
    value: formatNumber(totalBehaviorCount.value),
    sub: 'sum(all_behavior_count)',
  },
  {
    label: '峰值参与用户',
    value: formatNumber(totalUserCoverage.value),
    sub: 'max(user_count)',
  },
  {
    label: '购买占比',
    value: formatPercent(purchaseShare.value),
    sub: 'purchase / all_behavior',
  },
  {
    label: '加购→购买',
    value: formatPercent(cartToPurchaseRate.value),
    sub: 'purchase / cart',
  },
])

const behaviorOverviewNarrative = computed(() => {
  if (!totalBehaviorCount.value) {
    return '当前无有效行为样本；先检查 view、search、cart、purchase 采集链路。'
  }
  return `主行为 = ${topBehaviorText.value}；purchase_share = ${formatPercent(purchaseShare.value)}；cart_to_purchase = ${formatPercent(cartToPurchaseRate.value)}。`
})

const behaviorDefenseLines = computed(() => [
  `行为口径 = view / search / cart / purchase。`,
  `主行为 = ${topBehaviorText.value}。`,
  `purchase_share = ${formatPercent(purchaseShare.value)}；cart_to_purchase = ${formatPercent(cartToPurchaseRate.value)}。`,
])

const tableRows = computed(() => sortedBehaviorStats.value.map(item => ({
  label: item.label || item.behaviorLabel,
  count: formatNumber(item.count),
  userCount: formatNumber(item.user_count),
  share: formatPercent(totalBehaviorCount.value ? Number(item.count || 0) / totalBehaviorCount.value : 0),
})))

function evidenceCardClass(type) {
  if (type === 'primary') return 'border-blue-200 bg-blue-50/70 dark:border-blue-800/40 dark:bg-blue-900/10'
  if (type === 'success') return 'border-emerald-200 bg-emerald-50/70 dark:border-emerald-800/40 dark:bg-emerald-900/10'
  if (type === 'warning') return 'border-amber-200 bg-amber-50/70 dark:border-amber-800/40 dark:bg-amber-900/10'
  if (type === 'danger') return 'border-rose-200 bg-rose-50/70 dark:border-rose-800/40 dark:bg-rose-900/10'
  return 'border-slate-200 bg-slate-50/80 dark:border-slate-700 dark:bg-slate-900/20'
}

function formatNumber(value) {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : '0'
}

function formatPercent(value) {
  const number = Number(value || 0)
  return `${(number * 100).toFixed(number * 100 >= 10 ? 1 : 2)}%`
}

function formatPercentValue(value) {
  const number = Number(value || 0)
  return `${number.toFixed(number >= 10 ? 1 : 2)}%`
}

const getBehaviorType = item => item.behaviorType || item.behavior_type || ''
const getBehaviorCount = item => Number(item.count || 0)

async function fetchData() {
  loading.value = true
  try {
    const [behaviorRes, searchRes] = await Promise.all([
      getAdminBehaviorAnalytics(),
      getAdminSearchBehaviorAnalytics(14).catch(() => null),
    ])

    behaviorStats.value = (behaviorRes || []).map(item => {
      const behaviorType = getBehaviorType(item)
      return {
        behaviorType,
        behaviorLabel: behaviorTypeMap[behaviorType] || behaviorType || '未知行为',
        label: behaviorTypeMap[behaviorType] || behaviorType || '未知行为',
        count: getBehaviorCount(item),
        user_count: Number(item.user_count || item.userCount || 0),
      }
    })

    searchAnalytics.value = searchRes || {
      quality: {},
      trend: [],
      topKeywords: [],
      conversion: {},
    }

    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  charts.forEach(chart => chart?.dispose())
  charts = []

  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#9ca3af' : '#6b7280'
  const splitLineColor = isDark ? '#374151' : '#e5e7eb'
  const colors = ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981']

  const labels = sortedBehaviorStats.value.map(item => item.label)
  const values = sortedBehaviorStats.value.map(item => Number(item.count || 0))

  if (barChartRef.value && sortedBehaviorStats.value.length) {
    const chart = echarts.init(barChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '4%', right: '4%', bottom: 56, top: 24, containLabel: true },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { color: textColor, interval: 0, margin: 14 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
        axisLabel: { color: textColor },
      },
      series: [{
        type: 'bar',
        barMaxWidth: 40,
        itemStyle: { borderRadius: [6, 6, 0, 0], color: params => colors[params.dataIndex % colors.length] },
        data: values,
      }],
    })
    charts.push(chart)
  }

  if (pieChartRef.value && sortedBehaviorStats.value.length) {
    const chart = echarts.init(pieChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 8, type: 'scroll', textStyle: { color: textColor } },
      series: [{
        type: 'pie',
        radius: ['38%', '66%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 10, borderColor: isDark ? '#1f2937' : '#fff', borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold', color: textColor } },
        data: sortedBehaviorStats.value.map((item, index) => ({
          value: Number(item.count || 0),
          name: item.label,
          itemStyle: { color: colors[index % colors.length] },
        })),
      }],
    })
    charts.push(chart)
  }

  if (searchTrendChartRef.value && searchTrendRows.value.length) {
    const trendChart = echarts.init(searchTrendChartRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '4%', right: '4%', bottom: 56, top: 24, containLabel: true },
      legend: { top: 0, textStyle: { color: textColor }, data: ['搜索总次数', '纠错命中率'] },
      xAxis: {
        type: 'category',
        data: searchTrendRows.value.map(item => item.date),
        axisLabel: { color: textColor },
      },
      yAxis: [
        {
          type: 'value',
          name: '次数',
          axisLabel: { color: textColor },
          splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
        },
        {
          type: 'value',
          name: '命中率',
          max: 100,
          axisLabel: { color: textColor, formatter: value => `${value}%` },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: '搜索总次数',
          type: 'bar',
          barMaxWidth: 28,
          itemStyle: { borderRadius: [8, 8, 0, 0], color: '#3b82f6' },
          data: searchTrendRows.value.map(item => item.totalQueries),
        },
        {
          name: '纠错命中率',
          type: 'line',
          yAxisIndex: 1,
          smooth: true,
          showSymbol: false,
          lineStyle: { width: 3, color: '#f59e0b' },
          data: searchTrendRows.value.map(item => item.correctionHitRate),
        },
      ],
    })
    charts.push(trendChart)
  }

  if (searchKeywordChartRef.value && searchTopKeywords.value.length) {
    const keywordChart = echarts.init(searchKeywordChartRef.value)
    const keywordRows = [...searchTopKeywords.value].slice(0, 8).reverse()
    keywordChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '4%', right: '4%', bottom: 24, top: 24, containLabel: true },
      xAxis: {
        type: 'value',
        axisLabel: { color: textColor },
        splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
      },
      yAxis: {
        type: 'category',
        data: keywordRows.map(item => item.keyword),
        axisLabel: { color: textColor },
      },
      series: [{
        type: 'bar',
        barMaxWidth: 22,
        itemStyle: { borderRadius: 8, color: '#0ea5e9' },
        data: keywordRows.map(item => item.totalCount),
      }],
    })
    charts.push(keywordChart)
  }
}

function handleResize() {
  charts.forEach(chart => chart?.resize())
}

watch(
  () => route.query.tab,
  value => {
    const nextTab = normalizeBehaviorTab(value)
    if (nextTab !== behaviorActiveTab.value) {
      behaviorActiveTab.value = nextTab
    }
  }
)

watch(behaviorActiveTab, value => {
  const nextTab = normalizeBehaviorTab(value)
  const currentTab = normalizeBehaviorTab(route.query.tab)
  if (nextTab !== currentTab) {
    router.replace({
      query: {
        ...route.query,
        tab: nextTab === behaviorTabs[0].key ? undefined : nextTab,
      },
    })
  }
  nextTick(() => renderCharts())
})

onMounted(async () => {
  await fetchData()
  window.addEventListener('resize', handleResize)
  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(item => item.attributeName === 'class')) {
      nextTick(() => renderCharts())
    }
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  charts.forEach(chart => chart?.dispose())
})
</script>

<style scoped>
.behavior-overview-board {
  grid-template-columns: minmax(0, 1.14fr) minmax(300px, 0.86fr);
}

.behavior-overview-facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
}

.behavior-overview-facts__item {
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.behavior-overview-facts__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #64748b;
}

.behavior-overview-facts__value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.2;
  color: #0f172a;
}

.behavior-overview-facts__text,
.behavior-overview-rail__text {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.dark .behavior-overview-facts__item {
  border-top-color: rgba(71, 85, 105, 0.48);
}

.dark .behavior-overview-facts__label {
  color: #94a3b8;
}

.dark .behavior-overview-facts__value {
  color: #f8fafc;
}

.dark .behavior-overview-facts__text,
.dark .behavior-overview-rail__text {
  color: #cbd5e1;
}

@media (max-width: 768px) {
  .behavior-overview-facts {
    grid-template-columns: 1fr;
  }
}
</style>
