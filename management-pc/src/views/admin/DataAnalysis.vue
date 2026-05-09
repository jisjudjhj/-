<template>
  <div class="space-y-6 analytics-ui-page defense-page">
    <section class="analysis-compact-head">
      <div>
        <div class="analysis-compact-head__kicker">经营洞察</div>
        <h1 class="analysis-compact-head__title">传统系统 vs 当前系统</h1>
      </div>
      <div class="analysis-compact-head__meta">行为采集 · 漏斗 · 分群</div>
    </section>

    <section class="defense-metric-strip">
      <article v-for="item in analysisMetricStrip" :key="item.label" class="defense-metric-strip__item">
        <span class="defense-metric-strip__label">{{ item.label }}</span>
        <strong class="defense-metric-strip__value">{{ item.value }}</strong>
        <span class="defense-metric-strip__sub">{{ item.sub }}</span>
      </article>
    </section>

    <PageSectionTabs
      v-model="activeTab"
      :tabs="analysisTabs"
      primary-label="管理端"
      page-label="大数据分析"
      title="页面导航"
      description=""
      :active-label="activeTabInfo.label"
    />

    <div class="analysis-stage">
      <div class="analysis-stage__body" v-loading="loading">
        <!-- 传统痛点对比 -->
        <div v-if="activeTab === 'painpoints'">
          <section class="analysis-system-compare">
            <div class="analysis-system-compare__head">
              <div>
                <div class="analysis-system-compare__eyebrow">系统对比</div>
                <h3 class="analysis-system-compare__title">传统系统 vs 当前系统</h3>
              </div>
              <span class="analysis-system-compare__tag">两列对照</span>
            </div>

            <div class="analysis-system-compare__grid">
              <article
                v-for="column in systemCompareColumns"
                :key="column.title"
                class="analysis-system-compare__column"
                :class="`analysis-system-compare__column--${column.type}`"
              >
                <div class="analysis-system-compare__column-head">
                  <div class="analysis-system-compare__column-kicker">{{ column.kicker }}</div>
                  <h4 class="analysis-system-compare__column-title">{{ column.title }}</h4>
                </div>
                <div class="analysis-system-compare__rows">
                  <div v-for="row in column.rows" :key="row.label" class="analysis-system-compare__row">
                    <span>{{ row.label }}</span>
                    <strong>{{ row.value }}</strong>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <section class="defense-surface defense-surface--split analysis-compare-board">
            <div class="defense-surface__main">
              <div class="defense-surface__eyebrow">经营问题</div>
              <h3 class="defense-surface__title">差异与流失</h3>

              <div class="analysis-compare-list">
                <article v-for="(item, index) in traditionalPainRows" :key="item.pain" class="analysis-compare-list__row">
                  <div class="analysis-compare-list__index">{{ String(index + 1).padStart(2, '0') }}</div>
                  <div class="analysis-compare-list__content">
                    <h4 class="analysis-compare-list__title">{{ item.pain }}</h4>
                    <div class="analysis-compare-list__grid">
                      <div>
                        <div class="analysis-compare-list__label">传统做法</div>
                        <p class="analysis-compare-list__text">{{ item.oldWay }}</p>
                      </div>
                      <div>
                        <div class="analysis-compare-list__label">问题后果</div>
                        <p class="analysis-compare-list__text">{{ item.evidence }}</p>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <aside class="defense-surface__side">
              <div class="defense-surface__eyebrow">解决动作</div>
              <h3 class="defense-surface__title">数据闭环</h3>
              <div class="defense-rail-list">
                <article v-for="item in traditionalPainRows" :key="`${item.pain}-solution`" class="defense-rail-list__item">
                  <div class="defense-rail-list__label">{{ item.pain }}</div>
                  <div class="defense-rail-list__title">解决</div>
                  <p class="defense-rail-list__text">{{ item.newWay }}</p>
                </article>
              </div>
            </aside>
          </section>

          <section class="analysis-summary-line">
            <div class="analysis-summary-line__label">业务主线</div>
            <p class="analysis-summary-line__text">
              漏斗 · 分层 · 实验
            </p>
          </section>
        </div>

        <!-- 漏斗分析 -->
        <div v-if="activeTab === 'funnel'">
          <div class="analysis-funnel-grid">
            <section class="analysis-funnel-panel">
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">用户行为转化漏斗</h4>
              <div ref="funnelChartRef" class="analysis-funnel-chart"></div>
            </section>
            <section class="analysis-funnel-panel">
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">转化率指标</h4>
              <div class="analysis-funnel-rate-list">
                <div v-for="(stage, i) in funnelData.stages || []" :key="i"
                     class="analysis-funnel-rate-row">
                  <span class="analysis-funnel-rate-label">{{ stage.name }}</span>
                  <div class="analysis-funnel-rate-track">
                    <div
                      class="analysis-funnel-rate-fill"
                      :style="{ width: `${Math.max(Number(stage.rate) || 0, 3)}%`, background: funnelColors[i % funnelColors.length] }"
                    ></div>
                  </div>
                  <strong class="analysis-funnel-rate-value">{{ formatNum(stage.count) }} 人 <span>{{ stage.rate }}%</span></strong>
                </div>
                <div class="analysis-funnel-conversion-grid" v-if="funnelData.conversion">
                  <div class="analysis-funnel-conversion-card bg-blue-50 dark:bg-blue-900/30">
                    <div class="text-2xl font-bold text-blue-600">{{ funnelData.conversion.viewToCart }}%</div>
                    <div class="text-xs text-gray-500 mt-1">浏览→加购</div>
                  </div>
                  <div class="analysis-funnel-conversion-card bg-green-50 dark:bg-green-900/30">
                    <div class="text-2xl font-bold text-green-600">{{ funnelData.conversion.cartToBuy }}%</div>
                    <div class="text-xs text-gray-500 mt-1">加购→购买</div>
                  </div>
                  <div class="analysis-funnel-conversion-card bg-purple-50 dark:bg-purple-900/30">
                    <div class="text-2xl font-bold text-purple-600">{{ funnelData.conversion.viewToBuy }}%</div>
                    <div class="text-xs text-gray-500 mt-1">浏览→购买</div>
                  </div>
                </div>
              </div>
            </section>
          </div>
        </div>

        <!-- RFM 分群 -->
        <div v-if="activeTab === 'rfm'">
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div>
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">用户价值分群分布</h4>
              <div ref="rfmChartRef" class="h-96 w-full"></div>
            </div>
            <div>
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">RFM 模型阈值</h4>
              <div class="space-y-3 mt-4" v-if="rfmData.thresholds">
                <div class="flex justify-between p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
                  <span class="text-gray-600 dark:text-gray-300">R (最近消费天数中位数)</span>
                  <span class="font-semibold">≤ {{ rfmData.thresholds.recencyMedian }} 天 = 高</span>
                </div>
                <div class="flex justify-between p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
                  <span class="text-gray-600 dark:text-gray-300">F (消费频率中位数)</span>
                  <span class="font-semibold">≥ {{ rfmData.thresholds.frequencyMedian }} 次 = 高</span>
                </div>
                <div class="flex justify-between p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
                  <span class="text-gray-600 dark:text-gray-300">M (消费金额中位数)</span>
                  <span class="font-semibold">≥ ¥{{ rfmData.thresholds.monetaryMedian }} = 高</span>
                </div>
              </div>
              <div class="mt-6">
                <el-table :data="rfmData.segments || []" size="small" stripe border>
                  <el-table-column prop="name" label="分群" min-width="140" />
                  <el-table-column prop="count" label="用户数" width="80" align="center" />
                  <el-table-column label="占比" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="info">{{ row.percentage }}%</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </div>
        </div>

        <!-- 关联规则 -->
        <div v-if="activeTab === 'association'">
          <div class="flex items-center gap-4 mb-4">
            <span class="text-sm text-gray-500">最小支持度:</span>
            <el-input-number v-model="assocMinSupport" :min="1" :max="100" size="small" />
            <span class="text-sm text-gray-500">最小置信度:</span>
            <el-input-number v-model="assocMinConf" :min="0.01" :max="1" :step="0.05" :precision="2" size="small" />
            <el-button type="primary" size="small" @click="loadAssociation">重新计算</el-button>
            <span class="text-xs text-gray-400 ml-2">共 {{ assocData.totalTransactions || 0 }} 笔多商品订单, {{ assocData.filteredRules || 0 }} 条规则</span>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div>
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">关联规则网络图</h4>
              <div ref="assocChartRef" class="h-96 w-full"></div>
            </div>
            <div>
              <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">规则明细 (按提升度排序)</h4>
              <el-table :data="assocData.rules || []" size="small" stripe border max-height="380">
                <el-table-column label="商品A" prop="productA" width="80" align="center" />
                <el-table-column label="商品B" prop="productB" width="80" align="center" />
                <el-table-column label="共现" prop="support" width="60" align="center" />
                <el-table-column label="置信度 A→B" width="110" align="center">
                  <template #default="{ row }">{{ (row.confidenceAB * 100).toFixed(1) }}%</template>
                </el-table-column>
                <el-table-column label="置信度 B→A" width="110" align="center">
                  <template #default="{ row }">{{ (row.confidenceBA * 100).toFixed(1) }}%</template>
                </el-table-column>
                <el-table-column label="提升度" width="80" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row.lift > 1 ? 'success' : 'info'" size="small">{{ row.lift }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>

        <!-- 留存分析 -->
        <div v-if="activeTab === 'retention'">
          <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">用户月度留存矩阵</h4>
          <div ref="retentionChartRef" class="h-[500px] w-full"></div>
        </div>

        <!-- 销售趋势 -->
        <div v-if="activeTab === 'trend'">
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6" v-if="trendData.totalRevenue !== undefined">
            <div class="text-center p-3 bg-blue-50 dark:bg-blue-900/30 rounded-xl">
              <div class="text-xl font-bold text-blue-600">¥{{ formatNum(trendData.totalRevenue) }}</div>
              <div class="text-xs text-gray-500 mt-1">30日总营收</div>
            </div>
            <div class="text-center p-3 bg-green-50 dark:bg-green-900/30 rounded-xl">
              <div class="text-xl font-bold text-green-600">{{ trendData.totalOrders }}</div>
              <div class="text-xs text-gray-500 mt-1">30日订单数</div>
            </div>
            <div class="text-center p-3 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
              <div class="text-xl font-bold text-purple-600">¥{{ formatNum(trendData.avgOrderValue) }}</div>
              <div class="text-xs text-gray-500 mt-1">平均客单价</div>
            </div>
            <div class="text-center p-3 rounded-xl"
                 :class="trendData.weekOverWeek >= 0 ? 'bg-emerald-50 dark:bg-emerald-900/30' : 'bg-red-50 dark:bg-red-900/30'">
              <div class="text-xl font-bold" :class="trendData.weekOverWeek >= 0 ? 'text-emerald-600' : 'text-red-600'">
                {{ trendData.weekOverWeek > 0 ? '+' : '' }}{{ trendData.weekOverWeek }}%
              </div>
              <div class="text-xs text-gray-500 mt-1">周环比</div>
            </div>
          </div>
          <div ref="trendChartRef" class="h-96 w-full"></div>
        </div>

        <!-- 热力图 -->
        <div v-if="activeTab === 'heatmap'">
          <h4 class="text-base font-semibold text-gray-700 dark:text-gray-200 mb-4">用户活跃时段分布 (按星期×小时)</h4>
          <div ref="heatmapChartRef" class="h-[400px] w-full"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { useRoute, useRouter } from 'vue-router'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import {
  getAnalysisSummary, getFunnelAnalysis, getRfmAnalysis,
  getAssociationRules, getRetentionAnalysis, getSalesTrendAnalysis,
  getActivityHeatmap
} from '../../api/admin'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const summary = ref({})
const funnelData = ref({})
const rfmData = ref({})
const assocData = ref({})
const retentionData = ref({})
const trendData = ref({})
const heatmapData = ref({})

