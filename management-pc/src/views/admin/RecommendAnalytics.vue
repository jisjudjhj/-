<template>
  <div v-loading="loading" class="analytics-ui-page defense-page recommend-compact-page space-y-6">
    <section class="recommend-page-header">
      <div>
        <h1 class="recommend-page-header__title">推荐效果分析</h1>
      </div>
      <div class="recommend-page-header__controls">
        <div class="recommend-page-header__label">数据周期</div>
        <el-radio-group v-model="selectedDays" size="large" @change="loadData">
          <el-radio-button v-for="item in dayOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="recommend-page-header__meta">
          {{ dateRangeText }} · 用户 {{ formatNumber(summary.userCount) }} · 商品 {{ formatNumber(totalProducts) }}
        </div>
      </div>
    </section>

    <FeatureBrief
      kicker="推荐归因"
      title="指标判断与组成"
      :items="recommendAnalyticsBrief"
    />

    <template v-if="activeTab === 'overview'">
      <section class="defense-metric-strip">
        <article v-for="item in overviewCards" :key="item.title" class="defense-metric-strip__item">
          <span class="defense-metric-strip__label">{{ item.title }}</span>
          <strong class="defense-metric-strip__value">{{ item.value }}</strong>
          <span class="defense-metric-strip__sub">{{ item.detail }}</span>
        </article>
      </section>

      <section class="defense-surface defense-surface--split recommend-overview-board">
        <div class="defense-surface__main">
          <div class="defense-surface__eyebrow">推荐归因</div>
          <h3 class="defense-surface__title">曝光 · 点击 · 成交</h3>
          <div v-if="dailyTrend.length" ref="trendChartRef" class="mt-6 h-[360px] w-full"></div>
          <el-empty v-else description="暂无趋势" />
        </div>

        <aside class="defense-surface__side">
          <div class="defense-surface__eyebrow">对比结论</div>
          <h3 class="defense-surface__title">最优策略</h3>
          <div class="defense-rail-list">
            <article v-for="item in comparisonHighlights" :key="item.dimension" class="defense-rail-list__item">
              <div class="defense-rail-list__label">{{ item.title }}</div>
              <div class="defense-rail-list__title">{{ item.winnerLabel || '等待结果' }}</div>
              <p class="recommend-overview-board__meta">
                基线 {{ item.baselineLabel || '--' }} · 转化 {{ formatLift(item.conversionLiftRatio) }} · 成功率 {{ formatLift(item.successLiftRatio) }}
              </p>
            </article>
          </div>
        </aside>
      </section>

      <PageSectionTabs
        v-model="activeTab"
        :tabs="tabs"
        primary-label="管理端"
        page-label="推荐分析"
        title="专题切换"
        description=""
        :active-label="activeTabInfo.label"
      />
    </template>

    <template v-else-if="activeTab === 'compare'">
      <PageSectionTabs
        v-model="activeTab"
        :tabs="tabs"
        primary-label="管理端"
        page-label="推荐分析"
        title="专题切换"
        description=""
        :active-label="activeTabInfo.label"
      />
      <section class="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">不同信号源的效果对比</div>
          <div v-if="sceneMetrics.length" ref="sceneChartRef" class="mt-5 h-[340px] w-full"></div>
          <el-empty v-else description="暂无信号源效果数据" />
        </article>

        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">不同算法的效果对比</div>
          <div v-if="algorithmMetrics.length" ref="algorithmChartRef" class="mt-5 h-[340px] w-full"></div>
          <el-empty v-else description="暂无算法效果数据" />
        </article>
      </section>

      <section class="grid grid-cols-1 gap-6 xl:grid-cols-[1.1fr_0.9fr]">
        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">各信号源最佳算法</div>
          <el-table :data="sceneAlgorithmLeaders" size="small" stripe class="mt-4">
            <el-table-column prop="sceneLabel" label="信号源" min-width="120" />
            <el-table-column prop="algorithmLabel" label="最佳算法" min-width="160" />
            <el-table-column label="场景占比" width="110" align="right">
              <template #default="{ row }">{{ formatPercent(row.shareInsideScene) }}</template>
            </el-table-column>
            <el-table-column label="转化率" width="110" align="right">
              <template #default="{ row }">{{ formatPercent(row.conversionRate) }}</template>
            </el-table-column>
          </el-table>
        </article>

        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">最佳算法 × 人群组合</div>
          <div class="recommend-inline-panel mt-4 p-5">
            <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {{ bestAlgorithmSegment.available ? `${bestAlgorithmSegment.segmentLabel} × ${bestAlgorithmSegment.algorithmLabel}` : '等待结果' }}
            </div>
            <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ bestAlgorithmSegment.summary || '暂无组合数据。' }}</p>
            <div v-if="bestAlgorithmSegment.available" class="mt-4 flex flex-wrap gap-2">
              <span class="recommend-chip">转化 {{ formatPercent(bestAlgorithmSegment.conversionRate) }}</span>
              <span class="recommend-chip">成功率 {{ formatPercent(bestAlgorithmSegment.successRate) }}</span>
              <span class="recommend-chip">曝光 {{ formatNumber(bestAlgorithmSegment.exposureCount) }}</span>
            </div>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeTab === 'optimize'">
      <PageSectionTabs
        v-model="activeTab"
        :tabs="tabs"
        primary-label="管理端"
        page-label="推荐分析"
        title="专题切换"
        description=""
        :active-label="activeTabInfo.label"
      />
      <section class="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <article v-for="item in optimizationStages" :key="item.key" class="defense-surface recommend-panel p-6">
          <div class="flex items-center justify-between gap-3">
            <div class="text-lg font-semibold text-slate-950 dark:text-slate-50">{{ item.title }}</div>
            <span class="recommend-inline-badge">{{ item.focus }}</span>
          </div>
          <div class="mt-3 text-sm font-medium text-slate-700 dark:text-slate-200">{{ item.techStack }}</div>
          <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ item.evidence }}</p>
          <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">{{ item.result }}</p>
          <div class="mt-4 border-t border-slate-200 pt-4 text-sm leading-6 text-slate-500 dark:border-slate-700 dark:text-slate-400">
            {{ item.note }}
          </div>
        </article>
      </section>

      <section class="grid grid-cols-1 gap-5 xl:grid-cols-4">
        <article v-for="item in diagnosticCards" :key="item.title" class="defense-surface recommend-panel p-5">
          <div class="text-xs uppercase tracking-[0.18em] text-slate-400">{{ item.title }}</div>
          <div class="mt-3 text-2xl font-semibold text-slate-950 dark:text-slate-50">{{ item.value }}</div>
          <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ item.summary }}</p>
          <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">{{ item.detail }}</p>
        </article>
      </section>
    </template>

    <template v-else-if="activeTab === 'sample'">
      <PageSectionTabs
        v-model="activeTab"
        :tabs="tabs"
        primary-label="管理端"
        page-label="推荐分析"
        title="专题切换"
        description=""
        :active-label="activeTabInfo.label"
      />
      <section class="defense-surface recommend-panel p-6">
        <div class="grid grid-cols-1 gap-6 xl:grid-cols-[1.02fr_0.98fr]">
          <div>
            <div class="recommend-section-title">同一用户的多算法样本</div>
            <div class="mt-4 flex flex-wrap items-center gap-2">
              <el-input-number v-model="proofUserId" :min="1" controls-position="right" class="!w-40" />
              <el-button type="primary" :loading="proofLoading" @click="loadProofCase">更新样本</el-button>
              <el-button plain @click="goToPreviewWithUser">进入推荐预览</el-button>
            </div>
            <div class="mt-3 flex flex-wrap gap-2">
              <button
                v-for="id in sampleProofUsers"
                :key="id"
                type="button"
                class="proof-user-chip"
                :class="{ 'is-active': id === proofUserId }"
                @click="applyProofUser(id)"
              >
                用户 {{ id }}
              </button>
            </div>
            <div class="recommend-inline-panel mt-4 p-5">
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">
                用户 {{ proofUserId }} / {{ proofSegmentName }} / {{ proofExperimentGroup }}
              </div>
              <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
                <li v-for="line in proofInsightLines" :key="line">{{ line }}</li>
              </ul>
            </div>
            <el-alert v-if="proofError" class="mt-4" type="warning" show-icon :closable="false" :title="proofError" />
          </div>

          <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
            <article v-for="item in proofAlgorithms" :key="item.key" class="recommend-proof-card">
              <div class="flex items-start justify-between gap-3">
                <div>
                  <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item.label }}</div>
                  <p class="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ item.hint }}</p>
                </div>
                <span class="recommend-inline-badge">{{ item.items.length }} 项</span>
              </div>

              <div class="mt-4 space-y-3">
                <article v-for="product in item.items.slice(0, 3)" :key="`${item.key}-${product.id}`" class="recommend-proof-item">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0 flex-1">
                      <div class="truncate text-sm font-medium text-slate-900 dark:text-slate-100">{{ product.name }}</div>
                      <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">¥{{ product.price }} · 销量 {{ product.sales }}</div>
                    </div>
                    <span class="text-xs text-slate-400">#{{ product.rank }}</span>
                  </div>
                  <div class="mt-2 flex flex-wrap gap-2">
                    <span v-for="label in proofSignalLabels(product.id)" :key="`${product.id}-${label}`" class="recommend-chip">
                      {{ label }}
                    </span>
                  </div>
                </article>
                <el-empty v-if="!item.items.length" description="暂无样本" :image-size="42" />
              </div>
            </article>
          </div>
        </div>
      </section>
    </template>

    <template v-else>
      <PageSectionTabs
        v-model="activeTab"
        :tabs="tabs"
        primary-label="管理端"
        page-label="推荐分析"
        title="专题切换"
        description=""
        :active-label="activeTabInfo.label"
      />
      <section class="grid grid-cols-1 gap-6 xl:grid-cols-[1.08fr_0.92fr]">
        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">结果解读</div>
          <ol class="mt-4 space-y-3">
            <li v-for="(line, index) in defenseNarrative" :key="`${index}-${line}`" class="recommend-defense-row">
              <span class="recommend-order">{{ index + 1 }}</span>
              <p class="text-sm leading-6 text-slate-600 dark:text-slate-300">{{ line }}</p>
            </li>
          </ol>
        </article>

        <article class="defense-surface recommend-panel p-6">
          <div class="recommend-section-title">核心指标口径</div>
          <div class="mt-4 space-y-3">
            <article v-for="item in formulaRows" :key="item.title" class="recommend-formula-card">
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item.title }}</div>
              <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">{{ item.description }}</p>
              <div class="mt-3 rounded-2xl bg-slate-950 px-4 py-3 font-mono text-xs text-cyan-50">
                {{ item.expression }}
              </div>
            </article>
          </div>
        </article>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAdminRecommendAnalytics } from '../../api/admin'
