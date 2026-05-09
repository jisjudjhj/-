<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <el-tabs v-model="statusFilter" @tab-change="handleStatusChange">
        <el-tab-pane label="全部" name="-1" />
        <el-tab-pane label="待处理" name="0" />
        <el-tab-pane label="已退款" name="3" />
        <el-tab-pane label="已拒绝" name="2" />
      </el-tabs>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg p-6">
      <el-table
        v-loading="loading"
        :data="refunds"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="id" label="退款ID" width="100" />
        <el-table-column prop="orderId" label="订单ID" width="100" />
        <el-table-column label="退款金额" width="120">
          <template #default="{ row }">￥{{ getRefundAmount(row).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="退款原因" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="refundStatusType(row.status)" class="!rounded-full">{{ refundStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="平台介入" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.interventionStatus" :type="interventionTagType(row.interventionStatus)" class="!rounded-full">
              {{ interventionLabel(row.interventionStatus) }}
            </el-tag>
            <span v-else class="text-gray-400">-</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="180">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <template v-if="row.status === 0">
                <el-button size="small" type="success" @click="handleApprove(row)">同意</el-button>
                <el-button size="small" type="danger" plain @click="handleReject(row)">拒绝</el-button>
              </template>
              <el-button v-else size="small" @click="showDetail(row)">详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          :page-size="pagination.size"
          v-model:current-page="pagination.page"
          @current-change="fetchList"
        />
      </div>
    </div>

    <el-drawer v-model="detailVisible" title="退款详情" size="min(92vw, 480px)" direction="rtl">
      <template v-if="currentRefund">
        <div class="space-y-3 text-sm p-2">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="text-gray-500">退款ID</div><div>{{ currentRefund.id }}</div>
            <div class="text-gray-500">订单ID</div><div>{{ currentRefund.orderId }}</div>
            <div class="text-gray-500">退款金额</div><div class="font-medium">￥{{ getRefundAmount(currentRefund).toFixed(2) }}</div>
            <div class="text-gray-500">状态</div><div><el-tag :type="refundStatusType(currentRefund.status)" size="small">{{ refundStatusText(currentRefund.status) }}</el-tag></div>
            <div class="text-gray-500">退款原因</div><div>{{ currentRefund.reason || '-' }}</div>
            <div class="text-gray-500">拒绝原因</div><div>{{ currentRefund.rejectReason || '-' }}</div>
            <div class="text-gray-500">平台介入</div><div>{{ interventionLabel(currentRefund.interventionStatus) || '-' }}</div>
            <div class="text-gray-500">介入说明</div><div>{{ currentRefund.interventionReason || '-' }}</div>
            <div class="text-gray-500">申请时间</div><div>{{ currentRefund.createTime || '-' }}</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMerchantRefunds } from '../../api/merchant'
import { approveRefund, getRefundDetail, rejectRefund } from '../../api/refunds'

const loading = ref(false)
const refunds = ref([])
const statusFilter = ref('-1')
const detailVisible = ref(false)
const currentRefund = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })

const refundStatusMap = {
  0: { text: '待处理', type: 'warning' },
  1: { text: '已同意', type: 'success' },
  2: { text: '已拒绝', type: 'danger' },
  3: { text: '已退款', type: 'success' },
}

const refundStatusText = s => refundStatusMap[s]?.text || '未知'
const refundStatusType = s => refundStatusMap[s]?.type || 'info'
const getRefundAmount = refund => Number(refund?.amount ?? refund?.refundAmount ?? 0)
const interventionLabel = status => ({
  pending: '平台介入中',
  approved: '平台已同意',
  rejected: '平台维持拒绝',
}[status] || '')
const interventionTagType = status => ({
  pending: 'warning',
  approved: 'success',
  rejected: 'info',
}[status] || 'info')

const handleStatusChange = () => {
  pagination.page = 1
  fetchList()
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMerchantRefunds({
      status: Number(statusFilter.value),
      page: pagination.page,
      size: pagination.size,
    })
    const data = res || {}
    refunds.value = data.records || []
    pagination.total = data.total || 0
  } finally {
    loading.value = false
  }
}

const showDetail = async row => {
  try {
    const res = await getRefundDetail(row.id)
    currentRefund.value = res || row
  } catch {
    currentRefund.value = row
  }
  detailVisible.value = true
}

const handleApprove = async row => {
  try {
    await ElMessageBox.confirm(`确定同意退款（￥${getRefundAmount(row).toFixed(2)}）吗？`, '退款审核', { type: 'warning' })
    await approveRefund(row.id)
    ElMessage.success('退款已同意')
    await fetchList()
    window.dispatchEvent(new Event('merchant-workbench-refresh'))
  } catch {}
}

const handleReject = async row => {
  try {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝退款', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValidator: v => !!v?.trim() || '拒绝原因不能为空',
    })
    await rejectRefund(row.id, value.trim())
    ElMessage.success('退款已拒绝')
    await fetchList()
    window.dispatchEvent(new Event('merchant-workbench-refresh'))
  } catch {}
}

onMounted(fetchList)
</script>

<style scoped>
:deep(.el-table) {
  background-color: transparent !important;
  --el-table-border-color: rgba(156, 163, 175, 0.2);
  --el-table-header-bg-color: rgba(243, 244, 246, 0.5);
  --el-table-row-hover-bg-color: rgba(59, 130, 246, 0.05);
}
.dark :deep(.el-table) {
  --el-table-header-bg-color: rgba(31, 41, 55, 0.5);
  --el-table-row-hover-bg-color: rgba(59, 130, 246, 0.1);
}
:deep(.el-table th.el-table__cell) { background-color: var(--el-table-header-bg-color) !important; backdrop-filter: blur(10px); }
:deep(.el-table tr) { background-color: transparent !important; }
:deep(.el-table td.el-table__cell) { border-bottom-color: var(--el-table-border-color); }
</style>
