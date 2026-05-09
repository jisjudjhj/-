<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <el-tabs v-model="statusFilter" @tab-change="handleStatusChange">
        <el-tab-pane label="全部" name="-1" />
        <el-tab-pane label="待付款" name="0" />
        <el-tab-pane label="已付款" name="1" />
        <el-tab-pane label="已发货" name="2" />
        <el-tab-pane label="已完成" name="3" />
        <el-tab-pane label="已取消" name="4" />
        <el-tab-pane label="已退款" name="5" />
      </el-tabs>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg p-6">
      <el-table
        v-loading="loading"
        :data="orders"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">￥{{ Number(row.totalAmount || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="orderStatusType(row.status)" class="!rounded-full">{{ orderStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <el-button size="small" @click="showDetail(row)">详情</el-button>
              <el-button v-if="row.status === 1" size="small" type="primary" @click="handleShip(row)">发货</el-button>
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

    <el-drawer v-model="detailVisible" title="订单详情" size="min(92vw, 500px)" direction="rtl">
      <template v-if="currentOrder">
        <div class="space-y-4 p-2">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
            <div class="text-gray-500 dark:text-gray-400">订单号</div>
            <div class="text-gray-800 dark:text-gray-100">{{ currentOrder.orderNo }}</div>
            <div class="text-gray-500 dark:text-gray-400">状态</div>
            <div><el-tag :type="orderStatusType(currentOrder.status)" size="small">{{ orderStatusText(currentOrder.status) }}</el-tag></div>
            <div class="text-gray-500 dark:text-gray-400">总金额</div>
            <div class="text-gray-800 dark:text-gray-100 font-medium">￥{{ Number(currentOrder.totalAmount || 0).toFixed(2) }}</div>
            <div class="text-gray-500 dark:text-gray-400">收货地址</div>
            <div class="text-gray-800 dark:text-gray-100">{{ currentOrder.shippingAddress || '-' }}</div>
            <div class="text-gray-500 dark:text-gray-400">下单时间</div>
            <div class="text-gray-800 dark:text-gray-100">{{ currentOrder.createTime || '-' }}</div>
          </div>
          <el-divider />
          <div class="text-sm font-medium text-gray-800 dark:text-gray-100 mb-2">订单商品</div>
          <div
            v-for="item in (currentOrder.items || [])"
            :key="item.id"
            class="flex items-center gap-3 p-3 rounded-xl bg-gray-50/50 dark:bg-gray-700/30"
          >
            <img v-if="item.productImage" :src="item.productImage" class="w-12 h-12 rounded-lg object-cover" />
            <div class="flex-1 min-w-0">
              <div class="text-sm truncate">{{ item.productName }}</div>
              <div class="text-xs text-gray-500">￥{{ Number(item.price || 0).toFixed(2) }} x {{ item.quantity }}</div>
            </div>
            <div class="text-sm font-medium">￥{{ Number(item.subtotal || (item.price * item.quantity) || 0).toFixed(2) }}</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMerchantOrders, shipMerchantOrder } from '../../api/merchant'

const loading = ref(false)
const orders = ref([])
const statusFilter = ref('-1')
const detailVisible = ref(false)
const currentOrder = ref(null)
const pagination = reactive({ page: 1, size: 10, total: 0 })

const orderStatusMap = {
  0: { text: '待付款', type: 'info' },
  1: { text: '已付款', type: 'warning' },
  2: { text: '已发货', type: '' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'danger' },
  5: { text: '已退款', type: 'danger' },
}

const orderStatusText = s => orderStatusMap[s]?.text || '未知'
const orderStatusType = s => orderStatusMap[s]?.type || 'info'

const handleStatusChange = () => {
  pagination.page = 1
  fetchList()
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMerchantOrders({
      status: Number(statusFilter.value),
      page: pagination.page,
      size: pagination.size,
    })
    const data = res || {}
    orders.value = data.records || []
    pagination.total = data.total || 0
  } finally {
    loading.value = false
  }
}

const showDetail = row => {
  currentOrder.value = row
  detailVisible.value = true
}

const handleShip = async row => {
  try {
    await ElMessageBox.confirm(`确定对订单「${row.orderNo}」进行发货操作吗？`, '发货确认', { type: 'warning' })
    await shipMerchantOrder(row.id)
    ElMessage.success('发货成功')
    await fetchList()
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
:deep(.el-table th.el-table__cell) {
  background-color: var(--el-table-header-bg-color) !important;
  backdrop-filter: blur(10px);
}
:deep(.el-table tr) { background-color: transparent !important; }
:deep(.el-table td.el-table__cell) { border-bottom-color: var(--el-table-border-color); }
</style>
