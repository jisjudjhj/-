<template>
  <div class="space-y-6">
    <div class="panel-card p-5">
      <div class="grid grid-cols-1 gap-3 md:grid-cols-4">
        <el-select
          v-model="filters.type"
          clearable
          placeholder="消息类型"
          @change="handleFilterChange"
        >
          <el-option label="订单消息" value="order" />
          <el-option label="系统消息" value="system" />
          <el-option label="营销消息" value="promotion" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索标题或内容"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <div class="md:col-span-2 flex justify-start gap-2 md:justify-end">
          <el-button @click="handleFilterChange">查询</el-button>
          <el-button @click="markAllRead" :disabled="unreadCount <= 0">全部已读</el-button>
        </div>
      </div>
    </div>

    <div class="panel-card p-5">
      <div class="mb-4 flex items-center justify-between text-sm text-slate-500 dark:text-slate-400">
        <span>共 {{ pagination.total }} 条消息，未读 {{ unreadCount }} 条</span>
        <span>提示：秒杀相关消息可直接跳转到报名记录</span>
      </div>

      <el-table
        v-loading="loading"
        :data="displayMessages"
        class="!bg-transparent"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
        :row-class-name="getRowClassName"
        @row-click="handleRowClick"
      >
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.isRead === 1 ? 'info' : 'danger'" effect="light" round>
              {{ row.isRead === 1 ? '已读' : '未读' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="typeMap[row.type]?.type || 'info'" effect="light" round>
              {{ typeMap[row.type]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right" fixed="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <el-button
                size="small"
                v-if="row.isRead !== 1"
                @click.stop="markRead(row)"
              >
                标记已读
              </el-button>
              <el-button
                size="small"
                type="primary"
                v-if="isSeckillMessage(row)"
                @click.stop="goToSeckillApplication(row)"
              >
                查看报名
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          @current-change="fetchMessages"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { subscribeRealtime } from '../../utils/realtime'
import {
  getMerchantMessageUnreadCount,
  getMerchantMessages,
  markMerchantMessageRead,
  markMerchantMessageReadAll,
} from '../../api/merchant'

const router = useRouter()
const MESSAGE_REFRESH_DEBOUNCE_MS = 250

const loading = ref(false)
const messages = ref([])
const unreadCount = ref(0)

const filters = reactive({
  type: undefined,
  keyword: '',
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const typeMap = {
  order: { label: '订单', type: 'primary' },
  system: { label: '系统', type: 'info' },
  promotion: { label: '营销', type: 'warning' },
}
let unsubscribeMessageRealtime = null
let unsubscribeWorkbenchMessageRealtime = null
let messageRefreshTimer = null

const normalizePageData = payload => {
  if (Array.isArray(payload)) {
    return { records: payload, total: payload.length }
  }
  if (!payload || typeof payload !== 'object') {
    return { records: [], total: 0 }
  }
  const records = Array.isArray(payload.records) ? payload.records : []
  return {
    records,
    total: Number(payload.total ?? records.length ?? 0),
  }
}

const isSeckillMessage = row => {
  const relatedId = Number(row?.relatedId)
  if (!Number.isFinite(relatedId) || relatedId <= 0) {
    return false
  }
  const text = `${row?.title || ''} ${row?.content || ''}`
  return text.includes('秒杀')
}

const displayMessages = computed(() => {
  const keyword = `${filters.keyword || ''}`.trim()
  if (!keyword) return messages.value
  return messages.value.filter(item => {
    const title = `${item?.title || ''}`
    const content = `${item?.content || ''}`
    return title.includes(keyword) || content.includes(keyword)
  })
})

const fetchUnreadCount = async () => {
  try {
    const res = await getMerchantMessageUnreadCount()
    unreadCount.value = Number(res?.count || 0)
  } catch {
    unreadCount.value = 0
  }
}

const fetchMessages = async () => {
  loading.value = true
  try {
    const res = await getMerchantMessages({
      page: pagination.page,
      size: pagination.size,
      type: filters.type,
    })
    const pageData = normalizePageData(res)
    messages.value = pageData.records
    pagination.total = pageData.total
  } finally {
    loading.value = false
  }
}

const refreshData = async () => {
  await Promise.all([fetchMessages(), fetchUnreadCount()])
  window.dispatchEvent(new Event('merchant-message-refresh'))
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchMessages()
}

const markRead = async row => {
  if (!row?.id || row.isRead === 1) return
  await markMerchantMessageRead(row.id)
  row.isRead = 1
  unreadCount.value = Math.max(0, unreadCount.value - 1)
  window.dispatchEvent(new Event('merchant-message-refresh'))
}

const markAllRead = async () => {
  if (unreadCount.value <= 0) return
  await markMerchantMessageReadAll()
  ElMessage.success('全部消息已读')
  await refreshData()
}

const goToSeckillApplication = async row => {
  if (!isSeckillMessage(row)) return
  await markRead(row)
  router.push({
    path: '/merchant/seckill',
    query: {
      tab: 'applications',
      applyId: String(row.relatedId),
      from: 'message',
    },
  })
}

const handleRowClick = row => {
  if (!row) return
  if (isSeckillMessage(row)) {
    goToSeckillApplication(row)
    return
  }
  markRead(row)
}

const getRowClassName = ({ row }) => (isSeckillMessage(row) ? 'seckill-link-row' : '')

const handleMessageRealtimeEvent = () => {
  if (messageRefreshTimer) {
    clearTimeout(messageRefreshTimer)
  }
  messageRefreshTimer = setTimeout(() => {
    refreshData().catch(() => null)
  }, MESSAGE_REFRESH_DEBOUNCE_MS)
}

const setupMessageRealtimeSubscription = () => {
  if (typeof unsubscribeMessageRealtime === 'function') {
    unsubscribeMessageRealtime()
  }
  if (typeof unsubscribeWorkbenchMessageRealtime === 'function') {
    unsubscribeWorkbenchMessageRealtime()
  }
  unsubscribeMessageRealtime = subscribeRealtime('/user/queue/user-message-refresh', handleMessageRealtimeEvent)
  unsubscribeWorkbenchMessageRealtime = subscribeRealtime('/user/queue/workbench-refresh', payload => {
    const scope = String(payload?.scope || '')
    if (!scope || scope.includes('message')) {
      handleMessageRealtimeEvent()
    }
  })
}

onMounted(() => {
  refreshData()
  setupMessageRealtimeSubscription()
})

onUnmounted(() => {
  if (typeof unsubscribeMessageRealtime === 'function') {
    unsubscribeMessageRealtime()
    unsubscribeMessageRealtime = null
  }
  if (typeof unsubscribeWorkbenchMessageRealtime === 'function') {
    unsubscribeWorkbenchMessageRealtime()
    unsubscribeWorkbenchMessageRealtime = null
  }
  if (messageRefreshTimer) {
    clearTimeout(messageRefreshTimer)
    messageRefreshTimer = null
  }
})
</script>

<style scoped>
:deep(.seckill-link-row) {
  cursor: pointer;
}

:deep(.seckill-link-row > td) {
  background: rgba(59, 130, 246, 0.06) !important;
}
</style>
