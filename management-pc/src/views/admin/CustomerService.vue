<template>
  <div class="support-page space-y-4">
    <div class="panel-card p-5">
      <div class="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div>
          <div class="text-lg font-semibold text-slate-900 dark:text-slate-100">官方客服工作台</div>
          <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">工单 · 会话 · 介入</div>
        </div>
        <div class="flex flex-wrap gap-3 text-sm text-slate-500 dark:text-slate-400">
          <span>工单 {{ tickets.length }}</span>
          <span :class="overtimeCount > 0 ? 'text-amber-600 dark:text-amber-300' : ''">超时 {{ overtimeCount }}</span>
          <span v-if="severeOvertimeCount > 0" class="text-red-600 dark:text-red-300">严重超时 {{ severeOvertimeCount }}</span>
          <span>客服席位 {{ agents.length }}</span>
          <el-button size="small" @click="refreshAll">刷新</el-button>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-2 gap-3 xl:grid-cols-4">
      <div class="panel-card p-4">
        <div class="text-xs text-slate-500 dark:text-slate-400">AI解决率</div>
        <div class="mt-2 text-2xl font-semibold text-emerald-600 dark:text-emerald-300">{{ formatPercent(metrics.aiResolveRate) }}</div>
        <div class="mt-1 text-xs text-slate-400">AI独立完结 {{ metrics.aiResolvedCount }} / {{ metrics.totalSupportConversations }}</div>
      </div>
      <div class="panel-card p-4">
        <div class="text-xs text-slate-500 dark:text-slate-400">转人工率</div>
        <div class="mt-2 text-2xl font-semibold text-blue-600 dark:text-blue-300">{{ formatPercent(metrics.transferRate) }}</div>
        <div class="mt-1 text-xs text-slate-400">转人工会话 {{ metrics.transferredToHumanCount }}</div>
      </div>
      <div class="panel-card p-4">
        <div class="text-xs text-slate-500 dark:text-slate-400">人工首响时长</div>
        <div class="mt-2 text-2xl font-semibold text-violet-600 dark:text-violet-300">{{ formatMinutes(metrics.avgFirstResponseMinutes) }}</div>
        <div class="mt-1 text-xs text-slate-400">从建单到首条人工回复</div>
      </div>
      <div class="panel-card p-4">
        <div class="text-xs text-slate-500 dark:text-slate-400">超时工单占比</div>
        <div class="mt-2 text-2xl font-semibold text-amber-600 dark:text-amber-300">{{ formatPercent(metrics.overtimeTicketRatio) }}</div>
        <div class="mt-1 text-xs text-slate-400">活跃工单 {{ metrics.overtimeTicketCount }} / {{ metrics.activeTicketCount }}</div>
      </div>
    </div>

    <div class="support-workspace">
      <aside class="panel-card support-queue p-4">
          <div v-if="overtimeCount > 0" class="mb-3 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">
            当前有 {{ overtimeCount }} 个超时工单
            <span v-if="severeOvertimeCount > 0">，其中 {{ severeOvertimeCount }} 个严重超时</span>
          </div>

          <div class="mb-4 space-y-3">
            <div class="flex items-center justify-between gap-3">
              <div class="text-sm font-semibold text-slate-800 dark:text-slate-100">会话队列</div>
              <el-button size="small" text @click="refreshAll">刷新</el-button>
            </div>
            <div class="grid grid-cols-[1fr_auto] items-center gap-2">
              <el-select v-model="filters.ticketStatus" clearable placeholder="全部状态" class="!w-full" @change="fetchTickets">
                <el-option label="待分配" value="pending_assign" />
                <el-option label="处理中" value="processing" />
                <el-option label="已解决" value="resolved" />
              </el-select>
              <el-switch v-model="filters.overtimeOnly" active-text="超时" @change="handleFilterChange" />
            </div>
          </div>

          <div class="support-ticket-list">
            <button
              v-for="item in filteredTickets"
              :key="item.id"
              type="button"
              class="w-full rounded-2xl border p-3 text-left transition"
              :class="activeTicket?.id === item.id
                ? 'border-blue-500 bg-blue-50/80 shadow-sm dark:border-blue-400 dark:bg-blue-500/10'
                : item.isOvertime
                  ? (item.overtimeLevel === 'severe'
                    ? 'border-red-200 bg-red-50/70 hover:border-red-300 dark:border-red-500/40 dark:bg-red-500/10 dark:hover:border-red-400'
                    : 'border-amber-200 bg-amber-50/70 hover:border-amber-300 dark:border-amber-500/40 dark:bg-amber-500/10 dark:hover:border-amber-400')
                : 'border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900/60 dark:hover:border-slate-500'"
              @click="selectTicket(item)"
            >
              <div class="flex items-center justify-between gap-3">
                <div class="min-w-0">
                  <div class="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {{ item.issueSummary || '平台介入工单' }}
                  </div>
                  <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ item.ticketNo }}</div>
                </div>
                <div class="flex shrink-0 items-center gap-1">
                  <el-tag size="small" round :type="item.ticketStatus === 'resolved' ? 'success' : (item.ticketStatus === 'processing' ? 'danger' : 'warning')">
                    {{ item.ticketStatus }}
                  </el-tag>
                  <el-tag v-if="item.isOvertime" size="small" round :type="item.overtimeLevel === 'severe' ? 'danger' : 'warning'">
                    超时 {{ item.overtimeText }}
                  </el-tag>
                </div>
              </div>
              <div class="mt-2 line-clamp-2 text-sm text-slate-600 dark:text-slate-300">{{ item.issueDetail || '暂无补充说明' }}</div>
              <div class="mt-2 text-xs text-slate-400">{{ item.createTime || '-' }}</div>
            </button>

            <div v-if="!filteredTickets.length" class="rounded-2xl border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400">
              {{ filters.overtimeOnly ? '暂无超时工单' : '暂无客服工单' }}
            </div>
          </div>

          <div class="mt-4 border-t border-slate-200 pt-4 dark:border-slate-800">
          <div class="mb-3 flex items-center justify-between">
            <div class="text-sm font-semibold text-slate-800 dark:text-slate-100">客服席位</div>
            <el-button size="small" text @click="openAgentDialog">新增</el-button>
          </div>

          <div class="support-agent-list">
            <div v-for="agent in agents" :key="agent.id" class="flex items-center justify-between gap-3 rounded-2xl border border-slate-200 px-3 py-2.5 dark:border-slate-700">
              <div>
                <div class="text-sm font-medium text-slate-900 dark:text-slate-100">{{ agent.displayName }}</div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">账号ID {{ agent.userId }} · {{ agent.agentType }}</div>
              </div>
              <el-tag size="small" round :type="agent.onlineStatus === 1 ? 'success' : 'info'">
                {{ agent.onlineStatus === 1 ? '在线' : '离线' }}
              </el-tag>
            </div>
          </div>
        </div>
      </aside>

      <section class="panel-card support-chat-shell p-0">
        <div v-if="activeConversation" class="support-chat-panel flex flex-col">
          <div class="support-chat-header border-b border-slate-200 px-5 py-4 dark:border-slate-800">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="flex items-center gap-2">
                  <div class="text-base font-semibold text-slate-900 dark:text-slate-100">
                    {{ activeConversation.counterpart?.name || '买家' }}
                  </div>
                  <el-tag size="small" round type="danger">官方介入</el-tag>
                  <el-tag size="small" round effect="plain">{{ activeConversation.status }}</el-tag>
                </div>
                <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">
                  {{ activeConversation.context?.order?.orderNo || activeConversation.context?.product?.name || activeConversation.conversationNo }}
                </div>
              </div>

              <div class="support-chat-actions flex flex-wrap gap-2">
                <el-select v-model="assignSupportUserId" class="!w-40" placeholder="分配客服">
                  <el-option v-for="agent in agents" :key="agent.id" :label="agent.displayName" :value="agent.userId" />
                </el-select>
                <el-button size="small" @click="assignTicket">分配</el-button>
                <el-button size="small" :disabled="activeTicket?.ticketStatus === 'processing'" @click="markTicketProcessing">受理</el-button>
                <el-button size="small" type="success" :disabled="activeTicket?.ticketStatus === 'resolved'" @click="markTicketResolved">标记解决</el-button>
                <el-button size="small" :disabled="activeTicket?.ticketStatus !== 'resolved'" @click="reopenTicket">重开工单</el-button>
                <el-button size="small" @click="toggleConversationStatus">
                  {{ activeConversation.status === 'closed' ? '重开会话' : '关闭会话' }}
                </el-button>
                <el-button size="small" @click="refreshMessages">刷新消息</el-button>
              </div>
            </div>

            <div class="support-context-strip" v-if="activeConversation.context?.order || activeConversation.context?.product || activeConversation.ticket?.aiHandoff">
              <div v-if="activeConversation.context?.order" class="support-context-item">
                <div class="support-context-label">订单</div>
                <div class="support-context-title">{{ activeConversation.context.order.orderNo }}</div>
                <div class="support-context-meta">状态 {{ activeConversation.context.order.status }} · 金额 ¥{{ activeConversation.context.order.totalAmount }}</div>
              </div>
              <div v-if="activeConversation.ticket" class="support-context-item support-context-item--ticket">
                <div class="support-context-label">工单</div>
                <div class="support-context-title">{{ activeConversation.ticket.issueSummary }}</div>
                <div class="support-context-meta">
                  {{ activeConversation.ticket.ticketNo }} · {{ activeConversation.ticket.ticketStatus }}
                </div>
              </div>
              <div v-if="activeConversation.ticket?.aiHandoff" class="support-context-item support-context-item--ai">
                <div class="support-context-label">AI交接</div>
                <div class="support-context-title">
                  {{ activeConversation.ticket.aiHandoff.demand || '待补充' }} ·
                  {{ activeConversation.ticket.aiHandoff.verifiedInfo || '待补充' }} ·
                  {{ activeConversation.ticket.aiHandoff.suggestedAction || '待补充' }}
                </div>
              </div>
            </div>
          </div>

          <div ref="messageBoxRef" class="support-message-list flex-1 space-y-4 overflow-y-auto px-5 py-5">
            <div
              v-for="msg in messages"
              :key="msg.id"
              class="flex"
              :class="msg.senderRole === 'admin' ? 'justify-end' : 'justify-start'"
            >
              <div
                class="support-message-bubble max-w-[78%] rounded-3xl px-4 py-3 text-sm leading-7 shadow-sm"
                :class="msg.senderRole === 'admin'
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

          <div class="support-reply-box border-t border-slate-200 px-5 py-4 dark:border-slate-800">
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
                placeholder="输入官方客服回复，例如：您好，平台已介入核实，我们会优先协调商家处理。"
                @keyup.enter.exact.prevent="handleSend"
              />
              <el-button type="primary" :loading="sending" class="self-end" @click="handleSend">发送</el-button>
            </div>
          </div>
        </div>

        <div v-else class="support-chat-panel flex items-center justify-center text-sm text-slate-500 dark:text-slate-400">
          请先选择左侧工单
        </div>
      </section>
    </div>

    <el-dialog v-model="agentDialogVisible" title="新增官方客服" width="420px">
      <div class="space-y-4">
        <el-input v-model="agentForm.userId" placeholder="填写后台用户ID，例如 1" />
        <el-input v-model="agentForm.displayName" placeholder="展示名称，例如 官方客服小优" />
      </div>
      <template #footer>
        <el-button @click="agentDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAgent">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { subscribeRealtime } from '../../utils/realtime'
