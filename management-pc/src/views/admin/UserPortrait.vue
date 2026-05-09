<template>
  <div v-loading="loading" class="space-y-6 analytics-ui-page defense-page">
    <section class="analysis-page-header">
      <h1 class="analysis-page-header__title">用户画像</h1>
      <div class="analysis-page-header__meta">行为向量 · 类目权重 · 标签 · 分群</div>
    </section>

    <FeatureBrief
      kicker="用户画像"
      title="判断依据与组成"
      :items="portraitFeatureBrief"
    />

    <section class="defense-surface portrait-query-panel">
      <div class="min-w-0">
        <div class="defense-surface__eyebrow">画像检索</div>
        <h2 class="portrait-query-panel__title">输入用户 ID，查看向量、权重、公式</h2>
        <p class="portrait-query-panel__text">
          画像 = 行为 + 搜索 + 类目 + 标签。
        </p>
      </div>
      <div class="portrait-query-panel__actions">
        <el-input
          v-model="userIdInput"
          class="!w-44"
          placeholder="用户 ID"
          clearable
          @keyup.enter="handleLoad"
        />
        <el-button type="primary" :loading="loading" @click="handleLoad">生成画像</el-button>
        <el-button plain @click="goRecommendPreview">推荐预览</el-button>
      </div>
    </section>

    <section class="defense-metric-strip">
      <article v-for="item in summaryCards" :key="item.label" class="defense-metric-strip__item">
        <span class="defense-metric-strip__label">{{ item.label }}</span>
        <strong class="defense-metric-strip__value">{{ item.value }}</strong>
        <span class="defense-metric-strip__sub">{{ item.sub }}</span>
      </article>
    </section>

    <section class="portrait-grid">
      <article class="defense-surface portrait-profile-card">
        <div class="portrait-profile-card__head">
          <el-avatar :size="58" class="portrait-avatar">
            {{ displayName.charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="min-w-0">
            <h3 class="portrait-profile-card__name">{{ displayName }}</h3>
            <div class="portrait-profile-card__meta">
              ID {{ resolvedUserId }} · {{ profile.experimentGroupDesc || '标准混合推荐组' }}
            </div>
          </div>
        </div>

        <div class="portrait-proof-list">
          <div v-for="item in portraitProofRows" :key="item.label" class="portrait-proof-list__item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="portrait-callout">
          <div class="portrait-callout__title">公式</div>
          <p>
            用户向量 = view*1 + search*2 + cart*2 + favorite*3 + purchase*8。
          </p>
        </div>
      </article>

      <article class="defense-surface portrait-section">
        <div class="portrait-section__head">
          <div>
            <div class="defense-surface__eyebrow">User Vector</div>
            <h3 class="portrait-section__title">行为向量</h3>
          </div>
          <el-tag type="primary" effect="plain" round>{{ behaviorRows.length }} 类行为</el-tag>
        </div>
        <div v-if="behaviorRows.length" class="portrait-bars">
          <div v-for="item in behaviorRows" :key="item.key" class="portrait-bar-row">
            <div class="portrait-bar-row__label">
              <span>{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
            <div class="portrait-bar-row__track">
              <span :style="{ width: `${item.percent}%` }"></span>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无行为画像数据" :image-size="64" />
      </article>
    </section>

    <section class="portrait-grid portrait-grid--wide">
      <article class="defense-surface portrait-section">
        <div class="portrait-section__head">
          <div>
            <div class="defense-surface__eyebrow">Category Weight</div>
            <h3 class="portrait-section__title">品类偏好权重</h3>
          </div>
          <el-tag type="success" effect="plain" round>画像主干</el-tag>
        </div>
        <div v-if="categoryRows.length" class="portrait-category-list">
          <div v-for="item in categoryRows" :key="item.key" class="portrait-category-list__item">
            <div>
              <strong>{{ item.label }}</strong>
              <span>权重 {{ item.rawValue }}</span>
            </div>
            <el-progress :percentage="item.percent" :stroke-width="9" :show-text="false" />
          </div>
        </div>
        <el-empty v-else description="暂无品类偏好" :image-size="64" />
      </article>

      <article class="defense-surface portrait-section">
        <div class="portrait-section__head">
          <div>
            <div class="defense-surface__eyebrow">Tag & Query</div>
            <h3 class="portrait-section__title">标签与搜索意图</h3>
          </div>
          <el-tag type="warning" effect="plain" round>解释来源</el-tag>
        </div>

        <div class="portrait-tag-area">
          <el-tag
            v-for="tag in userTags"
            :key="tag"
            class="!rounded-full"
            :type="tagType(tag)"
            effect="light"
          >
            {{ tag }}
          </el-tag>
          <span v-if="!userTags.length" class="portrait-empty-text">暂无标签</span>
        </div>

        <div class="portrait-search-list">
          <div v-for="item in searchPreferenceRows" :key="item.label" class="portrait-search-list__item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
          <div v-if="!searchPreferenceRows.length" class="portrait-empty-text">暂无搜索偏好</div>
        </div>
      </article>
    </section>

    <section class="defense-surface portrait-section">
      <div class="portrait-section__head">
        <div>
          <div class="defense-surface__eyebrow">Recommend Formula</div>
          <h3 class="portrait-section__title">画像进入推荐排序</h3>
        </div>
        <el-tag effect="plain" round>推荐解释链路</el-tag>
      </div>

      <div class="portrait-layer-grid">
        <article v-for="item in portraitLayerRows" :key="item.title" class="portrait-layer-card">
          <div class="portrait-layer-card__index">{{ item.index }}</div>
          <div>
            <h4>{{ item.title }}</h4>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserProfile } from '../../api/recommend'
import FeatureBrief from '../../components/FeatureBrief.vue'

const route = useRoute()
const router = useRouter()
const portraitFeatureBrief = [
  { label: '判断依据', value: 'view / search / cart / favorite / purchase', text: '行为计数转成 user vector。' },
  { label: '功能组成', value: 'vector + category + tag + segment', text: '向量、类目、标签、分群四层合并。' },
  { label: '输出结果', value: '召回权重 + 排序解释', text: '给 CF、CB、Hot、Hybrid 提供输入。' },
]

const behaviorTypeMap = {
  view: '浏览',
  cart: '加购',
  favorite: '收藏',
  purchase: '购买',
  search: '搜索',
  dislike: '不感兴趣',
}

const userIdInput = ref(String(route.query.userId || 4))
const resolvedUserId = ref(Number(route.query.userId || 4))
const loading = ref(false)
const profile = ref({})

const displayName = computed(() =>
  profile.value.username || profile.value.nickname || `用户 #${resolvedUserId.value || userIdInput.value || 4}`
)

const behaviorRows = computed(() => {
  const rows = Array.isArray(profile.value.behaviorStats) ? profile.value.behaviorStats : []
  const max = Math.max(...rows.map(item => Number(item.count || item.countValue || 0)), 1)
  return rows.map(item => {
    const key = item.behaviorType || item.behavior_type || item.type || ''
    const count = Number(item.count || item.countValue || 0)
    return {
      key,
      label: behaviorTypeMap[key] || key || '未知行为',
      count,
      percent: Math.max(6, Math.round((count / max) * 100)),
    }
  }).sort((left, right) => right.count - left.count)
})

const categoryRows = computed(() => {
  const weights = profile.value.categoryWeights || {}
  const entries = Object.entries(weights)
  const max = Math.max(...entries.map(([, value]) => Number(value || 0)), 1)
  return entries.map(([key, value]) => {
    const numberValue = Number(value || 0)
    return {
      key,
      label: `品类 ${key}`,
      rawValue: numberValue.toFixed(2),
      percent: Math.min(100, Math.round((numberValue / max) * 100)),
    }
  }).sort((left, right) => Number(right.rawValue) - Number(left.rawValue)).slice(0, 8)
})

const userTags = computed(() =>
  Array.isArray(profile.value.userTags) ? profile.value.userTags.filter(Boolean).slice(0, 18) : []
)

const searchPreferenceRows = computed(() => {
  const prefs = profile.value.searchCategoryPreferences || {}
  return Object.entries(prefs).slice(0, 6).map(([label, value]) => ({
    label,
    value: Number(value || 0).toFixed(2),
  }))
})

const summaryCards = computed(() => [
  {
    label: '行为商品',
    value: String(profile.value.interactedProducts || 0),
    sub: 'matrix 中已有行为 item 数',
  },
  {
    label: '向量维度',
    value: String(profile.value.vectorDimension || 0),
    sub: 'vector 维度',
  },
  {
    label: '画像标签',
    value: String(userTags.value.length),
    sub: 'tag_count',
  },
  {
    label: '偏好品类',
    value: String(categoryRows.value.length),
    sub: 'category_weight > 0',
  },
])

const portraitProofRows = computed(() => [
  { label: '行为数据源', value: behaviorRows.value.length ? 'online' : 'empty' },
  { label: '搜索偏好', value: searchPreferenceRows.value.length ? 'query_ready' : 'none' },
  { label: '推荐实验组', value: profile.value.experimentGroup || 'hybrid' },
])

const portraitLayerRows = computed(() => {
  const layers = profile.value.portraitLayers
  if (Array.isArray(layers) && layers.length) {
    return layers.slice(0, 6).map((item, index) => ({
      index: `0${index + 1}`,
      title: item.title || item.name || `画像层 ${index + 1}`,
      text: item.description || item.text || item.summary || '参与召回、排序或解释。',
    }))
  }
  return [
    { index: '01', title: '行为层', text: 'score = view*1 + search*2 + cart*2 + favorite*3 + purchase*8。' },
    { index: '02', title: '偏好层', text: 'category_weight + tag_weight 决定内容相似度。' },
    { index: '03', title: '分群层', text: 'RFM / KMeans 结果进入实验组和运营触达。' },
    { index: '04', title: '解释层', text: 'reason = source + tag + token。' },
  ]
})

function normalizeUserId(value) {
  const numberValue = Number(value)
  return Number.isFinite(numberValue) && numberValue > 0 ? Math.floor(numberValue) : 4
}

function tagType(tag) {
  const types = ['primary', 'success', 'warning', 'danger', 'info']
  const code = String(tag || '').split('').reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return types[Math.abs(code) % types.length]
}

async function loadProfile(nextUserId = normalizeUserId(userIdInput.value)) {
  loading.value = true
  try {
    const data = await getUserProfile(nextUserId)
    profile.value = data || {}
    resolvedUserId.value = nextUserId
    userIdInput.value = String(nextUserId)
    router.replace({ path: route.path, query: { ...route.query, userId: nextUserId } })
  } catch (error) {
    ElMessage.error(error?.message || '用户画像加载失败')
  } finally {
    loading.value = false
  }
}

function handleLoad() {
  loadProfile(normalizeUserId(userIdInput.value))
}

function goRecommendPreview() {
  router.push({
    path: '/admin/recommend/preview',
    query: { userId: resolvedUserId.value || normalizeUserId(userIdInput.value) },
  })
}

onMounted(() => {
  loadProfile(normalizeUserId(route.query.userId || userIdInput.value))
})
</script>

<style scoped>
.portrait-query-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px;
}

.portrait-query-panel__title {
  margin-top: 6px;
  font-size: 20px;
  font-weight: 800;
  color: rgb(15 23 42);
}

.dark .portrait-query-panel__title {
  color: rgb(248 250 252);
}

.portrait-query-panel__text {
  margin-top: 8px;
  max-width: 760px;
  color: rgb(100 116 139);
  font-size: 14px;
  line-height: 1.7;
}

.dark .portrait-query-panel__text {
  color: rgb(203 213 225);
}

.portrait-query-panel__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.portrait-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.4fr);
  gap: 20px;
}

