<template>
  <div class="page-stack">
    <GlassCard>
      <div class="page-heading">
        <div>
          <p class="section-kicker">Risk Control</p>
          <h1 class="page-heading__title">风控中心</h1>
          <p class="page-heading__desc">
            统一查看限流命中、黑名单状态和高风险接口。支持在线调整规则、手动封禁与解封，帮助运营快速处理异常流量。
          </p>
        </div>
        <div class="flex flex-wrap gap-3">
          <el-button @click="refreshAll">刷新数据</el-button>
          <el-button type="primary" @click="openAddBlacklistDialog">新增黑名单</el-button>
        </div>
      </div>
    </GlassCard>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      <div class="panel-card p-4">
        <div class="risk-stat__label">总请求数</div>
        <div class="risk-stat__value">{{ formatNumber(overview.totalRequests) }}</div>
      </div>
      <div class="panel-card p-4">
        <div class="risk-stat__label">限流命中</div>
        <div class="risk-stat__value text-amber-600 dark:text-amber-300">{{ formatNumber(overview.limitedRequests) }}</div>
      </div>
      <div class="panel-card p-4">
        <div class="risk-stat__label">黑名单命中</div>
        <div class="risk-stat__value text-rose-600 dark:text-rose-300">{{ formatNumber(overview.blacklistHits) }}</div>
      </div>
      <div class="panel-card p-4">
        <div class="risk-stat__label">限流命中率</div>
        <div class="risk-stat__value text-blue-600 dark:text-blue-300">{{ formatPercent(overview.limitRate) }}</div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-3">
      <GlassCard title="高频限流接口" class="xl:col-span-2">
        <div ref="routeChartRef" class="h-80 w-full"></div>
      </GlassCard>
      <GlassCard title="黑名单分布">
        <div ref="blacklistChartRef" class="h-80 w-full"></div>
      </GlassCard>
    </div>

    <GlassCard title="限流规则配置">
      <template #header>
        <div class="flex items-center gap-3">
          <el-tag size="small" :type="overview.enabled ? 'success' : 'danger'">
            {{ overview.enabled ? '风控已启用' : '风控未启用' }}
          </el-tag>
          <el-tag size="small" effect="plain">模式 {{ String(overview.mode || 'BLOCK').toUpperCase() }}</el-tag>
          <el-input
            v-model="ruleKeyword"
            class="!w-64"
            clearable
            placeholder="搜索 routeId / 名称 / 路径"
          />
        </div>
      </template>

      <el-table :data="visibleRules" v-loading="loadingRules" class="!bg-transparent">
        <el-table-column prop="routeId" label="Route ID" min-width="180" />
        <el-table-column label="接口" min-width="240">
          <template #default="{ row }">
            <div class="font-medium text-slate-800 dark:text-slate-100">{{ row.method }} {{ row.pathPattern }}</div>
            <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column label="限流阈值" min-width="180">
          <template #default="{ row }">
            {{ row.maxRequests }} 次 / {{ row.windowSeconds }} 秒
          </template>
        </el-table-column>
        <el-table-column label="封禁策略" min-width="200">
          <template #default="{ row }">
            触发 {{ row.banThreshold }} 次后封禁 {{ row.banDurationSeconds }} 秒
          </template>
        </el-table-column>
        <el-table-column prop="subjectType" label="主体维度" width="110" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="!!row.enabled"
              @change="value => handleRuleEnabledChange(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <el-button size="small" @click="openRuleDialog(row)">编辑</el-button>
              <el-button size="small" type="warning" plain @click="handleResetRule(row)">重置</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </GlassCard>

    <GlassCard title="黑名单管理">
      <template #header>
        <div class="flex flex-wrap items-center gap-3">
          <el-select v-model="blacklistFilters.subjectType" class="!w-32" @change="handleBlacklistSearch">
            <el-option label="全部" value="ALL" />
            <el-option label="IP" value="IP" />
            <el-option label="用户" value="USER" />
            <el-option label="设备" value="DEVICE" />
          </el-select>
          <el-input
            v-model="blacklistFilters.keyword"
            class="!w-64"
            clearable
            placeholder="关键词（对象值/原因）"
            @keyup.enter="handleBlacklistSearch"
          />
          <el-button @click="handleBlacklistSearch">查询</el-button>
        </div>
      </template>

      <el-table :data="blacklistRecords" v-loading="loadingBlacklist" class="!bg-transparent">
        <el-table-column prop="subjectType" label="类型" width="100" />
        <el-table-column prop="subjectValue" label="对象" min-width="180" />
        <el-table-column prop="reason" label="原因" min-width="220" />
        <el-table-column prop="source" label="来源" width="100" />
        <el-table-column prop="operator" label="操作者" width="120" />
        <el-table-column label="有效期" min-width="190">
          <template #default="{ row }">
            <span v-if="row.permanent">永久</span>
            <span v-else>{{ row.expireAt || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain @click="handleRemoveBlacklist(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="blacklistPagination.page"
          v-model:page-size="blacklistPagination.size"
          background
          layout="total, prev, pager, next"
          :total="blacklistPagination.total"
          @current-change="fetchBlacklist"
        />
      </div>
    </GlassCard>

    <el-dialog v-model="ruleDialogVisible" title="编辑限流规则" width="620px">
      <el-form label-width="120px">
        <el-form-item label="Route ID">
          <el-input v-model="ruleForm.routeId" disabled />
        </el-form-item>
        <el-form-item label="主体维度">
          <el-select v-model="ruleForm.subjectType">
            <el-option label="IP" value="IP" />
            <el-option label="USER" value="USER" />
            <el-option label="DEVICE" value="DEVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间窗口(秒)">
          <el-input-number v-model="ruleForm.windowSeconds" :min="1" :max="86400" />
        </el-form-item>
        <el-form-item label="窗口最大次数">
          <el-input-number v-model="ruleForm.maxRequests" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="封禁触发次数">
          <el-input-number v-model="ruleForm.banThreshold" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="封禁时长(秒)">
          <el-input-number v-model="ruleForm.banDurationSeconds" :min="1" :max="2592000" />
        </el-form-item>
        <el-form-item label="违规统计窗口">
          <el-input-number v-model="ruleForm.violationWindowSeconds" :min="1" :max="86400" />
        </el-form-item>
        <el-form-item label="规则开关">
          <el-switch v-model="ruleForm.enabled" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="blacklistDialogVisible" title="新增黑名单" width="520px">
      <el-form label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="blacklistForm.subjectType">
            <el-option label="IP" value="IP" />
            <el-option label="USER" value="USER" />
            <el-option label="DEVICE" value="DEVICE" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象值">
          <el-input v-model="blacklistForm.subjectValue" placeholder="例如 127.0.0.1 / 10001 / device-xxx" />
        </el-form-item>
        <el-form-item label="封禁时长">
          <el-select v-model="blacklistForm.durationSeconds">
            <el-option label="15 分钟" :value="900" />
            <el-option label="1 小时" :value="3600" />
            <el-option label="24 小时" :value="86400" />
            <el-option label="永久" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="blacklistForm.reason" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blacklistDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBlacklist" @click="saveBlacklist">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import GlassCard from '../../components/GlassCard.vue'
import {
  addAdminRiskBlacklist,
  getAdminRiskBlacklist,
  getAdminRiskOverview,
  getAdminRiskRules,
  removeAdminRiskBlacklist,
  resetAdminRiskRule,
  updateAdminRiskRule,
} from '../../api/admin'

const loadingRules = ref(false)
const loadingBlacklist = ref(false)
const overview = ref({})
const rules = ref([])
const blacklistRecords = ref([])
const ruleKeyword = ref('')
const routeChartRef = ref(null)
const blacklistChartRef = ref(null)
const ruleDialogVisible = ref(false)
const savingRule = ref(false)
const blacklistDialogVisible = ref(false)
const savingBlacklist = ref(false)
const blacklistFilters = reactive({
  subjectType: 'ALL',
  keyword: '',
})
const blacklistPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})
const ruleForm = reactive({
  routeId: '',
  subjectType: 'IP',
  windowSeconds: 60,
  maxRequests: 10,
  banThreshold: 6,
  banDurationSeconds: 900,
  violationWindowSeconds: 600,
  enabled: true,
  description: '',
})
const blacklistForm = reactive({
  subjectType: 'IP',
  subjectValue: '',
  durationSeconds: 3600,
  reason: '',
})