import { getRecommendCompare, getRecommendRealtime } from '../../api/recommend'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'
import { getAnalyticsChartTheme } from '../../utils/chartTheme'

const route = useRoute()
const router = useRouter()

const dayOptions = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

const tabs = [
  { key: 'overview', label: '归因总览', hint: '结果', description: '' },
  { key: 'compare', label: '策略对比', hint: '差异', description: '' },
  { key: 'optimize', label: '优化分层', hint: '实现', description: '' },
  { key: 'sample', label: '样本验证', hint: '样本', description: '' },
  { key: 'defense', label: '运营结论', hint: '结论', description: '' },
]

const tabKeySet = new Set(tabs.map(item => item.key))
const normalizeTab = value => {
  const next = String(value || '').trim()
  return tabKeySet.has(next) ? next : tabs[0].key
}

const sceneLabelMap = {
  personal: '主信号源',
  guess_you_like: '推荐信号源',
  hot: '热度信号源',
  similar: '内容信号源',
  collaborative_filtering: '协同信号源',
  search_keyword: '检索词信号源',
  search_category: '检索类目信号源',
  search_personalized: '检索补位信号源',
  search_hot: '检索热度信号源',
  order_completed: '转化后信号源',
}

const proofAlgorithmMeta = [
  { key: 'hybrid', label: 'Hybrid 混合推荐', hint: '综合多路召回和排序结果' },
  { key: 'cf', label: 'CF 协同过滤', hint: '由相似用户行为驱动' },
  { key: 'cb', label: 'CB 内容推荐', hint: '由品类与标签偏好驱动' },
  { key: 'hot', label: '热门候选', hint: '适合作为基线和冷启动候选池' },
]

