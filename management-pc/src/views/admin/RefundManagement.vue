<template>
  <div class="space-y-6">
    <div class="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
      <div
        v-for="card in statCards"
        :key="card.label"
        class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-5 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg"
      >
        <div class="text-sm text-gray-500">{{ card.label }}</div>
        <div class="mt-2 text-2xl font-bold text-gray-800 dark:text-gray-100">{{ card.value }}</div>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <el-tabs v-model="activeStatus" class="custom-tabs" @tab-change="handleStatusChange">
        <el-tab-pane label="全部" name="-1" />
        <el-tab-pane label="待审核" name="0" />
        <el-tab-pane label="已同意" name="1" />
        <el-tab-pane label="已拒绝" name="2" />
        <el-tab-pane label="已退款" name="3" />
      </el-tabs>

      <div v-loading="loading" class="mt-6 space-y-4">
        <div
          v-for="refund in refunds"
          :key="refund.id"
          class="bg-white/40 dark:bg-gray-800/40 rounded-xl p-6 border border-gray-200/50 dark:border-gray-700/50 hover:shadow-md transition-shadow"
        >
          <div class="flex justify-between items-start gap-4 mb-4">
            <div>
              <div class="flex items-center gap-3 mb-1">
                <span class="font-semibold text-gray-800 dark:text-gray-200">退款单 #{{ refund.id }}</span>
                <el-tag :type="getRefundStatusType(refund.status)" class="!rounded-full">
                  {{ getLabel(REFUND_STATUS_MAP, refund.status) }}
                </el-tag>
                <el-tag
                  v-if="refund.interventionStatus"
                  :type="getInterventionTagType(refund.interventionStatus)"
                  class="!rounded-full"
                >
                  {{ getInterventionLabel(refund.interventionStatus) }}
                </el-tag>
              </div>
              <div class="text-sm text-gray-500">
                订单号：{{ refund.orderNo || refund.orderId }} · 申请用户：{{ refund.username || '-' }}
              </div>
            </div>
            <div class="text-right">
              <div class="text-sm text-gray-500 mb-1">退款金额</div>
              <div class="text-lg font-bold text-rose-500">￥{{ Number(refund.amount || 0).toFixed(2) }}</div>
            </div>
          </div>

          <div class="bg-gray-50/50 dark:bg-gray-900/50 rounded-lg p-4 mb-4">
            <div class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">退款原因：{{ refund.reason }}</div>
            <p class="text-sm text-gray-500 leading-6">{{ refund.description || '无补充说明' }}</p>
            <div v-if="refund.interventionReason" class="mt-3 text-sm text-amber-600 dark:text-amber-400">
              平台介入说明：{{ refund.interventionReason }}
              <span v-if="refund.interventionTime" class="text-gray-400">（{{ refund.interventionTime }}）</span>
            </div>
          </div>

          <div class="flex justify-end gap-3">
            <el-button plain @click="showDetail(refund)">查看详情</el-button>
            <template v-if="refund.status === 0">
              <el-button type="danger" plain @click="handleReject(refund)">拒绝</el-button>
              <el-button type="primary" class="shadow-lg shadow-blue-500/20" @click="handleApprove(refund)">同意并退款</el-button>
            </template>
            <el-button
              v-else-if="refund.status === 2 && refund.interventionStatus === 'pending'"
              type="warning"
              class="shadow-lg shadow-amber-500/20"
              @click="handleResolveIntervention(refund)"
            >
              平台裁定
            </el-button>
          </div>
        </div>
      </div>

      <div class="mt-6 flex justify-end">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="fetchList"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="退款详情" size="min(92vw, 520px)">
      <div v-if="detail" class="space-y-4 text-sm">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <div class="text-gray-500">退款单号</div>
            <div class="font-medium">{{ detail.id }}</div>
          </div>
          <div>
            <div class="text-gray-500">订单号</div>
            <div class="font-medium">{{ detail.orderNo || detail.orderId }}</div>
          </div>
          <div>
            <div class="text-gray-500">申请用户</div>
            <div class="font-medium">{{ detail.username || '-' }}</div>
          </div>
          <div>
            <div class="text-gray-500">退款状态</div>
            <div class="font-medium">{{ getLabel(REFUND_STATUS_MAP, detail.status) }}</div>
          </div>
        </div>
        <div>
          <div class="text-gray-500">退款金额</div>
          <div class="font-semibold text-lg">￥{{ Number(detail.amount || 0).toFixed(2) }}</div>
        </div>
        <div>
          <div class="text-gray-500">退款原因</div>
          <div class="font-medium">{{ detail.reason }}</div>
        </div>
        <div>
          <div class="text-gray-500">补充说明</div>
          <div class="leading-6">{{ detail.description || '无' }}</div>
        </div>
        <div v-if="detail.rejectReason">
          <div class="text-gray-500">拒绝原因</div>
          <div class="leading-6 text-red-500">{{ detail.rejectReason }}</div>
        </div>
        <div v-if="detail.interventionStatus">
          <div class="text-gray-500">平台介入状态</div>
          <div class="leading-6">{{ getInterventionLabel(detail.interventionStatus) }}</div>
        </div>
        <div v-if="detail.interventionReason">
          <div class="text-gray-500">平台介入说明</div>
          <div class="leading-6 text-amber-600">{{ detail.interventionReason }}</div>
        </div>
        <div v-if="detail.interventionTime">
          <div class="text-gray-500">平台介入时间</div>
          <div class="leading-6">{{ detail.interventionTime }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminRefunds } from '../../api/admin'
