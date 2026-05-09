<template>
  <div class="space-y-6">
    <div class="panel-card p-5">
      <div class="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div class="text-lg font-semibold text-slate-900 dark:text-slate-100">客服会话</div>
          <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">
            集中处理买家咨询、订单协商与平台介入后的跟进，减少来回切换。
          </div>
        </div>
        <div class="flex flex-wrap gap-3 text-sm text-slate-500 dark:text-slate-400">
          <span>会话 {{ conversations.length }}</span>
          <span>未读 {{ unreadCount }}</span>
          <el-button size="small" @click="refreshAll">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-6 xl:grid-cols-[360px_minmax(0,1fr)]">
      <div class="panel-card p-4">
        <div class="mb-3 flex items-center justify-between">
          <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">会话列表</div>
          <el-select v-model="filters.status" placeholder="状态" clearable class="!w-32" @change="fetchConversations">
            <el-option label="处理中" value="open" />
            <el-option label="待客服" value="pending_support" />
            <el-option label="已关闭" value="closed" />
          </el-select>
        </div>

        <div class="space-y-3">
          <button
            v-for="item in conversations"
            :key="item.id"
            type="button"
            class="w-full rounded-2xl border p-4 text-left transition"
            :class="activeConversation?.id === item.id
              ? 'border-blue-500 bg-blue-50/80 shadow-sm dark:border-blue-400 dark:bg-blue-500/10'
              : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900/60 dark:hover:border-slate-500'"
            @click="selectConversation(item)"
          >
            <div class="flex items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ item.counterpart?.name || '买家' }}
                  </span>
                  <el-tag size="small" round :type="item.conversationType === 'support' ? 'danger' : 'primary'">
                    {{ item.conversationType === 'support' ? '平台介入' : '商家协商' }}
                  </el-tag>
                </div>
                <div class="mt-1 truncate text-xs text-slate-500 dark:text-slate-400">
                  {{ item.context?.order?.orderNo || item.context?.product?.name || item.conversationNo }}
                </div>
              </div>
              <div class="text-right">
                <div class="text-[11px] text-slate-400">{{ item.lastMessageTime || '-' }}</div>
                <el-badge :value="item.unreadCount" :hidden="!item.unreadCount" :max="99" class="mt-1" />
              </div>
            </div>
            <div class="mt-3 line-clamp-2 text-sm text-slate-600 dark:text-slate-300">
              {{ item.lastMessage || '暂无消息' }}
            </div>
          </button>

          <div v-if="!conversations.length" class="rounded-2xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
            暂无客服会话
          </div>
        </div>
      </div>

      <div class="panel-card p-0">
        <div v-if="activeConversation" class="support-chat-panel flex flex-col">
          <div class="border-b border-slate-200 px-5 py-4 dark:border-slate-800">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <div class="text-base font-semibold text-slate-900 dark:text-slate-100">
                    {{ activeConversation.counterpart?.name || '买家' }}
                  </div>
                  <el-tag size="small" round :type="activeConversation.conversationType === 'support' ? 'danger' : 'success'">
                    {{ activeConversation.conversationType === 'support' ? '平台介入中' : '商家处理中' }}
                  </el-tag>
                  <el-tag size="small" round effect="plain">{{ activeConversation.status }}</el-tag>
                </div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {{ activeConversation.context?.order?.orderNo || activeConversation.context?.product?.name || activeConversation.conversationNo }}
                </div>
              </div>
              <div class="flex gap-2">
                <el-button size="small" @click="toggleConversationStatus">
                  {{ activeConversation.status === 'closed' ? '重开会话' : '关闭会话' }}
                </el-button>
                <el-button size="small" @click="refreshMessages">刷新消息</el-button>
              </div>
            </div>

            <div class="mt-4 grid grid-cols-1 gap-3 lg:grid-cols-2" v-if="activeConversation.context?.order || activeConversation.context?.product">
              <div v-if="activeConversation.context?.order" class="rounded-2xl bg-slate-50 px-4 py-3 text-sm dark:bg-slate-800/70">
                <div class="font-medium text-slate-900 dark:text-slate-100">关联订单</div>
                <div class="mt-1 text-slate-600 dark:text-slate-300">{{ activeConversation.context.order.orderNo }}</div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">状态 {{ activeConversation.context.order.status }} · 金额 ¥{{ activeConversation.context.order.totalAmount }}</div>
              </div>
              <div v-if="activeConversation.context?.product" class="rounded-2xl bg-slate-50 px-4 py-3 text-sm dark:bg-slate-800/70">
                <div class="font-medium text-slate-900 dark:text-slate-100">关联商品</div>
                <div class="mt-1 text-slate-600 dark:text-slate-300">{{ activeConversation.context.product.name }}</div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">售价 ¥{{ activeConversation.context.product.price }}</div>
              </div>
            </div>
          </div>

          <div ref="messageBoxRef" class="flex-1 space-y-4 overflow-y-auto px-5 py-5">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="flex"
              :class="msg.senderRole === 'merchant' ? 'justify-end' : 'justify-start'"
            >
              <div
                class="max-w-[78%] rounded-3xl px-4 py-3 text-sm leading-7 shadow-sm"
                :class="msg.senderRole === 'merchant'
                  ? 'bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900'
                  : msg.senderRole === 'system'
                    ? 'bg-amber-50 text-amber-900 dark:bg-amber-500/10 dark:text-amber-200'
                    : 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-100'"
              >
                <div class="mb-1 text-[11px] opacity-70">
                  {{ msg.senderName || roleText(msg.senderRole) }} · {{ msg.createTime || '' }}
                </div>
                <div>{{ msg.content }}</div>
              </div>
            </div>

            <div v-if="!messages.length" class="rounded-2xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
              当前会话还没有消息
            </div>
          </div>

          <div class="border-t border-slate-200 px-5 py-4 dark:border-slate-800">
            <div class="mb-3 flex flex-wrap gap-2">
              <el-button v-for="item in quickReplies" :key="item.label" size="small" @click="applyQuickReply(item.content)">
                {{ item.label }}
              </el-button>
            </div>
            <div class="flex gap-3">
              <el-input
                v-model="draft"
                type="textarea"
                :rows="3"
                resize="none"
                placeholder="输入给买家的回复内容，例如：您好，订单已经优先安排，预计今天出库。"
                @keyup.enter.exact.prevent="handleSend"
              />
              <el-button type="primary" :loading="sending" class="self-end" @click="handleSend">发送</el-button>
            </div>
          </div>
        </div>

        <div v-else class="support-chat-panel flex items-center justify-center text-sm text-slate-500 dark:text-slate-400">
          请选择左侧会话查看详情
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { subscribeRealtime } from '../../utils/realtime'
import {
  getImConversationMessages,
  getImConversationUnreadCount,
  getImConversations,
  markImConversationRead,
  sendImMessage,
  updateImConversationStatus,
} from '../../api/im'