const sampleProofUsers = [1, 7, 12, 25]

const loading = ref(false)
const selectedDays = ref(30)
const recommendAnalyticsBrief = [
  { label: '判断依据', value: 'Exposure / Click / Cart / Purchase', text: '同 token 归因。' },
  { label: '功能组成', value: 'Signal / Algorithm / Segment', text: '三维对比。' },
  { label: '输出结果', value: 'CTR / CVR / Lift', text: '输出最优策略。' },
]
const dashboard = ref({})
const activeTab = ref(normalizeTab(route.query.tab))
const proofUserId = ref(1)
const proofLoading = ref(false)
const proofError = ref('')
const proofCompare = ref({})
const proofRealtime = ref({})

const trendChartRef = ref(null)
const sceneChartRef = ref(null)
const algorithmChartRef = ref(null)

let charts = []
let echartsModule = null
let themeObserver = null

const performance = computed(() => dashboard.value?.performance || {})
const summary = computed(() => normalizeMetric(performance.value?.summary || {}))
const sceneMetrics = computed(() => normalizeMetricList(performance.value?.sceneMetrics).map(item => ({ ...item, label: formatSceneLabel(item.scene) })))
const algorithmMetrics = computed(() => normalizeMetricList(performance.value?.algorithmMetrics).map(item => ({ ...item, label: formatAlgorithmLabel(item.algorithm) })))
const dailyTrend = computed(() => normalizeMetricList(performance.value?.dailyTrend))
const comparisonHighlights = computed(() => Array.isArray(performance.value?.comparisonHighlights) ? performance.value.comparisonHighlights : [])
const optimizationStages = computed(() => Array.isArray(performance.value?.optimizationStages) ? performance.value.optimizationStages : [])
const diagnosticCards = computed(() => Array.isArray(performance.value?.diagnosticCards) ? performance.value.diagnosticCards : [])
const sceneAlgorithmLeaders = computed(() =>
  Array.isArray(performance.value?.sceneAlgorithmLeaders)
    ? performance.value.sceneAlgorithmLeaders.map(item => ({
        ...item,
        sceneLabel: formatSceneLabel(item.scene || item.sceneLabel),
      }))
    : []
)
const bestAlgorithmSegment = computed(() => performance.value?.bestAlgorithmSegment || {})
const distributionQuality = computed(() => performance.value?.distributionQuality || {})
const defenseNarrative = computed(() => Array.isArray(performance.value?.defenseNarrative) ? performance.value.defenseNarrative : [])
const totalProducts = computed(() => toNumber(dashboard.value?.totalProducts))
const totalUsers = computed(() => toNumber(dashboard.value?.totalUsers))
const activeDays = computed(() => toNumber(performance.value?.days) || selectedDays.value)
const coverageRate = computed(() => totalUsers.value > 0 ? (summary.value.userCount / totalUsers.value) * 100 : 0)
const sevenDayRepurchase = computed(() => ({
  attributedPurchaseUsers: toNumber(performance.value?.sevenDayRepurchase?.attributedPurchaseUsers),
  repurchaseUsers: toNumber(performance.value?.sevenDayRepurchase?.repurchaseUsers),
  sevenDayRepurchaseRate: toNumber(performance.value?.sevenDayRepurchase?.sevenDayRepurchaseRate),
}))
const dateRangeText = computed(() => `${performance.value?.startDate || '--'} 至 ${performance.value?.endDate || '--'}`)
const activeTabInfo = computed(() => tabs.find(item => item.key === activeTab.value) || tabs[0])

