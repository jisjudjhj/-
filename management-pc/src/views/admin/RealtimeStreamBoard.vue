<template>
  <div class="page-stack stream-page stream-page--aligned analytics-ui-page defense-page" v-loading="loading">
    <section
      v-if="demoContext"
      class="demo-guide-banner panel-card"
    >
      <div>
        <div class="stream-kicker analytics-kicker">引导</div>
        <div class="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">
          步骤 {{ demoContext.index + 1 }}/{{ demoContext.total }} · {{ demoContext.step.title }}
        </div>
        <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">{{ demoContext.step.spotlightDescription }}</p>
      </div>
      <div class="demo-guide-banner__actions">
        <el-button v-if="demoContext.previous" size="small" @click="goDemoStep(demoContext.previous)">上一步</el-button>
        <el-button v-if="demoContext.next" type="primary" size="small" @click="goDemoStep(demoContext.next)">下一步</el-button>
        <el-button v-else type="success" size="small" @click="goDemoStep(demoContext.step)">重新查看本页</el-button>
        <el-button size="small" plain @click="stopDemoGuide">结束引导</el-button>
      </div>
    </section>

    <DefenseSplitHero
      :class="demoContext ? 'demo-focus-panel' : ''"
      kicker="实时链路"
      title="实时监控"
      description="热榜 / 画像 / 积压 / 告警。"
      badge="event → profile → hot → alert"
      left-eyebrow="离线问题"
      left-title="只看离线不够"
      :left-rows="streamIntroRows"
      right-eyebrow="实时方案"
      right-title="保持链路可见"
      :right-rows="streamSolutionRows"
    >
      <template #left-extra>
        <div class="stream-hero__actions">
          <el-button type="primary" @click="refreshAll">刷新</el-button>
          <el-button @click="goTo('/admin/recommend/preview')">查看推荐预览</el-button>
          <el-button plain @click="startDemoFromHere">查看引导</el-button>
        </div>
      </template>

      <template #right-extra>
        <div class="stream-hero__status mt-5">
          <span v-for="step in pipelineSteps" :key="step.name" class="stream-chip" :class="`is-${step.status}`">
            {{ step.name }}
          </span>
        </div>
      </template>
    </DefenseSplitHero>

    <FeatureBrief
      kicker="实时链路"
      title="判断依据与组成"
      :items="streamFeatureBrief"
    />

    <PageSectionTabs
      v-model="streamActiveTab"
      :tabs="streamTabs"
      primary-label="管理端"
      page-label="实时链路"
      title="专题切换"
      description="总览、画像、状态。"
      :active-label="streamActiveTabInfo.label"
    />

    <div v-if="streamActiveTab === 'overview'" class="grid grid-cols-1 gap-6 xl:grid-cols-[1.8fr_1fr]">
      <GlassCard title="热榜" class="stream-shell-card">
        <template #header>
          <div class="stream-toggle">
            <button
              v-for="item in hotWindowOptions"
              :key="item.value"
              type="button"
              class="stream-toggle__item"
              :class="{ 'is-active': hotWindow === item.value }"
              @click="hotWindow = item.value"
            >
              {{ item.label }}
            </button>
          </div>
        </template>

        <div class="space-y-5">
          <div ref="hotChartRef" class="h-72 w-full"></div>

          <div class="stream-hot-list">
            <button
              v-for="item in visibleHotProducts"
              :key="`${hotWindow}-${item.productId}`"
              type="button"
              class="stream-hot-item"
            >
              <div class="stream-hot-item__rank">{{ item.rank }}</div>
              <img
                v-if="item.productImage"
                :src="item.productImage"
                :alt="item.productName || `商品${item.productId}`"
                class="stream-hot-item__image"
              />
              <div v-else class="stream-hot-item__image stream-hot-item__image--placeholder">
                {{ String(item.productName || item.productId || '--').slice(0, 2) }}
              </div>
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                  {{ item.productName || `商品 #${item.productId}` }}
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
                  <span>{{ item.categoryName || '未分类' }}</span>
                  <span>¥{{ formatPrice(item.price) }}</span>
                  <span>销量 {{ formatNumber(item.salesCount) }}</span>
                </div>
              </div>
              <div class="text-right">
                <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ formatDecimal(item.score) }}</div>
                <div class="text-xs text-slate-500 dark:text-slate-400">热度分</div>
              </div>
            </button>
          </div>
        </div>
      </GlassCard>

      <GlassCard title="状态" class="stream-shell-card">
        <div class="space-y-4">
          <div class="stream-status-grid">
            <div class="stream-status-grid__item">
              <div class="label">实时画像</div>
              <div class="value">{{ overview.status?.realtimeEnabled ? '已开启' : '未开启' }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">Kafka 消费</div>
              <div class="value">{{ overview.status?.kafkaConsumerEnabled ? '已接入' : '未开启' }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">消费组</div>
              <div class="value">{{ overview.status?.consumerGroupId || '--' }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">最后刷新</div>
              <div class="value">{{ formatDateTime(overview.status?.redisHotLastUpdate) }}</div>
            </div>
          </div>

          <div class="stream-status-grid">
            <div class="stream-status-grid__item">
              <div class="label">总积压</div>
              <div class="value">{{ formatNumber(monitorData.consumerLag?.totalLag || 0) }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">死信总量</div>
              <div class="value">{{ formatNumber(monitorData.deadLetter?.totalMessages || 0) }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">监控状态</div>
              <div class="value">{{ monitorData.available ? '在线' : '离线' }}</div>
            </div>
            <div class="stream-status-grid__item">
              <div class="label">监控刷新</div>
              <div class="value">{{ formatDateTime(monitorData.updatedAt) }}</div>
            </div>
          </div>

          <div class="panel-card--muted p-4">
            <div class="mb-2 text-sm font-semibold text-slate-900 dark:text-slate-100">消费积压 Top</div>
            <div class="space-y-2">
              <div v-for="item in visibleLagTopics" :key="item.topic" class="stream-kv-row">
                <span class="stream-kv-row__topic">{{ item.topic }}</span>
                <span class="stream-kv-row__metric">{{ formatNumber(item.lag) }}</span>
              </div>
              <div v-if="!lagTopics.length" class="text-xs text-slate-500 dark:text-slate-400">暂无积压数据</div>
            </div>
          </div>

          <div class="panel-card--muted p-4">
            <div class="mb-2 text-sm font-semibold text-slate-900 dark:text-slate-100">死信队列</div>
            <div class="space-y-2">
              <div v-for="item in visibleDeadLetterTopics" :key="item.topic" class="stream-kv-row">
                <span class="stream-kv-row__topic">{{ item.topic }}</span>
                <span class="stream-kv-row__metric">{{ formatNumber(item.messages) }}</span>
              </div>
              <div v-if="!deadLetterTopics.length" class="text-xs text-slate-500 dark:text-slate-400">暂无死信数据</div>
            </div>
          </div>

          <div class="panel-card--muted p-4">
            <div class="mb-2 text-sm font-semibold text-slate-900 dark:text-slate-100">告警面板</div>
            <div class="space-y-2">
              <div v-for="item in visibleMonitorAlerts" :key="item.code" class="stream-alert" :class="`is-${item.level}`">
                <div class="stream-alert__title">{{ item.message }}</div>
                <div class="stream-alert__meta">值 {{ formatNumber(item.metric) }} / 阈值 {{ formatNumber(item.threshold) }}</div>
              </div>
              <div v-if="!monitorAlerts.length" class="text-xs text-slate-500 dark:text-slate-400">暂无告警</div>
            </div>
          </div>

          <div class="space-y-3">
            <div v-for="step in pipelineSteps" :key="step.name" class="stream-pipeline">
              <div class="stream-pipeline__dot" :class="`is-${step.status}`"></div>
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ step.name }}</div>
                  <el-tag :type="step.status === 'active' ? 'success' : step.status === 'ready' ? 'info' : 'warning'" effect="light">
                    {{ step.status === 'active' ? '运行中' : step.status === 'ready' ? '已接入' : '待开启' }}
                  </el-tag>
                </div>
                <div class="mt-1 text-sm text-slate-600 dark:text-slate-300">{{ step.summary }}</div>
                <div class="mt-1 text-xs break-all text-slate-500 dark:text-slate-400">{{ step.target }}</div>
              </div>
            </div>
          </div>
        </div>
      </GlassCard>
    </div>

    <div v-if="streamActiveTab === 'profile'" class="grid grid-cols-1 gap-6 xl:grid-cols-[1.35fr_1fr]">
      <GlassCard title="画像" class="stream-shell-card">
        <div v-if="selectedSnapshot.user" class="space-y-5">
          <div class="stream-user-spotlight">
            <div class="flex items-center gap-4">
              <el-avatar :size="48" class="stream-user-spotlight__avatar">{{ getUserInitial(selectedSnapshot.user) }}</el-avatar>
              <div>
                <div class="text-lg font-semibold text-slate-900 dark:text-slate-100">
                  {{ selectedSnapshot.user.nickname || selectedSnapshot.user.username || `用户 #${selectedSnapshot.user.id}` }}
                </div>
                <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">刷新 {{ formatDateTime(selectedSnapshot.lastUpdate) }}</div>
              </div>
            </div>

            <div class="stream-user-spotlight__facts">
              <div class="fact"><span>行为总量</span><strong>{{ formatNumber(totalBehaviorCount) }}</strong></div>
              <div class="fact"><span>偏好类目</span><strong>{{ selectedSnapshot.categoryWeights?.length || 0 }}</strong></div>
              <div class="fact"><span>实时标签</span><strong>{{ selectedSnapshot.tags?.length || 0 }}</strong></div>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-5 lg:grid-cols-[1.05fr_1fr]">
            <div class="panel-card--muted p-4">
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">行为分布</div>
              <div ref="behaviorChartRef" class="mt-3 h-72 w-full"></div>
            </div>
            <div class="panel-card--muted p-4">
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">类目权重</div>
              <div ref="categoryChartRef" class="mt-3 h-72 w-full"></div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无实时画像样本" />
      </GlassCard>

      <GlassCard title="标签与规则" class="stream-shell-card">
        <div class="space-y-4">
          <div class="panel-card--muted p-4">
            <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">用户标签</div>
            <div class="mt-3 flex flex-wrap gap-2">
              <el-tag v-for="tag in visibleSnapshotTags" :key="tag" effect="light" round>{{ tag }}</el-tag>
              <span v-if="!visibleSnapshotTags.length" class="text-sm text-slate-500 dark:text-slate-400">暂无标签</span>
            </div>
          </div>

          <div class="panel-card--muted p-4">
            <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">高权重类目</div>
            <div class="mt-3 space-y-3">
              <div v-for="item in visibleTopCategories" :key="item.categoryName" class="stream-score-row">
                <div class="flex items-center justify-between gap-3">
                  <span class="truncate text-sm text-slate-700 dark:text-slate-200">{{ item.categoryName }}</span>
                  <span class="text-xs text-slate-500 dark:text-slate-400">{{ formatDecimal(item.weight) }}</span>
                </div>
                <div class="stream-score-bar"><div class="stream-score-bar__fill" :style="{ width: `${item.ratio}%` }"></div></div>
              </div>
            </div>
          </div>

          <div class="panel-card--muted p-4">
            <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">规则</div>
            <ul class="mt-3 space-y-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
              <li>DWD 清洗，DWS 聚合。</li>
              <li>热度 = 浏览 + 加购 + 收藏 + 支付。</li>
              <li>Redis 输出画像与热榜。</li>
            </ul>
          </div>
        </div>
      </GlassCard>
    </div>

    <GlassCard v-if="streamActiveTab === 'profile'" title="样本用户" class="stream-shell-card">
      <div class="stream-sample-grid">
        <button
          v-for="item in visibleSampleUsers"
          :key="item.user?.id"
          type="button"
          class="stream-sample-card"
          :class="{ 'is-active': selectedUserId === item.user?.id }"
          @click="selectUser(item.user?.id)"
        >
          <div class="flex items-center justify-between gap-3">
            <div>
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">
                {{ item.user?.nickname || item.user?.username || `用户 #${item.user?.id}` }}
              </div>
              <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ formatDateTime(item.lastUpdate) }}</div>
            </div>
            <el-tag effect="plain">行为 {{ formatNumber(item.behaviorEventCount) }}</el-tag>
          </div>
          <div class="mt-4 flex flex-wrap gap-2">
            <span v-for="tag in getUserTagPreview(item)" :key="tag" class="stream-mini-tag">{{ tag }}</span>
          </div>
          <div class="mt-4 text-xs text-slate-500 dark:text-slate-400">Top 类目：{{ item.topCategory || '暂无' }}</div>
        </button>
      </div>
    </GlassCard>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getAdminStreamOverview, getAdminStreamUserSnapshot } from '../../api/admin'
