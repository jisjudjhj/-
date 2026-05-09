<template>
  <div class="analytics-ui-page tech-overview-page space-y-6">
    <section class="tech-overview-hero p-6 md:p-7">
      <div class="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div class="max-w-4xl">
          <div class="tech-kicker">Technology Overview</div>
          <h1 class="mt-2 text-3xl font-black tracking-tight text-slate-950 dark:text-slate-50 md:text-4xl">
            平台技术总览
          </h1>
        </div>
        <span class="tech-badge">交互载体 + 推荐链路</span>
      </div>

      <div class="mt-5 flex flex-wrap gap-2">
        <span v-for="item in topStacks" :key="item" class="tech-chip">{{ item }}</span>
      </div>
    </section>

    <FeatureBrief
      kicker="技术总览"
      title="核心链路"
      :items="techFeatureBrief"
    />

    <PageSectionTabs
      v-model="activeTab"
      :tabs="tabs"
      primary-label="管理端"
      page-label="技术总览页"
      title="技术分区"
      description=""
      :active-label="activeTabInfo.label"
    />

    <template v-if="activeTab === 'stack'">
      <section class="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <article v-for="group in techDomains" :key="group.title" class="panel-card tech-panel p-6">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="text-xs uppercase tracking-[0.18em] text-slate-400">{{ group.eyebrow }}</div>
              <div class="mt-2 text-xl font-semibold text-slate-950 dark:text-slate-50">{{ group.title }}</div>
            </div>
            <span class="tech-inline-badge">{{ group.goal }}</span>
          </div>
          <div class="mt-4 flex flex-wrap gap-2">
            <span v-for="item in group.stack" :key="item" class="tech-chip">{{ item }}</span>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeTab === 'architecture'">
      <section class="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <article v-for="layer in architectureLayers" :key="layer.title" class="panel-card tech-panel p-6">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="text-xl font-semibold text-slate-950 dark:text-slate-50">{{ layer.title }}</div>
            <span class="tech-inline-badge">{{ layer.role }}</span>
          </div>
          <div class="mt-4 text-xs uppercase tracking-[0.18em] text-slate-400">主要技术</div>
          <div class="mt-2 flex flex-wrap gap-2">
            <span v-for="item in layer.stack" :key="item" class="tech-chip">{{ item }}</span>
          </div>
          <div class="mt-4 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300">
            对应实现页面：{{ layer.pageLabel }}
          </div>
        </article>
      </section>
    </template>

    <template v-else>
      <section class="panel-card tech-panel p-6">
        <div class="tech-section-title">实现细节导航</div>

        <div class="mt-5 grid grid-cols-1 gap-3 xl:grid-cols-2">
          <button
            v-for="item in implementationPages"
            :key="item.route"
            type="button"
            class="tech-route-row"
            @click="goTo(item.route)"
          >
            <div>
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item.label }}</div>
            </div>
            <span class="tech-route-tag">进入页面</span>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'

const route = useRoute()
const router = useRouter()

const tabs = [
  { key: 'stack', label: '技术版图', hint: '选型', description: '' },
  { key: 'architecture', label: '架构分层', hint: '边界', description: '' },
  { key: 'entry', label: '实现导航', hint: '页面', description: '' },
]

const tabKeySet = new Set(tabs.map(item => item.key))
const normalizeTab = value => {
  const next = String(value || '').trim()
  return tabKeySet.has(next) ? next : tabs[0].key
}

const activeTab = ref(normalizeTab(route.query.tab))
const activeTabInfo = computed(() => tabs.find(item => item.key === activeTab.value) || tabs[0])

const topStacks = ['微信交互载体', '用户行为矩阵', '余弦相似度', 'Hybrid', 'Top-N', 'Precision@K', 'Recall@K', 'Spring Boot', 'MySQL', 'Redis']

const techFeatureBrief = [
  { label: '判断依据', value: '载体 + 推荐链路', text: '看信号、矩阵、排序。' },
  { label: '功能组成', value: '载体 / API / MySQL / Redis', text: '信号进入，服务计算。' },
  { label: '输出结果', value: 'Top-N / 指标 / 解释', text: '结果可验证。' },
]

const techDomains = [
  {
    eyebrow: 'Interaction',
    title: '交互载体',
    goal: '承接行为信号',
    summary: '',
    stack: ['微信交互载体', '行为信号采集', '推荐结果反馈', '曝光归因'],
  },
  {
    eyebrow: 'Recommendation',
    title: '推荐与个性化',
    goal: '提升点击与转化',
    summary: '',
    stack: ['user-item 矩阵', '余弦相似度', 'Hybrid', 'Top-N', 'Precision@K', 'Recall@K', 'A/B Test'],
  },
  {
    eyebrow: 'Realtime',
    title: '实时计算与指标回流',
    goal: '提升实时承接能力',
    summary: '',
    stack: ['Kafka', 'Flink CDC', 'Flink SQL', 'DWD/DWS', 'Redis Sink', 'ECharts'],
  },
  {
    eyebrow: 'Transaction',
    title: '交易与服务治理',
    goal: '保证一致性与稳定性',
    summary: '',
    stack: ['Spring Security', 'JWT', '事务控制', '幂等保护', 'Redis + Lua', '限流'],
  },
]