const heroCards = computed(() => {
  const algorithmHighlight = comparisonHighlights.value.find(item => item.dimension === 'algorithm') || {}
  return [
    { eyebrow: '覆盖', title: '有效曝光', value: formatNumber(summary.value.exposureCount), detail: `覆盖率 ${formatPercent(coverageRate.value)}` },
    { eyebrow: '策略', title: algorithmHighlight.winnerLabel || '算法对比', value: formatLift(algorithmHighlight.conversionLiftRatio), detail: '相对基线' },
    { eyebrow: '复购', title: '7 日复购', value: formatPercent(sevenDayRepurchase.value.sevenDayRepurchaseRate), detail: `${formatNumber(sevenDayRepurchase.value.repurchaseUsers)} / ${formatNumber(sevenDayRepurchase.value.attributedPurchaseUsers)}` },
    { eyebrow: '治理', title: '流量健康度', value: formatPercent(distributionQuality.value.qualityScore), detail: distributionQuality.value.insight || '分布健康' },
  ]
})

const overviewCards = computed(() => [
  { eyebrow: '点击', title: '点击率', value: formatPercent(summary.value.clickThroughRate), detail: `${formatNumber(summary.value.clickCount)} 次` },
  { eyebrow: '成交', title: '成交转化率', value: formatPercent(summary.value.conversionRate), detail: `${formatNumber(summary.value.purchaseCount)} 次` },
  { eyebrow: '成功', title: '推荐成功率', value: formatPercent(summary.value.recommendationSuccessRate), detail: '有效互动' },
  { eyebrow: '转化', title: '点击后成交', value: formatPercent(summary.value.postClickConversionRate), detail: '点击后购买' },
])