let routeChart = null
let blacklistChart = null
let themeObserver = null

const visibleRules = computed(() => {
  const keyword = String(ruleKeyword.value || '').trim().toLowerCase()
  if (!keyword) {
    return rules.value
  }
  return rules.value.filter(item => {
    const source = `${item.routeId || ''} ${item.name || ''} ${item.pathPattern || ''}`.toLowerCase()
    return source.includes(keyword)
  })
})

const fetchOverview = async () => {
  overview.value = await getAdminRiskOverview()
}

const fetchRules = async () => {
  loadingRules.value = true
  try {
    rules.value = await getAdminRiskRules()
  } finally {
    loadingRules.value = false
  }
}

const fetchBlacklist = async () => {
  loadingBlacklist.value = true
  try {
    const data = await getAdminRiskBlacklist({
      page: blacklistPagination.page,
      size: blacklistPagination.size,
      subjectType: blacklistFilters.subjectType,
      keyword: blacklistFilters.keyword || undefined,
    })
    blacklistRecords.value = Array.isArray(data?.records) ? data.records : []
    blacklistPagination.total = Number(data?.total || 0)
  } finally {
    loadingBlacklist.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([fetchOverview(), fetchRules(), fetchBlacklist()])
  await nextTick()
  renderCharts()
}

const handleBlacklistSearch = () => {
  blacklistPagination.page = 1
  fetchBlacklist()
}

const openRuleDialog = row => {
  Object.assign(ruleForm, {
    routeId: row.routeId,
    subjectType: row.subjectType || 'IP',
    windowSeconds: Number(row.windowSeconds || 60),
    maxRequests: Number(row.maxRequests || 10),
    banThreshold: Number(row.banThreshold || 6),
    banDurationSeconds: Number(row.banDurationSeconds || 900),
    violationWindowSeconds: Number(row.violationWindowSeconds || 600),
    enabled: !!row.enabled,
    description: row.description || '',
  })
  ruleDialogVisible.value = true
}

const saveRule = async () => {
  if (!ruleForm.routeId) return
  savingRule.value = true
  try {
    await updateAdminRiskRule(ruleForm.routeId, {
      subjectType: ruleForm.subjectType,
      windowSeconds: ruleForm.windowSeconds,
      maxRequests: ruleForm.maxRequests,
      banThreshold: ruleForm.banThreshold,
      banDurationSeconds: ruleForm.banDurationSeconds,
      violationWindowSeconds: ruleForm.violationWindowSeconds,
      enabled: ruleForm.enabled,
      description: ruleForm.description,
    })
    ElMessage.success('规则更新成功')
    ruleDialogVisible.value = false
    await refreshAll()
  } finally {
    savingRule.value = false
  }
}

const handleRuleEnabledChange = async (row, value) => {
  const previous = !!row.enabled
  row.enabled = !!value
  try {
    await updateAdminRiskRule(row.routeId, { enabled: !!value })
    ElMessage.success(`规则已${value ? '启用' : '停用'}`)
  } catch (error) {
    row.enabled = previous
    ElMessage.error(error?.message || '规则更新失败')
  }
}

const handleResetRule = async row => {
  await ElMessageBox.confirm(`确定重置规则「${row.routeId}」到默认配置吗？`, '重置规则', {
    type: 'warning',
    confirmButtonText: '重置',
    cancelButtonText: '取消',
  })
  await resetAdminRiskRule(row.routeId)
  ElMessage.success('规则已重置')
  await refreshAll()
}

const openAddBlacklistDialog = () => {
  Object.assign(blacklistForm, {
    subjectType: 'IP',
    subjectValue: '',
    durationSeconds: 3600,
    reason: '',
  })
  blacklistDialogVisible.value = true
}

const saveBlacklist = async () => {
  if (!String(blacklistForm.subjectValue || '').trim()) {
    ElMessage.warning('请填写黑名单对象值')
    return
  }
  savingBlacklist.value = true
  try {
    await addAdminRiskBlacklist({
      subjectType: blacklistForm.subjectType,
      subjectValue: blacklistForm.subjectValue.trim(),
      durationSeconds: Number(blacklistForm.durationSeconds),
      reason: String(blacklistForm.reason || '').trim(),
    })
    ElMessage.success('黑名单已添加')
    blacklistDialogVisible.value = false
    await refreshAll()
  } finally {
    savingBlacklist.value = false
  }
}

const handleRemoveBlacklist = async row => {
  await ElMessageBox.confirm(`确定移除 ${row.subjectType} ${row.subjectValue} 吗？`, '移除黑名单', {
    type: 'warning',
    confirmButtonText: '移除',
    cancelButtonText: '取消',
  })
  await removeAdminRiskBlacklist(row.subjectType, row.subjectValue)
  ElMessage.success('已移除黑名单')
  await refreshAll()
}

const formatNumber = value => Number(value || 0).toLocaleString('zh-CN')
const formatPercent = value => `${Number(value || 0).toFixed(2)}%`

const renderCharts = () => {
  renderRouteChart()
  renderBlacklistChart()
}

const renderRouteChart = () => {
  if (!routeChartRef.value) return
  routeChart?.dispose()
  routeChart = echarts.init(routeChartRef.value)
  const rows = Array.isArray(overview.value?.topLimitedRoutes) ? overview.value.topLimitedRoutes : []
  const labels = rows.map(item => item.routeId || '-')
  const values = rows.map(item => Number(item.limited || 0))

  routeChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 24, left: 16, right: 16, bottom: 24, containLabel: true },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: labels.length ? labels : ['暂无命中数据'],
      axisLabel: { width: 180, overflow: 'truncate' },
    },
    series: [{
      type: 'bar',
      data: values.length ? values : [0],
      barWidth: 14,
      itemStyle: {
        borderRadius: [0, 8, 8, 0],
        color: '#3b82f6',
      },
    }],
  })
}

