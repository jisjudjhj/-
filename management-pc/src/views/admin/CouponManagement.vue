<template>
  <div class="space-y-6">
    <div class="panel-card flex flex-wrap items-center justify-between gap-4 p-5">
      <div class="flex flex-wrap items-center gap-3">
        <el-select
          v-model="statusFilter"
          placeholder="按状态筛选"
          class="w-full sm:w-40"
          clearable
          @change="handleFilterChange"
        >
          <el-option
            v-for="item in couponStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <el-button type="primary" class="!rounded-xl" @click="openCreateDialog">
        <el-icon class="mr-2"><Plus /></el-icon>
        创建优惠券
      </el-button>
    </div>

    <div v-loading="loading" class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="coupon in filteredCoupons"
        :key="coupon.id"
        class="panel-card overflow-hidden p-0"
      >
        <div class="border-b border-slate-200/70 p-5 dark:border-slate-700/60">
          <div class="flex items-start justify-between gap-3">
            <div>
              <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">
                {{ coupon.name }}
              </h3>
              <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">
                {{ getCouponDescription(coupon) }}
              </p>
            </div>
            <div class="flex flex-col items-end gap-2">
              <el-tag :type="getStatusTagType(coupon.status)" effect="light" round>
                {{ getStatusLabel(coupon.status) }}
              </el-tag>
              <el-tag :type="getAudienceTagType(coupon.audienceType)" effect="plain" round>
                {{ getAudienceLabel(coupon.audienceType) }}
              </el-tag>
            </div>
          </div>

          <div class="mt-4 flex items-end gap-2 text-slate-900 dark:text-slate-100">
            <span class="text-3xl font-semibold">{{ getCouponValueText(coupon) }}</span>
            <span class="pb-1 text-sm text-slate-500 dark:text-slate-400">
              {{ getTypeLabel(coupon.type) }}
            </span>
          </div>

          <div class="mt-4 space-y-2 text-sm text-slate-600 dark:text-slate-300">
            <div class="flex items-center justify-between gap-3">
              <span>发行数量</span>
              <span class="font-medium text-slate-900 dark:text-slate-100">{{ coupon.totalCount }}</span>
            </div>
            <div class="flex items-center justify-between gap-3">
              <span>已领取</span>
              <span class="font-medium text-slate-900 dark:text-slate-100">{{ coupon.usedCount || 0 }}</span>
            </div>
            <div class="flex items-center justify-between gap-3">
              <span>使用门槛</span>
              <span class="font-medium text-slate-900 dark:text-slate-100">￥{{ formatMoney(coupon.minAmount) }}</span>
            </div>
            <div class="space-y-1 rounded-2xl bg-slate-50/90 px-4 py-3 dark:bg-slate-800/80">
              <div class="text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400">领取范围</div>
              <div class="text-sm font-medium text-slate-900 dark:text-slate-100">
                {{ getAudienceDescription(coupon) }}
              </div>
              <div v-if="coupon.audienceNote" class="text-xs text-slate-500 dark:text-slate-400">
                说明：{{ coupon.audienceNote }}
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between gap-4 bg-slate-50/80 px-5 py-4 text-sm dark:bg-slate-900/60">
          <div class="text-slate-500 dark:text-slate-400">
            <el-icon class="mr-1 align-middle"><Timer /></el-icon>
            {{ coupon.startTime }} 至 {{ coupon.endTime }}
          </div>
          <div class="flex items-center gap-3">
            <el-button link type="primary" size="small" @click="openEditDialog(coupon)">编辑</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="flex justify-end">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        background
        layout="total, prev, pager, next"
        :total="pagination.total"
        @current-change="fetchList"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建优惠券' : '编辑优惠券'"
      width="min(92vw, 760px)"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="优惠券名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：春季满减券" />
        </el-form-item>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="优惠券类型" prop="type">
            <el-select v-model="form.type" class="w-full">
              <el-option
                v-for="item in couponTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" class="w-full">
              <el-option
                v-for="item in couponStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="券值" prop="value">
            <el-input-number v-model="form.value" :min="0" :precision="2" class="!w-full" />
          </el-form-item>

          <el-form-item label="使用门槛" prop="minAmount">
            <el-input-number v-model="form.minAmount" :min="0" :precision="2" class="!w-full" />
          </el-form-item>

          <el-form-item v-if="form.type === 2" label="最高优惠">
            <el-input-number v-model="form.maxDiscount" :min="0" :precision="2" class="!w-full" />
          </el-form-item>

          <el-form-item label="发行数量" prop="totalCount">
            <el-input-number v-model="form.totalCount" :min="1" class="!w-full" />
          </el-form-item>
        </div>

        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-full"
            />
          </el-form-item>

          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="!w-full"
            />
          </el-form-item>
        </div>

        <div class="rounded-3xl border border-slate-200/80 p-4 dark:border-slate-700/70">
          <div class="mb-4 flex items-center justify-between gap-3">
            <div>
              <div class="text-sm font-semibold text-slate-900 dark:text-slate-100">发放范围</div>
              <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                用来控制“不是所有用户都能领到所有券”的定向发券规则。
              </p>
            </div>
            <el-tag type="info" effect="plain" round>{{ getAudienceLabel(form.audienceType) }}</el-tag>
          </div>

          <el-form-item label="领取范围" prop="audienceType" class="mb-4">
            <el-select v-model="form.audienceType" class="w-full" @change="handleAudienceTypeChange">
              <el-option
                v-for="item in audienceTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.audienceType === 1" label="目标分群" prop="targetSegmentCodeList">
            <el-select
              v-model="form.targetSegmentCodeList"
              class="w-full"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="选择已有分群，或手动输入分群编码"
            >
              <el-option
                v-for="item in segmentOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.audienceType === 1" label="分群说明">
            <div class="w-full rounded-2xl bg-slate-50/90 px-4 py-3 text-xs text-slate-500 dark:bg-slate-800/80 dark:text-slate-400">
              当前读取的是最新一次 KMeans 分群结果。没在列表里的编码，也可以手动输入。
            </div>
          </el-form-item>

          <el-form-item v-if="form.audienceType === 2" label="指定用户" prop="targetUserIdsText">
            <el-input
              v-model="form.targetUserIdsText"
              type="textarea"
              :rows="3"
              placeholder="请输入用户 ID，使用英文逗号分隔，例如：1001,1002,1003"
            />
          </el-form-item>

          <el-form-item label="补充说明">
            <el-input
              v-model="form.audienceNote"
              placeholder="例如：低活跃用户专属、首单激活券"
              maxlength="255"
              show-word-limit
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Timer } from '@element-plus/icons-vue'
import {
  createAdminCoupon,
  getAdminCoupons,
  getAdminKmeansSegments,
  updateAdminCoupon,
} from '../../api/admin'