import GlassCard from '../../components/GlassCard.vue'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import DefenseSplitHero from '../../components/DefenseSplitHero.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'
import { subscribeRealtime } from '../../utils/realtime'
import { getAnalyticsChartTheme } from '../../utils/chartTheme'
import {
  DEFENSE_DEMO_STEPS,
  buildDefenseDemoRoute,
  getDefenseDemoContext,
  markDefenseDemoStep,
  readDefenseDemoState,
  startDefenseDemo,
  stopDefenseDemo,
} from '../../utils/workflowGuide'

const route = useRoute()
const router = useRouter()
const streamFeatureBrief = [
  { label: '判断依据', value: 'delay / hot / profile', text: '事件回流后同时看热榜、画像、告警。' },
  { label: '功能组成', value: 'CDC → DWD → DWS → Redis', text: '采集、清洗、聚合、缓存四段。' },
  { label: '输出结果', value: 'hot list + snapshot + alert', text: '给热门推荐、实时画像、异常监控提供信号。' },
]
const loading = ref(false)
const streamTabs = [
  { key: 'overview', label: '监控总览', hint: '状态', description: 'hot、lag、dead letter、alert。' },
  { key: 'profile', label: '实时画像', hint: 'snapshot', description: 'behavior、tag、category_weight。' },
]
const streamTabKeySet = new Set(streamTabs.map(item => item.key))
const normalizeStreamTab = (value) => {
  const tab = String(value || '').trim()
  return streamTabKeySet.has(tab) ? tab : streamTabs[0].key
}
const streamActiveTab = ref(normalizeStreamTab(route.query.tab))
const overview = ref({})
const selectedSnapshot = ref({})
const selectedUserId = ref(null)
const hotWindow = ref('1h')
const compactMode = ref(false)
const hotChartRef = ref(null)
const behaviorChartRef = ref(null)
const categoryChartRef = ref(null)