const assocMinSupport = ref(1)
const assocMinConf = ref(0.1)

const funnelColors = ['#3b82f6', '#8b5cf6', '#f59e0b', '#10b981']

const funnelChartRef = ref(null)
const rfmChartRef = ref(null)
const assocChartRef = ref(null)
const retentionChartRef = ref(null)
const trendChartRef = ref(null)
const heatmapChartRef = ref(null)

const charts = []
const analysisTabs = [
  { key: 'painpoints', label: '经营诊断', hint: '定位', description: '采集、排序、实验三段。' },
  { key: 'funnel', label: '转化漏斗', hint: '链路', description: 'view → cart → purchase。' },
  { key: 'rfm', label: '价值分层', hint: '分群', description: 'RFM 阈值与人群占比。' },
]
const tabKeySet = new Set(analysisTabs.map(item => item.key))

function normalizeTab(value) {
  const tab = String(value || '').trim()
  return tabKeySet.has(tab) ? tab : analysisTabs[0].key
}

const activeTab = ref(normalizeTab(route.query.tab))
const activeTabInfo = computed(() => analysisTabs.find(item => item.key === activeTab.value) || analysisTabs[0])
const systemCompareColumns = [
  {
    type: 'legacy',
    kicker: 'Traditional',
    title: '传统电商系统',
    rows: [
      { label: '推荐方式', value: '固定榜单、活动位、人工排序' },
      { label: '用户理解', value: '只看成交结果，难解释流失' },
      { label: '运营动作', value: '统一投放，缺少分群承接' },
      { label: '效果验证', value: '看总量波动，缺少对照实验' },
    ],
  },
  {
    type: 'current',
    kicker: 'Current',
    title: '当前大数据电商系统',
    rows: [
      { label: '推荐方式', value: '用户行为矩阵 + Hybrid 排序' },
      { label: '用户理解', value: 'view / cart / purchase 漏斗拆解' },
      { label: '运营动作', value: 'RFM / KMeans 分群触达' },
      { label: '效果验证', value: 'A/B 对比 CTR / CVR / Purchase' },
    ],
  },
]
const traditionalPainRows = computed(() => [
  {
    pain: '所有用户看到同一套商品',
    oldWay: '固定榜单 + 人工排序，无法区分用户兴趣。',
    newWay: 'user-item 矩阵 + 行为权重 + 分群标签进入推荐排序。',
    evidence: `active_user = ${formatNum(summary.value.totalActiveUsers)}；人群已分层。`,
  },
  {
    pain: '只看成交结果，看不到流失在哪里',
    oldWay: '只看 GMV / order_total，看不到断点。',
    newWay: '漏斗按 view、cart、purchase 拆解。',
    evidence: funnelData.value?.conversion
      ? `view_to_buy = ${funnelData.value.conversion.viewToBuy}%`
      : '读取 view、cart、purchase 三段转化率。',
  },
  {
    pain: '策略好坏靠感觉，没有实验对照',
    oldWay: '只看整体波动，无法判断参数影响。',
    newWay: 'A/B：A=Hot；B=0.40CF+0.30CB+0.30Hot；C=0.55CF+0.20CB+0.25Hot。',
    evidence: '同信号源下直接比较 CTR、CVR、Purchase。',
  },
])
const analysisMetricStrip = computed(() => [
  {
    label: '行为数据总量',
    value: formatNum(summary.value.totalBehaviorRecords),
    sub: 'behavior_record_count',
  },
  {
    label: '有效订单数',
    value: formatNum(summary.value.totalPaidOrders),
    sub: 'paid_order_count',
  },
  {
    label: '活跃用户数',
    value: formatNum(summary.value.totalActiveUsers),
    sub: 'active_user_count',
  },
  {
    label: '整体转化率',
    value: `${summary.value.overallConversionRate || 0}%`,
    sub: 'purchase / exposure',
  },
])

