<template>
  <div class="seckill-page">
    <div class="panel-card seckill-hero">
      <div class="seckill-hero__content">
        <div>
          <p class="seckill-hero__eyebrow">营销活动</p>
          <h2 class="seckill-hero__title">秒杀活动管理</h2>
        </div>
        <div class="seckill-hero__stats">
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">{{ activeTab === 'activities' ? '活动总数' : '报名总数' }}</span>
            <strong class="seckill-hero__stat-value">{{ activeTab === 'activities' ? activityPagination.total : applicationPagination.total }}</strong>
          </article>
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">当前页记录</span>
            <strong class="seckill-hero__stat-value">{{ activeTab === 'activities' ? activities.length : applications.length }}</strong>
          </article>
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">当前视图</span>
            <strong class="seckill-hero__stat-value seckill-hero__stat-value--text">{{ activeTab === 'activities' ? '活动管理' : '报名审核' }}</strong>
          </article>
        </div>
      </div>
    </div>

    <div class="panel-card section-shell">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="活动管理" name="activities" />
          <el-tab-pane label="报名审核" name="applications" />
        </el-tabs>
        <el-button
          v-if="activeTab === 'activities'"
          type="primary"
          class="!rounded-xl"
          @click="openCreateActivityDialog"
        >
          <el-icon class="mr-2"><Plus /></el-icon>
          新建秒杀活动
        </el-button>
      </div>
    </div>

    <div class="panel-card section-shell seckill-diagnostics">
      <div class="section-shell__head">
        <div>
          <h3 class="section-shell__title">会场诊断与压测</h3>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <el-tag :type="diagnosticLevel" effect="light" round>
            {{ diagnostics.diagnosis || '等待诊断' }}
          </el-tag>
          <el-button size="small" :loading="diagnosticsLoading" @click="fetchSeckillDiagnostics">刷新诊断</el-button>
        </div>
      </div>

      <div class="diagnostic-grid">
        <div class="diagnostic-metric" v-for="item in diagnosticStats" :key="item.label">
          <span class="diagnostic-metric__label">{{ item.label }}</span>
          <strong class="diagnostic-metric__value">{{ item.value }}</strong>
        </div>
      </div>

      <div class="stress-panel">
        <div>
          <div class="stress-panel__title">一键秒杀链路测试</div>
          <div class="stress-panel__desc">库存 · 并发 · 限流</div>
        </div>
        <div class="stress-panel__command">
          <code>{{ stressCommand }}</code>
          <div class="stress-panel__actions">
            <el-button size="small" @click="copyStressCommand">复制命令</el-button>
            <el-button
              size="small"
              type="primary"
              :loading="stressRunning"
              @click="runStressTest"
            >
              创建秒杀商品并测试
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="stressResult" class="stress-result">
        <div class="stress-result__head">
          <div>
            <div class="stress-result__title">最近一次测试结果</div>
            <div class="stress-result__desc">报告：{{ stressResult.reportPath || '-' }}</div>
          </div>
          <el-tag :type="stressResult.success ? 'success' : 'warning'" effect="light" round>
            {{ stressResult.success ? '有成功订单' : '未产生成功订单' }}
          </el-tag>
        </div>
        <div class="stress-result__grid">
          <div v-for="item in stressSummaryStats" :key="item.label" class="stress-result__metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </div>

    <template v-if="activeTab === 'activities'">
      <div class="panel-card section-shell">
        <div class="section-shell__head">
          <div>
            <h3 class="section-shell__title">活动筛选</h3>
          </div>
          <div class="section-shell__meta">共 {{ activityPagination.total }} 条活动记录</div>
        </div>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
          <el-input
            v-model="activityFilters.keyword"
            clearable
            placeholder="搜索活动名称"
            @keyup.enter="handleActivityFilterChange"
            @clear="handleActivityFilterChange"
          />
          <el-select
            v-model="activityFilters.publishStatus"
            clearable
            placeholder="发布状态"
            @change="handleActivityFilterChange"
          >
            <el-option label="全部状态" value="" />
            <el-option label="未发布" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
          <div class="md:col-span-2 flex justify-start md:justify-end">
            <el-button @click="handleActivityFilterChange">查询</el-button>
          </div>
        </div>
      </div>

      <div class="panel-card section-shell section-shell--table">
        <div class="section-shell__head section-shell__head--table">
          <div>
            <h3 class="section-shell__title">活动列表</h3>
          </div>
          <div class="section-shell__meta">当前页 {{ activities.length }} 条</div>
        </div>
        <el-table
          v-loading="activitiesLoading"
          :data="activities"
          class="!bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
          <el-table-column prop="id" label="活动ID" width="96" />
          <el-table-column label="活动信息" min-width="260">
            <template #default="{ row }">
              <div class="entity-block">
                <el-image
                  v-if="row.coverImage"
                  :src="row.coverImage"
                  fit="cover"
                  class="entity-block__cover"
                />
                <div class="entity-block__content">
                  <div class="entity-block__title">
                    {{ row.name || '-' }}
                  </div>
                  <div class="entity-block__desc">
                    {{ row.description || '暂无活动说明' }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="活动时间" min-width="220">
            <template #default="{ row }">
              <div class="time-block">
                <div class="time-block__item">
                  <span class="time-block__label">开始</span>
                  <span class="time-block__value">{{ row.startTime || '-' }}</span>
                </div>
                <div class="time-block__item">
                  <span class="time-block__label">结束</span>
                  <span class="time-block__value">{{ row.endTime || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="resolvePublishTagType(row)" effect="light" round>
                {{ resolvePublishText(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="活动状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="resolveRuntimeTagType(row)" effect="plain" round>
                {{ resolveRuntimeText(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="报名概览" width="140" align="center">
            <template #default="{ row }">
              <div class="overview-block">
                <div class="overview-block__item">
                  <span class="overview-block__label">报名</span>
                  <strong class="overview-block__value">{{ row.applyCount ?? 0 }}</strong>
                </div>
                <div class="overview-block__divider"></div>
                <div class="overview-block__item overview-block__item--success">
                  <span class="overview-block__label">通过</span>
                  <strong class="overview-block__value">{{ row.approvedCount ?? 0 }}</strong>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" align="right" fixed="right">
            <template #default="{ row }">
              <div class="flex justify-end gap-2">
                <el-button size="small" @click="openEditActivityDialog(row)">编辑</el-button>
                <el-button
                  size="small"
                  :type="isPublished(row) ? 'warning' : 'success'"
                  plain
                  @click="togglePublish(row)"
                >
                  {{ isPublished(row) ? '下线' : '发布' }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="activityPagination.page"
            v-model:page-size="activityPagination.size"
            background
            layout="total, prev, pager, next"
            :total="activityPagination.total"
            @current-change="fetchActivities"
          />
        </div>
      </div>
    </template>

    <template v-else>
      <div class="panel-card section-shell">
        <div class="section-shell__head">
          <div>
            <h3 class="section-shell__title">报名筛选</h3>
          </div>
          <div class="section-shell__meta">共 {{ applicationPagination.total }} 条报名记录</div>
        </div>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
          <el-select
            v-model="applicationFilters.activityId"
            clearable
            placeholder="按活动筛选"
            @change="handleApplicationFilterChange"
          >
            <el-option
              v-for="item in activityOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <el-select
            v-model="applicationFilters.status"
            clearable
            placeholder="审核状态"
            @change="handleApplicationFilterChange"
          >
            <el-option label="全部状态" value="" />
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
            <el-option label="已撤回" :value="3" />
          </el-select>
          <div class="md:col-span-2 flex justify-start md:justify-end">
            <el-button @click="handleApplicationFilterChange">查询</el-button>
          </div>
        </div>
      </div>

      <div class="panel-card section-shell section-shell--table">
        <div class="section-shell__head section-shell__head--table">
          <div>
            <h3 class="section-shell__title">报名审核列表</h3>
          </div>
          <div class="section-shell__meta">当前页 {{ applications.length }} 条</div>
        </div>
        <el-table
          v-loading="applicationsLoading"
          :data="applications"
          class="!bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
          <el-table-column prop="id" label="报名ID" width="94" />
          <el-table-column prop="activityName" label="活动名称" min-width="160" show-overflow-tooltip />
          <el-table-column label="活动时间" min-width="220">
            <template #default="{ row }">
              <div class="time-block">
                <div class="time-block__item">
                  <span class="time-block__label">开始</span>
                  <span class="time-block__value">{{ row.activityStartTime || row.startTime || '-' }}</span>
                </div>
                <div class="time-block__item">
                  <span class="time-block__label">结束</span>
                  <span class="time-block__value">{{ row.activityEndTime || row.endTime || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="merchantName" label="商家" width="120" show-overflow-tooltip />
          <el-table-column prop="productName" label="商品" min-width="170" show-overflow-tooltip />
          <el-table-column label="价格" width="140">
            <template #default="{ row }">
              <div class="price-block">
                <div class="price-block__origin">原价 ¥{{ formatMoney(row.originalPrice) }}</div>
                <div class="price-block__current">秒杀 ¥{{ formatMoney(row.seckillPrice) }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="库存/限购" width="120" align="center">
            <template #default="{ row }">
              <div class="quota-block">
                <div class="quota-block__item">库存 {{ row.seckillStock ?? 0 }}</div>
                <div class="quota-block__item">限购 {{ row.limitPerUser ?? 1 }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="applicationStatusMap[resolveApplicationStatus(row)]?.type || 'info'" effect="light" round>
                {{ applicationStatusMap[resolveApplicationStatus(row)]?.label || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="驳回原因" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.rejectReason || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="报名时间" width="168">
            <template #default="{ row }">{{ row.createTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="审核时间" width="168">
            <template #default="{ row }">{{ row.auditTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="250" align="right" fixed="right">
            <template #default="{ row }">
              <div class="flex justify-end gap-2">
                <template v-if="canScheduleApplication(row)">
                  <el-button size="small" type="primary" plain @click="openScheduleDialog(row)">安排</el-button>
                </template>
                <template v-if="resolveApplicationStatus(row) === 0">
                  <el-button size="small" type="success" @click="approveApplication(row)">通过</el-button>
                  <el-button size="small" type="danger" plain @click="rejectApplication(row)">驳回</el-button>
                </template>
                <span v-if="!canScheduleApplication(row) && resolveApplicationStatus(row) !== 0" class="text-xs text-slate-400">
                  已处理
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="applicationPagination.page"
            v-model:page-size="applicationPagination.size"
            background
            layout="total, prev, pager, next"
            :total="applicationPagination.total"
            @current-change="fetchApplications"
          />
        </div>
      </div>
    </template>

    <el-dialog
      v-model="activityDialog.visible"
      :title="activityDialog.mode === 'create' ? '新建秒杀活动' : '编辑秒杀活动'"
      width="min(92vw, 760px)"
      destroy-on-close
    >
      <el-form ref="activityFormRef" :model="activityForm" :rules="activityRules" label-width="110px">
        <div class="dialog-tip">
          <div class="dialog-tip__title">创建规则提示</div>
          <div class="dialog-tip__desc">活动开始后，涉及交易的核心时间与商品配置不建议再调整，保持场次配置稳定。</div>
        </div>
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="activityForm.name" maxlength="100" show-word-limit placeholder="例如：618 手机秒杀专场" />
        </el-form-item>

        <el-form-item label="活动封面">
          <el-input v-model="activityForm.coverImage" placeholder="请输入封面图片 URL" />
        </el-form-item>

        <el-form-item label="活动说明">
          <el-input
            v-model="activityForm.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="描述活动亮点和规则"
          />
        </el-form-item>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="activityForm.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-full"
            />
          </el-form-item>
          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="activityForm.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-full"
            />
          </el-form-item>
        </div>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="排序权重">
            <el-input-number v-model="activityForm.sortOrder" :min="0" class="!w-full" />
          </el-form-item>
          <el-form-item label="发布状态">
            <el-select v-model="activityForm.publishStatus" class="w-full">
              <el-option label="未发布" :value="0" />
              <el-option label="已发布" :value="1" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="activityDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="activityDialog.submitting" @click="submitActivityForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scheduleDialog.visible"
      title="安排报名到活动"
      width="min(92vw, 640px)"
      destroy-on-close
    >
      <el-form ref="scheduleFormRef" :model="scheduleForm" :rules="scheduleRules" label-width="120px">
        <div class="dialog-tip dialog-tip--subtle">
          <div class="dialog-tip__title">安排说明</div>
          <div class="dialog-tip__desc">优先展示未开始且已发布的活动，安排成功后将自动进入对应秒杀场次的商家通知链路。</div>
        </div>
        <el-form-item label="报名记录">
          <div class="dialog-info-card">
            {{ scheduleDialog.current?.productName || '-' }}（商家：{{ scheduleDialog.current?.merchantName || '-' }}）
          </div>
        </el-form-item>

        <el-form-item label="当前活动">
          <div class="dialog-info-card">
            {{ scheduleDialog.current?.activityName || '-' }}
          </div>
        </el-form-item>

        <el-form-item label="目标活动" prop="activityId">
          <el-select
            v-model="scheduleForm.activityId"
            class="w-full"
            filterable
            placeholder="请选择目标活动（优先显示未开始且已发布）"
          >
            <el-option
              v-for="item in scheduleActivityOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
              :disabled="item.disabled"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="truncate text-sm">{{ item.name }}</span>
                <el-tag size="small" :type="item.runtimeTagType" effect="plain">{{ item.runtimeText }}</el-tag>
              </div>
              <div class="truncate text-xs text-slate-400">
                {{ item.startTime || '-' }} 至 {{ item.endTime || '-' }}
              </div>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="scheduleDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="scheduleDialog.submitting" @click="submitSchedule">
          确认安排
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  approveAdminSeckillApplication,
  createAdminSeckillActivity,
  getAdminSeckillActivities,
  getAdminSeckillApplications,
  getAdminSeckillDiagnostics,
  rejectAdminSeckillApplication,
  runAdminSeckillStressTest,
  scheduleAdminSeckillApplication,
  toggleAdminSeckillActivityPublish,
  updateAdminSeckillActivity,
} from '../../api/admin'

const activeTab = ref('activities')

const activitiesLoading = ref(false)
const activities = ref([])
const activityOptions = ref([])
const activityFilters = reactive({
  keyword: '',
  publishStatus: '',
})
const activityPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const applicationsLoading = ref(false)
const applications = ref([])
const applicationFilters = reactive({
  activityId: undefined,
  status: '',
})
const applicationPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const diagnosticsLoading = ref(false)
const diagnostics = ref({})
const stressRunning = ref(false)
const stressResult = ref(null)
const stressCommand = 'POST /api/admin/seckill/stress-test/run { autoLoginUsers: 20, seckillStock: 10, rechargeAmount: 50000, concurrency: 20, requests: 200 }'

const diagnosticStats = computed(() => [
  { label: '模块状态', value: diagnostics.value.moduleEnabled === false ? '关闭' : '开启' },
  { label: '活动总数', value: diagnostics.value.activityCount ?? 0 },
  { label: '已发布活动', value: diagnostics.value.publishedActivityCount ?? 0 },
  { label: '报名总数', value: diagnostics.value.applicationCount ?? 0 },
  { label: '通过报名', value: diagnostics.value.approvedApplicationCount ?? 0 },
  { label: '会场可见商品', value: diagnostics.value.visibleProductCount ?? 0 },
])

const diagnosticLevel = computed(() => {
  if (diagnostics.value.moduleEnabled === false) return 'danger'
  if (Number(diagnostics.value.visibleProductCount || 0) <= 0) return 'warning'
  return 'success'
})

const stressSummary = computed(() => stressResult.value?.report?.summary || {})
const stressPreparation = computed(() => stressResult.value?.report?.preparation || {})
const stressSeckillData = computed(() => stressPreparation.value?.seckillData || {})
const stressAccountSummary = computed(() => {
  const accounts = stressPreparation.value.accounts || []
  const rechargeAmount = Number(stressPreparation.value.rechargeAmount || 0)
  const productName = stressSeckillData.value.productName
  if (!accounts.length) {
    return '暂无账号准备信息'
  }
  return `已创建 ${productName || '秒杀测试商品'}，已准备 ${accounts.length} 个普通买家账号，每个账号充值 ¥${rechargeAmount.toFixed(2)}`
})
const stressSummaryStats = computed(() => [
  { label: '请求数', value: stressSummary.value.requests ?? 0 },
  { label: '成功', value: stressSummary.value.success ?? 0 },
  { label: '失败', value: stressSummary.value.failed ?? 0 },
  { label: '耗时', value: `${stressSummary.value.duration_seconds ?? 0} 秒` },
  { label: '抢购结果', value: Number(stressSummary.value.success || 0) > 0 ? '抢到' : '未抢到' },
  { label: '成功率', value: stressSuccessRate.value },
  { label: '吞吐/秒', value: stressSummary.value.throughput_per_second ?? 0 },
  { label: 'P95 延迟', value: `${stressSummary.value.latency_ms?.p95 ?? 0} ms` },
])
const stressSuccessRate = computed(() => {
  const requests = Number(stressSummary.value.requests || 0)
  if (!requests) return '0%'
  return `${((Number(stressSummary.value.success || 0) / requests) * 100).toFixed(1)}%`
})

const applicationStatusMap = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
  3: { label: '已撤回', type: 'info' },
}

const activityDialog = reactive({
  visible: false,
  mode: 'create',
  submitting: false,
})
const activityFormRef = ref(null)
const activityForm = reactive({
  id: null,
  name: '',
  coverImage: '',
  description: '',
  startTime: '',
  endTime: '',
  sortOrder: 0,
  publishStatus: 0,
})

const scheduleDialog = reactive({
  visible: false,
  submitting: false,
  current: null,
})
const scheduleFormRef = ref(null)
const scheduleForm = reactive({
  activityId: undefined,
})
const scheduleRules = {
  activityId: [{ required: true, message: '请选择目标活动', trigger: 'change' }],
}
const scheduleActivityOptions = ref([])

const validateActivityTime = (_rule, _value, callback) => {
  if (!activityForm.startTime || !activityForm.endTime) {
    callback(new Error('请选择完整活动时间'))
    return
  }
  if (new Date(activityForm.endTime).getTime() <= new Date(activityForm.startTime).getTime()) {
    callback(new Error('结束时间必须晚于开始时间'))
    return
  }
  callback()
}

const activityRules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  startTime: [{ validator: validateActivityTime, trigger: 'change' }],
  endTime: [{ validator: validateActivityTime, trigger: 'change' }],
}

const normalizePageData = payload => {
  if (Array.isArray(payload)) {
    return { records: payload, total: payload.length }
  }
  if (!payload || typeof payload !== 'object') {
    return { records: [], total: 0 }
  }
  const records = Array.isArray(payload.records) ? payload.records : []
  const total = Number(payload.total ?? records.length ?? 0)
  return { records, total }
}

const resolveApplicationStatus = row => Number(row?.status ?? row?.auditStatus ?? 0)

const formatMoney = value => Number(value || 0).toFixed(2)

const isPublished = row => Number(row?.publishStatus ?? row?.published ?? 0) === 1

const canScheduleApplication = row => {
  const status = resolveApplicationStatus(row)
  return status === 0 || status === 1
}

const resolveRuntimeCode = row => {
  const runtimeStatus = row?.runtimeStatus
  if (runtimeStatus != null && runtimeStatus !== '') {
    const normalized = Number(runtimeStatus)
    if (Number.isFinite(normalized) && [0, 1, 2].includes(normalized)) {
      return normalized
    }
  }
  const start = row?.startTime ? new Date(row.startTime).getTime() : null
  const end = row?.endTime ? new Date(row.endTime).getTime() : null
  const now = Date.now()
  if (start && now < start) return 0
  if (end && now > end) return 2
  return 1
}

const resolvePublishText = row => (isPublished(row) ? '已发布' : '未发布')

const resolvePublishTagType = row => (isPublished(row) ? 'success' : 'info')

const resolveRuntimeText = row => {
  const runtimeCode = resolveRuntimeCode(row)
  if (runtimeCode === 0) return '未开始'
  if (runtimeCode === 1) return '进行中'
  return '已结束'
}

const resolveRuntimeTagType = row => {
  const text = resolveRuntimeText(row)
  if (text === '进行中') return 'danger'
  if (text === '未开始') return 'warning'
  return 'info'
}

const refreshActivityOptions = async () => {
  try {
    const res = await getAdminSeckillActivities({ page: 1, size: 500 })
    const pageData = normalizePageData(res)
    activityOptions.value = pageData.records.map(item => ({
      id: item.id ?? item.activityId,
      name: item.name || item.activityName || `活动#${item.id ?? item.activityId}`,
      startTime: item.startTime,
      endTime: item.endTime,
      publishStatus: Number(item.publishStatus ?? item.published ?? 0),
      runtimeStatus: resolveRuntimeCode(item),
    }))
  } catch {
    activityOptions.value = []
  }
}

const buildScheduleActivityOptions = currentActivityId =>
  activityOptions.value
    .filter(item => item.id !== currentActivityId)
    .map(item => {
      const runtimeCode = resolveRuntimeCode(item)
      const selectable = item.publishStatus === 1 && runtimeCode === 0
      const priority = item.publishStatus === 1
        ? (runtimeCode === 0 ? 0 : runtimeCode === 1 ? 1 : 2)
        : 3
      return {
        ...item,
        runtimeCode,
        runtimeText: runtimeCode === 0 ? '未开始' : runtimeCode === 1 ? '进行中' : '已结束',
        runtimeTagType: runtimeCode === 0 ? 'warning' : runtimeCode === 1 ? 'danger' : 'info',
        disabled: !selectable,
        priority,
      }
    })
    .sort((a, b) => {
      if (a.priority !== b.priority) return a.priority - b.priority
      const aStart = a.startTime ? new Date(a.startTime).getTime() : Number.MAX_SAFE_INTEGER
      const bStart = b.startTime ? new Date(b.startTime).getTime() : Number.MAX_SAFE_INTEGER
      if (aStart !== bStart) return aStart - bStart
      return a.id - b.id
    })

const fetchActivities = async () => {
  activitiesLoading.value = true
  try {
    const res = await getAdminSeckillActivities({
      page: activityPagination.page,
      size: activityPagination.size,
      keyword: activityFilters.keyword || undefined,
      publishStatus: activityFilters.publishStatus === '' ? undefined : activityFilters.publishStatus,
    })
    const pageData = normalizePageData(res)
    activities.value = pageData.records
    activityPagination.total = pageData.total
  } finally {
    activitiesLoading.value = false
  }
}

const fetchApplications = async () => {
  applicationsLoading.value = true
  try {
    const res = await getAdminSeckillApplications({
      page: applicationPagination.page,
      size: applicationPagination.size,
      activityId: applicationFilters.activityId,
      status: applicationFilters.status === '' ? undefined : applicationFilters.status,
    })
    const pageData = normalizePageData(res)
    applications.value = pageData.records
    applicationPagination.total = pageData.total
  } finally {
    applicationsLoading.value = false
  }
}

const fetchSeckillDiagnostics = async () => {
  diagnosticsLoading.value = true
  try {
    diagnostics.value = await getAdminSeckillDiagnostics()
  } finally {
    diagnosticsLoading.value = false
  }
}

const copyStressCommand = async () => {
  try {
    await navigator.clipboard.writeText(stressCommand)
    ElMessage.success('压测命令已复制')
  } catch {
    ElMessage.info(stressCommand)
  }
}

const escapeHtml = value => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#39;')

const showStressResultPopup = result => {
  const summary = result?.report?.summary || {}
  const preparation = result?.report?.preparation || {}
  const seckillData = preparation.seckillData || {}
  const accounts = preparation.accounts || []
  const requests = Number(summary.requests || 0)
  const success = Number(summary.success || 0)
  const failed = Number(summary.failed || 0)
  const messages = Object.entries(summary.messages || {})
    .map(([message, count]) => `<li>${escapeHtml(message)}：${count}</li>`)
    .join('')

  ElMessageBox.alert(
    `<div class="stress-popup">
      <p><strong>测试商品：</strong>${escapeHtml(seckillData.productName || '-')}（商品ID：${escapeHtml(seckillData.productId || '-')}）</p>
      <p><strong>秒杀报名：</strong>报名ID ${escapeHtml(seckillData.applyId || result?.applyId || '-')}，秒杀库存 ${escapeHtml(seckillData.seckillStock ?? '-')}，测试用户 ${escapeHtml(seckillData.plannedUsers ?? accounts.length)}，限购 ${escapeHtml(seckillData.limitPerUser ?? '-')} 件/人，秒杀价 ¥${escapeHtml(seckillData.seckillPrice ?? '-')}</p>
      <p><strong>测试目的：</strong>${escapeHtml(seckillData.competitionNote || '验证秒杀链路')}</p>
      <p><strong>用时：</strong>${escapeHtml(summary.duration_seconds ?? 0)} 秒</p>
      <p><strong>抢购结果：</strong>${success > 0 ? '抢到' : '未抢到'}</p>
      <p><strong>请求发起：</strong>共 ${requests} 次，成功 ${success} 次，失败 ${failed} 次，成功率 ${escapeHtml(stressSuccessRate.value)}</p>
      <p><strong>测试账号：</strong>${accounts.length} 个，每个充值 ¥${Number(preparation.rechargeAmount || 0).toFixed(2)}</p>
      <p><strong>P95 延迟：</strong>${escapeHtml(summary.latency_ms?.p95 ?? 0)} ms</p>
      <p><strong>报告文件：</strong>${escapeHtml(result?.reportPath || '-')}</p>
      ${messages ? `<p><strong>接口返回：</strong></p><ul>${messages}</ul>` : ''}
    </div>`,
    '秒杀链路测试结果',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '知道了',
    }
  )
}

const runStressTest = async () => {
  stressRunning.value = true
  try {
    stressResult.value = await runAdminSeckillStressTest({
      autoLoginUsers: 20,
      seckillStock: 10,
      rechargeAmount: 50000,
      concurrency: 20,
      requests: 200,
      processTimeoutSeconds: 180,
    })
    const summary = stressResult.value?.report?.summary || {}
    ElMessage.success(`秒杀测试完成：成功 ${summary.success ?? 0}，失败 ${summary.failed ?? 0}`)
    showStressResultPopup(stressResult.value)
    await fetchSeckillDiagnostics()
  } finally {
    stressRunning.value = false
  }
}

const handleTabChange = async tab => {
  if (tab === 'activities') {
    await fetchActivities()
    return
  }
  await Promise.all([refreshActivityOptions(), fetchApplications()])
}

const handleActivityFilterChange = () => {
  activityPagination.page = 1
  fetchActivities()
}

const handleApplicationFilterChange = () => {
  applicationPagination.page = 1
  fetchApplications()
}

const resetActivityForm = () => {
  activityForm.id = null
  activityForm.name = ''
  activityForm.coverImage = ''
  activityForm.description = ''
  activityForm.startTime = ''
  activityForm.endTime = ''
  activityForm.sortOrder = 0
  activityForm.publishStatus = 0
}

const openCreateActivityDialog = () => {
  activityDialog.mode = 'create'
  resetActivityForm()
  activityDialog.visible = true
}

const openEditActivityDialog = row => {
  activityDialog.mode = 'edit'
  activityForm.id = row.id
  activityForm.name = row.name || ''
  activityForm.coverImage = row.coverImage || ''
  activityForm.description = row.description || ''
  activityForm.startTime = row.startTime || ''
  activityForm.endTime = row.endTime || ''
  activityForm.sortOrder = Number(row.sortOrder || 0)
  activityForm.publishStatus = Number(row.publishStatus ?? row.published ?? 0)
  activityDialog.visible = true
}

const submitActivityForm = async () => {
  if (!activityFormRef.value || activityDialog.submitting) return

  await activityFormRef.value.validate()
  activityDialog.submitting = true
  try {
    const payload = {
      name: activityForm.name.trim(),
      coverImage: activityForm.coverImage?.trim() || '',
      description: activityForm.description?.trim() || '',
      startTime: activityForm.startTime,
      endTime: activityForm.endTime,
      sortOrder: activityForm.sortOrder ?? 0,
      publishStatus: activityForm.publishStatus,
      published: Number(activityForm.publishStatus) === 1,
    }
    if (activityDialog.mode === 'create') {
      await createAdminSeckillActivity(payload)
      ElMessage.success('秒杀活动创建成功')
    } else {
      await updateAdminSeckillActivity(activityForm.id, payload)
      ElMessage.success('秒杀活动更新成功')
    }
    activityDialog.visible = false
    await Promise.all([fetchActivities(), refreshActivityOptions()])
  } finally {
    activityDialog.submitting = false
  }
}

const togglePublish = async row => {
  const nextPublished = !isPublished(row)
  const actionText = nextPublished ? '发布' : '下线'
  try {
    await ElMessageBox.confirm(`确定要${actionText}活动“${row.name || row.id}”吗？`, '提示', { type: 'warning' })
    await toggleAdminSeckillActivityPublish(row.id, nextPublished)
    ElMessage.success(`活动已${actionText}`)
    await Promise.all([fetchActivities(), fetchSeckillDiagnostics()])
  } catch {
    // ignore cancel
  }
}

const approveApplication = async row => {
  try {
    await ElMessageBox.confirm(`确定通过报名「${row.productName || row.id}」吗？`, '审核通过', { type: 'warning' })
    await approveAdminSeckillApplication(row.id)
    ElMessage.success('已通过报名审核')
    await Promise.all([fetchApplications(), fetchSeckillDiagnostics()])
    window.dispatchEvent(new Event('admin-workbench-refresh'))
  } catch {
    // ignore cancel
  }
}

const rejectApplication = async row => {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '审核驳回', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputValidator: input => !!input?.trim() || '驳回原因不能为空',
    })
    await rejectAdminSeckillApplication(row.id, value.trim())
    ElMessage.success('已驳回报名')
    await Promise.all([fetchApplications(), fetchSeckillDiagnostics()])
    window.dispatchEvent(new Event('admin-workbench-refresh'))
  } catch {
    // ignore cancel
  }
}

const openScheduleDialog = async row => {
  await refreshActivityOptions()
  scheduleDialog.current = row
  scheduleActivityOptions.value = buildScheduleActivityOptions(row.activityId)
  const selectableOptions = scheduleActivityOptions.value.filter(item => !item.disabled)
  if (!selectableOptions.length) {
    ElMessage.warning('暂无可安排的目标活动')
    return
  }
  scheduleForm.activityId = selectableOptions[0].id
  scheduleDialog.visible = true
}

const submitSchedule = async () => {
  if (!scheduleFormRef.value || scheduleDialog.submitting || !scheduleDialog.current?.id) return
  await scheduleFormRef.value.validate()
  scheduleDialog.submitting = true
  try {
    const targetActivityId = scheduleForm.activityId
    await scheduleAdminSeckillApplication(scheduleDialog.current.id, {
      activityId: targetActivityId,
      targetActivityId,
    })
    ElMessage.success('安排成功')
    scheduleDialog.visible = false
    await Promise.all([fetchApplications(), fetchSeckillDiagnostics()])
    window.dispatchEvent(new Event('admin-workbench-refresh'))
  } finally {
    scheduleDialog.submitting = false
  }
}

onMounted(async () => {
  await Promise.all([fetchActivities(), refreshActivityOptions(), fetchSeckillDiagnostics()])
})
</script>

<style scoped>
.seckill-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.seckill-hero {
  position: relative;
  overflow: hidden;
  padding: 28px;
}

.seckill-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at top right, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(135deg, rgba(15, 23, 42, 0.02), rgba(15, 23, 42, 0));
  pointer-events: none;
}

.seckill-hero__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.seckill-hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #10b981;
}

.seckill-hero__title {
  margin: 0;
  font-size: 30px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text-primary);
}

.seckill-hero__desc {
  margin: 12px 0 0;
  max-width: 720px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-secondary);
}

.seckill-hero__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  min-width: min(100%, 360px);
  flex: 1;
}

.seckill-hero__stat {
  border: 1px solid rgba(148, 163, 184, 0.22);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  padding: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.seckill-hero__stat-label {
  display: block;
  font-size: 12px;
  color: var(--text-tertiary);
}

.seckill-hero__stat-value {
  display: block;
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.seckill-hero__stat-value--text {
  font-size: 18px;
}

.section-shell {
  padding: 24px;
}

.section-shell--table {
  overflow: hidden;
  padding: 0;
}

.section-shell__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.section-shell__head--table {
  padding: 22px 24px 0;
  margin-bottom: 12px;
}

.section-shell__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}

.section-shell__desc {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-secondary);
}

.section-shell__meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.08);
  color: #047857;
  font-size: 12px;
  font-weight: 600;
}

.seckill-diagnostics {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.diagnostic-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.diagnostic-metric {
  min-width: 0;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.04);
  padding: 14px;
}

.diagnostic-metric__label {
  display: block;
  font-size: 12px;
  color: var(--text-tertiary);
}

.diagnostic-metric__value {
  display: block;
  margin-top: 8px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
}

.stress-panel {
  display: grid;
  grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr);
  gap: 16px;
  align-items: center;
  border-radius: 18px;
  border: 1px solid rgba(16, 185, 129, 0.16);
  background: rgba(16, 185, 129, 0.06);
  padding: 16px;
}

.stress-panel__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.stress-panel__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.stress-panel__command {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.stress-panel__command code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.86);
  color: #e2e8f0;
  padding: 10px 12px;
  font-size: 12px;
}