const proofSegmentName = computed(() => proofRealtime.value?.segment?.segmentName || '待识别人群')
const proofExperimentGroup = computed(() => proofCompare.value?.experimentGroup || '默认策略')
const proofAlgorithms = computed(() => proofAlgorithmMeta.map(item => ({ ...item, items: normalizeProofProducts(proofCompare.value?.[item.key]) })))
const proofIdSets = computed(() => {
  const bucket = {}
  proofAlgorithms.value.forEach(item => { bucket[item.key] = new Set(item.items.map(product => String(product.id))) })
  return bucket
})
const proofInsightLines = computed(() => {
  const hybrid = proofAlgorithms.value.find(item => item.key === 'hybrid')?.items || []
  const hot = proofAlgorithms.value.find(item => item.key === 'hot')?.items || []
  const cf = proofAlgorithms.value.find(item => item.key === 'cf')?.items || []
  const cb = proofAlgorithms.value.find(item => item.key === 'cb')?.items || []
  const hybridSet = new Set(hybrid.map(item => String(item.id)))
  const hotSet = new Set(hot.map(item => String(item.id)))
  const overlap = [...hybridSet].filter(id => hotSet.has(id)).length
  const cfOnly = cf.filter(item => !hybridSet.has(String(item.id))).slice(0, 2).map(item => item.name)
  const cbOnly = cb.filter(item => !hybridSet.has(String(item.id))).slice(0, 2).map(item => item.name)
  return [
    `实验组 = ${proofExperimentGroup.value}；分群 = ${proofSegmentName.value}。`,
    `Hybrid∩Hot = ${overlap} 项。`,
    cfOnly.length ? `CF-only: ${cfOnly.join('、')}。` : 'CF-only: 0。',
    cbOnly.length ? `CB-only: ${cbOnly.join('、')}。` : 'CB-only: 0。',
  ]
})

const formulaRows = computed(() => {
  const formula = performance.value?.formula || {}
  return [
    { title: '成交转化率', description: 'purchase / exposure', expression: String(formula.conversionRate || 'purchaseCount / exposureCount * 100') },
    { title: '推荐成功率', description: 'success / exposure', expression: String(formula.recommendationSuccessRate || 'successCount / exposureCount * 100') },
    { title: '点击率', description: 'click / exposure', expression: String(formula.clickThroughRate || 'clickCount / exposureCount * 100') },
    { title: '加购率', description: 'cart / exposure', expression: String(formula.addToCartRate || 'cartCount / exposureCount * 100') },
    { title: '点击后转化率', description: 'purchase / click', expression: String(formula.postClickConversionRate || 'purchaseCount / clickCount * 100') },
    { title: '7日复购率', description: 'repurchase / attributedPurchaseUsers', expression: String(formula.sevenDayRepurchaseRate || 'repurchaseUsers / attributedPurchaseUsers * 100') },
  ]
})

