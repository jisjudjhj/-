<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAiConfig,
  testAiConnection,
  getAiReviewSummary,
  askAiProductQA,
  getAiModuleSwitches,
  updateAiModuleSwitch,
  sendAiChat,
} from '../../api/ai'
import { getAdminRecommendAnalytics, getAdminKmeansSummary } from '../../api/admin'
import GlassCard from '../../components/GlassCard.vue'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'

const pageLoading = ref(false)
const chatLoading = ref(false)
const testLoading = ref(false)
const summaryLoading = ref(false)
const qaLoading = ref(false)
const chatBoxRef = ref(null)

const aiConfig = ref({})
const moduleSwitches = ref({
  'ai-chat': true,
  'ai-merchant-copilot': true,
  'ai-review-summary': true,
  'ai-product-qa': true,
})
const switchLoading = ref({})
const recommendAnalytics = ref({})
const kmeansSummary = ref({})
const testMessage = ref('请用一句话总结当前 AI 服务状态')
const testResult = ref(null)
const summaryProductId = ref('')
const summaryResult = ref('')
const qaProductId = ref('')
const qaQuestion = ref('')
const qaResult = ref('')
const chatInput = ref('')
const chatMessages = ref([])
const activeAiTab = ref('overview')
const competitionMode = import.meta.env.VITE_COMPETITION_MODE !== 'false'

const moduleList = [
  ['ai-chat', '用户导购对话', '对话 / 推荐 / 追问'],
  ['ai-merchant-copilot', '商家运营 Copilot', '文案 / 卖点 / 建议'],
  ['ai-review-summary', '评价智能摘要', '优点 / 风险 / 人群'],
  ['ai-product-qa', '商品问答', '指定商品问答'],
]

const performanceSummary = computed(() => recommendAnalytics.value?.performance?.summary || {})
const clusterTask = computed(() => kmeansSummary.value?.task || {})
const clusterOverview = computed(() => kmeansSummary.value?.llmOverview || {})
const latestAssistant = computed(() => [...chatMessages.value].reverse().find(item => item.role === 'assistant') || null)
const enabledModuleCount = computed(() => Object.values(moduleSwitches.value).filter(Boolean).length)
const aiPageTabs = [
  {
    key: 'overview',
    label: '实例总览',
    hint: '状态 + 分群 + 摘要',
    description: '状态、联动、摘要。',
  },
  {
    key: 'copilot',
    label: 'AI 导购工作台',
    hint: '对话 + 推荐结果',
    description: '对话、商品、线索。',
  },
  {
    key: 'modules',
    label: '模块开关',
    hint: '能力矩阵 + 服务配置',
    description: '开关、配置、摘要。',
  },
  {
    key: 'tools',
    label: '工具验证',
    hint: '连通检查 + QA / 摘要',
    description: '连通、摘要、问答。',
  },
]
const activeAiTabInfo = computed(() => aiPageTabs.find(item => item.key === activeAiTab.value) || aiPageTabs[0])
const displayAiPageTabs = computed(() =>
  competitionMode
    ? aiPageTabs.filter(item => item.key === 'overview' || item.key === 'copilot')
    : aiPageTabs
)
const aiServiceStatusLabel = computed(() => {
  const status = String(aiConfig.value.status || '').trim().toLowerCase()
  if (!status) return '待检测'
  if (['up', 'ok', 'online', 'enabled', 'success', 'connected'].includes(status)) return '已连接'
  if (['down', 'error', 'failed', 'offline'].includes(status)) return '未连接'
  if (['missing', 'unconfigured', 'disabled'].includes(status)) return '未配置'
  return status.toUpperCase()
})
const aiModelLabel = computed(() => String(aiConfig.value.model || '').trim() || '未配置模型')
const aiApiUrlLabel = computed(() => String(aiConfig.value.apiUrl || '').trim() || '尚未配置 API 地址')
const aiSegmentCountLabel = computed(() => kmeansSummary.value?.segmentCount || clusterTask.value?.clusterCount || '待生成')
const aiFeatureBrief = computed(() => [
  { label: '判断依据', value: `${aiServiceStatusLabel.value} · ${aiModelLabel.value}`, text: '看状态、地址、指标。' },
  { label: '功能组成', value: '对话 / 摘要 / 问答 / 开关', text: '统一管理。' },
  { label: '输出结果', value: `${formatPercent(performanceSummary.value.conversionRate)} 转化`, text: `曝光 ${formatNumber(performanceSummary.value.exposureCount)} · 分群 ${aiSegmentCountLabel.value}` },
])

