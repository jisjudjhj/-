<template>
  <div class="seckill-page">
    <div class="panel-card seckill-hero">
      <div class="seckill-hero__content">
        <div>
          <p class="seckill-hero__eyebrow">商家营销</p>
          <h2 class="seckill-hero__title">秒杀报名中心</h2>
        </div>
        <div class="seckill-hero__stats">
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">{{ activeTab === 'available' ? '可报名活动' : '报名总数' }}</span>
            <strong class="seckill-hero__stat-value">{{ activeTab === 'available' ? availablePagination.total : applicationPagination.total }}</strong>
          </article>
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">当前页记录</span>
            <strong class="seckill-hero__stat-value">{{ activeTab === 'available' ? availableActivities.length : myApplications.length }}</strong>
          </article>
          <article class="seckill-hero__stat">
            <span class="seckill-hero__stat-label">当前视图</span>
            <strong class="seckill-hero__stat-value seckill-hero__stat-value--text">{{ activeTab === 'available' ? '可报名活动' : '我的报名' }}</strong>
          </article>
        </div>
      </div>
    </div>

    <div class="panel-card section-shell">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="可报名活动" name="available" />
        <el-tab-pane label="我的报名" name="applications" />
      </el-tabs>
    </div>

    <template v-if="activeTab === 'available'">
      <div class="panel-card section-shell">
        <div class="section-shell__head">
          <div>
            <h3 class="section-shell__title">活动筛选</h3>
          </div>
          <div class="section-shell__meta">共 {{ availablePagination.total }} 条活动</div>
        </div>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
          <el-input
            v-model="availableFilters.keyword"
            clearable
            placeholder="搜索活动名称"
            @keyup.enter="handleAvailableFilterChange"
            @clear="handleAvailableFilterChange"
          />
          <el-select
            v-model="availableFilters.status"
            clearable
            placeholder="活动状态"
            @change="handleAvailableFilterChange"
          >
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
          <div class="md:col-span-2 flex justify-start md:justify-end">
            <el-button @click="handleAvailableFilterChange">查询</el-button>
          </div>
        </div>
      </div>

      <div class="panel-card section-shell section-shell--table">
        <div class="section-shell__head section-shell__head--table">
          <div>
            <h3 class="section-shell__title">可报名活动列表</h3>
          </div>
          <div class="section-shell__meta">当前页 {{ availableActivities.length }} 条</div>
        </div>
        <el-table
          v-loading="availableLoading"
          :data="availableActivities"
          class="!bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
          <el-table-column prop="id" label="活动ID" width="96" />
          <el-table-column label="活动信息" min-width="250">
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
                    {{ row.description || '无说明' }}
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
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="runtimeStatusMap[resolveRuntimeStatus(row)]?.type || 'info'" effect="light" round>
                {{ runtimeStatusMap[resolveRuntimeStatus(row)]?.label || '未知' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                :disabled="!canApply(row)"
                @click="openApplyDialog(row)"
              >
                立即报名
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="availablePagination.page"
            v-model:page-size="availablePagination.size"
            background
            layout="total, prev, pager, next"
            :total="availablePagination.total"
            @current-change="fetchAvailableActivities"
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
          <div class="section-shell__meta">共 {{ applicationPagination.total }} 条报名</div>
        </div>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
          <el-select
            v-model="applicationFilters.status"
            clearable
            placeholder="审核状态"
            @change="handleApplicationFilterChange"
          >
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已驳回" :value="2" />
            <el-option label="已撤回" :value="3" />
          </el-select>
          <div class="md:col-span-3 flex justify-start md:justify-end">
            <el-button @click="handleApplicationFilterChange">查询</el-button>
          </div>
        </div>
      </div>

      <div class="panel-card section-shell section-shell--table">
        <div class="section-shell__head section-shell__head--table">
          <div>
            <h3 class="section-shell__title">我的报名列表</h3>
          </div>
          <div class="section-shell__meta">当前页 {{ myApplications.length }} 条</div>
        </div>
        <div
          v-if="highlightedApplicationId"
          class="locate-banner"
        >
          已定位到报名记录：#{{ highlightedApplicationId }}
        </div>
        <el-table
          ref="applicationsTableRef"
          v-loading="applicationsLoading"
          :data="myApplications"
          :row-class-name="getApplicationRowClassName"
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
                  <span class="time-block__value">{{ resolveActivityStartTime(row) }}</span>
                </div>
                <div class="time-block__item">
                  <span class="time-block__label">结束</span>
                  <span class="time-block__value">{{ resolveActivityEndTime(row) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="报名商品" min-width="170" show-overflow-tooltip />
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
            <template #default="{ row }">{{ row.rejectReason || '-' }}</template>
          </el-table-column>
          <el-table-column label="报名时间" width="168">
            <template #default="{ row }">{{ row.createTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="审核时间" width="168">
            <template #default="{ row }">{{ row.auditTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="right" fixed="right">
            <template #default="{ row }">
              <div class="flex justify-end gap-2">
                <template v-if="canEditApplication(row)">
                  <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click="revokeApplication(row)">撤回</el-button>
                </template>
                <span v-else class="text-xs text-slate-400">不可编辑</span>
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
      v-model="applicationDialog.visible"
      :title="applicationDialog.mode === 'create' ? '提交秒杀报名' : '编辑秒杀报名'"
      width="min(92vw, 760px)"
      destroy-on-close
    >
      <el-form ref="applicationFormRef" :model="applicationForm" :rules="applicationRules" label-width="120px">
        <div class="dialog-tip">
          <div class="dialog-tip__title">报名填写建议</div>
          <div class="dialog-tip__desc">请优先选择未开始场次，秒杀价需低于现价，库存建议预留给本场秒杀单独使用。</div>
        </div>
        <el-form-item label="活动" prop="activityId">
          <el-select
            v-model="applicationForm.activityId"
            class="w-full"
            filterable
            :loading="activityOptionsLoading"
            no-data-text="暂无可选活动"
            placeholder="请选择活动（含时间）"
          >
            <el-option
              v-for="item in activityOptions"
              :key="item.id"
              :label="formatActivityOptionLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="商品" prop="productId">
          <el-select
            v-model="applicationForm.productId"
            class="w-full"
            filterable
            placeholder="请选择你的在售商品"
          >
            <el-option
              v-for="item in merchantProductOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="秒杀价" prop="seckillPrice">
            <el-input-number v-model="applicationForm.seckillPrice" :min="0.01" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item label="秒杀库存" prop="seckillStock">
            <el-input-number v-model="applicationForm.seckillStock" :min="1" class="!w-full" />
          </el-form-item>
        </div>

        <el-form-item label="每人限购" prop="limitPerUser">
          <el-input-number v-model="applicationForm.limitPerUser" :min="1" class="!w-full" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="applicationDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="applicationDialog.submitting" @click="submitApplication">
          {{ applicationDialog.mode === 'create' ? '提交报名' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMerchantSeckillApplication,
  getMerchantProducts,
  getMerchantSeckillActivityOptions,
  getMerchantSeckillApplications,
  getMerchantSeckillAvailableActivities,
  revokeMerchantSeckillApplication,
  updateMerchantSeckillApplication,
} from '../../api/merchant'

const route = useRoute()
const activeTab = ref('available')
const applicationsTableRef = ref(null)
const highlightedApplicationId = ref(null)

const runtimeStatusMap = {
  0: { label: '未开始', type: 'warning' },
  1: { label: '进行中', type: 'danger' },
  2: { label: '已结束', type: 'info' },
}

const applicationStatusMap = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
  3: { label: '已撤回', type: 'info' },
}

const availableLoading = ref(false)
const availableActivities = ref([])
const availableFilters = reactive({
  keyword: '',
  status: undefined,
})
const availablePagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const applicationsLoading = ref(false)
const myApplications = ref([])
const applicationFilters = reactive({
  status: undefined,
})
const applicationPagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const merchantProductOptions = ref([])
const activityOptionsLoading = ref(false)
const activityOptions = ref([])

const applicationDialog = reactive({
  visible: false,
  mode: 'create',
  submitting: false,
})
const applicationFormRef = ref(null)
const applicationForm = reactive({
  id: null,
  activityId: undefined,
  productId: undefined,
  seckillPrice: 0,
  seckillStock: 1,
  limitPerUser: 1,
})

const applicationRules = {
  activityId: [{ required: true, message: '请选择活动', trigger: 'change' }],
  productId: [{ required: true, message: '请选择商品', trigger: 'change' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价', trigger: 'change' }],
  seckillStock: [{ required: true, message: '请输入秒杀库存', trigger: 'change' }],
  limitPerUser: [{ required: true, message: '请输入限购数量', trigger: 'change' }],
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

const formatMoney = value => Number(value || 0).toFixed(2)

const resolveActivityStartTime = row =>
  row?.activityStartTime || row?.activity?.startTime || row?.seckillStartTime || row?.startTime || '-'

const resolveActivityEndTime = row =>
  row?.activityEndTime || row?.activity?.endTime || row?.seckillEndTime || row?.endTime || '-'

const parseTime = value => {
  const timestamp = value ? new Date(value).getTime() : 0
  return Number.isFinite(timestamp) ? timestamp : 0
}

const formatActivityOptionLabel = row => {
  const name = row?.name || row?.activityName || `活动#${row?.id || '-'}`
  const startTime = resolveActivityStartTime(row)
  const endTime = resolveActivityEndTime(row)
  return `${name}（${startTime} 至 ${endTime}）`
}

const canSelectActivityOption = row => resolveRuntimeStatus(row) === 0

const resolveRuntimeStatus = row => {
  if (row?.runtimeStatus != null) return Number(row.runtimeStatus)
  const start = row?.startTime ? new Date(row.startTime).getTime() : null
  const end = row?.endTime ? new Date(row.endTime).getTime() : null
  const now = Date.now()
  if (start && now < start) return 0
  if (end && now > end) return 2
  return 1
}

const resolveApplicationStatus = row => Number(row?.status ?? row?.auditStatus ?? 0)

const canApply = row => {
  if (row?.canApply != null) return !!row.canApply
  return resolveRuntimeStatus(row) === 0
}

const canEditApplication = row => {
  if (row?.editable != null) return !!row.editable
  return resolveApplicationStatus(row) === 0
}

const fetchAvailableActivities = async () => {
  availableLoading.value = true
  try {
    const res = await getMerchantSeckillAvailableActivities({
      page: availablePagination.page,
      size: availablePagination.size,
      keyword: availableFilters.keyword || undefined,
      status: availableFilters.status,
    })
    const pageData = normalizePageData(res)
    availableActivities.value = pageData.records
    availablePagination.total = pageData.total
  } finally {
    availableLoading.value = false
  }
}

const fetchApplications = async () => {
  applicationsLoading.value = true
  try {
    const res = await getMerchantSeckillApplications({
      page: applicationPagination.page,
      size: applicationPagination.size,
      status: applicationFilters.status,
    })
    const pageData = normalizePageData(res)
    myApplications.value = pageData.records
    applicationPagination.total = pageData.total
  } finally {
    applicationsLoading.value = false
  }
}

const fetchMerchantProducts = async () => {
  try {
    const res = await getMerchantProducts({ page: 1, size: 300, status: 1 })
    const pageData = normalizePageData(res)
    merchantProductOptions.value = pageData.records.map(item => ({
      id: item.id,
      name: item.name || `商品#${item.id}`,
      price: Number(item.price || 0),
      stock: Number(item.stock || 0),
    }))
  } catch {
    merchantProductOptions.value = []
  }
}

const mergeActivityOptions = records => {
  const map = new Map()
  records.forEach(item => {
    const id = Number(item?.id)
    if (!Number.isFinite(id) || id <= 0) return
    if (map.has(id)) return
    map.set(id, item)
  })
  return Array.from(map.values()).sort((a, b) => parseTime(resolveActivityStartTime(a)) - parseTime(resolveActivityStartTime(b)))
}

const ensureActivityOptionFromApplication = row => {
  const activityId = Number(row?.activityId)
  if (!Number.isFinite(activityId) || activityId <= 0) return
  if (activityOptions.value.some(item => Number(item.id) === activityId)) return
  activityOptions.value = [
    {
      id: activityId,
      name: row?.activityName || `活动#${activityId}`,
      activityStartTime: row?.activityStartTime || row?.seckillStartTime || '',
      activityEndTime: row?.activityEndTime || row?.seckillEndTime || '',
    },
    ...activityOptions.value,
  ]
}

const fetchActivityOptions = async () => {
  activityOptionsLoading.value = true
  try {
    const statusList = [0, 1, 2]
    const responses = await Promise.allSettled(
      statusList.map(status =>
        getMerchantSeckillActivityOptions({
          page: 1,
          size: 200,
          status,
        }),
      ),
    )

    const records = []
    responses.forEach(result => {
      if (result.status === 'fulfilled') {
        records.push(...normalizePageData(result.value).records)
      }
    })

    if (!records.length) {
      const fallback = await getMerchantSeckillActivityOptions({ page: 1, size: 300 })
      records.push(...normalizePageData(fallback).records)
    }

    activityOptions.value = mergeActivityOptions(records).filter(item => canSelectActivityOption(item))
  } catch {
    activityOptions.value = []
  } finally {
    activityOptionsLoading.value = false
  }
}

const handleTabChange = async tab => {
  if (tab !== 'applications') {
    highlightedApplicationId.value = null
  }
  if (tab === 'available') {
    await fetchAvailableActivities()
    return
  }
  await fetchApplications()
}

const handleAvailableFilterChange = () => {
  availablePagination.page = 1
  fetchAvailableActivities()
}

const handleApplicationFilterChange = () => {
  highlightedApplicationId.value = null
  applicationPagination.page = 1
  fetchApplications()
}

const resetApplicationForm = () => {
  applicationForm.id = null
  applicationForm.activityId = undefined
  applicationForm.productId = undefined
  applicationForm.seckillPrice = 0
  applicationForm.seckillStock = 1
  applicationForm.limitPerUser = 1
}

const openApplyDialog = async activity => {
  applicationDialog.mode = 'create'
  resetApplicationForm()
  await Promise.all([fetchMerchantProducts(), fetchActivityOptions()])
  applicationForm.activityId = Number(activity?.id) || undefined
  applicationDialog.visible = true
}

const openEditDialog = async row => {
  applicationDialog.mode = 'edit'
  resetApplicationForm()
  applicationForm.id = row.id
  applicationForm.activityId = Number(row.activityId) || undefined
  applicationForm.productId = row.productId
  applicationForm.seckillPrice = Number(row.seckillPrice || 0)
  applicationForm.seckillStock = Number(row.seckillStock || 1)
  applicationForm.limitPerUser = Number(row.limitPerUser || 1)
  await Promise.all([fetchMerchantProducts(), fetchActivityOptions()])
  ensureActivityOptionFromApplication(row)
  applicationDialog.visible = true
}

const submitApplication = async () => {
  if (!applicationFormRef.value || applicationDialog.submitting) return

  await applicationFormRef.value.validate()
  const product = merchantProductOptions.value.find(item => item.id === applicationForm.productId)
  if (product) {
    if (applicationForm.seckillPrice >= product.price) {
      ElMessage.warning('秒杀价必须小于商品现价')
      return
    }
    if (applicationForm.seckillStock > product.stock) {
      ElMessage.warning('秒杀库存不能超过当前商品库存')
      return
    }
  }

  applicationDialog.submitting = true
  try {
    const payload = {
      activityId: applicationForm.activityId,
      productId: applicationForm.productId,
      seckillPrice: applicationForm.seckillPrice,
      seckillStock: applicationForm.seckillStock,
      limitPerUser: applicationForm.limitPerUser,
    }
    if (applicationDialog.mode === 'create') {
      await createMerchantSeckillApplication(payload)
      ElMessage.success('报名提交成功')
    } else {
      await updateMerchantSeckillApplication(applicationForm.id, payload)
      ElMessage.success('报名信息已更新')
    }
    applicationDialog.visible = false
    await Promise.all([fetchAvailableActivities(), fetchApplications()])
    window.dispatchEvent(new Event('merchant-workbench-refresh'))
  } finally {
    applicationDialog.submitting = false
  }
}

const revokeApplication = async row => {
  try {
    await ElMessageBox.confirm(`确定撤回报名「${row.productName || row.id}」吗？`, '撤回确认', { type: 'warning' })
    await revokeMerchantSeckillApplication(row.id)
    ElMessage.success('报名已撤回')
    await fetchApplications()
    window.dispatchEvent(new Event('merchant-workbench-refresh'))
  } catch {
    // ignore cancel
  }
}

const parseQueryApplyId = () => {
  const candidate = route.query.applyId || route.query.seckillApplyId || route.query.relatedId
  const targetId = Number(candidate)
  return Number.isFinite(targetId) && targetId > 0 ? targetId : null
}

const getApplicationRowClassName = ({ row }) =>
  Number(row?.id) === Number(highlightedApplicationId.value) ? 'seckill-apply-row-highlight' : ''

const scrollToHighlightedApplication = async () => {
  await nextTick()
  const tableRoot = applicationsTableRef.value?.$el
  if (!tableRoot) return
  const rowEl = tableRoot.querySelector('.seckill-apply-row-highlight')
  if (rowEl && typeof rowEl.scrollIntoView === 'function') {
    rowEl.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

const locateApplicationById = async targetId => {
  if (!targetId) return false
  applicationFilters.status = undefined

  const pageSize = applicationPagination.size || 10
  let currentPage = 1
  let total = 0
  let foundRow = null
  let foundPageRecords = []

  do {
    const res = await getMerchantSeckillApplications({
      page: currentPage,
      size: pageSize,
      status: undefined,
    })
    const pageData = normalizePageData(res)
    const records = pageData.records || []
    total = Number(pageData.total || 0)
    const match = records.find(item => Number(item.id) === Number(targetId))
    if (match) {
      foundRow = match
      foundPageRecords = records
      break
    }
    if (!records.length) break
    currentPage += 1
  } while ((currentPage - 1) * pageSize < total && currentPage <= 200)

  if (!foundRow) {
    highlightedApplicationId.value = null
    applicationPagination.page = 1
    await fetchApplications()
    ElMessage.warning(`未找到报名记录 #${targetId}，已展示“我的报名”列表`)
    return false
  }

  myApplications.value = foundPageRecords
  applicationPagination.page = currentPage
  applicationPagination.total = total
  highlightedApplicationId.value = targetId
  await scrollToHighlightedApplication()
  ElMessage.success(`已定位到报名记录 #${targetId}`)
  return true
}

const applyRouteQuery = async () => {
  const targetApplyId = parseQueryApplyId()
  const targetTab = route.query.tab
  if (targetTab === 'applications' || targetApplyId) {
    activeTab.value = 'applications'
    if (targetApplyId) {
      await locateApplicationById(targetApplyId)
      return
    }
    applicationPagination.page = 1
    await fetchApplications()
    return
  }
  highlightedApplicationId.value = null
}

onMounted(async () => {
  await Promise.all([fetchAvailableActivities(), fetchActivityOptions()])
  if (route.query.tab === 'applications' || parseQueryApplyId()) {
    await applyRouteQuery()
    return
  }
  await fetchApplications()
})

watch(
  () => [route.query.tab, route.query.applyId, route.query.seckillApplyId, route.query.relatedId],
  () => {
    applyRouteQuery()
  }
)
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

.time-block__value,
.quota-block__item {
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
}

.locate-banner {
  margin: 0 24px 12px;
  border-radius: 16px;
  border: 1px solid rgba(16, 185, 129, 0.16);
  background: rgba(16, 185, 129, 0.08);
  padding: 12px 14px;
  font-size: 12px;
  font-weight: 600;
  color: #047857;
}

.dialog-tip {
  margin-bottom: 20px;
  border: 1px solid rgba(16, 185, 129, 0.16);
  border-radius: 18px;
  background: rgba(16, 185, 129, 0.08);
  padding: 14px 16px;
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

:deep(.seckill-apply-row-highlight > td) {
  background: rgba(16, 185, 129, 0.12) !important;
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
}
</style>
