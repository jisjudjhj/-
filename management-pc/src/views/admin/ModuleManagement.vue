<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import GlassCard from '../../components/GlassCard.vue'
import {
  getAdminModuleSwitches,
  getAdminModuleSwitchSummary,
  updateAdminModuleSwitch,
  updateAdminModuleSwitchBatch,
} from '../../api/admin'

const loading = ref(false)
const modules = ref({})
const summary = ref({})
const switchLoading = ref({})
const keyword = ref('')
const groupFilter = ref('all')

const groupMeta = {
  ai: { label: 'AI 智能服务', icon: 'ChatLineSquare', order: 1 },
  recommendation: { label: '推荐系统', icon: 'MagicStick', order: 2 },
  product: { label: '商品相关', icon: 'Goods', order: 3 },
  marketing: { label: '营销工具', icon: 'Ticket', order: 4 },
  transaction: { label: '交易相关', icon: 'Money', order: 5 },
  user: { label: '用户相关', icon: 'User', order: 6 },
  system: { label: '系统服务', icon: 'Setting', order: 7 },
}

const riskMeta = {
  low: { label: '低风险', type: 'success' },
  medium: { label: '中风险', type: 'warning' },
  high: { label: '高风险', type: 'danger' },
  critical: { label: '关键', type: 'danger' },
}

const moduleEntries = computed(() => {
  return Object.entries(modules.value || {}).map(([key, value]) => ({
    key,
    ...value,
    dependencies: Array.isArray(value.dependencies) ? value.dependencies : [],
    requiredBy: Array.isArray(value.requiredBy) ? value.requiredBy : [],
  }))
})

const visibleModules = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return moduleEntries.value.filter(item => {
    if (groupFilter.value !== 'all' && item.group !== groupFilter.value) {
      return false
    }
    if (!search) {
      return true
    }
    const source = `${item.key} ${item.name || ''} ${item.desc || ''}`.toLowerCase()
    return source.includes(search)
  })
})

const groupedModules = computed(() => {
  const groups = {}
  visibleModules.value.forEach(item => {
    const group = item.group || 'system'
    if (!groups[group]) groups[group] = []
    groups[group].push(item)
  })

  return Object.entries(groups)
    .sort(([left], [right]) => (groupMeta[left]?.order || 99) - (groupMeta[right]?.order || 99))
    .map(([group, items]) => ({
      group,
      meta: groupMeta[group] || { label: group, icon: 'Grid' },
      items,
      allEnabled: items.every(item => item.enabled),
      partialEnabled: items.some(item => item.enabled) && !items.every(item => item.enabled),
    }))
})

const groupOptions = computed(() => {
  const values = new Set(moduleEntries.value.map(item => item.group || 'system'))
  const options = [{ value: 'all', label: '全部分组' }]
  Array.from(values)
    .sort((left, right) => (groupMeta[left]?.order || 99) - (groupMeta[right]?.order || 99))
    .forEach(group => {
      options.push({
        value: group,
        label: groupMeta[group]?.label || group,
      })
    })
  return options
})

const totalModules = computed(() => moduleEntries.value.length)
const enabledCount = computed(() => moduleEntries.value.filter(item => item.enabled).length)
const disabledCount = computed(() => totalModules.value - enabledCount.value)
const coreDisabledCount = computed(() => {
  if (typeof summary.value?.coreDisabled === 'number') {
    return summary.value.coreDisabled
  }
  return moduleEntries.value.filter(item => item.isCore && !item.enabled).length
})
const criticalDisabledCount = computed(() => {
  if (typeof summary.value?.criticalDisabled === 'number') {
    return summary.value.criticalDisabled
  }
  return moduleEntries.value.filter(item => item.riskLevel === 'critical' && !item.enabled).length
})
const hasRiskAlert = computed(() => coreDisabledCount.value > 0 || criticalDisabledCount.value > 0)