function disposeCharts() {
  charts.forEach(c => c?.dispose())
  charts.length = 0
}

function formatNum(n) {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN')
}

const chartStyle = () => {
  const isDark = document.documentElement.classList.contains('dark')
  return {
    textColor: isDark ? '#9ca3af' : '#6b7280',
    splitLine: isDark ? '#374151' : '#e5e7eb',
    bg: 'transparent'
  }
}

async function loadSummary() {
  try {
    summary.value = await getAnalysisSummary() || {}
  } catch (e) { console.error(e) }
}

async function loadFunnel() {
  loading.value = true
  try {
    funnelData.value = await getFunnelAnalysis() || {}
    await nextTick()
    renderFunnelChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

async function loadRfm() {
  loading.value = true
  try {
    rfmData.value = await getRfmAnalysis() || {}
    await nextTick()
    renderRfmChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

async function loadAssociation() {
  loading.value = true
  try {
    assocData.value = await getAssociationRules({
      minSupport: assocMinSupport.value,
      minConfidence: assocMinConf.value
    }) || {}
    await nextTick()
    renderAssocChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

async function loadRetention() {
  loading.value = true
  try {
    retentionData.value = await getRetentionAnalysis() || {}
    await nextTick()
    renderRetentionChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

async function loadTrend() {
  loading.value = true
  try {
    trendData.value = await getSalesTrendAnalysis() || {}
    await nextTick()
    renderTrendChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

async function loadHeatmap() {
  loading.value = true
  try {
    heatmapData.value = await getActivityHeatmap() || {}
    await nextTick()
    renderHeatmapChart()
  } catch (e) { console.error(e) }
  loading.value = false
}

function onTabChange(tab) {
  disposeCharts()
  const loaders = { painpoints: loadSummary, funnel: loadFunnel, rfm: loadRfm }
  loaders[tab]?.()
}

watch(
  () => route.query.tab,
  value => {
    const nextTab = normalizeTab(value)
    if (nextTab !== activeTab.value) {
      activeTab.value = nextTab
    }
  }
)

watch(activeTab, value => {
  const nextTab = normalizeTab(value)
  const currentTab = normalizeTab(route.query.tab)
  if (nextTab !== currentTab) {
    router.replace({
      query: {
        ...route.query,
        tab: nextTab === analysisTabs[0].key ? undefined : nextTab,
      },
    })
  }
  onTabChange(nextTab)
})

// ========== Chart Renderers ==========

function renderFunnelChart() {
  if (!funnelChartRef.value) return
  const s = chartStyle()
  const c = echarts.init(funnelChartRef.value)
  charts.push(c)
  const stages = funnelData.value.stages || []
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
    series: [{
      type: 'funnel', left: '4%', right: '4%', top: 28, bottom: 28, width: '92%',
      min: 0, max: stages.length ? stages[0].count : 100,
      sort: 'descending', gap: 4,
      label: {
        show: true,
        position: 'inside',
        formatter: params => Number(params.value || 0) > 0 ? `${params.name} ${formatNum(params.value)} 人` : '',
        color: '#fff',
        fontSize: 13,
        fontWeight: 700,
      },
      labelLine: {
        show: false,
      },
      itemStyle: { borderWidth: 2, borderColor: '#fff' },
      labelLayout: { hideOverlap: true },
      data: stages.map((st, i) => ({ name: st.name, value: st.count, itemStyle: { color: funnelColors[i] } }))
    }]
  })
}

function renderRfmChart() {
  if (!rfmChartRef.value) return
  const c = echarts.init(rfmChartRef.value)
  charts.push(c)
  const segments = rfmData.value.segments || []
  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#6366f1']
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人 ({d}%)' },
    legend: {
      bottom: 8,
      type: 'scroll',
      textStyle: { color: chartStyle().textColor },
    },
    series: [{
      type: 'pie', radius: ['35%', '64%'], center: ['50%', '40%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderWidth: 2, borderColor: '#fff' },
      label: { show: false },
      emphasis: { label: { show: true, formatter: '{b}\n{d}%', fontSize: 12 } },
      data: segments.map((seg, i) => ({
        name: seg.name, value: seg.count,
        itemStyle: { color: colors[i % colors.length] }
      }))
    }]
  })
}

function renderAssocChart() {
  if (!assocChartRef.value) return
  const c = echarts.init(assocChartRef.value)
  charts.push(c)
  const rules = assocData.value.rules || []
  if (!rules.length) {
    c.setOption({ title: { text: '暂无满足条件的关联规则', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } })
    return
  }
  const nodeSet = new Set()
  rules.forEach(r => { nodeSet.add(r.productA); nodeSet.add(r.productB) })
  const nodes = [...nodeSet].map(id => ({
    name: String(id), symbolSize: 30 + rules.filter(r => r.productA === id || r.productB === id).length * 5,
    label: { show: true }
  }))
  const links = rules.slice(0, 30).map(r => ({
    source: String(r.productA), target: String(r.productB),
    lineStyle: { width: Math.max(1, r.lift), curveness: 0.2 }
  }))
  c.setOption({
    tooltip: {},
    series: [{
      type: 'graph', layout: 'force', roam: true,
      force: { repulsion: 200, edgeLength: [80, 200] },
      label: { show: true, formatter: '商品{b}', fontSize: 10 },
      edgeLabel: { show: false },
      data: nodes, links,
      lineStyle: { color: '#aaa', opacity: 0.6 }
    }]
  })
}

function renderRetentionChart() {
  if (!retentionChartRef.value) return
  const c = echarts.init(retentionChartRef.value)
  charts.push(c)
  const cohorts = retentionData.value.cohorts || []
  const months = retentionData.value.months || []
  if (!cohorts.length) return

  const data = []
  const yLabels = cohorts.map(co => `${co.cohort} (${co.size}人)`)
  cohorts.forEach((co, yi) => {
    ;(co.retention || []).forEach((val, xi) => {
      data.push([xi, yi, val])
    })
  })
  const maxMonth = Math.max(...cohorts.map(co => (co.retention || []).length))
  const xLabels = Array.from({ length: maxMonth }, (_, i) => i === 0 ? '注册月' : `+${i}月`)

  c.setOption({
    tooltip: { formatter: p => `${yLabels[p.value[1]]}<br/>${xLabels[p.value[0]]}: ${p.value[2]}%` },
    grid: { left: 130, right: 30, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: xLabels, splitArea: { show: true } },
    yAxis: { type: 'category', data: yLabels, splitArea: { show: true } },
    visualMap: { min: 0, max: 100, calculable: true, orient: 'horizontal', left: 'center', bottom: 0,
      inRange: { color: ['#f3f4f6', '#bbf7d0', '#4ade80', '#16a34a', '#15803d'] } },
    series: [{
      type: 'heatmap', data, label: { show: true, formatter: p => p.value[2] + '%', fontSize: 10 },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } }
    }]
  })
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  const s = chartStyle()
  const c = echarts.init(trendChartRef.value)
  charts.push(c)
  const d = trendData.value
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['日营收', '7日均线', '订单数'], textStyle: { color: s.textColor } },
    grid: { left: '4%', right: '4%', bottom: 72, top: 44, containLabel: true },
    xAxis: { type: 'category', data: d.dates || [], axisLabel: { color: s.textColor, rotate: 45, fontSize: 10 } },
    yAxis: [
      { type: 'value', name: '金额 (¥)', splitLine: { lineStyle: { color: s.splitLine, type: 'dashed' } }, axisLabel: { color: s.textColor } },
      { type: 'value', name: '订单数', splitLine: { show: false }, axisLabel: { color: s.textColor } }
    ],
    series: [
      {
        name: '日营收', type: 'bar', yAxisIndex: 0, barMaxWidth: 20,
        itemStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1, [
          { offset: 0, color: 'rgba(59,130,246,0.8)' }, { offset: 1, color: 'rgba(59,130,246,0.2)' }
        ]), borderRadius: [4,4,0,0] },
        data: d.revenues || []
      },
      {
        name: '7日均线', type: 'line', yAxisIndex: 0, smooth: true,
        lineStyle: { width: 3, color: '#ef4444' },
        itemStyle: { color: '#ef4444' }, showSymbol: false,
        data: d.movingAverage7 || []
      },
      {
        name: '订单数', type: 'line', yAxisIndex: 1, smooth: true,
        lineStyle: { width: 2, color: '#10b981', type: 'dashed' },
        itemStyle: { color: '#10b981' }, showSymbol: false,
        data: d.orderCounts || []
      }
    ]
  })
}