function toNumber(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function formatNumber(value) {
  return toNumber(value).toLocaleString('zh-CN')
}

function formatPercent(value) {
  return `${toNumber(value).toFixed(2)}%`
}

function formatLift(value) {
  if (value === null || value === undefined || !Number.isFinite(Number(value))) return '--'
  const result = Number(value) * 100
  return `${result >= 0 ? '+' : ''}${result.toFixed(2)}%`
}

function normalizeMetric(metric = {}) {
  return {
    exposureCount: toNumber(metric.exposureCount),
    userCount: toNumber(metric.userCount),
    clickCount: toNumber(metric.clickCount),
    favoriteCount: toNumber(metric.favoriteCount),
    cartCount: toNumber(metric.cartCount),
    purchaseCount: toNumber(metric.purchaseCount),
    successCount: toNumber(metric.successCount),
    clickThroughRate: toNumber(metric.clickThroughRate),
    favoriteRate: toNumber(metric.favoriteRate),
    addToCartRate: toNumber(metric.addToCartRate),
    conversionRate: toNumber(metric.conversionRate),
    recommendationSuccessRate: toNumber(metric.recommendationSuccessRate),
    postClickConversionRate: toNumber(metric.postClickConversionRate),
    scene: metric.scene || '',
    algorithm: metric.algorithm || '',
    label: metric.label || '',
    statDate: metric.statDate || '',
  }
}

const normalizeMetricList = metrics => (Array.isArray(metrics) ? metrics.map(item => normalizeMetric(item)) : [])
const formatSceneLabel = scene => sceneLabelMap[scene] || scene || '未知场景'

function formatAlgorithmLabel(algorithm) {
  const value = String(algorithm || '').trim()
  const normalized = value.toLowerCase()
  if (!value) return '未知算法'
  if (normalized.includes('hybrid')) return 'Hybrid 混合推荐'
  if (normalized === 'cf' || normalized.includes('_cf') || normalized.includes('collaborative')) return 'CF 协同过滤'
  if (normalized === 'cb' || normalized.includes('content')) return 'CB 内容推荐'
  if (normalized.includes('hot') || normalized.includes('snapshot')) return '热门候选'
  if (normalized.includes('control')) return '对照策略'
  return value
}

function normalizeProofProducts(list) {
  if (!Array.isArray(list)) return []
  return list.map((item, index) => ({
    id: item?.id ?? item?.productId ?? index,
    rank: index + 1,
    name: item?.name || item?.productName || `商品 #${index + 1}`,
    price: toNumber(item?.price).toFixed(2),
    sales: formatNumber(item?.salesCount || item?.sales || 0),
  }))
}

function proofSignalLabels(productId) {
  const id = String(productId)
  const labels = []
  if (proofIdSets.value.hybrid?.has(id)) labels.push('Hybrid')
  if (proofIdSets.value.cf?.has(id)) labels.push('CF')
  if (proofIdSets.value.cb?.has(id)) labels.push('CB')
  if (proofIdSets.value.hot?.has(id)) labels.push('Hot')
  return labels
}

function applyProofUser(id) {
  if (!id || id === proofUserId.value) return
  proofUserId.value = id
  loadProofCase()
}

function goToPreviewWithUser() {
  router.push({ path: '/admin/recommend/preview', query: { userId: String(proofUserId.value || 1) } })
}

async function loadProofCase() {
  if (!proofUserId.value || Number(proofUserId.value) < 1) {
    proofError.value = '请输入有效的用户 ID'
    return
  }
  proofLoading.value = true
  proofError.value = ''
  try {
    const [compareResult, realtimeResult] = await Promise.allSettled([
      getRecommendCompare(proofUserId.value, 8),
      getRecommendRealtime(proofUserId.value, 8),
    ])
    if (compareResult.status === 'fulfilled') {
      proofCompare.value = compareResult.value || {}
    } else {
      proofCompare.value = {}
      proofError.value = '推荐对比样本拉取失败，请检查推荐服务是否可用。'
    }
    proofRealtime.value = realtimeResult.status === 'fulfilled' ? (realtimeResult.value || {}) : {}
  } finally {
    proofLoading.value = false
  }
}

function rateAxisMax(values) {
  const max = Math.max(0, ...values.map(item => toNumber(item)))
  return max > 0 ? Math.max(10, Math.ceil(max / 10) * 10) : 10
}

async function getEcharts() {
  if (!echartsModule) {
    echartsModule = await import('echarts')
  }
  return echartsModule
}

function disposeCharts() {
  charts.forEach(chart => chart?.dispose())
  charts = []
}

function handleResize() {
  charts.forEach(chart => chart?.resize())
}

function renderTrendChart(theme, echarts) {
  if (!trendChartRef.value || !dailyTrend.value.length) return
  const chart = echarts.init(trendChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: theme.textColor }, data: ['曝光', '点击', '成交', '成交转化率'] },
    grid: { left: '4%', right: '4%', top: 52, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      data: dailyTrend.value.map(item => item.statDate || '--'),
      axisLabel: { color: theme.textColor },
      axisLine: { lineStyle: { color: theme.axisColor } },
    },
    yAxis: [
      { type: 'value', name: '次数', axisLabel: { color: theme.textColor }, splitLine: { lineStyle: { color: theme.splitLineColor, type: 'dashed' } } },
      { type: 'value', name: '比率', max: rateAxisMax(dailyTrend.value.map(item => item.conversionRate)), axisLabel: { color: theme.textColor, formatter: value => `${value}%` }, splitLine: { show: false } },
    ],
    series: [
      { name: '曝光', type: 'bar', barMaxWidth: 24, itemStyle: { borderRadius: [10, 10, 0, 0], color: '#94a3b8' }, data: dailyTrend.value.map(item => item.exposureCount) },
      { name: '点击', type: 'line', smooth: true, showSymbol: false, lineStyle: { width: 3, color: '#0f766e' }, data: dailyTrend.value.map(item => item.clickCount) },
      { name: '成交', type: 'line', smooth: true, showSymbol: false, lineStyle: { width: 3, color: '#1d4ed8' }, data: dailyTrend.value.map(item => item.purchaseCount) },
      { name: '成交转化率', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, lineStyle: { width: 3, color: '#0f172a' }, data: dailyTrend.value.map(item => item.conversionRate) },
    ],
  })
  charts.push(chart)
}