.stress-panel__actions {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
}

.stress-result {
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.72);
  padding: 16px;
}

.stress-result__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.stress-result__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.stress-result__desc {
  margin-top: 6px;
  word-break: break-all;
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-secondary);
}

.stress-result__grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.stress-result__metric {
  min-width: 0;
  border-radius: 12px;
  background: rgba(15, 23, 42, 0.04);
  padding: 12px;
}

.stress-result__metric span {
  display: block;
  color: var(--text-secondary);
  font-size: 12px;
}

.stress-result__metric strong {
  display: block;
  margin-top: 6px;
  color: var(--text-primary);
  font-size: 17px;
}

.entity-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.entity-block__cover {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.entity-block__content {
  min-width: 0;
}

.entity-block__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.entity-block__desc {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-tertiary);
}

.time-block,
.quota-block,
.price-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.time-block__item {
  display: flex;
  gap: 8px;
  font-size: 12px;
}

.time-block__label {
  color: var(--text-tertiary);
}

.time-block__value {
  color: var(--text-secondary);
}

.price-block__origin {
  font-size: 12px;
  color: var(--text-tertiary);
}

.price-block__current {
  font-size: 13px;
  font-weight: 700;
  color: #047857;
}

.quota-block__item {
  font-size: 12px;
  color: var(--text-secondary);
}