let hotChart = null
let behaviorChart = null
let categoryChart = null
let themeObserver = null
let unsubscribeStreamRealtime = null
let refreshPromise = null
let compactQuery = null
const demoState = ref(readDefenseDemoState())

const hotWindowOptions = [{ label: '近 1 分钟', value: '1m' }, { label: '近 1 小时', value: '1h' }, { label: '近 1 天', value: '1d' }]
const pipelineSteps = computed(() => overview.value.pipeline || [])
const sampleUsers = computed(() => overview.value.sampleUsers || [])
const monitorData = computed(() => overview.value.monitor || {})
const lagTopics = computed(() => monitorData.value.consumerLag?.topics || [])
const deadLetterTopics = computed(() => monitorData.value.deadLetter?.topics || [])
const monitorAlerts = computed(() => monitorData.value.alerts || [])
const currentHotProducts = computed(() => {
  if (hotWindow.value === '1m') return overview.value.hotProducts1m || []
  if (hotWindow.value === '1d') return overview.value.hotProducts1d || []
  return overview.value.hotProducts1h || []
})
const visibleHotProducts = computed(() => (compactMode.value ? currentHotProducts.value.slice(0, 5) : currentHotProducts.value))
const visibleLagTopics = computed(() => (compactMode.value ? lagTopics.value.slice(0, 3) : lagTopics.value.slice(0, 4)))
const visibleDeadLetterTopics = computed(() => (compactMode.value ? deadLetterTopics.value.slice(0, 3) : deadLetterTopics.value.slice(0, 4)))
const visibleMonitorAlerts = computed(() => (compactMode.value ? monitorAlerts.value.slice(0, 3) : monitorAlerts.value))
const visibleSampleUsers = computed(() => (compactMode.value ? sampleUsers.value.slice(0, 4) : sampleUsers.value))
const visibleSnapshotTags = computed(() => {
  const tags = selectedSnapshot.value.tags || []
  return compactMode.value ? tags.slice(0, 6) : tags
})
const heroMetrics = computed(() => [
  { label: '分钟榜商品', value: formatNumber(overview.value.metrics?.hotProducts1m || 0), sub: 'hot_1m_count' },
  { label: '小时榜商品', value: formatNumber(overview.value.metrics?.hotProducts1h || 0), sub: 'hot_1h_count' },
  { label: '在线画像样本', value: formatNumber(overview.value.metrics?.sampleUsers || 0), sub: 'realtime_profile_count' },
  { label: '消费积压', value: formatNumber(monitorData.value.consumerLag?.totalLag || 0), sub: monitorData.value.available ? 'consumer_lag' : 'monitor_offline' },
])
const streamIntroRows = computed(() => [
  {
    label: '离线局限',
    value: '只看 T+1 报表时，event 是否实时生效不可见。',
  },
  {
    label: '排障成本',
    value: '没有 lag、dead letter、alert，就难定位卡点。',
  },
  {
    label: '经营断层',
    value: 'event 已发生，但 hot/profile 未刷新，实时价值就断层。',
  },
])
const streamSolutionRows = computed(() => [
  {
    label: '链路可见',
    value: `hot_1m = ${formatNumber(overview.value.metrics?.hotProducts1m || 0)}；hot_1h = ${formatNumber(overview.value.metrics?.hotProducts1h || 0)}。`,
  },
  {
    label: '监控告警',
    value: `consumer_lag = ${formatNumber(monitorData.value.consumerLag?.totalLag || 0)}；monitor = ${monitorData.value.available ? 'online' : 'offline'}。`,
  },
  {
    label: '实时画像',
    value: `profile_count = ${formatNumber(overview.value.metrics?.sampleUsers || 0)}；hot/profile/recommend 同页。`,
  },
])
const totalBehaviorCount = computed(() => (selectedSnapshot.value.behaviorDistribution || []).reduce((sum, item) => sum + Number(item.count || 0), 0))
const demoContext = computed(() => {
  if (!demoState.value.active) {
    return null
  }
  return getDefenseDemoContext(route.path)
})
const streamActiveTabInfo = computed(() => streamTabs.find(item => item.key === streamActiveTab.value) || streamTabs[0])
const topCategories = computed(() => {
  const rows = selectedSnapshot.value.categoryWeights || []
  if (!rows.length) return []
  const max = Math.max(...rows.map(item => Number(item.weight || 0)), 1)
  return rows.map(item => ({ ...item, ratio: Math.max(12, Math.round((Number(item.weight || 0) / max) * 100)) }))
})
const visibleTopCategories = computed(() => (compactMode.value ? topCategories.value.slice(0, 4) : topCategories.value.slice(0, 6)))