import {
  assignImSupportTicket,
  getImConversationDetail,
  getImConversationMessages,
  getImSupportAgents,
  getImSupportMetrics,
  getImSupportTickets,
  markImConversationRead,
  saveImSupportAgent,
  sendImMessage,
  updateImConversationStatus,
  updateImSupportTicketStatus,
} from '../../api/im'

const PENDING_ASSIGN_TIMEOUT_MINUTES = 10
const PROCESSING_TIMEOUT_MINUTES = 30
const SEVERE_OVERTIME_MINUTES = 60
const TICKET_POLLING_INTERVAL_MS = 60000
const IM_REFRESH_DEBOUNCE_MS = 250

const tickets = ref([])
const agents = ref([])
const messages = ref([])
const activeTicket = ref(null)
const activeConversation = ref(null)
const sending = ref(false)
const draft = ref('')
const messageBoxRef = ref(null)
const assignSupportUserId = ref()
const agentDialogVisible = ref(false)
const agentForm = reactive({
  userId: '',
  displayName: '',
})
const filters = reactive({
  ticketStatus: '',
  overtimeOnly: false,
})
const overtimeCount = ref(0)
const severeOvertimeCount = ref(0)
const lastOvertimeFingerprint = ref('')
const metrics = ref({
  totalSupportConversations: 0,
  aiResolvedCount: 0,
  transferredToHumanCount: 0,
  activeTicketCount: 0,
  overtimeTicketCount: 0,
  aiResolveRate: 0,
  transferRate: 0,
  avgFirstResponseMinutes: 0,
  overtimeTicketRatio: 0,
})
const quickReplies = [
  { label: '已接入', content: '您好，我是官方客服，已接入当前会话并开始核实处理。' },
  { label: '补充凭证', content: '为加快处理，请补充订单截图、商品实拍或沟通记录。' },
  { label: '处理中', content: '问题已受理，我们正在与商家核对，预计 24 小时内反馈。' },
  { label: '处理完成', content: '本次问题已处理完成，如仍有疑问可继续在会话内反馈。' },
]
let ticketPollingTimer = null
let unsubscribeImRealtime = null
let imRefreshTimer = null