.portrait-grid--wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.portrait-profile-card,
.portrait-section {
  padding: 22px;
}

.portrait-profile-card__head,
.portrait-section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.portrait-profile-card__head {
  justify-content: flex-start;
  align-items: center;
}

.portrait-avatar {
  flex: none;
  background: linear-gradient(135deg, #2563eb, #06b6d4);
  color: white;
  font-weight: 800;
}

.portrait-profile-card__name {
  font-size: 20px;
  font-weight: 800;
  color: rgb(15 23 42);
}

.dark .portrait-profile-card__name {
  color: rgb(248 250 252);
}

.portrait-profile-card__meta {
  margin-top: 5px;
  color: rgb(100 116 139);
  font-size: 12px;
}

.dark .portrait-profile-card__meta {
  color: rgb(148 163 184);
}

.portrait-proof-list {
  margin-top: 22px;
  display: grid;
  gap: 10px;
}

.portrait-proof-list__item,
.portrait-search-list__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.76);
  padding: 13px 14px;
  color: rgb(100 116 139);
  font-size: 13px;
}

.dark .portrait-proof-list__item,
.dark .portrait-search-list__item {
  background: rgba(15, 23, 42, 0.38);
  border-color: rgba(51, 65, 85, 0.9);
  color: rgb(203 213 225);
}

