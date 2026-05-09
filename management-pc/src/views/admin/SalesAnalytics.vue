<template>
  <div v-loading="loading" class="space-y-6 sales-analytics-page analytics-ui-page">
    <section class="panel-card analytics-hero sales-analytics-hero relative overflow-hidden p-6 md:p-8">
      <div class="sales-analytics-hero__glow sales-analytics-hero__glow--primary"></div>
      <div class="sales-analytics-hero__glow sales-analytics-hero__glow--secondary"></div>
      <div class="relative grid gap-6 xl:grid-cols-[1.14fr_0.86fr]">
        <div>
          <div class="analytics-kicker">Revenue Operations</div>
          <h1 class="mt-3 text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100 md:text-4xl">销售分析</h1>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300 md:text-base">
            营收 / 趋势 / 品类。
          </p>
          <div class="mt-5 flex flex-wrap gap-2 text-xs md:text-sm">
            <span v-for="item in heroTags" :key="item" class="analytics-tag px-3 py-1.5 font-medium">
              {{ item }}
            </span>
          </div>

          <div class="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
            <article v-for="item in salesIntroRows" :key="item.label" class="sales-analytics-hero-note">
              <div class="sales-analytics-hero-note__label">{{ item.label }}</div>
              <p class="sales-analytics-hero-note__text">{{ item.value }}</p>
            </article>
          </div>
        </div>

        <div class="grid gap-4">
          <article class="analytics-status-card sales-analytics-status-card p-5">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="sales-analytics-status-card__label">营收快照</div>
                <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ formatCurrency(totalRevenue) }}</div>
                <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">累计订单 {{ formatNumber(totalOrders) }}，平均客单价 {{ formatCurrency(avgOrderValue) }}</p>
              </div>
              <div ref="miniChart1Ref" class="sales-analytics-mini-chart"></div>
            </div>
            <div class="mt-4 grid grid-cols-2 gap-3 text-sm">
              <div class="sales-analytics-mini-stat">
                <span>阶段涨幅</span>
                <strong>{{ formatPercent(revenueGrowth) }}</strong>
              </div>
              <div class="sales-analytics-mini-stat">
                <span>热销单品</span>
                <strong>{{ topProductName }}</strong>
              </div>
            </div>
          </article>

          <article class="analytics-status-card sales-analytics-status-card p-5">
            <div class="sales-analytics-status-card__label">当前重点</div>
            <div class="mt-2 text-xl font-black text-slate-900 dark:text-slate-100">{{ bestCategory ? getCategoryName(bestCategory) : '等待结构回流' }}</div>
            <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
              {{ salesSolutionRows[0]?.value }}
            </p>
            <div class="mt-4 grid grid-cols-1 gap-3">
              <div v-for="item in salesSolutionRows.slice(1)" :key="item.label" class="sales-analytics-focus-row">
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <section class="sales-analytics-evidence-grid">
      <article
        v-for="item in salesEvidenceTracks"
        :key="item.title"
        class="panel-card sales-analytics-evidence-card p-5"
        :class="evidenceCardClass(item.type)"
      >
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item.title }}</div>
            <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ item.instance }}</p>
          </div>
          <span class="analytics-pill px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.16em]">{{ item.tag }}</span>
        </div>
        <div class="mt-4 border-t border-slate-200/80 pt-4 text-sm leading-6 text-slate-500 dark:border-slate-700/70 dark:text-slate-400">
          {{ item.evidence }}
        </div>
      </article>
    </section>

    <PageSectionTabs
      v-model="salesActiveTab"
      primary-label="管理端"
      page-label="销售分析"
      title="经营视图"
      description="总览、趋势、品类。"
      :tabs="salesTabs"
      :active-label="salesActiveTabInfo.label"
    />

    <div v-if="salesActiveTab === 'overview'" class="space-y-6">
      <section class="sales-analytics-summary-grid">
        <article v-for="item in salesSummaryCards" :key="item.label" class="panel-card sales-analytics-summary-card p-5">
          <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">{{ item.label }}</div>
          <div class="mt-3 text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100">{{ item.value }}</div>
          <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ item.sub }}</p>
        </article>
      </section>

      <section class="panel-card sales-analytics-board p-6">
        <div class="grid gap-6 xl:grid-cols-[1.08fr_0.92fr]">
          <div>
            <div class="section-kicker">经营概览</div>
            <h3 class="text-xl font-bold text-slate-900 dark:text-slate-100">规模、结构、增长</h3>
            <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ salesOverviewNarrative }}</p>

            <div class="sales-overview-points">
              <article class="sales-overview-points__item">
                <div class="sales-overview-points__label">当前爆品</div>
                <div class="sales-overview-points__value">{{ topProductName }}</div>
                <p class="sales-overview-points__text">销量 {{ formatNumber(topProductSales) }}，当前营收主要拉动商品。</p>
              </article>
              <article class="sales-overview-points__item">
                <div class="sales-overview-points__label">趋势变化</div>
                <div class="sales-overview-points__value">{{ formatPercent(revenueGrowth) }}</div>
                <p class="sales-overview-points__text">近期营收从 {{ formatCurrency(getAmount(firstTrend)) }} 变化到 {{ formatCurrency(getAmount(latestTrend)) }}。</p>
              </article>
              <article class="sales-overview-points__item">
                <div class="sales-overview-points__label">结构关注</div>
                <div class="sales-overview-points__value">{{ bestCategory ? getCategoryName(bestCategory) : '等待分类回流' }}</div>
                <p class="sales-overview-points__text">继续看品类集中度，避免增长被单一类目过度支撑。</p>
              </article>
            </div>
          </div>

          <aside class="sales-analytics-rail">
            <div class="section-kicker">热销商品</div>
            <h3 class="text-xl font-bold text-slate-900 dark:text-slate-100">营收拉动商品</h3>
            <div class="mt-4 grid gap-3">
              <article v-for="(item, index) in visibleHotProductRows" :key="`${item.name}-${index}`" class="sales-analytics-product-card p-4">
                <div class="flex items-start justify-between gap-3">
                  <div>
                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">TOP {{ index + 1 }}</div>
                    <div class="mt-2 text-base font-semibold text-slate-900 dark:text-slate-100">{{ getHotProductName(item) }}</div>
                  </div>
                  <span class="analytics-pill px-3 py-1 text-[11px] font-semibold">销量 {{ formatNumber(item.salesCount) }}</span>
                </div>
                <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">这款商品是当前营收拉动项之一，适合和趋势图一起判断活动与推荐效果。</p>
              </article>
              <el-empty v-if="!visibleHotProductRows.length" description="暂无热销商品数据" :image-size="84" />
            </div>
          </aside>
        </div>
      </section>
    </div>

    <section v-else-if="salesActiveTab === 'trend'" class="panel-card p-6">
      <div class="grid grid-cols-1 gap-6 xl:grid-cols-[0.9fr_1.1fr]">
        <div>
          <div class="section-kicker">趋势复盘</div>
          <h3 class="text-xl font-bold text-slate-900 dark:text-slate-100">营收 vs 订单</h3>
          <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">
            看订单量、客单价、活动波动。
          </p>
          <div class="sales-analytics-narrative mt-5 rounded-2xl p-5">
            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">近段时间销售主结论</div>
            <div class="mt-3 text-sm leading-7 text-slate-600 dark:text-slate-300">{{ trendNarrative }}</div>
          </div>
        </div>
        <div ref="mainChartRef" class="h-96 w-full"></div>
      </div>
    </section>

    <div v-else class="grid grid-cols-1 gap-6 xl:grid-cols-[0.92fr_1.08fr]">
      <section class="panel-card p-6">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="section-kicker">品类结构</div>
            <h3 class="text-xl font-bold text-slate-900 dark:text-slate-100">分类销量分布</h3>
            <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">先看销量占比。</p>
          </div>
          <span class="analytics-pill px-3 py-1 text-[11px] font-semibold">{{ categoryRows.length }} 个分类</span>
        </div>
        <div ref="categoryChartRef" class="mt-4 h-80 w-full"></div>
      </section>

      <section class="panel-card p-6">
        <div class="flex items-start justify-between gap-3">
          <div>
            <div class="section-kicker">结构明细</div>
            <h3 class="text-xl font-bold text-slate-900 dark:text-slate-100">品类贡献明细</h3>
            <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">销量与占比。</p>
          </div>
        </div>
        <el-table
          :data="categoryRows"
          class="!mt-5 !bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
          <el-table-column prop="name" label="分类" min-width="180" />
          <el-table-column prop="sales" label="销量" width="150" />
          <el-table-column prop="share" label="占比" width="140" />
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useRoute, useRouter } from 'vue-router'
import { getAdminSalesAnalytics } from '../../api/admin'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import { getAnalyticsChartTheme } from '../../utils/chartTheme'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const salesData = ref({})
const hotProducts = ref([])
const recentStats = ref([])
const categorySales = ref([])