function syncCompactMode() {
  compactMode.value = compactQuery?.matches || false
}

function getUserTagPreview(item) {
  const tags = item?.tags || []
  return compactMode.value ? tags.slice(0, 3) : tags.slice(0, 4)
}

function formatNumber(value) {
  const number = Number(value || 0)
  return Number.isNaN(number) ? '0' : number.toLocaleString('zh-CN')
}

function formatPrice(value) {
  const number = Number(value || 0)
  return Number.isNaN(number) ? '0.00' : number.toFixed(2)
}

function formatDecimal(value, digits = 1) {
  const number = Number(value || 0)
  return Number.isNaN(number) ? '0.0' : number.toFixed(digits)
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}

function getUserInitial(user) {
  const name = user?.nickname || user?.username || 'U'
  return String(name).slice(0, 1).toUpperCase()
}

function goTo(path) {
  if (router.currentRoute.value.path !== path) router.push(path)
}

function cleanDemoQuery() {
  const query = { ...route.query }
  delete query.workflowGuide
  delete query.workflowStep
  return query
}

function syncDemoStateForRoute() {
  const latest = readDefenseDemoState()
  const context = getDefenseDemoContext(route.path)
  if (context && route.query.workflowGuide === '1') {
    demoState.value = markDefenseDemoStep(context.step.key)
    return
  }
  if (!latest.active || !context) {
    demoState.value = latest
    return
  }
  demoState.value = latest.stepKey === context.step.key
    ? latest
    : markDefenseDemoStep(context.step.key)
}