import { approveRefund, getRefundCompetitionStats, getRefundDetail, rejectRefund, resolveRefundIntervention } from '../../api/refunds'
import { getLabel, REFUND_STATUS_MAP } from '../../utils/status'

const loading = ref(false)
const activeStatus = ref('-1')
const refunds = ref([])
const detailVisible = ref(false)
const detail = ref(null)
const stats = ref({
  total: 0,
  pending: 0,
  rejected: 0,
  refunded: 0,
  interventionPending: 0,
  interventionResolved: 0,
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const statCards = computed(() => ([
  { label: '退款总数', value: stats.value.total || 0 },
  { label: '待审核', value: stats.value.pending || 0 },
  { label: '已拒绝', value: stats.value.rejected || 0 },
  { label: '已退款', value: stats.value.refunded || 0 },
  { label: '介入处理中', value: stats.value.interventionPending || 0 },
  { label: '介入已裁定', value: stats.value.interventionResolved || 0 },
]))

const getRefundStatusType = status => {
  const map = {
    0: 'warning',
    1: 'primary',
    2: 'danger',
    3: 'success',
  }
  return map[status] || 'info'
}

const getInterventionLabel = status => {
  const map = {
    pending: '平台介入中',
    approved: '平台已同意',
    rejected: '平台维持拒绝',
  }
  return map[status] || '平台介入'
}

const getInterventionTagType = status => {
  const map = {
    pending: 'warning',
    approved: 'success',
    rejected: 'info',
  }
  return map[status] || 'info'
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminRefunds({
      status: Number(activeStatus.value),
      page: pagination.page,
      size: pagination.size,
    })
    refunds.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try {
    const res = await getRefundCompetitionStats()
    stats.value = {
      total: Number(res?.total || 0),
      pending: Number(res?.pending || 0),
      rejected: Number(res?.rejected || 0),
      refunded: Number(res?.refunded || 0),
      interventionPending: Number(res?.interventionPending || 0),
      interventionResolved: Number(res?.interventionResolved || 0),
    }
  } catch {
    // ignore
  }
}

const handleStatusChange = () => {
  pagination.page = 1
  fetchList()
}

const showDetail = async refund => {
  const res = await getRefundDetail(refund.id)
  detail.value = res
  detailVisible.value = true
}

const handleApprove = async refund => {
  try {
    await ElMessageBox.confirm('确认同意该退款申请并执行退款吗？', '退款确认', {
      type: 'warning',
    })
    await approveRefund(refund.id)
    ElMessage.success('退款已通过并完成打款')
    await fetchList()
    await fetchStats()
    window.dispatchEvent(new Event('admin-workbench-refresh'))
  } catch (error) {
    // 用户取消操作时不需要提示错误
  }
}

const handleReject = async refund => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝退款', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入拒绝原因',
    })
    await rejectRefund(refund.id, value)
    ElMessage.success('退款已拒绝')
    await fetchList()
    await fetchStats()
    window.dispatchEvent(new Event('admin-workbench-refresh'))
  } catch (error) {
    // 用户取消操作时不需要提示错误
  }
}

const handleResolveIntervention = async refund => {
  try {
    await ElMessageBox.confirm('确认平台裁定同意退款吗？若取消，可继续选择“维持拒绝”。', '平台裁定', {
      distinguishCancelAndClose: true,
      confirmButtonText: '同意退款',
      cancelButtonText: '维持拒绝',
      type: 'warning',
    })
    const { value } = await ElMessageBox.prompt('请输入平台同意退款的说明', '平台裁定说明', {
      confirmButtonText: '提交裁定',
      cancelButtonText: '跳过',
      inputPlaceholder: '例如：平台核实后支持用户退款诉求',
    }).catch(() => ({ value: '平台核实后支持用户退款诉求' }))
    await resolveRefundIntervention(refund.id, true, value || '平台核实后支持用户退款诉求')
    ElMessage.success('平台已裁定同意退款')
  } catch (error) {
    if (error === 'cancel') {
      try {
        const { value } = await ElMessageBox.prompt('请输入维持拒绝的原因', '平台维持拒绝', {
          confirmButtonText: '提交裁定',
          cancelButtonText: '取消',
          inputValidator: v => !!v?.trim() || '原因不能为空',
        })
        await resolveRefundIntervention(refund.id, false, value.trim())
        ElMessage.success('平台已维持拒绝结果')
      } catch {
        return
      }
    } else {
      return
    }
  }
  await fetchList()
  await fetchStats()
  window.dispatchEvent(new Event('admin-workbench-refresh'))
}

onMounted(async () => {
  await Promise.all([fetchList(), fetchStats()])
})
</script>

<style scoped>
:deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(156, 163, 175, 0.2);
}
:deep(.el-tabs__item) {
  color: var(--el-text-color-regular);
  font-weight: 500;
}
:deep(.el-tabs__item.is-active) {
  color: #3b82f6;
}
:deep(.el-tabs__active-bar) {
  background-color: #3b82f6;
}
</style>