function renderHeatmapChart() {
  if (!heatmapChartRef.value) return
  const c = echarts.init(heatmapChartRef.value)
  charts.push(c)
  const d = heatmapData.value
  c.setOption({
    tooltip: { formatter: p => `${(d.days||[])[p.value[1]]} ${(d.hours||[])[p.value[0]]}<br/>行为次数: ${p.value[2]}` },
    grid: { left: 60, right: 30, top: 20, bottom: 60 },
    xAxis: { type: 'category', data: d.hours || [], splitArea: { show: true }, axisLabel: { fontSize: 10 } },
    yAxis: { type: 'category', data: d.days || [], splitArea: { show: true } },
    visualMap: { min: 0, max: d.max || 10, calculable: true, orient: 'horizontal', left: 'center', bottom: 0,
      inRange: { color: ['#eef2ff', '#a5b4fc', '#6366f1', '#4338ca', '#312e81'] } },
    series: [{
      type: 'heatmap', data: d.data || [], label: { show: true, fontSize: 9 },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.3)' } }
    }]
  })
}

onMounted(async () => {
  await loadSummary()
  await onTabChange(activeTab.value)
})

onUnmounted(() => disposeCharts())
</script>

<style scoped>
.analysis-compact-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 10px 0 4px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.analysis-compact-head__kicker {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #2563eb;
}