const miniChart1Ref = ref(null)
const mainChartRef = ref(null)
const categoryChartRef = ref(null)

let charts = []
let themeObserver = null
let compactQuery = null
const syncCompactMode = () => {
  compactMode.value = compactQuery?.matches || false
}

const heroTags = ['热销商品', '营收走势', '订单变化', '品类贡献']
const salesTabs = [
  { key: 'overview', label: '经营总览', hint: '规模 + 爆品', description: '先看总体规模、热销商品和经营概览，适合快速理解当前销售盘面。' },
  { key: 'trend', label: '趋势复盘', hint: '营收变化', description: '用营收与订单双轴图解释近期销售变化和增长节奏。' },
  { key: 'category', label: '品类结构', hint: '贡献拆解', description: '品类分布、销售贡献和结构集中度。' },
]
const salesTabKeySet = new Set(salesTabs.map(item => item.key))

function normalizeSalesTab(value) {
  const tab = String(value || '').trim()
  return salesTabKeySet.has(tab) ? tab : salesTabs[0].key
}

const salesActiveTab = ref(normalizeSalesTab(route.query.tab))
const salesActiveTabInfo = computed(() => salesTabs.find(item => item.key === salesActiveTab.value) || salesTabs[0])

const totalRevenue = computed(() => Number(salesData.value.totalRevenue || 0))
const totalOrders = computed(() => {
  if (salesData.value.totalOrders != null) return Number(salesData.value.totalOrders || 0)
  return recentStats.value.reduce((sum, item) => sum + getCount(item), 0)
})
const topProduct = computed(() => hotProducts.value[0] || null)
const topProductName = computed(() => getHotProductName(topProduct.value) || '等待热销商品')
const topProductSales = computed(() => Number(topProduct.value?.salesCount || topProduct.value?.sales_count || 0))
const bestCategory = computed(() => {
  if (!categorySales.value.length) return null
  return [...categorySales.value].sort((left, right) =>
    getCategoryValue(right) - getCategoryValue(left)
  )[0]
})
const totalCategorySales = computed(() => categorySales.value.reduce((sum, item) => sum + getCategoryValue(item), 0))
const avgOrderValue = computed(() => {
  if (salesData.value.avgOrderValue != null) return Number(salesData.value.avgOrderValue || 0)
  return totalOrders.value ? totalRevenue.value / totalOrders.value : 0
})
const latestTrend = computed(() => recentStats.value[recentStats.value.length - 1] || null)
const firstTrend = computed(() => recentStats.value[0] || null)
const revenueGrowth = computed(() => {
  const start = getAmount(firstTrend.value)
  const end = getAmount(latestTrend.value)
  return start ? (end - start) / start : 0
})