const architectureLayers = [
  {
    title: '交互层（管理端 / 交互载体）',
    role: '信号回流',
    scope: '',
    stack: ['交互载体', '行为信号回流', '推荐 token 归因', 'ECharts'],
    pageLabel: '负责信号承载、行为进入和结果反馈',
  },
  {
    title: '业务服务层（后端 API）',
    role: '业务编排与规则治理',
    scope: '',
    stack: ['Spring Boot', 'Spring Security', 'MyBatis', 'JWT', 'Redis'],
    pageLabel: '商品、订单、推荐、A/B、秒杀接口',
  },
  {
    title: '数据分析层（离线 + 实时）',
    role: '矩阵构建与排序计算',
    scope: '',
    stack: ['user-item 矩阵', '余弦相似度', 'Hybrid', 'Precision@K / Recall@K', 'Kafka', 'Flink'],
    pageLabel: '推荐分析、用户画像、实时流式看板',
  },
  {
    title: '存储与缓存层',
    role: '数据持久化与加速访问',
    scope: '',
    stack: ['MySQL', 'Redis', 'Local / OSS'],
    pageLabel: '行为、商品、订单、画像、推荐结果',
  },
]

const implementationPages = [
  { label: '推荐分析', route: '/admin/analytics/recommend', detail: '' },
  { label: '用户分群', route: '/admin/analytics/user-clusters', detail: '' },
  { label: '实时流式看板', route: '/admin/analytics/realtime-stream', detail: '' },
  { label: 'A/B 实验', route: '/admin/recommend/abtest', detail: '' },
  { label: '秒杀管理', route: '/admin/seckill', detail: '' },
  { label: '经营诊断', route: '/admin/analytics/bigdata', detail: '' },
]

function goTo(path) {
  router.push(path)
}

watch(
  () => route.query.tab,
  value => {
    const next = normalizeTab(value)
    if (next !== activeTab.value) {
      activeTab.value = next
    }
  }
)

watch(activeTab, value => {
  const next = normalizeTab(value)
  const current = normalizeTab(route.query.tab)
  if (next === current) {
    return
  }
  router.replace({
    query: {
      ...route.query,
      tab: next === tabs[0].key ? undefined : next,
    },
  })
})
</script>

<style scoped>
.tech-overview-page {
  --tech-border: rgba(148, 163, 184, 0.22);
}

.tech-overview-hero,
.tech-panel,
.tech-route-row {
  border: 1px solid var(--tech-border);
  background: rgba(255, 255, 255, 0.96);
}

.dark .tech-overview-hero,
.dark .tech-panel,
.dark .tech-route-row {
  border-color: rgba(71, 85, 105, 0.58);
  background: rgba(15, 23, 42, 0.52);
}

.tech-overview-hero {
  border-radius: 26px;
  background:
    radial-gradient(circle at 0 0, rgba(14, 165, 233, 0.12), transparent 32%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
}

.dark .tech-overview-hero {
  background:
    radial-gradient(circle at 0 0, rgba(34, 211, 238, 0.12), transparent 32%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.72), rgba(2, 6, 23, 0.54));
}

.tech-panel {
  border-radius: 24px;
}

.tech-kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #0f766e;
}

.dark .tech-kicker {
  color: #67e8f9;
}

.tech-badge,
.tech-chip,
.tech-inline-badge,
.tech-route-tag {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.86);
  color: #475569;
}

.tech-badge {
  padding: 9px 14px;
  font-size: 12px;
  font-weight: 700;
}

.tech-chip,
.tech-inline-badge,
.tech-route-tag {
  padding: 7px 12px;
  font-size: 12px;
}

.dark .tech-badge,
.dark .tech-chip,
.dark .tech-inline-badge,
.dark .tech-route-tag {
  border-color: rgba(100, 116, 139, 0.5);
  background: rgba(15, 23, 42, 0.46);
  color: #cbd5e1;
}

.tech-route-row {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-radius: 20px;
  padding: 16px;
  text-align: left;
  transition: all 0.2s ease;
}

.tech-route-row:hover {
  border-color: rgba(37, 99, 235, 0.28);
  transform: translateY(-1px);
}

.tech-section-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.dark .tech-section-title {
  color: #f8fafc;
}
</style>