const couponStatusOptions = [
  { label: '未开始', value: 0 },
  { label: '进行中', value: 1 },
  { label: '已结束', value: 2 },
]

const couponTypeOptions = [
  { label: '满减券', value: 1 },
  { label: '折扣券', value: 2 },
  { label: '无门槛券', value: 3 },
]

const audienceTypeOptions = [
  { label: '公开券', value: 0 },
  { label: '分群定向券', value: 1 },
  { label: '指定用户券', value: 2 },
]

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const statusFilter = ref(null)
const coupons = ref([])
const segmentOptions = ref([])

const pagination = reactive({
  page: 1,
  size: 9,
  total: 0,
})

const form = reactive({
  id: null,
  name: '',
  type: 1,
  value: 0,
  minAmount: 0,
  maxDiscount: null,
  totalCount: 1,
  usedCount: 0,
  startTime: '',
  endTime: '',
  status: 1,
  audienceType: 0,
  targetSegmentCodeList: [],
  targetUserIdsText: '',
  audienceNote: '',
})

const validateSegmentCodes = (_rule, value, callback) => {
  if (form.audienceType !== 1) {
    callback()
    return
  }
  if (!Array.isArray(value) || value.length === 0) {
    callback(new Error('请至少选择一个分群编码'))
    return
  }
  callback()
}

const validateTargetUserIds = (_rule, value, callback) => {
  if (form.audienceType !== 2) {
    callback()
    return
  }
  if (!parseUserIdText(value).length) {
    callback(new Error('请至少填写一个有效的用户 ID'))
    return
  }
  callback()
}