function goDemoStep(step) {
  if (!step) {
    return
  }
  demoState.value = markDefenseDemoStep(step.key)
  router.push(buildDefenseDemoRoute(step, cleanDemoQuery()))
}

function startDemoFromHere() {
  const context = getDefenseDemoContext(route.path)
  const step = context?.step || DEFENSE_DEMO_STEPS[0]
  demoState.value = startDefenseDemo(step.key)
  router.push(buildDefenseDemoRoute(step, cleanDemoQuery()))
}

function stopDemoGuide() {
  demoState.value = stopDefenseDemo()
  if (route.query.workflowGuide || route.query.workflowStep) {
    router.replace({ path: route.path, query: cleanDemoQuery() })
  }
}

async function refreshAll(options) {
  const silent = Boolean(options && options.silent)
  if (refreshPromise) {
    return refreshPromise
  }

  if (!silent) {
    loading.value = true
  }

  refreshPromise = (async () => {
    overview.value = (await getAdminStreamOverview({ hotLimit: 8, userLimit: 6 })) || {}
    const candidates = overview.value.sampleUsers || []
    if (candidates.length) {
      const nextUserId = selectedUserId.value && candidates.some(item => item.user?.id === selectedUserId.value)
        ? selectedUserId.value
        : candidates[0].user?.id
      await selectUser(nextUserId, false)
    } else {
      selectedUserId.value = null
      selectedSnapshot.value = {}
    }
    await nextTick()
    renderCharts()
  })()

  try {
    await refreshPromise
  } finally {
    refreshPromise = null
    if (!silent) {
      loading.value = false
    }
  }
}

async function selectUser(userId, shouldRender = true) {
  if (!userId) {
    selectedUserId.value = null
    selectedSnapshot.value = {}
    return
  }
  selectedUserId.value = userId
  selectedSnapshot.value = (await getAdminStreamUserSnapshot(userId)) || {}
  if (shouldRender) {
    await nextTick()
    renderCharts()
  }
}