.analysis-compact-head__title {
  margin-top: 8px;
  font-size: clamp(26px, 3vw, 40px);
  line-height: 1.1;
  font-weight: 900;
  color: #0f172a;
}

.analysis-compact-head__meta {
  flex: none;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.analysis-stage {
  min-width: 0;
}

.analysis-stage__body {
  padding: 4px 0 0;
}

.analysis-evidence-list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 48px;
}

.analysis-evidence-list .defense-evidence-item:first-child {
  grid-column: 1 / -1;
}

.analysis-evidence-list .defense-evidence-item__head {
  align-items: flex-start;
}

.analysis-evidence-list .defense-evidence-item__text {
  max-width: 860px;
}

.analysis-system-compare {
  margin-bottom: 18px;
  padding: 22px 24px 24px;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
}

.analysis-system-compare__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.analysis-system-compare__eyebrow,
.analysis-system-compare__column-kicker {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #2563eb;
}

.analysis-system-compare__title {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 850;
  line-height: 1.28;
  color: #0f172a;
}

.analysis-system-compare__tag {
  flex: none;
  border-radius: 999px;
  border: 1px solid rgba(37, 99, 235, 0.22);
  padding: 7px 12px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  background: rgba(239, 246, 255, 0.76);
}

.analysis-system-compare__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.analysis-system-compare__column {
  min-width: 0;
  border-radius: 16px;
  border: 1px solid rgba(203, 213, 225, 0.86);
  background: rgba(248, 250, 252, 0.88);
  overflow: hidden;
}