async function loadModules() {
  loading.value = true
  try {
    const [switches, switchSummary] = await Promise.all([
      getAdminModuleSwitches(),
      getAdminModuleSwitchSummary().catch(() => ({})),
    ])
    modules.value = switches || {}
    summary.value = switchSummary || {}
  } catch (error) {
    ElMessage.error('获取模块开关失败')
  } finally {
    loading.value = false
  }
}

function getModuleLabel(moduleKey) {
  return modules.value[moduleKey]?.name || moduleKey
}

async function askEnableDependencies(item) {
  const disabledDeps = item.dependencies.filter(dep => !modules.value[dep]?.enabled)
  if (!disabledDeps.length) {
    return { proceed: true, autoEnableDependencies: false }
  }
  const depLabels = disabledDeps.map(dep => `「${getModuleLabel(dep)}」`).join('、')
  try {
    await ElMessageBox.confirm(
      `开启「${item.name}」需要同时开启依赖模块：${depLabels}。是否自动开启？`,
      '依赖校验',
      { type: 'warning', confirmButtonText: '自动开启', cancelButtonText: '取消' },
    )
    return { proceed: true, autoEnableDependencies: true }
  } catch {
    return { proceed: false, autoEnableDependencies: false }
  }
}

async function askDisableDependents(item) {
  const activeDependents = item.requiredBy.filter(dep => modules.value[dep]?.enabled)
  if (!activeDependents.length) {
    return { proceed: true, force: false }
  }
  const labels = activeDependents.map(dep => `「${getModuleLabel(dep)}」`).join('、')
  try {
    await ElMessageBox.confirm(
      `关闭「${item.name}」会联动关闭依赖它的模块：${labels}。是否继续？`,
      '风险提示',
      { type: 'warning', confirmButtonText: '继续关闭', cancelButtonText: '取消' },
    )
    return { proceed: true, force: true }
  } catch {
    return { proceed: false, force: false }
  }
}

async function toggleModule(item, enabled) {
  const previous = !!item.enabled
  let force = false
  let autoEnableDependencies = false

  if (enabled) {
    const check = await askEnableDependencies(item)
    if (!check.proceed) {
      modules.value[item.key].enabled = previous
      return
    }
    autoEnableDependencies = check.autoEnableDependencies
  } else {
    const check = await askDisableDependents(item)
    if (!check.proceed) {
      modules.value[item.key].enabled = previous
      return
    }
    force = check.force
  }

  switchLoading.value = { ...switchLoading.value, [item.key]: true }
  try {
    const result = await updateAdminModuleSwitch({
      module: item.key,
      enabled,
      force,
      autoEnableDependencies,
    })
    if (result?.snapshot) {
      modules.value = result.snapshot
    } else {
      modules.value[item.key].enabled = enabled
    }
    if (result?.summary) {
      summary.value = result.summary
    }
    ElMessage.success(result?.message || `${item.name} 已${enabled ? '开启' : '关闭'}`)
  } catch (error) {
    modules.value[item.key].enabled = previous
    ElMessage.error(error?.message || '操作失败')
    await loadModules()
  } finally {
    switchLoading.value = { ...switchLoading.value, [item.key]: false }
  }
}

function buildGroupBatch(group, enabled) {
  const target = groupedModules.value.find(item => item.group === group)
  if (!target) return {}
  const batch = {}
  target.items.forEach(item => {
    batch[item.key] = enabled
  })
  return batch
}