const salesEvidenceTracks = computed(() => [
  {
    title: '热销商品实例',
    tag: '商品',
    type: 'danger',
    instance: topProduct.value
      ? `${topProductName.value} 当前销量 ${formatNumber(topProductSales.value)}。`
      : '等待热销商品数据回流。',
    evidence: topProduct.value
      ? '适合直接解释“当前销售额由哪款商品直接拉动”。'
      : '当前最强销售驱动项。',
  },
  {
    title: '营收波动实例',
    tag: '趋势',
    type: 'primary',
    instance: recentStats.value.length
      ? `最近一段时间营收从 ${formatCurrency(getAmount(firstTrend.value))} 变化到 ${formatCurrency(getAmount(latestTrend.value))}。`
      : '等待趋势数据加载后生成波动区间。',
    evidence: `阶段涨幅 ${formatPercent(revenueGrowth.value)}。`,
  },
  {
    title: '品类结构实例',
    tag: bestCategory.value ? '品类' : '待加载',
    type: bestCategory.value ? 'warning' : 'info',
    instance: bestCategory.value
      ? `当前贡献最高的分类是 ${getCategoryName(bestCategory.value)}，销量 ${formatNumber(getCategoryValue(bestCategory.value))}。`
      : '等待分类贡献数据回流。',
    evidence: '适合解释销售结构是不是过于依赖单一类目。',
  },
  {
    title: '销售闭环实例',
    tag: '结果',
    type: 'success',
    instance: `总销售额 ${formatCurrency(totalRevenue.value)}，平均客单价 ${formatCurrency(avgOrderValue.value)}。`,
    evidence: salesOverviewNarrative.value,
  },
])
const salesIntroRows = computed(() => [
  {
    label: '传统做法',
    value: '只看总额和订单数。',
  },
  {
    label: '结构盲区',
    value: '如果不拆热销商品、趋势和品类贡献，就很难判断增长来自爆品、时间波动还是类目结构变化。',
  },
  {
    label: '经营风险',
    value: bestCategory.value
      ? `当前最高贡献类目是 ${getCategoryName(bestCategory.value)}，如果结构过于集中，风险会被总额掩盖。`
      : '没有品类结构拆分时，单一类目依赖风险会被总额掩盖。',
  },
])
const salesSolutionRows = computed(() => [
  {
    label: '爆品识别',
    value: topProduct.value
      ? `${topProductName.value} 当前销量 ${formatNumber(topProductSales.value)}，可直接解释当前营收由谁拉动。`
      : '先识别当前热销商品，再解释营收拉动项。',
  },
  {
    label: '趋势拆解',
    value: `总销售额 ${formatCurrency(totalRevenue.value)}，近期订单 ${formatNumber(totalOrders.value)}，可区分订单量和客单价的影响。`,
  },
  {
    label: '品类贡献',
    value: '把分类销量和占比展开，避免只看总额而看不到结构性问题。',
  },
])