.portrait-proof-list__item strong,
.portrait-search-list__item strong {
  color: rgb(15 23 42);
}

.dark .portrait-proof-list__item strong,
.dark .portrait-search-list__item strong {
  color: rgb(248 250 252);
}

.portrait-callout {
  margin-top: 18px;
  border-radius: 18px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(236, 254, 255, 0.82));
  padding: 16px;
  color: rgb(51 65 85);
  font-size: 13px;
  line-height: 1.75;
}

.dark .portrait-callout {
  border-color: rgba(56, 189, 248, 0.18);
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.86), rgba(8, 47, 73, 0.48));
  color: rgb(203 213 225);
}

.portrait-callout__title {
  margin-bottom: 6px;
  color: rgb(37 99 235);
  font-weight: 800;
}

.dark .portrait-callout__title {
  color: rgb(125 211 252);
}

.portrait-section__title {
  margin-top: 5px;
  font-size: 18px;
  font-weight: 800;
  color: rgb(15 23 42);
}

.dark .portrait-section__title {
  color: rgb(248 250 252);
}

.portrait-bars,
.portrait-category-list,
.portrait-search-list {
  margin-top: 20px;
  display: grid;
  gap: 14px;
}

.portrait-bar-row__label {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  color: rgb(71 85 105);
  font-size: 13px;
}