const IM_REFRESH_DEBOUNCE_MS = 250

const loading = ref(false)
const sending = ref(false)
const unreadCount = ref(0)
const conversations = ref([])
const activeConversation = ref(null)
const messages = ref([])
const draft = ref('')
const messageBoxRef = ref(null)

const filters = reactive({
  status: 'open',
})
const quickReplies = [
  { label: '已查看', content: '您好，您的问题已收到，我们正在核对订单信息。' },
  { label: '发货进度', content: '订单已在加急处理，预计 24 小时内发出。' },
  { label: '售后方案', content: '我们可提供换货或退款方案，您可告知更倾向哪一种。' },
  { label: '结束确认', content: '问题已处理完成，如需帮助可随时再次联系。' },
]
let unsubscribeImRealtime = null
let imRefreshTimer = null

const normalizePageData = payload => {
  if (!payload || typeof payload !== 'object') return { records: [], total: 0 }
  return {
    records: Array.isArray(payload.records) ? payload.records : [],
    total: Number(payload.total ?? 0),
  }
}

const roleText = role => {
  if (role === 'user') return '买家'
  if (role === 'merchant') return '商家'
  if (role === 'admin') return '官方客服'
  return '系统'
}

const parseBackendTime = value => {
  if (!value) return null
  const date = new Date(String(value).replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return null
  return date
}

const messageTimeValue = message => {
  const parsed = parseBackendTime(message?.createTime)
  if (parsed) return parsed.getTime()
  return Number(message?.id || 0)
}

const normalizeMessages = list => [...list].sort((a, b) => {
  const timeDelta = messageTimeValue(a) - messageTimeValue(b)
  if (timeDelta !== 0) return timeDelta
  return Number(a?.id || 0) - Number(b?.id || 0)
})

const scrollToBottom = async () => {
  await nextTick()
  if (messageBoxRef.value) {
    messageBoxRef.value.scrollTop = messageBoxRef.value.scrollHeight
  }
}

const fetchUnread = async () => {
  try {
    const res = await getImConversationUnreadCount()
    unreadCount.value = Number(res?.count || 0)
  } catch {
    unreadCount.value = 0
  }
}

const fetchConversations = async () => {
  loading.value = true
  try {
    const res = await getImConversations({
      page: 1,
      size: 100,
      status: filters.status || undefined,
    })
    const pageData = normalizePageData(res)
    conversations.value = pageData.records
    if (conversations.value.length) {
      const currentId = activeConversation.value?.id
      const matched = conversations.value.find(item => item.id === currentId) || conversations.value[0]
      await selectConversation(matched)
    } else {
      activeConversation.value = null
      messages.value = []
    }
  } finally {
    loading.value = false
  }
}

const refreshMessages = async () => {
  if (!activeConversation.value?.id) return
  const res = await getImConversationMessages(activeConversation.value.id, { page: 1, size: 200 })
  const pageData = normalizePageData(res)
  messages.value = normalizeMessages(pageData.records)
  await scrollToBottom()
}

const selectConversation = async conversation => {
  if (!conversation?.id) return
  activeConversation.value = conversation
  await Promise.all([
    refreshMessages(),
    markImConversationRead(conversation.id).catch(() => null),
  ])
  const target = conversations.value.find(item => item.id === conversation.id)
  if (target) target.unreadCount = 0
  await fetchUnread()
}

const handleSend = async () => {
  const text = draft.value.trim()
  if (!text || !activeConversation.value?.id || sending.value) return
  sending.value = true
  try {
    await sendImMessage(activeConversation.value.id, {
      content: text,
      messageType: 'text',
    })
    draft.value = ''
    await Promise.all([refreshMessages(), fetchConversations(), fetchUnread()])
    ElMessage.success('已发送')
  } finally {
    sending.value = false
  }
}

const toggleConversationStatus = async () => {
  if (!activeConversation.value?.id) return
  const targetStatus = activeConversation.value.status === 'closed' ? 'open' : 'closed'
  await updateImConversationStatus(activeConversation.value.id, {
    status: targetStatus,
    note: targetStatus === 'closed' ? '会话已由商家客服关闭。' : '会话已由商家客服重新开启。',
  })
  ElMessage.success(targetStatus === 'closed' ? '会话已关闭' : '会话已开启')
  await fetchConversations()
}

const applyQuickReply = content => {
  draft.value = content
}

const refreshAll = async () => {
  await Promise.all([fetchConversations(), fetchUnread()])
}

const refreshByImEvent = async payload => {
  const eventConversationId = Number(payload?.conversationId || 0)
  const currentConversationId = Number(activeConversation.value?.id || 0)
  const tasks = [
    fetchConversations(),
    fetchUnread(),
  ]
  if (eventConversationId > 0 && currentConversationId === eventConversationId) {
    tasks.push(markImConversationRead(currentConversationId).catch(() => null))
  }
  await Promise.all(tasks)
}

const handleImRealtimeEvent = payload => {
  if (imRefreshTimer) {
    clearTimeout(imRefreshTimer)
  }
  imRefreshTimer = setTimeout(() => {
    refreshByImEvent(payload).catch(() => null)
  }, IM_REFRESH_DEBOUNCE_MS)
}

const setupImRealtimeSubscription = () => {
  if (typeof unsubscribeImRealtime === 'function') {
    unsubscribeImRealtime()
  }
  unsubscribeImRealtime = subscribeRealtime('/user/queue/im-refresh', handleImRealtimeEvent)
}

onMounted(() => {
  refreshAll()
  setupImRealtimeSubscription()
})

onUnmounted(() => {
  if (typeof unsubscribeImRealtime === 'function') {
    unsubscribeImRealtime()
    unsubscribeImRealtime = null
  }
  if (imRefreshTimer) {
    clearTimeout(imRefreshTimer)
    imRefreshTimer = null
  }
})
</script>

<style scoped>
.support-chat-panel {
  height: clamp(560px, calc(100vh - 200px), 760px);
  min-height: 560px;
}

@media (max-height: 720px) {
  .support-chat-panel {
    height: calc(100vh - 150px);
    min-height: 500px;
  }
}
</style>