.overview-block {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.04);
}

.overview-block__item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 38px;
}

.overview-block__label {
  font-size: 11px;
  color: var(--text-tertiary);
}

.overview-block__value {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.overview-block__item--success .overview-block__value {
  color: #047857;
}

.overview-block__divider {
  width: 1px;
  height: 24px;
  background: rgba(148, 163, 184, 0.3);
}

.dialog-tip {
  margin-bottom: 20px;
  border: 1px solid rgba(16, 185, 129, 0.16);
  border-radius: 18px;
  background: rgba(16, 185, 129, 0.08);
  padding: 14px 16px;
}

.dialog-tip--subtle {
  background: rgba(15, 23, 42, 0.04);
  border-color: rgba(148, 163, 184, 0.18);
}

.dialog-tip__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.dialog-tip__desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}

.dialog-info-card {
  width: 100%;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(248, 250, 252, 0.92);
  padding: 12px 14px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-secondary);
}

:deep(.el-tabs__item) {
  height: 38px;
  padding: 0 16px;
  border-radius: 999px;
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

:deep(.el-tabs__item.is-active) {
  color: #0f172a;
  font-weight: 700;
}

:deep(.el-tabs__active-bar) {
  height: 3px;
  background: linear-gradient(90deg, #111827, #10b981);
  border-radius: 999px;
}

:deep(.el-table .el-table__cell) {
  border-bottom-color: rgba(148, 163, 184, 0.16);
}

:deep(.el-dialog__body) {
  padding-top: 20px;
}

@media (max-width: 900px) {
  .seckill-hero__stats {
    grid-template-columns: 1fr;
  }

  .diagnostic-grid,
  .stress-panel {
    grid-template-columns: 1fr;
  }

  .stress-panel__command {
    align-items: stretch;
    flex-direction: column;
  }

  .stress-panel__actions,
  .stress-result__head {
    flex-direction: column;
    align-items: stretch;
  }

  .stress-result__grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