const rules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择优惠券类型', trigger: 'change' }],
  value: [{ required: true, message: '请输入券值', trigger: 'change' }],
  totalCount: [{ required: true, message: '请输入发行数量', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  audienceType: [{ required: true, message: '请选择领取范围', trigger: 'change' }],
  targetSegmentCodeList: [{ validator: validateSegmentCodes, trigger: 'change' }],
  targetUserIdsText: [{ validator: validateTargetUserIds, trigger: 'blur' }],
}

const filteredCoupons = computed(() => coupons.value)

const resetForm = () => {
  form.id = null
  form.name = ''
  form.type = 1
  form.value = 0
  form.minAmount = 0
  form.maxDiscount = null
  form.totalCount = 1
  form.usedCount = 0
  form.startTime = ''
  form.endTime = ''
  form.status = 1
  form.audienceType = 0
  form.targetSegmentCodeList = []
  form.targetUserIdsText = ''
  form.audienceNote = ''
}

const formatMoney = value => Number(value || 0).toFixed(2)

const getStatusLabel = status =>
  couponStatusOptions.find(item => item.value === Number(status))?.label || '未知状态'

const getTypeLabel = type =>
  couponTypeOptions.find(item => item.value === Number(type))?.label || '未知类型'

const getAudienceLabel = audienceType =>
  audienceTypeOptions.find(item => item.value === Number(audienceType || 0))?.label || '公开券'

const getStatusTagType = status => {
  if (Number(status) === 1) return 'success'
  if (Number(status) === 2) return 'info'
  return 'warning'
}

const getAudienceTagType = audienceType => {
  if (Number(audienceType) === 1) return 'warning'
  if (Number(audienceType) === 2) return 'danger'
  return 'success'
}

const splitCsv = rawValue =>
  String(rawValue || '')
    .split(/[,\uFF0C;\uFF1B\s]+/)
    .map(item => item.trim())
    .filter(Boolean)

const parseUserIdText = rawValue =>
  splitCsv(rawValue)
    .map(item => Number(item))
    .filter(item => Number.isInteger(item) && item > 0)

const getCouponDescription = coupon => {
  if (Number(coupon.type) === 1) {
    return `满 ￥${formatMoney(coupon.minAmount)} 减 ￥${formatMoney(coupon.value)}`
  }
  if (Number(coupon.type) === 2) {
    return `满 ￥${formatMoney(coupon.minAmount)} 可享 ${coupon.value} 折`
  }
  return '无门槛立减优惠券'
}

const getCouponValueText = coupon => {
  if (Number(coupon.type) === 2) {
    return `${coupon.value} 折`
  }
  return `￥${formatMoney(coupon.value)}`
}

const getAudienceDescription = coupon => {
  const audienceType = Number(coupon.audienceType || 0)
  if (audienceType === 1) {
    const segmentCodes = splitCsv(coupon.targetSegmentCodes)
    return segmentCodes.length ? `仅限分群 ${segmentCodes.join('、')} 领取` : '仅限指定分群领取'
  }
  if (audienceType === 2) {
    const userIds = splitCsv(coupon.targetUserIds)
    return userIds.length ? `仅限指定用户领取，共 ${userIds.length} 人` : '仅限指定用户领取'
  }
  return '所有满足基础条件的用户都可领取'
}

const handleAudienceTypeChange = async () => {
  if (form.audienceType !== 1) {
    form.targetSegmentCodeList = []
  }
  if (form.audienceType !== 2) {
    form.targetUserIdsText = ''
  }
  await nextTick()
  formRef.value?.clearValidate(['targetSegmentCodeList', 'targetUserIdsText'])

  if (form.audienceType === 1) {
    await formRef.value?.validateField('targetSegmentCodeList').catch(() => {})
  } else if (form.audienceType === 2) {
    await formRef.value?.validateField('targetUserIdsText').catch(() => {})
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchList()
}

const fetchSegments = async () => {
  try {
    const response = await getAdminKmeansSegments()
    const records = Array.isArray(response?.records) ? response.records : []
    segmentOptions.value = records.map(item => ({
      value: item.segmentCode,
      label: item.segmentName ? `${item.segmentName} (${item.segmentCode})` : item.segmentCode,
    }))
  } catch (error) {
    segmentOptions.value = []
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const response = await getAdminCoupons({
      page: pagination.page,
      size: pagination.size,
      status: statusFilter.value ?? undefined,
    })
    coupons.value = response?.records || []
    pagination.total = response?.total || 0
  } finally {
    loading.value = false
  }
}

const openCreateDialog = () => {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = row => {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name || ''
  form.type = row.type ?? 1
  form.value = Number(row.value || 0)
  form.minAmount = Number(row.minAmount || 0)
  form.maxDiscount = row.maxDiscount == null ? null : Number(row.maxDiscount)
  form.totalCount = row.totalCount || 1
  form.usedCount = row.usedCount || 0
  form.startTime = row.startTime || ''
  form.endTime = row.endTime || ''
  form.status = row.status ?? 1
  form.audienceType = Number(row.audienceType || 0)
  form.targetSegmentCodeList = splitCsv(row.targetSegmentCodes)
  form.targetUserIdsText = splitCsv(row.targetUserIds).join(',')
  form.audienceNote = row.audienceNote || ''
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value || submitting.value) return

  await formRef.value.validate()
  submitting.value = true

  try {
    const payload = {
      name: form.name,
      type: form.type,
      value: form.value,
      minAmount: form.minAmount,
      maxDiscount: form.type === 2 ? form.maxDiscount : null,
      totalCount: form.totalCount,
      startTime: form.startTime,
      endTime: form.endTime,
      status: form.status,
      audienceType: form.audienceType,
      targetSegmentCodes: form.audienceType === 1 ? splitCsv(form.targetSegmentCodeList.join(',')).join(',') : '',
      targetUserIds: form.audienceType === 2 ? parseUserIdText(form.targetUserIdsText).join(',') : '',
      audienceNote: form.audienceNote?.trim() || '',
    }

    if (dialogMode.value === 'create') {
      await createAdminCoupon(payload)
      ElMessage.success('优惠券创建成功')
    } else {
      await updateAdminCoupon(form.id, payload)
      ElMessage.success('优惠券更新成功')
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchList(), fetchSegments()])
})
</script>