function renderCharts() {
  const theme = getAnalyticsChartTheme()
  const { textColor, splitLineColor, axisColor, surfaceColor, palette } = theme
  const axisLine = { lineStyle: { color: axisColor } }

  hotChart?.dispose()
  behaviorChart?.dispose()
  categoryChart?.dispose()

  if (hotChartRef.value) {
    hotChart = echarts.init(hotChartRef.value)
    const hotRows = visibleHotProducts.value.slice().reverse()
    hotChart.setOption({
      animationDuration: 520,
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '3%', top: 16, bottom: 24, containLabel: true },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } }, axisLabel: { color: textColor } },
      yAxis: { type: 'category', axisLabel: { color: textColor, width: 96, overflow: 'truncate' }, axisLine, data: hotRows.map(item => item.productName || `#${item.productId}`) },
      series: [{
        type: 'bar',
        barWidth: 16,
        data: hotRows.map(item => Number(item.score || 0)),
        itemStyle: {
          borderRadius: [0, 10, 10, 0],
          color: new echarts.graphic.LinearGradient(1, 0, 0, 0, [
            { offset: 0, color: palette.primary },
            { offset: 1, color: palette.secondary },
          ]),
        },
      }],
    })
  }

  if (behaviorChartRef.value) {
    const rows = selectedSnapshot.value.behaviorDistribution || []
    const behaviorColors = [palette.primary, palette.secondary, palette.success, palette.warning, palette.accent, palette.danger]
    behaviorChart = echarts.init(behaviorChartRef.value)
    behaviorChart.setOption({
      animationDuration: 520,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: textColor } },
      series: [{
        type: 'pie',
        radius: ['48%', '74%'],
        center: ['50%', '42%'],
        itemStyle: { borderRadius: 10, borderColor: surfaceColor, borderWidth: 3 },
        label: { formatter: '{b}\n{d}%', color: textColor, fontSize: 11 },
        data: rows.length
          ? rows.map((item, index) => ({
              name: item.behaviorType,
              value: Number(item.count || 0),
              itemStyle: { color: behaviorColors[index % behaviorColors.length] },
            }))
          : [{ name: '暂无数据', value: 1, itemStyle: { color: axisColor } }],
      }],
    })
  }

  if (categoryChartRef.value) {
    const rows = (selectedSnapshot.value.categoryWeights || []).slice(0, 6)
    categoryChart = echarts.init(categoryChartRef.value)
    categoryChart.setOption({
      animationDuration: 520,
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '4%', right: '4%', top: 12, bottom: 24, containLabel: true },
      xAxis: { type: 'category', axisLabel: { color: textColor, interval: 0, rotate: 20 }, axisLine, data: rows.map(item => item.categoryName) },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } }, axisLabel: { color: textColor } },
      series: [{
        type: 'bar',
        barWidth: 24,
        data: rows.map(item => Number(item.weight || 0)),
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: palette.secondary },
            { offset: 1, color: palette.primary },
          ]),
        },
      }],
    })
  }
}

watch(hotWindow, async () => {
  await nextTick()
  renderCharts()
})

watch(compactMode, async () => {
  await nextTick()
  renderCharts()
})

watch(
  () => route.query.tab,
  value => {
    const nextTab = normalizeStreamTab(value)
    if (nextTab !== streamActiveTab.value) {
      streamActiveTab.value = nextTab
    }
  }
)

watch(streamActiveTab, value => {
  const nextTab = normalizeStreamTab(value)
  const currentTab = normalizeStreamTab(route.query.tab)
  if (nextTab !== currentTab) {
    router.replace({
      query: {
        ...route.query,
        tab: nextTab === streamTabs[0].key ? undefined : nextTab,
      },
    })
  }
  nextTick(() => renderCharts())
})

watch(() => route.fullPath, () => {
  syncDemoStateForRoute()
}, { immediate: true })