function renderCompareChart(el, metrics, theme) {
  if (!el || !metrics.length || !echartsModule) return
  const chart = echartsModule.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: theme.textColor }, data: ['曝光', '成交转化率', '推荐成功率'] },
    grid: { left: '4%', right: '4%', top: 52, bottom: 72, containLabel: true },
    xAxis: {
      type: 'category',
      data: metrics.map(item => item.label),
      axisLabel: { color: theme.textColor, interval: 0, rotate: metrics.some(item => String(item.label || '').length > 6) ? 18 : 0 },
      axisLine: { lineStyle: { color: theme.axisColor } },
    },
    yAxis: [
      { type: 'value', name: '曝光', axisLabel: { color: theme.textColor }, splitLine: { lineStyle: { color: theme.splitLineColor, type: 'dashed' } } },
      { type: 'value', name: '比率', max: rateAxisMax(metrics.flatMap(item => [item.conversionRate, item.recommendationSuccessRate])), axisLabel: { color: theme.textColor, formatter: value => `${value}%` }, splitLine: { show: false } },
    ],
    series: [
      { name: '曝光', type: 'bar', barMaxWidth: 28, itemStyle: { borderRadius: [10, 10, 0, 0], color: '#cbd5e1' }, data: metrics.map(item => item.exposureCount) },
      { name: '成交转化率', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, lineStyle: { width: 3, color: '#1d4ed8' }, data: metrics.map(item => item.conversionRate) },
      { name: '推荐成功率', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, lineStyle: { width: 3, color: '#0f766e' }, data: metrics.map(item => item.recommendationSuccessRate) },
    ],
  })
  charts.push(chart)
}

async function renderCharts() {
  disposeCharts()
  const theme = getAnalyticsChartTheme()
  const echarts = await getEcharts()
  renderTrendChart(theme, echarts)
  renderCompareChart(sceneChartRef.value, sceneMetrics.value, theme)
  renderCompareChart(algorithmChartRef.value, algorithmMetrics.value, theme)
}

async function loadData() {
  loading.value = true
  try {
    const [dashboardResult] = await Promise.all([
      getAdminRecommendAnalytics(selectedDays.value),
      loadProofCase(),
    ])
    dashboard.value = dashboardResult || {}
    await nextTick()
    await renderCharts()
  } finally {
    loading.value = false
  }
}

watch(() => route.query.tab, value => {
  const next = normalizeTab(value)
  if (next !== activeTab.value) activeTab.value = next
})

watch(activeTab, value => {
  const next = normalizeTab(value)
  const current = normalizeTab(route.query.tab)
  if (next !== current) {
    router.replace({ query: { ...route.query, tab: next === tabs[0].key ? undefined : next } })
  }
  nextTick(() => {
    renderCharts()
  })
})

onMounted(async () => {
  await loadData()
  window.addEventListener('resize', handleResize)
  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(item => item.attributeName === 'class')) {
      nextTick(() => {
        renderCharts()
      })
    }
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  disposeCharts()
})
</script>