async function loadAll() {
  pageLoading.value = true
  try {
    const [configRes, switchRes, recommendRes, kmeansRes] = await Promise.allSettled([
      getAiConfig(),
      getAiModuleSwitches(),
      getAdminRecommendAnalytics(30),
      getAdminKmeansSummary(),
    ])
    aiConfig.value = configRes.status === 'fulfilled' ? (configRes.value || {}) : {}
    if (aiConfig.value.modules) {
      moduleSwitches.value = { ...moduleSwitches.value, ...aiConfig.value.modules }
    }
    if (switchRes.status === 'fulfilled' && switchRes.value) {
      const nextState = { ...moduleSwitches.value }
      Object.keys(nextState).forEach(key => {
        if (typeof switchRes.value[key]?.enabled === 'boolean') {
          nextState[key] = switchRes.value[key].enabled
        }
      })
      moduleSwitches.value = nextState
    }
    recommendAnalytics.value = recommendRes.status === 'fulfilled' ? (recommendRes.value || {}) : {}
    kmeansSummary.value = kmeansRes.status === 'fulfilled' ? (kmeansRes.value || {}) : {}
  } finally {
    pageLoading.value = false
  }
}

async function handleSwitch(module, value) {
  switchLoading.value = { ...switchLoading.value, [module]: true }
  try {
    await updateAiModuleSwitch(module, value)
    ElMessage.success(`${module} 已${value ? '开启' : '关闭'}`)
  } catch (error) {
    moduleSwitches.value = { ...moduleSwitches.value, [module]: !value }
    ElMessage.error('模块状态更新失败')
  } finally {
    switchLoading.value = { ...switchLoading.value, [module]: false }
  }
}

async function handleTest() {
  if (!testMessage.value.trim()) return ElMessage.warning('请输入检查消息')
  testLoading.value = true
  try {
    testResult.value = await testAiConnection(testMessage.value.trim())
  } catch (error) {
    testResult.value = { status: 'error', error: error.message || '请求失败' }
  } finally {
    testLoading.value = false
  }
}

async function handleSummary() {
  if (!summaryProductId.value) return ElMessage.warning('请输入商品 ID')
  summaryLoading.value = true
  try {
    summaryResult.value = await getAiReviewSummary(summaryProductId.value)
  } finally {
    summaryLoading.value = false
  }
}

async function handleQA() {
  if (!qaProductId.value || !qaQuestion.value.trim()) return ElMessage.warning('请输入商品 ID 和问题')
  qaLoading.value = true
  try {
    qaResult.value = await askAiProductQA(qaProductId.value, qaQuestion.value.trim())
  } finally {
    qaLoading.value = false
  }
}

async function submitChat(prompt) {
  const text = typeof prompt === 'string' ? prompt.trim() : chatInput.value.trim()
  if (!text || chatLoading.value) return
  const history = chatMessages.value.slice(-10).map(item => ({ role: item.role, content: item.content }))
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  chatLoading.value = true
  try {
    const res = await sendAiChat(text, history)
    chatMessages.value.push({
      role: 'assistant',
      content: sanitizeReply(res?.reply),
      products: Array.isArray(res?.products) ? res.products : [],
      shoppingBrief: res?.shoppingBrief || {},
      insightCards: Array.isArray(res?.insightCards) ? res.insightCards : [],
      nextActions: Array.isArray(res?.nextActions) ? res.nextActions : [],
      suggestedPrompts: Array.isArray(res?.suggestedPrompts) ? res.suggestedPrompts : [],
      strategyLabel: res?.strategyLabel || '',
      personaCard: res?.personaCard || {},
      needClarification: !!res?.needClarification,
      clarificationQuestion: res?.clarificationQuestion || '',
    })
    await nextTick()
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  } catch (error) {
    chatMessages.value.push({ role: 'assistant', content: '请求失败，请稍后重试。', products: [], shoppingBrief: {}, insightCards: [], nextActions: [], suggestedPrompts: [] })
  } finally {
    chatLoading.value = false
  }
}