const salesSummaryCards = computed(() => [
  {
    label: '总销售额',
    value: formatCurrency(totalRevenue.value),
    sub: '当前统计窗口内累计营收。',
  },
  {
    label: '近期订单数',
    value: formatNumber(totalOrders.value),
    sub: '结合趋势图看订单是否同步增长。',
  },
  {
    label: '平均客单价',
    value: formatCurrency(avgOrderValue.value),
    sub: '适合解释营收增长是靠订单数还是靠客单价。',
  },
  {
    label: '热销商品数',
    value: formatNumber(hotProducts.value.length),
    sub: '当前已纳入分析的热销商品条目数。',
  },
])

const compactMode = ref(false)
const hotProductRows = computed(() => hotProducts.value.slice(0, 4))
const visibleHotProductRows = computed(() => (compactMode.value ? hotProductRows.value.slice(0, 3) : hotProductRows.value))
const categoryRows = computed(() => categorySales.value.map(item => ({
  name: getCategoryName(item),
  sales: formatNumber(getCategoryValue(item)),
  share: formatPercent(totalCategorySales.value ? getCategoryValue(item) / totalCategorySales.value : 0),
})))

const salesOverviewNarrative = computed(() => {
  if (!totalRevenue.value) {
    return '暂无销售数据。'
  }
  return `当前总销售额 ${formatCurrency(totalRevenue.value)}，热销商品由“${topProductName.value}”领跑；结合近期趋势与品类分布，可判断销售增长来自商品爆发、订单增长，还是类目结构变化。`
})