onMounted(async () => {
  compactQuery = window.matchMedia('(max-width: 768px)')
  compactQuery.addEventListener('change', syncCompactMode)
  syncCompactMode()
  await refreshAll()
  unsubscribeStreamRealtime = subscribeRealtime('/topic/admin/stream/refresh', () => {
    refreshAll({ silent: true })
  })
  window.addEventListener('resize', renderCharts)
  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(mutation => mutation.attributeName === 'class')) nextTick(() => renderCharts())
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

onUnmounted(() => {
  if (compactQuery) {
    compactQuery.removeEventListener('change', syncCompactMode)
  }
  if (typeof unsubscribeStreamRealtime === 'function') {
    unsubscribeStreamRealtime()
    unsubscribeStreamRealtime = null
  }
  window.removeEventListener('resize', renderCharts)
  themeObserver?.disconnect()
  hotChart?.dispose()
  behaviorChart?.dispose()
  categoryChart?.dispose()
})
</script>

<style scoped>
.stream-page--aligned {
  --stream-border: rgba(148, 163, 184, 0.22);
  --stream-soft-bg: rgba(248, 250, 252, 0.92);
}

.stream-hero__main {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(320px, 0.85fr);
  gap: 24px;
  padding: 28px 28px 16px;
}

.stream-hero__title {
  margin: 0;
  font-size: 34px;
  line-height: 1.15;
  font-weight: 700;
  color: #0f172a;
}

.stream-hero__desc {
  margin: 14px 0 0;
  max-width: 700px;
  font-size: 14px;
  line-height: 1.85;
  color: #475569;
}

.stream-hero__actions,
.stream-toggle {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.stream-hero__actions {
  margin-top: 22px;
}

.stream-hero__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stream-metric,
.stream-hot-item,
.stream-sample-card,
.stream-user-spotlight {
  border: 1px solid var(--stream-border);
  background: rgba(255, 255, 255, 0.76);
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.stream-metric {
  border-radius: 20px;
  padding: 16px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.stream-metric:hover,
.stream-hot-item:hover,
.stream-sample-card:hover,
.stream-sample-card.is-active {
  transform: translateY(-2px);
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.08);
  border-color: rgba(96, 165, 250, 0.35);
}

.stream-metric__label {
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
}

.stream-metric__value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.stream-metric__sub {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.stream-hero__status {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 0 28px 24px;
}

.stream-chip,
.stream-toggle__item,
.stream-mini-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
}

.stream-chip {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid var(--stream-border);
  background: rgba(255, 255, 255, 0.78);
  color: #334155;
}

.stream-chip.is-active {
  color: #0f766e;
  background: rgba(240, 253, 250, 0.92);
  border-color: rgba(45, 212, 191, 0.4);
}

.stream-chip.is-ready {
  color: #0369a1;
  background: rgba(240, 249, 255, 0.92);
  border-color: rgba(125, 211, 252, 0.45);
}

.stream-chip.is-idle {
  color: #92400e;
  background: rgba(255, 251, 235, 0.92);
  border-color: rgba(252, 211, 77, 0.45);
}

.stream-toggle__item {
  padding: 8px 12px;
  border: 1px solid rgba(148, 163, 184, 0.34);
  background: rgba(255, 255, 255, 0.76);
  font-size: 12px;
  color: #475569;
  transition: all 180ms ease;
}

.stream-toggle__item.is-active {
  border-color: rgba(59, 130, 246, 0.45);
  background: rgba(239, 248, 255, 0.98);
  color: #0369a1;
  box-shadow: 0 10px 22px rgba(59, 130, 246, 0.14);
}

.stream-hot-list,
.stream-sample-grid {
  display: grid;
  gap: 12px;
}

.stream-hot-item {
  display: flex;
  align-items: center;
  gap: 14px;
  border-radius: 18px;
  padding: 12px 14px;
}

.stream-hot-item__rank {
  width: 30px;
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
  text-align: center;
}

.stream-hot-item__image {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  object-fit: cover;
  border: 1px solid var(--stream-border);
  background: #fff;
}

.stream-hot-item__image--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 700;
  color: #0f766e;
  background: linear-gradient(135deg, rgba(204, 251, 241, 0.9), rgba(236, 253, 245, 0.9));
}

.stream-status-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.stream-status-grid__item,
.stream-user-spotlight__facts .fact {
  border-radius: 16px;
  padding: 14px;
  background: rgba(248, 250, 252, 0.94);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.stream-status-grid__item .label,
.stream-user-spotlight__facts .fact span {
  font-size: 12px;
  color: #64748b;
}

.stream-status-grid__item .value,
.stream-user-spotlight__facts .fact strong {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.stream-kv-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid var(--stream-border);
}

.stream-kv-row__topic {
  max-width: 72%;
  font-size: 12px;
  color: #475569;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stream-kv-row__metric {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.stream-alert {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--stream-border);
  background: rgba(255, 255, 255, 0.76);
}

.stream-alert.is-warning {
  border-color: rgba(245, 158, 11, 0.48);
  background: rgba(255, 251, 235, 0.92);
}

.stream-alert.is-critical {
  border-color: rgba(239, 68, 68, 0.48);
  background: rgba(254, 242, 242, 0.92);
}

.stream-alert__title {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.stream-alert__meta {
  margin-top: 4px;
  font-size: 11px;
  color: #64748b;
}

.stream-pipeline {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 0;
}

.stream-pipeline__dot {
  width: 11px;
  height: 11px;
  margin-top: 7px;
  border-radius: 999px;
  background: #fbbf24;
  box-shadow: 0 0 0 6px rgba(251, 191, 36, 0.12);
}

.stream-pipeline__dot.is-ready {
  background: #38bdf8;
  box-shadow: 0 0 0 6px rgba(56, 189, 248, 0.12);
}

.stream-pipeline__dot.is-active {
  background: #14b8a6;
  box-shadow: 0 0 0 6px rgba(20, 184, 166, 0.12);
}

.stream-user-spotlight {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-radius: 22px;
  padding: 18px;
}

.stream-user-spotlight__avatar {
  background: linear-gradient(135deg, #4fa9ff, #b8e1ff);
  color: #fff;
}

.stream-user-spotlight__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.stream-score-row + .stream-score-row {
  margin-top: 12px;
}

.stream-score-bar {
  height: 8px;
  margin-top: 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.24);
  overflow: hidden;
}

.stream-score-bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #7cc6ff, #4fa9ff);
  transition: width 360ms ease-in-out;
}

.stream-sample-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.stream-sample-card {
  border-radius: 20px;
  padding: 16px;
  text-align: left;
}

.stream-mini-tag {
  padding: 5px 8px;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background: rgba(240, 249, 255, 0.98);
  color: #0c4a6e;
  font-size: 12px;
}

.demo-guide-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-color: rgba(125, 211, 252, 0.6);
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.05), rgba(14, 165, 233, 0.03)),
    var(--panel-bg);
}