<style scoped>
.recommend-compact-page {
  --recommend-border: rgba(148, 163, 184, 0.22);
}

.recommend-page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 2px 0 6px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.recommend-page-header__title {
  font-size: clamp(30px, 3vw, 42px);
  line-height: 1.06;
  font-weight: 900;
  color: #020617;
}

.recommend-page-header__controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.recommend-page-header__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #0f766e;
}

.recommend-page-header__meta {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
  text-align: right;
}

.recommend-compact-hero,
.recommend-summary-card,
.recommend-side-card,
.recommend-highlight-card,
.recommend-proof-card,
.recommend-proof-item,
.recommend-formula-card {
  border: 1px solid var(--recommend-border);
  background: rgba(255, 255, 255, 0.96);
}

.dark .recommend-compact-hero,
.dark .recommend-summary-card,
.dark .recommend-side-card,
.dark .recommend-highlight-card,
.dark .recommend-proof-card,
.dark .recommend-proof-item,
.dark .recommend-formula-card {
  border-color: rgba(71, 85, 105, 0.58);
  background: rgba(15, 23, 42, 0.52);
}

.recommend-compact-hero {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
}

.recommend-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #0f766e;
}

.dark .recommend-kicker {
  color: #67e8f9;
}

.recommend-chip,
.recommend-inline-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.86);
  padding: 7px 12px;
  font-size: 12px;
  color: #475569;
}

.dark .recommend-chip,
.dark .recommend-inline-badge {
  border-color: rgba(100, 116, 139, 0.5);
  background: rgba(15, 23, 42, 0.46);
  color: #cbd5e1;
}

.recommend-side-card,
.recommend-summary-card,
.recommend-highlight-card,
.recommend-proof-card,
.recommend-formula-card {
  border-radius: 20px;
  padding: 18px;
}

.recommend-overview-board {
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
}

.recommend-overview-board__meta {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.8;
  color: #64748b;
}

.recommend-section-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.dark .recommend-section-title {
  color: #f8fafc;
}

.dark .recommend-overview-board__meta {
  color: #94a3b8;
}

.recommend-highlight-card,
.recommend-proof-item,
.recommend-formula-card {
  background: rgba(248, 250, 252, 0.8);
}

.dark .recommend-highlight-card,
.dark .recommend-proof-item,
.dark .recommend-formula-card {
  background: rgba(15, 23, 42, 0.34);
}

.recommend-proof-card {
  padding: 18px;
}

.recommend-proof-item {
  border-radius: 18px;
  padding: 14px;
}

.proof-user-chip {
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.86);
  color: #475569;
  font-size: 12px;
  line-height: 1;
  padding: 9px 13px;
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;
}

.proof-user-chip.is-active {
  border-color: rgba(15, 118, 110, 0.5);
  color: #0f766e;
  background: rgba(240, 253, 250, 0.92);
}

.dark .proof-user-chip {
  border-color: rgba(71, 85, 105, 0.58);
  background: rgba(15, 23, 42, 0.46);
  color: #cbd5e1;
}

.dark .proof-user-chip.is-active {
  border-color: rgba(45, 212, 191, 0.46);
  background: rgba(19, 78, 74, 0.46);
  color: #99f6e4;
}

.recommend-defense-row {
  display: grid;
  grid-template-columns: 38px 1fr;
  gap: 12px;
  align-items: flex-start;
}

.recommend-order {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
}

.dark .recommend-order {
  background: rgba(148, 163, 184, 0.12);
  color: #e2e8f0;
}

.dark .recommend-page-header {
  border-bottom-color: rgba(71, 85, 105, 0.5);
}

.dark .recommend-page-header__title {
  color: #f8fafc;
}

.dark .recommend-page-header__label {
  color: #99f6e4;
}

.dark .recommend-page-header__meta {
  color: #94a3b8;
}

@media (max-width: 767px) {
  .recommend-page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .recommend-page-header__controls {
    align-items: flex-start;
  }

  .recommend-page-header__meta {
    text-align: left;
  }

  .recommend-proof-card {
    padding: 16px;
  }

  .recommend-proof-item:nth-child(n + 3) {
    display: none;
  }

  .recommend-overview-board__meta {
    line-height: 1.7;
  }
}
</style>