.analysis-system-compare__column--current {
  border-color: rgba(16, 185, 129, 0.22);
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.9), rgba(248, 250, 252, 0.88));
}

.analysis-system-compare__column-head {
  padding: 18px 18px 12px;
}

.analysis-system-compare__column--legacy .analysis-system-compare__column-kicker {
  color: #64748b;
}

.analysis-system-compare__column--current .analysis-system-compare__column-kicker {
  color: #059669;
}

.analysis-system-compare__column-title {
  margin-top: 4px;
  color: #0f172a;
  font-size: 18px;
  font-weight: 800;
}

.analysis-system-compare__rows {
  border-top: 1px solid rgba(203, 213, 225, 0.76);
}

.analysis-system-compare__row {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid rgba(226, 232, 240, 0.9);
}

.analysis-system-compare__row:first-child {
  border-top: none;
}

.analysis-system-compare__row span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.analysis-system-compare__row strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.55;
}

.dark .analysis-system-compare {
  border-color: rgba(71, 85, 105, 0.5);
  background: rgba(15, 23, 42, 0.42);
}

.dark .analysis-system-compare__title,
.dark .analysis-system-compare__column-title,
.dark .analysis-system-compare__row strong {
  color: #f8fafc;
}

.dark .analysis-system-compare__column {
  border-color: rgba(71, 85, 105, 0.58);
  background: rgba(15, 23, 42, 0.62);
}