const renderBlacklistChart = () => {
  if (!blacklistChartRef.value) return
  blacklistChart?.dispose()
  blacklistChart = echarts.init(blacklistChartRef.value)
  const summary = overview.value?.blacklistSummary || {}
  const data = [
    { name: 'IP', value: Number(summary.ip || 0) },
    { name: 'USER', value: Number(summary.user || 0) },
    { name: 'DEVICE', value: Number(summary.device || 0) },
  ]
  blacklistChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['42%', '70%'],
      center: ['50%', '42%'],
      data,
      label: { show: false },
      emphasis: { label: { show: true, formatter: '{b}\n{c}' } },
    }],
  })
}

const handleResize = () => {
  routeChart?.resize()
  blacklistChart?.resize()
}

onMounted(async () => {
  await refreshAll()
  window.addEventListener('resize', handleResize)
  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(item => item.attributeName === 'class')) {
      renderCharts()
    }
  })
  themeObserver.observe(document.documentElement, { attributes: true })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  routeChart?.dispose()
  blacklistChart?.dispose()
  themeObserver?.disconnect()
})
</script>

<style scoped>
.risk-stat__label {
  font-size: 12px;
  color: #64748b;
}

.dark .risk-stat__label {
  color: #94a3b8;
}

.risk-stat__value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.dark .risk-stat__value {
  color: #e2e8f0;
}
</style>