const filteredTickets = computed(() => {
  if (!filters.overtimeOnly) {
    return tickets.value
  }
  return tickets.value.filter(item => item.isOvertime)
})

const normalizePageData = payload => {
  if (!payload || typeof payload !== 'object') return { records: [], total: 0 }
  return {
    records: Array.isArray(payload.records) ? payload.records : [],
    total: Number(payload.total ?? 0),
  }
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

const roleText = role => {
  if (role === 'user') return '买家'
  if (role === 'merchant') return '商家'
  if (role === 'admin') return '官方客服'
  return '系统'
}

const formatPercent = value => `${Number(value || 0).toFixed(2)}%`

const formatMinutes = value => `${Number(value || 0).toFixed(1)} 分钟`

const buildRequestId = prefix => `${prefix || 'msg'}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`

const scrollToBottom = async () => {
  await nextTick()
  if (messageBoxRef.value) {
    messageBoxRef.value.scrollTop = messageBoxRef.value.scrollHeight
  }
}

const fetchAgents = async () => {
  agents.value = await getImSupportAgents()
}

const fetchMetrics = async () => {
  const data = await getImSupportMetrics()
  metrics.value = {
    totalSupportConversations: Number(data?.totalSupportConversations ?? 0),
    aiResolvedCount: Number(data?.aiResolvedCount ?? 0),
    transferredToHumanCount: Number(data?.transferredToHumanCount ?? 0),
    activeTicketCount: Number(data?.activeTicketCount ?? 0),
    overtimeTicketCount: Number(data?.overtimeTicketCount ?? 0),
    aiResolveRate: Number(data?.aiResolveRate ?? 0),
    transferRate: Number(data?.transferRate ?? 0),
    avgFirstResponseMinutes: Number(data?.avgFirstResponseMinutes ?? 0),
    overtimeTicketRatio: Number(data?.overtimeTicketRatio ?? 0),
  }
}

const parseBackendTime = value => {
  if (!value) return null
  const date = new Date(String(value).replace(/-/g, '/'))
  if (Number.isNaN(date.getTime())) return null
  return date
}

const formatDurationText = minutes => {
  const safeMinutes = Math.max(0, Number(minutes || 0))
  if (safeMinutes < 60) return `${safeMinutes} 分钟`
  const hours = Math.floor(safeMinutes / 60)
  const remainMinutes = safeMinutes % 60
  return remainMinutes > 0 ? `${hours} 小时 ${remainMinutes} 分钟` : `${hours} 小时`
}

const decorateTicket = ticket => {
  const now = Date.now()
  let threshold = 0
  let startTime = null

  if (ticket.ticketStatus === 'pending_assign') {
    threshold = PENDING_ASSIGN_TIMEOUT_MINUTES
    startTime = parseBackendTime(ticket.createTime)
  } else if (ticket.ticketStatus === 'processing') {
    threshold = PROCESSING_TIMEOUT_MINUTES
    startTime = parseBackendTime(ticket.assignedTime || ticket.createTime)
  }

  const passedMinutes = startTime ? Math.floor((now - startTime.getTime()) / 60000) : 0
  const overtimeMinutes = threshold > 0 ? Math.max(0, passedMinutes - threshold) : 0
  const isOvertime = threshold > 0 && overtimeMinutes > 0
  const overtimeLevel = overtimeMinutes >= SEVERE_OVERTIME_MINUTES ? 'severe' : 'warning'

  return {
    ...ticket,
    thresholdMinutes: threshold,
    passedMinutes: Math.max(0, passedMinutes),
    overtimeMinutes,
    isOvertime,
    overtimeLevel,
    overtimeText: formatDurationText(overtimeMinutes),
  }
}

const syncOvertimeStats = (list, { notify = false } = {}) => {
  const overtimeTickets = list.filter(item => item.isOvertime)
  const severeTickets = overtimeTickets.filter(item => item.overtimeLevel === 'severe')
  overtimeCount.value = overtimeTickets.length
  severeOvertimeCount.value = severeTickets.length

  const overtimeFingerprint = overtimeTickets
    .map(item => `${item.id}-${item.overtimeMinutes}`)
    .join(',')

  if (
    notify &&
    overtimeTickets.length > 0 &&
    overtimeFingerprint &&
    overtimeFingerprint !== lastOvertimeFingerprint.value
  ) {
    const severeText = severeTickets.length > 0 ? `，其中 ${severeTickets.length} 个严重超时` : ''
    ElMessage.warning(`超时提醒：当前 ${overtimeTickets.length} 个超时工单${severeText}`)
  }

  lastOvertimeFingerprint.value = overtimeFingerprint
}

const getVisibleTickets = list => {
  if (!filters.overtimeOnly) {
    return list
  }
  return list.filter(item => item.isOvertime)
}

const handleFilterChange = async () => {
  const visible = getVisibleTickets(tickets.value)
  if (!visible.length) {
    activeTicket.value = null
    activeConversation.value = null
    messages.value = []
    return
  }

  const currentId = activeTicket.value?.id
  const matched = visible.find(item => item.id === currentId) || visible[0]
  if (!currentId || currentId !== matched.id) {
    await selectTicket(matched)
  }
}

const fetchTickets = async (options = {}) => {
  const notify = !!(options && typeof options === 'object' && options.notify)
  const res = await getImSupportTickets({
    page: 1,
    size: 100,
    ticketStatus: filters.ticketStatus || undefined,
  })
  const pageData = normalizePageData(res)
  const nextTickets = pageData.records.map(decorateTicket)
  tickets.value = nextTickets
  syncOvertimeStats(nextTickets, { notify })

  const visibleTickets = getVisibleTickets(nextTickets)
  if (visibleTickets.length) {
    const currentId = activeTicket.value?.id
    const matched = visibleTickets.find(item => item.id === currentId) || visibleTickets[0]
    await selectTicket(matched)
  } else {
    activeTicket.value = null
    activeConversation.value = null
    messages.value = []
  }
}

const refreshMessages = async () => {
  if (!activeConversation.value?.id) return
  const res = await getImConversationMessages(activeConversation.value.id, { page: 1, size: 200 })
  const pageData = normalizePageData(res)
  messages.value = normalizeMessages(pageData.records)
  await scrollToBottom()
}

const selectTicket = async ticket => {
  if (!ticket?.conversationId) return
  activeTicket.value = ticket
  activeConversation.value = await getImConversationDetail(ticket.conversationId)
  assignSupportUserId.value = activeTicket.value.assignedSupportId || activeConversation.value?.supportAgent?.userId
  await Promise.all([
    refreshMessages(),
    markImConversationRead(ticket.conversationId).catch(() => null),
  ])
}

const assignTicket = async () => {
  if (!activeTicket.value?.id) return
  await assignImSupportTicket(activeTicket.value.id, { supportUserId: assignSupportUserId.value })
  ElMessage.success('工单已分配')
  await refreshAll()
}

const updateTicketState = async (ticketStatus, note) => {
  if (!activeTicket.value?.id) return
  await updateImSupportTicketStatus(activeTicket.value.id, {
    ticketStatus,
    note,
  })
  ElMessage.success('工单状态已更新')
  await refreshAll()
}

const markTicketProcessing = async () => {
  await updateTicketState('processing', '官方客服已受理当前工单。')
}

const markTicketResolved = async () => {
  await updateTicketState('resolved', '官方客服已确认问题处理完成，工单归档。')
}

const reopenTicket = async () => {
  await updateTicketState('processing', '工单已重新开启，官方客服继续跟进。')
}

const toggleConversationStatus = async () => {
  if (!activeConversation.value?.id) return
  const targetStatus = activeConversation.value.status === 'closed' ? 'open' : 'closed'
  await updateImConversationStatus(activeConversation.value.id, {
    status: targetStatus,
    note: targetStatus === 'closed' ? '会话已由官方客服关闭。' : '会话已由官方客服重新开启。',
  })
  ElMessage.success(targetStatus === 'closed' ? '会话已关闭' : '会话已开启')
  await fetchTickets()
}

const applyQuickReply = content => {
  draft.value = content
}

const handleSend = async () => {
  const text = draft.value.trim()
  if (!text || !activeConversation.value?.id || sending.value) return
  sending.value = true
  try {
    await sendImMessage(activeConversation.value.id, {
      content: text,
      messageType: 'text',
      requestId: buildRequestId('pc'),
    })
    draft.value = ''
    await Promise.all([refreshMessages(), fetchTickets()])
    ElMessage.success('已发送')
  } finally {
    sending.value = false
  }
}

const openAgentDialog = () => {
  agentForm.userId = ''
  agentForm.displayName = ''
  agentDialogVisible.value = true
}

const saveAgent = async () => {
  if (!agentForm.userId.trim()) {
    return ElMessage.warning('请填写用户ID')
  }
  await saveImSupportAgent({
    userId: Number(agentForm.userId),
    displayName: agentForm.displayName.trim(),
    onlineStatus: 1,
    enabled: 1,
    agentType: 'official',
  })
  agentDialogVisible.value = false
  ElMessage.success('客服席位已保存')
  await fetchAgents()
}

const refreshAll = async () => {
  await Promise.all([fetchAgents(), fetchTickets({ notify: false }), fetchMetrics()])
}

const refreshByImEvent = async payload => {
  const eventConversationId = Number(payload?.conversationId || 0)
  const currentConversationId = Number(activeConversation.value?.id || 0)
  const tasks = [
    fetchTickets({ notify: false }),
    fetchMetrics(),
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

const startTicketPolling = () => {
  if (ticketPollingTimer) {
    clearInterval(ticketPollingTimer)
  }
  ticketPollingTimer = setInterval(() => {
    fetchTickets({ notify: true }).catch(() => null)
  }, TICKET_POLLING_INTERVAL_MS)
}

const stopTicketPolling = () => {
  if (ticketPollingTimer) {
    clearInterval(ticketPollingTimer)
    ticketPollingTimer = null
  }
}

onMounted(() => {
  refreshAll()
  setupImRealtimeSubscription()
  startTicketPolling()
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
  stopTicketPolling()
})
</script>

<style scoped>
.support-page {
  --support-workspace-height: clamp(720px, calc(100vh - 240px), 900px);
}

.support-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.support-queue,
.support-chat-shell {
  min-height: var(--support-workspace-height);
}

.support-queue {
  display: flex;
  flex-direction: column;
}

.support-ticket-list,
.support-agent-list {
  min-height: 0;
  overflow-y: auto;
  padding-right: 2px;
}

.support-ticket-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.support-agent-list {
  max-height: 168px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.support-chat-shell {
  overflow: hidden;
}

.support-chat-panel {
  height: var(--support-workspace-height);
  min-height: 720px;
}

.support-chat-header,
.support-reply-box {
  flex-shrink: 0;
}

.support-message-list {
  background:
    radial-gradient(circle at 18px 18px, rgba(148, 163, 184, 0.12) 1px, transparent 1px),
    linear-gradient(180deg, rgba(248, 250, 252, 0.86), rgba(255, 255, 255, 0.96));
  background-size: 28px 28px, 100% 100%;
}

.support-message-bubble {
  word-break: break-word;
}

.support-chat-actions :deep(.el-button),
.support-chat-actions :deep(.el-select) {
  margin-left: 0;
}

.support-context-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.support-context-item {
  min-width: 0;
  border-radius: 14px;
  background: rgba(248, 250, 252, 0.92);
  padding: 10px 12px;
  font-size: 12px;
}

.support-context-item--ticket {
  background: rgba(255, 251, 235, 0.92);
}

.support-context-item--ai {
  background: rgba(238, 242, 255, 0.92);
}

.support-context-label {
  color: #64748b;
}

.support-context-title {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
  color: #0f172a;
}

.support-context-meta {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #64748b;
}

@media (max-height: 760px) {
  .support-page {
    --support-workspace-height: calc(100vh - 180px);
  }

  .support-chat-panel {
    min-height: 620px;
  }
}

@media (max-width: 1180px) {
  .support-workspace {
    grid-template-columns: 1fr;
  }

  .support-queue {
    min-height: auto;
  }

  .support-ticket-list {
    max-height: 360px;
  }
}

@media (max-width: 860px) {
  .support-context-strip {
    grid-template-columns: 1fr;
  }
}

:global(html.dark) .support-message-list {
  background:
    radial-gradient(circle at 18px 18px, rgba(71, 85, 105, 0.26) 1px, transparent 1px),
    linear-gradient(180deg, rgba(15, 23, 42, 0.68), rgba(15, 23, 42, 0.92));
  background-size: 28px 28px, 100% 100%;
}

:global(html.dark) .support-context-item {
  background: rgba(30, 41, 59, 0.82);
}

:global(html.dark) .support-context-item--ticket {
  background: rgba(120, 53, 15, 0.28);
}

:global(html.dark) .support-context-item--ai {
  background: rgba(49, 46, 129, 0.34);
}

:global(html.dark) .support-context-title {
  color: #f8fafc;
}

:global(html.dark) .support-context-label,
:global(html.dark) .support-context-meta {
  color: #94a3b8;
}
</style>
