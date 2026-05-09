<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <el-tabs v-model="activeStatus" class="custom-tabs" @tab-change="handleStatusChange">
        <el-tab-pane label="全部订单" name="-1" />
        <el-tab-pane label="待支付" name="0" />
        <el-tab-pane label="已支付" name="1" />
        <el-tab-pane label="已发货" name="2" />
        <el-tab-pane label="已完成" name="3" />
        <el-tab-pane label="已取消" name="4" />
        <el-tab-pane label="已退款" name="5" />
      </el-tabs>

      <div v-loading="loading" class="mt-6 space-y-4">
        <div
          v-for="order in orders"
          :key="order.id"
          class="bg-white/40 dark:bg-gray-800/40 rounded-xl p-6 border border-gray-200/50 dark:border-gray-700/50 hover:shadow-md transition-shadow"
        >
          <div class="flex justify-between items-center gap-4 mb-4 pb-4 border-b border-gray-200/50 dark:border-gray-700/50">
            <div>
              <div class="font-semibold text-gray-800 dark:text-gray-200">订单号：{{ order.orderNo }}</div>
              <div class="text-sm text-gray-500 mt-1">
                下单用户：{{ order.username || '未知用户' }} · {{ order.createTime }}
              </div>
            </div>
            <el-tag :type="getOrderStatusType(order.status)" class="!rounded-full">
              {{ getLabel(ORDER_STATUS_MAP, order.status) }}
            </el-tag>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-[1fr_220px] gap-6">
            <div class="space-y-3">
              <div
                v-for="item in order.items || []"
                :key="`${order.id}-${item.id || item.productId}`"
                class="flex items-center gap-4 bg-gray-50/60 dark:bg-gray-900/40 rounded-xl p-4"
              >
                <div class="w-12 h-12 rounded-xl bg-gray-200 dark:bg-gray-700 overflow-hidden flex items-center justify-center">
                  <img v-if="item.productImage" :src="item.productImage" class="w-full h-full object-cover" />
                  <el-icon v-else class="text-gray-400"><Picture /></el-icon>
                </div>
                <div class="flex-1 min-w-0">
                  <div class="font-medium text-gray-900 dark:text-gray-100 truncate">{{ item.productName }}</div>
                  <div class="text-sm text-gray-500 dark:text-gray-400">
                    单价 ￥{{ Number(item.price || 0).toFixed(2) }} · 数量 {{ item.quantity }}
                  </div>
                </div>
                <div class="text-right font-medium text-gray-800 dark:text-gray-100">
                  ￥{{ Number(item.subtotal || 0).toFixed(2) }}
                </div>
              </div>
            </div>

            <div class="bg-gray-50/60 dark:bg-gray-900/40 rounded-xl p-4 space-y-3">
              <div class="text-sm text-gray-500">收货人：{{ order.receiverName || '-' }}</div>
              <div class="text-sm text-gray-500">手机号：{{ order.receiverPhone || '-' }}</div>
              <div class="text-sm text-gray-500">地址：{{ order.address || '-' }}</div>
              <div class="text-sm text-gray-500">备注：{{ order.remark || '无' }}</div>
              <div class="pt-2 border-t border-gray-200/60 dark:border-gray-700/60">
                <div class="text-sm text-gray-500">订单金额</div>
                <div class="text-xl font-bold text-gray-900 dark:text-gray-100">
                  ￥{{ Number(order.totalAmount || 0).toFixed(2) }}
                </div>
              </div>
            </div>
          </div>

          <div class="mt-4 pt-4 border-t border-gray-200/50 dark:border-gray-700/50 flex justify-end gap-3">
            <el-dropdown
              v-if="ORDER_NEXT_STATUS_MAP[order.status]?.length"
              @command="value => handleChangeStatus(order, value)"
            >
              <el-button type="primary" class="!rounded-lg shadow-lg shadow-blue-500/20">
                更新状态
                <el-icon class="ml-2"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="item in ORDER_NEXT_STATUS_MAP[order.status]"
                    :key="item.value"
                    :command="item.value"
                  >
                    {{ item.label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminOrders, updateAdminOrderStatus } from '../../api/admin'
import { getLabel, ORDER_NEXT_STATUS_MAP, ORDER_STATUS_MAP } from '../../utils/status'

const loading = ref(false)
const activeStatus = ref('-1')
const orders = ref([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const getOrderStatusType = status => {
  const map = {
    0: 'warning',
    1: 'primary',
    2: 'success',
    3: 'success',
    4: 'info',
    5: 'danger',
  }
  return map[status] || 'info'
}

const fetchList = async () => {
  loading.value = true
  try {
    const status = Number(activeStatus.value)
    const res = await getAdminOrders({
      status,
      page: pagination.page,
      size: pagination.size,
    })
    orders.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const handleStatusChange = () => {
  pagination.page = 1
  fetchList()
}

const handleChangeStatus = async (order, status) => {
  await updateAdminOrderStatus(order.id, status)
  ElMessage.success(`订单状态已更新为${getLabel(ORDER_STATUS_MAP, status)}`)
  await fetchList()
}

onMounted(fetchList)
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