.dark .portrait-bar-row__label {
  color: rgb(203 213 225);
}

.portrait-bar-row__track {
  margin-top: 8px;
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: rgb(226 232 240);
}

.dark .portrait-bar-row__track {
  background: rgb(30 41 59);
}

.portrait-bar-row__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #2563eb, #06b6d4);
}

.portrait-category-list__item {
  display: grid;
  gap: 9px;
}

.portrait-category-list__item > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  font-size: 13px;
}

.portrait-category-list__item strong {
  color: rgb(30 41 59);
}

.dark .portrait-category-list__item strong {
  color: rgb(248 250 252);
}

.portrait-category-list__item span {
  color: rgb(100 116 139);
}

.portrait-tag-area {
  margin-top: 20px;
  display: flex;
  min-height: 92px;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 9px;
}

.portrait-empty-text {
  color: rgb(148 163 184);
  font-size: 13px;
}

.portrait-layer-grid {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.portrait-layer-card {
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.78);
  padding: 17px;
}

.dark .portrait-layer-card {
  background: rgba(15, 23, 42, 0.38);
  border-color: rgba(51, 65, 85, 0.88);
}

.portrait-layer-card__index {
  color: rgb(37 99 235);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
}

.portrait-layer-card h4 {
  margin-top: 8px;
  color: rgb(15 23 42);
  font-size: 15px;
  font-weight: 800;
}

.dark .portrait-layer-card h4 {
  color: rgb(248 250 252);
}

.portrait-layer-card p {
  margin-top: 8px;
  color: rgb(100 116 139);
  font-size: 13px;
  line-height: 1.65;
}

.dark .portrait-layer-card p {
  color: rgb(203 213 225);
}

@media (max-width: 1180px) {
  .portrait-grid,
  .portrait-grid--wide,
  .portrait-layer-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .portrait-query-panel,
  .portrait-section__head {
    align-items: stretch;
    flex-direction: column;
  }

  .portrait-query-panel__actions {
    justify-content: flex-start;
  }
}
</style>