function handleAction(action) {
  if (!action) return
  if (action.type === 'view_product' && action.productId) {
    qaProductId.value = String(action.productId)
    ElMessage.success(`已将商品 ${action.productId} 带入问答区`)
    return
  }
  if (action.prompt) submitChat(action.prompt)
}

function sanitizeReply(reply) {
  return (reply || '暂无 AI 回复').replace(/\[([^\]]+)\]\(product:\d+\)/g, '$1')
}
function formatNumber(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '0'
}
function formatPercent(value) {
  const n = Number(value || 0)
  return `${n.toFixed(2)}%`
}
function formatPrice(value) {
  const n = Number(value || 0)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: n % 1 === 0 ? 0 : 2, maximumFractionDigits: 2 }) : '--'
}
function joinList(value) {
  return Array.isArray(value) && value.length ? value.join(' / ') : '--'
}

onMounted(() => {
  if (competitionMode) {
    activeAiTab.value = 'copilot'
  }
  loadAll()
})
</script>

<template>
  <div v-loading="pageLoading" class="ai-page analytics-ui-page space-y-5">
    <section class="analysis-page-header">
      <h1 class="analysis-page-header__title">智能助手</h1>
      <div class="analysis-page-header__meta">模型服务 · 推荐联动 · 分群摘要 · 工具验证</div>
    </section>

    <FeatureBrief
      kicker="智能助手"
      title="判断依据与组成"
      :items="aiFeatureBrief"
    />

    <PageSectionTabs
      v-model="activeAiTab"
      primary-label="管理端"
      page-label="AI 助手页"
      title="页面导航"
      description="总览、导购、开关、验证。"
      :tabs="displayAiPageTabs"
      :active-label="activeAiTabInfo.label"
    />

    <div v-if="activeAiTab === 'overview'" class="grid grid-cols-1 gap-5 xl:grid-cols-[1.08fr_0.92fr]">
      <GlassCard title="AI 服务联动概览">
        <div class="grid gap-4 lg:grid-cols-2">
          <article class="rounded-2xl border border-slate-200 bg-slate-50/80 p-4 dark:border-slate-700 dark:bg-slate-900/35">
            <div class="text-xs uppercase tracking-[0.22em] text-slate-400">服务状态</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ aiServiceStatusLabel }}</div>
            <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">模型 {{ aiModelLabel }}</div>
            <div class="mt-1 text-xs text-slate-400">{{ aiApiUrlLabel }}</div>
          </article>
          <article class="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4 dark:border-emerald-800/40 dark:bg-emerald-900/20">
            <div class="text-xs uppercase tracking-[0.22em] text-emerald-600 dark:text-emerald-300">推荐联动</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ formatPercent(performanceSummary.conversionRate) }}</div>
            <div class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">
              推荐曝光 {{ formatNumber(performanceSummary.exposureCount) }}，成功率 {{ formatPercent(performanceSummary.recommendationSuccessRate) }}
            </div>
          </article>
          <article class="rounded-2xl border border-cyan-200 bg-cyan-50/80 p-4 dark:border-cyan-800/40 dark:bg-cyan-900/20">
            <div class="text-xs uppercase tracking-[0.22em] text-cyan-600 dark:text-cyan-300">分群洞察</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ aiSegmentCountLabel }} 类</div>
            <div class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ clusterOverview.overallSummary || '分群结果进入推荐排序。' }}</div>
          </article>
          <article class="rounded-2xl border border-violet-200 bg-violet-50/80 p-4 dark:border-violet-800/40 dark:bg-violet-900/20">
            <div class="text-xs uppercase tracking-[0.22em] text-violet-600 dark:text-violet-300">模块启用率</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ enabledModuleCount }}/{{ moduleList.length }}</div>
            <div class="mt-2 text-sm text-slate-600 dark:text-slate-300">已启用 {{ enabledModuleCount }}/{{ moduleList.length }}</div>
          </article>
        </div>
        <div class="mt-4 rounded-2xl border border-dashed border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-600 dark:border-slate-700 dark:bg-slate-900/35 dark:text-slate-300">
          查看顺序：总览 → 导购 → 开关 → 验证。
        </div>
      </GlassCard>

      <div class="space-y-5">
        <GlassCard title="最近一轮结果摘要">
          <div v-if="latestAssistant" class="space-y-2.5">
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">回复摘要</div>
              <div class="mt-2 text-sm leading-6 text-slate-700 dark:text-slate-200">{{ latestAssistant.content }}</div>
            </div>
            <div class="grid gap-2.5 md:grid-cols-2">
              <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">推荐商品数</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.products?.length || 0 }}</div></div>
              <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">是否需要追问</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.needClarification ? '是' : '否' }}</div></div>
            </div>
          </div>
          <el-empty v-else description="先发起一轮 AI 对话" />
        </GlassCard>

        <GlassCard title="切换建议">
          <div class="grid gap-2.5">
            <el-button plain @click="activeAiTab = 'copilot'">去 AI 导购工作台</el-button>
            <el-button plain @click="activeAiTab = 'modules'">去模块开关</el-button>
            <el-button plain @click="activeAiTab = 'tools'">去工具验证</el-button>
          </div>
        </GlassCard>
      </div>
    </div>

    <div v-else-if="activeAiTab === 'copilot'" class="grid grid-cols-1 gap-7 xl:grid-cols-[1.28fr_0.72fr]">
      <section class="ai-section ai-section--main">
        <div class="ai-section__header">
          <h3 class="ai-section__title">AI 导购工作台</h3>
          <p class="ai-section__desc">意图、商品、线索。</p>
        </div>
        <div class="space-y-3">
          <div ref="chatBoxRef" class="max-h-[500px] overflow-y-auto rounded-2xl border border-slate-200/80 bg-slate-50/85 p-3 dark:border-slate-700/60 dark:bg-slate-900/35">
            <div v-if="chatMessages.length === 0" class="rounded-2xl border border-dashed border-slate-300 bg-white/80 p-4 text-[13px] leading-6 text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300">
              示例：<br>1. 500 元以内蓝牙耳机<br>2. 适合通勤的护眼台灯<br>3. 高销量送礼候选
            </div>
            <div v-for="(msg, index) in chatMessages" :key="`${msg.role}-${index}`" class="mb-3 last:mb-0">
              <div class="mb-1.5 text-xs uppercase tracking-[0.2em] text-slate-400">{{ msg.role === 'user' ? 'User' : 'Assistant' }}</div>
              <div class="rounded-2xl px-4 py-3 text-sm leading-6" :class="msg.role === 'user' ? 'bg-slate-950 text-white' : 'border border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-800/85 dark:text-slate-200'">
                {{ msg.content }}
              </div>
              <div v-if="msg.role === 'assistant'" class="mt-2.5 space-y-2.5">
                <div v-if="msg.strategyLabel" class="inline-flex rounded-full bg-cyan-50 px-3 py-1 text-xs font-semibold text-cyan-700 dark:bg-cyan-900/30 dark:text-cyan-300">当前策略：{{ msg.strategyLabel }}</div>
                <div v-if="Object.keys(msg.shoppingBrief || {}).length" class="grid gap-2.5 rounded-2xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35 md:grid-cols-2">
                  <div><div class="text-xs text-slate-400">需求摘要</div><div class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ msg.shoppingBrief.summary || '--' }}</div></div>
                  <div><div class="text-xs text-slate-400">品类 / 预算</div><div class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ msg.shoppingBrief.category || '--' }} / {{ msg.shoppingBrief.budget || '未指定预算' }}</div></div>
                  <div><div class="text-xs text-slate-400">品牌偏好</div><div class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ joinList(msg.shoppingBrief.brands) }}</div></div>
                  <div><div class="text-xs text-slate-400">场景 / 关键词</div><div class="mt-1 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ joinList(msg.shoppingBrief.scenes) }} / {{ joinList(msg.shoppingBrief.keywords) }}</div></div>
                </div>
                <div v-if="Object.keys(msg.personaCard || {}).length" class="rounded-2xl border border-emerald-200 bg-emerald-50/80 p-3 dark:border-emerald-800/40 dark:bg-emerald-900/20">
                  <div class="text-xs uppercase tracking-[0.24em] text-emerald-600 dark:text-emerald-300">画像线索</div>
                  <div class="mt-1.5 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ msg.personaCard.segmentName || '当前兴趣画像' }}</div>
                  <p class="mt-1.5 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ msg.personaCard.summary || msg.personaCard.strategyHint }}</p>
                </div>
                <div v-if="msg.insightCards?.length" class="grid gap-2.5 md:grid-cols-3">
                  <div v-for="card in msg.insightCards" :key="card.title" class="rounded-2xl border border-slate-200 bg-white/85 p-3 dark:border-slate-700 dark:bg-slate-900/35">
                    <div class="text-xs text-slate-400">{{ card.title }}</div>
                    <div class="mt-1.5 text-sm font-semibold text-slate-900 dark:text-slate-100">{{ card.value }}</div>
                    <div class="mt-1.5 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ card.description }}</div>
                  </div>
                </div>
                <div v-if="msg.products?.length" class="grid gap-2.5">
                  <div v-for="product in msg.products" :key="product.id" class="rounded-2xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-900/35">
                    <div class="flex items-start justify-between gap-4">
                      <div class="min-w-0"><div class="text-base font-semibold text-slate-900 dark:text-slate-100">{{ product.name }}</div><div class="mt-1.5 text-sm leading-6 text-slate-500 dark:text-slate-400">{{ product.recommendReason || '已进入候选池' }}</div></div>
                      <div class="shrink-0 rounded-xl bg-slate-950 px-2.5 py-1.5 text-sm font-semibold text-white">¥{{ formatPrice(product.price) }}</div>
                    </div>
                  </div>
                </div>
                <div v-if="msg.nextActions?.length" class="grid gap-2.5 md:grid-cols-2">
                  <button v-for="action in msg.nextActions" :key="action.key" type="button" class="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-left transition hover:border-cyan-300 hover:bg-cyan-50 dark:border-slate-700 dark:bg-slate-800 dark:hover:border-cyan-700 dark:hover:bg-cyan-900/20" @click="handleAction(action)">
                    <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ action.label }}</div>
                    <div class="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ action.description }}</div>
                  </button>
                </div>
                <div v-if="msg.suggestedPrompts?.length" class="flex flex-wrap gap-2">
                  <el-button v-for="prompt in msg.suggestedPrompts" :key="prompt" size="small" round @click="submitChat(prompt)">{{ prompt }}</el-button>
                </div>
                <div v-if="msg.needClarification && msg.clarificationQuestion" class="rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 text-sm leading-6 text-amber-800 dark:border-amber-800/50 dark:bg-amber-900/20 dark:text-amber-200">{{ msg.clarificationQuestion }}</div>
              </div>
            </div>
            <div v-if="chatLoading" class="rounded-2xl border border-dashed border-slate-300 bg-white/80 px-4 py-3 text-sm text-slate-500 dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300">AI 正在生成导购结果...</div>
          </div>
          <div class="flex items-end gap-2.5">
            <el-input v-model="chatInput" type="textarea" :rows="3" resize="none" placeholder="例如：帮我找适合送礼、预算 400 元以内的蓝牙耳机" @keyup.enter.exact.prevent="submitChat()" />
            <el-button type="primary" class="self-end !px-4" :loading="chatLoading" @click="submitChat()">发送</el-button>
          </div>
        </div>
      </section>

      <div class="space-y-7">
        <section class="ai-section">
          <div class="ai-section__header">
            <h3 class="ai-section__title">模块开关</h3>
          </div>
          <div class="divide-y divide-slate-200 dark:divide-slate-700">
            <div v-for="item in moduleList" :key="item[0]" class="py-3 first:pt-0 last:pb-0">
              <div class="flex items-start justify-between gap-4">
                <div><div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item[1] }}</div><div class="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ item[2] }}</div></div>
                <el-switch :model-value="moduleSwitches[item[0]]" :loading="switchLoading[item[0]]" @change="value => handleSwitch(item[0], value)" />
              </div>
            </div>
          </div>
        </section>

        <section class="ai-section">
          <div class="ai-section__header">
            <h3 class="ai-section__title">AI 工具验证</h3>
          </div>
          <div class="space-y-3">
            <div>
              <div class="text-xs text-slate-400">连通检查</div>
              <div class="mt-2 flex gap-3">
                <el-input v-model="testMessage" placeholder="输入检查消息" @keyup.enter="handleTest" />
                <el-button type="primary" :loading="testLoading" @click="handleTest">执行</el-button>
              </div>
              <div v-if="testResult" class="mt-3 rounded-2xl border px-4 py-3 text-sm leading-6" :class="testResult.status === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800/40 dark:bg-emerald-900/20 dark:text-emerald-300' : 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800/40 dark:bg-rose-900/20 dark:text-rose-300'">
                {{ testResult.status === 'success' ? testResult.reply : testResult.error }}
              </div>
            </div>
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">评价摘要</div>
              <div class="mt-2 flex gap-3">
                <el-input v-model="summaryProductId" placeholder="商品 ID" />
                <el-button type="success" :loading="summaryLoading" @click="handleSummary">生成</el-button>
              </div>
              <div v-if="summaryResult" class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ summaryResult }}</div>
            </div>
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">商品问答</div>
              <div class="mt-2 space-y-2.5">
                <el-input v-model="qaProductId" placeholder="商品 ID" />
                <el-input v-model="qaQuestion" type="textarea" :rows="3" resize="none" placeholder="例如：这款更适合通勤还是送礼？" />
                <el-button type="warning" :loading="qaLoading" @click="handleQA">提问</el-button>
                <div v-if="qaResult" class="text-sm leading-6 text-slate-600 dark:text-slate-300">{{ qaResult }}</div>
              </div>
            </div>
          </div>
        </section>

        <section class="ai-section">
          <div class="ai-section__header">
            <h3 class="ai-section__title">最近一轮结果摘要</h3>
          </div>
          <div v-if="latestAssistant" class="space-y-2.5">
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">回复摘要</div>
              <div class="mt-2 text-sm leading-6 text-slate-700 dark:text-slate-200">{{ latestAssistant.content }}</div>
            </div>
            <div class="grid gap-2.5 md:grid-cols-2">
              <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">推荐商品数</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.products?.length || 0 }}</div></div>
              <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">是否需要追问</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.needClarification ? '是' : '否' }}</div></div>
            </div>
          </div>
          <el-empty v-else description="先发起一轮 AI 对话" />
        </section>
      </div>
    </div>

    <div v-else-if="activeAiTab === 'modules'" class="grid grid-cols-1 gap-5 xl:grid-cols-[1.02fr_0.98fr]">
      <GlassCard title="模块开关">
        <div class="space-y-2.5">
          <div v-for="item in moduleList" :key="`modules-${item[0]}`" class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70">
            <div class="flex items-start justify-between gap-4">
              <div><div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item[1] }}</div><div class="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ item[2] }}</div></div>
              <el-switch :model-value="moduleSwitches[item[0]]" :loading="switchLoading[item[0]]" @change="value => handleSwitch(item[0], value)" />
            </div>
          </div>
        </div>
      </GlassCard>

      <div class="space-y-5">
        <GlassCard title="服务与推荐摘要">
          <div class="grid gap-3 md:grid-cols-2">
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">服务状态</div>
              <div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ aiServiceStatusLabel }}</div>
              <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ aiApiUrlLabel }}</div>
            </div>
            <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
              <div class="text-xs text-slate-400">模型配置</div>
              <div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ aiModelLabel }}</div>
              <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">已启用 {{ enabledModuleCount }}/{{ moduleList.length }} 个模块</div>
            </div>
          </div>
          <div class="mt-4 rounded-2xl border border-dashed border-slate-200 bg-slate-50/80 px-4 py-3 text-sm leading-6 text-slate-600 dark:border-slate-700 dark:bg-slate-900/35 dark:text-slate-300">
            {{ clusterOverview.overallSummary || '分群结果进入推荐排序。' }}
          </div>
        </GlassCard>

        <GlassCard title="当前模块状态">
          <div class="grid gap-2.5">
            <div v-for="item in moduleList" :key="`status-${item[0]}`" class="flex items-center justify-between rounded-xl border border-slate-200 bg-white/90 px-3 py-3 dark:border-slate-700 dark:bg-slate-800/70">
              <div>
                <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item[1] }}</div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ item[2] }}</div>
              </div>
              <el-tag :type="moduleSwitches[item[0]] ? 'success' : 'info'" effect="plain" round>
                {{ moduleSwitches[item[0]] ? '已开启' : '已关闭' }}
              </el-tag>
            </div>
          </div>
        </GlassCard>
      </div>
    </div>

    <div v-else class="grid grid-cols-1 gap-5 xl:grid-cols-[1fr_0.82fr]">
      <GlassCard title="AI 工具验证">
        <div class="space-y-3">
          <div>
            <div class="text-xs text-slate-400">连通检查</div>
            <div class="mt-2 flex gap-3">
              <el-input v-model="testMessage" placeholder="输入检查消息" @keyup.enter="handleTest" />
              <el-button type="primary" :loading="testLoading" @click="handleTest">执行</el-button>
            </div>
            <div v-if="testResult" class="mt-3 rounded-2xl border px-4 py-3 text-sm leading-6" :class="testResult.status === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800/40 dark:bg-emerald-900/20 dark:text-emerald-300' : 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800/40 dark:bg-rose-900/20 dark:text-rose-300'">
              {{ testResult.status === 'success' ? testResult.reply : testResult.error }}
            </div>
          </div>
          <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
            <div class="text-xs text-slate-400">评价摘要</div>
            <div class="mt-2 flex gap-3">
              <el-input v-model="summaryProductId" placeholder="商品 ID" />
              <el-button type="success" :loading="summaryLoading" @click="handleSummary">生成</el-button>
            </div>
            <div v-if="summaryResult" class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ summaryResult }}</div>
          </div>
          <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
            <div class="text-xs text-slate-400">商品问答</div>
            <div class="mt-2 space-y-2.5">
              <el-input v-model="qaProductId" placeholder="商品 ID" />
              <el-input v-model="qaQuestion" type="textarea" :rows="3" resize="none" placeholder="例如：这款更适合通勤还是送礼？" />
              <el-button type="warning" :loading="qaLoading" @click="handleQA">提问</el-button>
              <div v-if="qaResult" class="text-sm leading-6 text-slate-600 dark:text-slate-300">{{ qaResult }}</div>
            </div>
          </div>
        </div>
      </GlassCard>

      <GlassCard title="最近一轮结果摘要">
        <div v-if="latestAssistant" class="space-y-2.5">
          <div class="rounded-xl border border-slate-200 bg-slate-50/80 p-3 dark:border-slate-700 dark:bg-slate-900/35">
            <div class="text-xs text-slate-400">回复摘要</div>
            <div class="mt-2 text-sm leading-6 text-slate-700 dark:text-slate-200">{{ latestAssistant.content }}</div>
          </div>
          <div class="grid gap-2.5 md:grid-cols-2">
            <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">推荐商品数</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.products?.length || 0 }}</div></div>
            <div class="rounded-xl border border-slate-200 bg-white/90 p-3 dark:border-slate-700 dark:bg-slate-800/70"><div class="text-xs text-slate-400">是否需要追问</div><div class="mt-1.5 text-xl font-bold text-slate-900 dark:text-slate-100">{{ latestAssistant.needClarification ? '是' : '否' }}</div></div>
          </div>
        </div>
        <el-empty v-else description="先发起一轮 AI 对话" />
      </GlassCard>
    </div>
  </div>
</template>

<style scoped>
.ai-section {
  border-top: 1px solid rgba(203, 213, 225, 0.9);
  padding-top: 18px;
}

.ai-section--main {
  min-width: 0;
}

.ai-section__header {
  margin-bottom: 14px;
}

.ai-section__title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.ai-section__desc {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.dark .ai-section {
  border-top-color: rgba(71, 85, 105, 0.55);
}

.dark .ai-section__title {
  color: #f8fafc;
}

.dark .ai-section__desc {
  color: #94a3b8;
}

@media (max-width: 1280px) {
  .ai-page :deep(.el-card),
  .ai-page :deep(.panel-card) {
    min-width: 0;
  }
}
</style>