const trendNarrative = computed(() => {
  if (!recentStats.value.length) {
    return '当前没有趋势数据。'
  }
  return `${formatCurrency(getAmount(firstTrend.value))} → ${formatCurrency(getAmount(latestTrend.value))}；变化 ${formatPercent(revenueGrowth.value)}。`
})

function evidenceCardClass(type) {
  if (type === 'primary') return 'sales-analytics-evidence-card--blue'
  if (type === 'success') return 'sales-analytics-evidence-card--emerald'
  if (type === 'warning') return 'sales-analytics-evidence-card--amber'
  if (type === 'danger') return 'sales-analytics-evidence-card--rose'
  return 'sales-analytics-evidence-card--slate'
}

function formatNumber(value) {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number.toLocaleString('zh-CN') : '0'
}

function formatCurrency(value) {
  const number = Number(value || 0)
  return `¥${number.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function formatPercent(value) {
  const number = Number(value || 0)
  return `${(number * 100).toFixed(number * 100 >= 10 ? 1 : 2)}%`
}

function getDate(item) {
  return item?.date || item?.orderDate || '--'
}

function getAmount(item) {
  return Number(item?.amount || item?.revenue || item?.totalRevenue || 0)
}

function getCount(item) {
  return Number(item?.count || item?.orders || item?.orderCount || 0)
}

function getCategoryName(item) {
  return item?.category_name || item?.categoryName || item?.name || '未命名分类'
}

function getCategoryValue(item) {
  return Number(item?.total_sales || item?.totalSales || item?.salesCount || item?.count || 0)
}

function getHotProductName(item) {
  return item?.name || item?.productName || item?.product_name || '未命名商品'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getAdminSalesAnalytics()
    const data = res || {}
    salesData.value = data
    hotProducts.value = data.hotProducts || []
    recentStats.value = data.recentStats || []
    categorySales.value = data.categorySales || []
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  charts.forEach(chart => chart?.dispose())
  charts = []

  const theme = getAnalyticsChartTheme()
  const { textColor, splitLineColor, axisColor, surfaceColor, palette } = theme

  if (miniChart1Ref.value && recentStats.value.length) {
    const chart = echarts.init(miniChart1Ref.value)
    chart.setOption({
      grid: { left: 0, right: 0, top: 0, bottom: 0 },
      xAxis: { type: 'category', show: false, data: recentStats.value.map(getDate) },
      yAxis: { type: 'value', show: false },
      series: [{
        type: 'line',
        smooth: true,
        showSymbol: false,
        lineStyle: { width: 3, color: palette.primary },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59,130,246,0.48)' },
            { offset: 1, color: 'rgba(59,130,246,0)' },
          ]),
        },
        data: recentStats.value.map(getAmount),
      }],
    })
    charts.push(chart)
  }

  if (mainChartRef.value && recentStats.value.length) {
    const chart = echarts.init(mainChartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { top: 0, data: ['营收', '订单数'], textStyle: { color: textColor } },
      grid: { left: '4%', right: '4%', bottom: 56, top: 44, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: recentStats.value.map(getDate),
        axisLabel: { color: textColor, margin: 14 },
        axisLine: { lineStyle: { color: axisColor } },
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
          lineStyle: { width: 3, color: palette.accent },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(139,92,246,0.5)' },
              { offset: 1, color: 'rgba(139,92,246,0)' },
            ]),
          },
          data: recentStats.value.map(getAmount),
        },
        {
          name: '订单数',
          type: 'bar',
          barMaxWidth: 30,
          itemStyle: { color: 'rgba(6,182,212,0.58)', borderRadius: [4, 4, 0, 0] },
          data: recentStats.value.map(getCount),
        },
      ],
    })
    charts.push(chart)
  }

  if (categoryChartRef.value) {
    const chart = echarts.init(categoryChartRef.value)
    const colors = [palette.primary, palette.accent, palette.danger, palette.warning, palette.success, '#ef4444', palette.secondary]
    const pieData = categorySales.value.length
      ? categorySales.value.map((item, index) => ({
          value: getCategoryValue(item),
          name: getCategoryName(item),
          itemStyle: { color: colors[index % colors.length] },
        }))
      : [{ value: 0, name: '暂无数据' }]

    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 8, type: 'scroll', textStyle: { color: textColor } },
      series: [{
        type: 'pie',
        radius: ['38%', '66%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 10, borderColor: surfaceColor, borderWidth: 2 },
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 18, fontWeight: 'bold', color: textColor } },
        data: pieData,
      }],
    })
    charts.push(chart)
  }
}

function handleResize() {
  charts.forEach(chart => chart?.resize())
}

watch(
  () => route.query.tab,
  value => {
    const nextTab = normalizeSalesTab(value)
    if (nextTab !== salesActiveTab.value) {
      salesActiveTab.value = nextTab
    }
  }
)

watch(salesActiveTab, value => {
  const nextTab = normalizeSalesTab(value)
  const currentTab = normalizeSalesTab(route.query.tab)
  if (nextTab !== currentTab) {
    router.replace({
      query: {
        ...route.query,
        tab: nextTab === salesTabs[0].key ? undefined : nextTab,
      },
    })
  }
  nextTick(() => renderCharts())
})

onMounted(async () => {
  compactQuery = window.matchMedia('(max-width: 768px)')
  compactQuery.addEventListener('change', syncCompactMode)
  syncCompactMode()
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
  if (compactQuery) {
    compactQuery.removeEventListener('change', syncCompactMode)
  }
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  charts.forEach(chart => chart?.dispose())
})
</script>

<style scoped>
.sales-analytics-hero__glow {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(58px);
}

.sales-analytics-hero__glow--primary {
  top: -38px;
  left: -18px;
  width: 220px;
  height: 220px;
  background: rgba(59, 130, 246, 0.12);
}

.sales-analytics-hero__glow--secondary {
  right: 18px;
  bottom: -68px;
  width: 260px;
  height: 260px;
  background: rgba(16, 185, 129, 0.08);
}

.sales-analytics-hero-note,
.sales-analytics-status-card,
.sales-analytics-summary-card,
.sales-analytics-product-card,
.sales-analytics-narrative {
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(248, 250, 252, 0.9);
}

.sales-analytics-hero-note {
  border-radius: var(--radius-md);
  padding: 16px 18px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.sales-analytics-hero-note__label,
.sales-analytics-status-card__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #64748b;
}

.sales-analytics-hero-note__text {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.sales-analytics-status-card {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.sales-analytics-mini-chart {
  width: 132px;
  height: 72px;
  flex: 0 0 auto;
}

.sales-analytics-mini-stat,
.sales-analytics-focus-row {
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.16);
  padding: 12px 14px;
}

.sales-analytics-mini-stat span,
.sales-analytics-focus-row span {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.sales-analytics-mini-stat strong,
.sales-analytics-focus-row strong {
  display: block;
  margin-top: 6px;
  font-size: 15px;
  line-height: 1.6;
  color: #0f172a;
}

.sales-analytics-evidence-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}

.sales-analytics-evidence-card {
  position: relative;
  overflow: hidden;
}

.sales-analytics-evidence-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 999px;
}

.sales-analytics-evidence-card--blue::before {
  background: linear-gradient(180deg, #3b82f6, #06b6d4);
}

.sales-analytics-evidence-card--emerald::before {
  background: linear-gradient(180deg, #10b981, #14b8a6);
}

.sales-analytics-evidence-card--amber::before {
  background: linear-gradient(180deg, #f59e0b, #fb7185);
}

.sales-analytics-evidence-card--rose::before {
  background: linear-gradient(180deg, #f43f5e, #ec4899);
}

.sales-analytics-evidence-card--slate::before {
  background: linear-gradient(180deg, #64748b, #94a3b8);
}

.sales-analytics-evidence-card--blue {
  border-color: rgba(59, 130, 246, 0.28);
}

.sales-analytics-evidence-card--emerald {
  border-color: rgba(16, 185, 129, 0.28);
}

.sales-analytics-evidence-card--amber {
  border-color: rgba(245, 158, 11, 0.28);
}

.sales-analytics-evidence-card--rose {
  border-color: rgba(244, 63, 94, 0.28);
}

.sales-analytics-evidence-card--slate {
  border-color: rgba(148, 163, 184, 0.28);
}

.sales-analytics-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}

.sales-analytics-board {
  border-color: rgba(148, 163, 184, 0.2);
}

.sales-analytics-rail {
  border-left: 1px solid rgba(148, 163, 184, 0.16);
  padding-left: 24px;
}

.sales-overview-points {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-top: 28px;
}

.sales-overview-points__item {
  min-width: 0;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.sales-overview-points__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #64748b;
}

.sales-overview-points__value {
  margin-top: 10px;
  font-size: 24px;
  font-weight: 800;
  line-height: 1.25;
  color: #0f172a;
}

.sales-overview-points__text {
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.sales-analytics-narrative {
  border-style: dashed;
}

.dark .sales-analytics-hero-note,
.dark .sales-analytics-status-card,
.dark .sales-analytics-summary-card,
.dark .sales-analytics-product-card,
.dark .sales-analytics-narrative,
.dark .sales-analytics-evidence-card {
  border-color: rgba(71, 85, 105, 0.58);
  background: rgba(15, 23, 42, 0.46);
}

.dark .sales-analytics-hero-note__label,
.dark .sales-analytics-status-card__label,
.dark .sales-analytics-mini-stat span,
.dark .sales-analytics-focus-row span {
  color: #94a3b8;
}

.dark .sales-analytics-hero-note__text,
.dark .sales-analytics-mini-stat strong,
.dark .sales-analytics-focus-row strong {
  color: #e2e8f0;
}

.dark .sales-analytics-mini-stat,
.dark .sales-analytics-focus-row {
  border-color: rgba(71, 85, 105, 0.5);
  background: rgba(15, 23, 42, 0.62);
}

.dark .sales-analytics-summary-card,
.dark .sales-analytics-product-card,
.dark .sales-analytics-narrative {
  background: rgba(15, 23, 42, 0.42);
}

.dark .sales-analytics-rail {
  border-left-color: rgba(71, 85, 105, 0.48);
}

.dark .sales-overview-points__item {
  border-top-color: rgba(71, 85, 105, 0.48);
}

.dark .sales-overview-points__label {
  color: #94a3b8;
}

.dark .sales-overview-points__value {
  color: #f8fafc;
}

.dark .sales-overview-points__text {
  color: #cbd5e1;
}

@media (max-width: 768px) {
  .sales-analytics-evidence-grid,
  .sales-analytics-summary-grid,
  .sales-overview-points {
    grid-template-columns: 1fr;
  }

  .sales-analytics-rail {
    border-left: 0;
    border-top: 1px solid rgba(148, 163, 184, 0.16);
    padding-left: 0;
    padding-top: 24px;
  }

  .dark .sales-analytics-rail {
    border-top-color: rgba(71, 85, 105, 0.48);
  }

  .sales-analytics-mini-chart {
    width: 100%;
    height: 64px;
  }

  .sales-analytics-status-card {
    padding: 14px;
  }

  .sales-analytics-evidence-card {
    padding: 14px !important;
  }
}
</style>