.dark .analysis-system-compare__column--current {
  border-color: rgba(52, 211, 153, 0.28);
  background: rgba(6, 78, 59, 0.22);
}

.dark .analysis-system-compare__rows,
.dark .analysis-system-compare__row {
  border-color: rgba(71, 85, 105, 0.5);
}

.dark .analysis-system-compare__row span {
  color: #94a3b8;
}

.analysis-funnel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, 0.95fr);
  gap: 28px;
  align-items: start;
}

.analysis-funnel-panel {
  min-width: 0;
}

.analysis-funnel-chart {
  width: 100%;
  height: 430px;
}

.analysis-funnel-rate-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 22px;
}

.analysis-funnel-rate-row {
  display: grid;
  grid-template-columns: 58px minmax(180px, 1fr) minmax(112px, max-content);
  gap: 14px;
  align-items: center;
}

.analysis-funnel-rate-label {
  color: #64748b;
  font-size: 14px;
  text-align: right;
}

.analysis-funnel-rate-track {
  height: 14px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.9);
}

.analysis-funnel-rate-fill {
  height: 100%;
  min-width: 12px;
  border-radius: inherit;
  transition: width 0.35s ease;
}

.analysis-funnel-rate-value {
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
}

.analysis-funnel-rate-value span {
  color: #64748b;
  font-weight: 600;
}