async function toggleGroup(group, enabled) {
  const target = groupedModules.value.find(item => item.group === group)
  if (!target) return

  const actionText = enabled ? '开启' : '关闭'
  try {
    await ElMessageBox.confirm(
      `确定批量${actionText}「${target.meta.label}」分组下的模块吗？`,
      '批量操作',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  const batch = buildGroupBatch(group, enabled)
  try {
    const result = await updateAdminModuleSwitchBatch({
      switches: batch,
      force: !enabled,
      autoEnableDependencies: enabled,
    })
    if (result?.snapshot) {
      modules.value = result.snapshot
    } else {
      Object.entries(batch).forEach(([key, value]) => {
        if (modules.value[key]) modules.value[key].enabled = value
      })
    }
    if (result?.summary) {
      summary.value = result.summary
    }
    ElMessage.success(result?.message || '批量更新成功')
  } catch (error) {
    ElMessage.error(error?.message || '批量更新失败')
    await loadModules()
  }
}

async function restoreDefaultModules() {
  const batch = {}
  moduleEntries.value.forEach(item => {
    batch[item.key] = item.defaultEnabled !== false
  })
  try {
    await ElMessageBox.confirm(
      '确定恢复到默认模块策略吗？该操作会覆盖当前开关状态。',
      '恢复默认',
      { type: 'warning', confirmButtonText: '恢复', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  try {
    const result = await updateAdminModuleSwitchBatch({
      switches: batch,
      force: true,
      autoEnableDependencies: true,
    })
    modules.value = result?.snapshot || {}
    summary.value = result?.summary || {}
    ElMessage.success('已恢复默认模块策略')
  } catch (error) {
    ElMessage.error(error?.message || '恢复默认失败')
  }
}

async function enableAllModules() {
  const batch = {}
  moduleEntries.value.forEach(item => {
    batch[item.key] = true
  })
  try {
    await ElMessageBox.confirm(
      '确定一键开启全部模块吗？',
      '全量开启',
      { type: 'warning', confirmButtonText: '开启全部', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const result = await updateAdminModuleSwitchBatch({
      switches: batch,
      force: true,
      autoEnableDependencies: true,
    })
    modules.value = result?.snapshot || {}
    summary.value = result?.summary || {}
    ElMessage.success('全部模块已开启')
  } catch (error) {
    ElMessage.error(error?.message || '批量开启失败')
  }
}

onMounted(() => {
  loadModules()
})
</script>

<template>
  <div class="space-y-6" v-loading="loading">
    <div class="module-head">
      <div>
        <h2 class="text-2xl font-bold text-slate-900 dark:text-slate-100">模块管理升级版</h2>
        <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">支持依赖校验、风险提示、批量编排与默认策略恢复</p>
      </div>
      <div class="module-head__actions">
        <el-button @click="restoreDefaultModules">恢复默认</el-button>
        <el-button type="success" @click="enableAllModules">全部开启</el-button>
        <el-button type="primary" @click="loadModules">
          <el-icon class="mr-1"><Refresh /></el-icon>
          刷新状态
        </el-button>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-6">
      <div class="module-stat">
        <div class="label">总模块数</div>
        <div class="value">{{ totalModules }}</div>
      </div>
      <div class="module-stat is-ok">
        <div class="label">运行中</div>
        <div class="value">{{ enabledCount }}</div>
      </div>
      <div class="module-stat is-warn">
        <div class="label">已关闭</div>
        <div class="value">{{ disabledCount }}</div>
      </div>
      <div class="module-stat" :class="summary.healthy === false ? 'is-risk' : 'is-ok'">
        <div class="label">系统健康</div>
        <div class="value">{{ summary.healthy === false ? '关注' : '正常' }}</div>
      </div>
      <div class="module-stat" :class="coreDisabledCount > 0 ? 'is-risk' : 'is-ok'">
        <div class="label">核心模块关闭</div>
        <div class="value">{{ coreDisabledCount }}</div>
      </div>
      <div class="module-stat" :class="criticalDisabledCount > 0 ? 'is-risk' : 'is-ok'">
        <div class="label">关键风险关闭</div>
        <div class="value">{{ criticalDisabledCount }}</div>
      </div>
    </div>

    <el-alert
      v-if="hasRiskAlert"
      type="warning"
      :closable="false"
      title="检测到核心链路风险：请优先恢复核心或关键风险模块，避免交易/通知等功能异常。"
      show-icon
    />

    <GlassCard>
      <template #header>
        <div class="flex items-center gap-3">
          <el-input v-model="keyword" placeholder="搜索模块名称、描述或 key" clearable class="!w-80">
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select v-model="groupFilter" class="!w-48">
            <el-option
              v-for="item in groupOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </template>
      <div class="text-xs text-slate-500 dark:text-slate-400">
        当前展示 {{ visibleModules.length }} / {{ totalModules }} 个模块
      </div>
    </GlassCard>

    <GlassCard v-for="group in groupedModules" :key="group.group">
      <template #header>
        <div class="flex items-center gap-3">
          <div class="module-group-icon">
            <el-icon><component :is="group.meta.icon" /></el-icon>
          </div>
          <div>
            <div class="text-base font-semibold text-slate-900 dark:text-slate-100">{{ group.meta.label }}</div>
            <div class="text-xs text-slate-500 dark:text-slate-400">
              {{ group.items.filter(item => item.enabled).length }}/{{ group.items.length }} 运行中
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <el-button v-if="!group.allEnabled" size="small" type="success" text @click="toggleGroup(group.group, true)">全部开启</el-button>
          <el-button v-if="group.partialEnabled || group.allEnabled" size="small" type="danger" text @click="toggleGroup(group.group, false)">全部关闭</el-button>
        </div>
      </template>

      <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
        <div
          v-for="item in group.items"
          :key="item.key"
          class="module-item"
          :class="item.enabled ? 'is-enabled' : 'is-disabled'"
        >
          <div class="module-item__head">
            <div class="flex items-center gap-2">
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">{{ item.name }}</div>
              <el-tag size="small" :type="item.enabled ? 'success' : 'danger'">
                {{ item.enabled ? '运行中' : '已关闭' }}
              </el-tag>
              <el-tag size="small" effect="plain" :type="riskMeta[item.riskLevel]?.type || 'info'">
                {{ riskMeta[item.riskLevel]?.label || '普通' }}
              </el-tag>
            </div>
            <el-switch
              :model-value="item.enabled"
              :loading="switchLoading[item.key]"
              @change="val => toggleModule(item, val)"
            />
          </div>

          <div class="mt-2 text-xs leading-6 text-slate-600 dark:text-slate-300">{{ item.desc }}</div>
          <div class="mt-2 text-xs leading-6 text-slate-500 dark:text-slate-400">{{ item.impact }}</div>

          <div class="mt-3 space-y-2 text-xs">
            <div class="module-link-row">
              <span class="module-link-label">依赖</span>
              <span v-if="!item.dependencies.length" class="module-link-empty">无</span>
              <div v-else class="module-link-tags">
                <el-tag v-for="dep in item.dependencies" :key="dep" size="small" effect="light" round>
                  {{ getModuleLabel(dep) }}
                </el-tag>
              </div>
            </div>
            <div class="module-link-row">
              <span class="module-link-label">被依赖</span>
              <span v-if="!item.requiredBy.length" class="module-link-empty">无</span>
              <div v-else class="module-link-tags">
                <el-tag v-for="dep in item.requiredBy" :key="dep" size="small" type="warning" effect="light" round>
                  {{ getModuleLabel(dep) }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.module-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.module-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.module-stat {
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  padding: 16px;
}

.module-stat .label {
  font-size: 12px;
  color: #64748b;
}

.module-stat .value {
  margin-top: 8px;
  font-size: 26px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.module-stat.is-ok .value {
  color: #059669;
}

.module-stat.is-warn .value,
.module-stat.is-risk .value {
  color: #dc2626;
}

.module-group-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #e0f2fe);
  color: #0369a1;
}

.module-item {
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.28);
  background: rgba(255, 255, 255, 0.75);
  padding: 14px;
  transition: border-color .24s ease, box-shadow .24s ease;
}

.module-item.is-enabled:hover {
  border-color: rgba(59, 130, 246, 0.4);
  box-shadow: 0 10px 28px rgba(2, 132, 199, 0.12);
}

.module-item.is-disabled {
  border-color: rgba(248, 113, 113, 0.4);
  background: rgba(254, 242, 242, 0.65);
}

.module-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.module-link-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.module-link-label {
  color: #64748b;
  min-width: 42px;
}

.module-link-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.module-link-empty {
  color: #94a3b8;
}

@media (max-width: 980px) {
  .module-head {
    flex-direction: column;
  }
}
</style>