.demo-guide-banner__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.demo-focus-panel {
  position: relative;
  box-shadow: 0 24px 48px rgba(30, 64, 175, 0.12);
}

.demo-focus-panel::after {
  content: '';
  position: absolute;
  inset: 8px;
  border: 2px dashed rgba(14, 116, 144, 0.36);
  border-radius: 22px;
  pointer-events: none;
}

:deep(.stream-shell-card > header) {
  border-bottom-color: rgba(148, 163, 184, 0.24) !important;
}

:deep(.stream-shell-card .panel-card--muted) {
  border-color: rgba(148, 163, 184, 0.22);
  background: var(--stream-soft-bg);
}

.dark .demo-guide-banner {
  border-color: rgba(56, 189, 248, 0.45);
  background: linear-gradient(135deg, rgba(8, 47, 73, 0.72), rgba(12, 74, 110, 0.6));
}

.dark .demo-focus-panel {
  box-shadow: 0 24px 48px rgba(14, 165, 233, 0.15);
}

.dark .demo-focus-panel::after {
  border-color: rgba(56, 189, 248, 0.45);
}

.dark .stream-hero__title,
.dark .stream-metric__value,
.dark .stream-hot-item__rank,
.dark .stream-status-grid__item .value,
.dark .stream-user-spotlight__facts .fact strong {
  color: #e5edf7;
}

.dark .stream-hero__desc,
.dark .stream-metric__sub,
.dark .stream-toggle__item,
.dark .stream-status-grid__item .label,
.dark .stream-user-spotlight__facts .fact span {
  color: #94a3b8;
}

.dark .stream-metric,
.dark .stream-hot-item,
.dark .stream-sample-card,
.dark .stream-user-spotlight {
  background: rgba(15, 23, 42, 0.76);
  border-color: rgba(71, 85, 105, 0.58);
}

.dark .stream-status-grid__item,
.dark .stream-user-spotlight__facts .fact {
  background: rgba(17, 24, 39, 0.94);
  border-color: rgba(71, 85, 105, 0.62);
}

.dark .stream-kv-row {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(71, 85, 105, 0.72);
}

.dark .stream-kv-row__topic {
  color: #cbd5e1;
}

.dark .stream-kv-row__metric {
  color: #e5edf7;
}

.dark .stream-alert {
  background: rgba(15, 23, 42, 0.72);
  border-color: rgba(71, 85, 105, 0.72);
}

.dark .stream-alert.is-warning {
  background: rgba(120, 53, 15, 0.2);
  border-color: rgba(245, 158, 11, 0.48);
}

.dark .stream-alert.is-critical {
  background: rgba(127, 29, 29, 0.2);
  border-color: rgba(239, 68, 68, 0.48);
}

.dark .stream-alert__title {
  color: #e5edf7;
}

.dark .stream-alert__meta {
  color: #cbd5e1;
}

.dark .stream-toggle__item.is-active {
  background: rgba(15, 23, 42, 0.9);
  color: #bae6fd;
}

.dark .stream-mini-tag {
  border-color: rgba(71, 85, 105, 0.64);
  background: rgba(15, 23, 42, 0.64);
  color: #bae6fd;
}

@media (max-width: 1279px) {
  .stream-hero__main {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 1024px) {
  .stream-sample-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stream-user-spotlight {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 768px) {
  .stream-hero__main,
  .stream-hero__status {
    padding-left: 18px;
    padding-right: 18px;
  }

  .stream-hero__title {
    font-size: 28px;
  }

  .stream-hero__metrics,
  .stream-status-grid,
  .stream-sample-grid {
    grid-template-columns: 1fr;
  }
}
</style>