.analysis-funnel-conversion-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 28px;
}

.analysis-funnel-conversion-card {
  min-width: 0;
  border-radius: 12px;
  padding: 18px 14px;
  text-align: center;
}

.dark .analysis-funnel-rate-track {
  background: rgba(51, 65, 85, 0.82);
}

.dark .analysis-funnel-rate-value {
  color: #f8fafc;
}

.dark .analysis-funnel-rate-value span,
.dark .analysis-funnel-rate-label {
  color: #94a3b8;
}

.analysis-compare-board {
  grid-template-columns: minmax(0, 1.24fr) minmax(300px, 0.76fr);
}

.analysis-compare-list {
  margin-top: 28px;
}

.analysis-compare-list__row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 18px;
  padding: 20px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.analysis-compare-list__row:first-child {
  padding-top: 0;
  border-top: none;
}

.analysis-compare-list__index {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: #0f766e;
}

.analysis-compare-list__title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.4;
  color: #0f172a;
}

.analysis-compare-list__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 16px;
}

.analysis-compare-list__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #64748b;
}

.analysis-compare-list__text {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.85;
  color: #475569;
}

.analysis-summary-line {
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  padding: 20px 6px 0;
  margin-top: 20px;
}

.analysis-summary-line__label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #0f766e;
}

.analysis-summary-line__text {
  font-size: 14px;
  line-height: 1.85;
  color: #475569;
}

.dark .analysis-compare-list__row,
.dark .analysis-summary-line {
  border-color: rgba(71, 85, 105, 0.48);
}

.dark .analysis-compare-list__index,
.dark .analysis-summary-line__label {
  color: #5eead4;
}

.dark .analysis-compare-list__title {
  color: #f8fafc;
}

.dark .analysis-compare-list__label {
  color: #94a3b8;
}

.dark .analysis-compare-list__text,
.dark .analysis-summary-line__text {
  color: #cbd5e1;
}

@media (max-width: 768px) {
  .analysis-evidence-list,
  .analysis-funnel-grid,
  .analysis-system-compare__grid,
  .analysis-compare-list__row,
  .analysis-summary-line {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .analysis-system-compare {
    padding: 18px 16px;
  }

  .analysis-system-compare__head {
    flex-direction: column;
    margin-bottom: 14px;
  }

  .analysis-system-compare__row {
    grid-template-columns: 1fr;
    gap: 6px;
  }

  .analysis-evidence-list .defense-evidence-item:first-child {
    grid-column: auto;
  }

  .analysis-funnel-chart {
    height: 340px;
  }

  .analysis-funnel-rate-row {
    grid-template-columns: 44px minmax(0, 1fr);
  }

  .analysis-funnel-rate-value {
    grid-column: 2;
    font-size: 13px;
  }

  .analysis-funnel-conversion-grid {
    grid-template-columns: 1fr;
  }

  .analysis-compare-list__grid {
    grid-template-columns: 1fr;
  }
}
</style>
