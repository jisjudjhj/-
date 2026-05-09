<template>
  <div class="space-y-6">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <StatCard v-for="(stat, index) in statsCards" :key="index"
                :title="stat.title" :value="stat.value" :sub="stat.sub"
                :icon="stat.icon" :color="stat.color" />
    </div>

    <!-- Tab 切换 -->
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <el-tabs v-model="activeTab" class="px-6 pt-4">
        <el-tab-pane label="用户余额" name="balances" />
        <el-tab-pane label="交易记录" name="transactions" />
      </el-tabs>

      <!-- 用户余额 Tab -->
      <div v-show="activeTab === 'balances'" class="p-6 pt-2">
        <div class="flex flex-wrap items-center gap-4 mb-4">
          <el-input v-model="balanceKeyword" placeholder="搜索用户名/昵称/手机号" clearable
                    style="max-width: 300px" @keyup.enter="loadUserBalances(1)" />
          <el-button type="primary" @click="loadUserBalances(1)">搜索</el-button>
        </div>

        <el-table :data="balanceList" stripe border class="w-full" v-loading="balanceLoading">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="用户" min-width="180">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <el-avatar :size="32" :src="row.avatar" v-if="row.avatar">
                  {{ (row.nickname || row.username || '').charAt(0) }}
                </el-avatar>
                <div>
                  <div class="font-medium text-gray-800 dark:text-gray-200">{{ row.nickname || row.username }}</div>
                  <div class="text-xs text-gray-400">{{ row.phone }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="余额" width="150" sortable>
            <template #default="{ row }">
              <span class="text-lg font-bold text-emerald-600 dark:text-emerald-400">
                ¥{{ Number(row.balance).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button size="small" type="primary" @click="openAdjustDialog(row)">调整余额</el-button>
              <el-button size="small" @click="viewUserTransactions(row)">流水</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="balancePage"
            :page-size="15"
            :total="balanceTotal"
            layout="total, prev, pager, next"
            @current-change="loadUserBalances"
          />
        </div>
      </div>

      <!-- 交易记录 Tab -->
      <div v-show="activeTab === 'transactions'" class="p-6 pt-2">
        <div class="flex items-center gap-4 mb-4 flex-wrap">
          <el-select v-model="txTypeFilter" placeholder="交易类型" clearable style="width: 140px">
            <el-option label="充值" value="recharge" />
            <el-option label="支付" value="pay" />
            <el-option label="退款" value="refund" />
          </el-select>
          <el-input v-model="txUserIdFilter" placeholder="用户ID" clearable style="width: 120px" />
          <el-input v-model="txKeyword" placeholder="搜索描述" clearable style="width: 200px" />
          <el-button type="primary" @click="loadTransactions(1)">搜索</el-button>
        </div>

        <el-table :data="txList" stripe border class="w-full" v-loading="txLoading">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="用户" min-width="150">
            <template #default="{ row }">
              <div>
                <div class="font-medium">{{ row.username || '-' }}</div>
                <div class="text-xs text-gray-400">ID: {{ row.userId }}{{ row.phone ? ' | ' + row.phone : '' }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="txTypeTag(row.type)" size="small">{{ txTypeLabel(row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="130">
            <template #default="{ row }">
              <span :class="Number(row.amount) >= 0 ? 'text-emerald-600 font-bold' : 'text-red-500 font-bold'">
                {{ Number(row.amount) >= 0 ? '+' : '' }}{{ Number(row.amount).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变更前" width="110">
            <template #default="{ row }">¥{{ Number(row.balanceBefore).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column label="变更后" width="110">
            <template #default="{ row }">¥{{ Number(row.balanceAfter).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
          <el-table-column prop="orderNo" label="关联订单" width="160" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>

        <div class="mt-4 flex justify-end">
          <el-pagination
            v-model:current-page="txPage"
            :page-size="15"
            :total="txTotal"
            layout="total, prev, pager, next"
            @current-change="loadTransactions"
          />
        </div>
      </div>
    </div>

    <!-- 调整余额弹窗 -->
    <el-dialog v-model="adjustDialogVisible" title="调整用户余额" width="min(92vw, 460px)" destroy-on-close>
      <div class="space-y-4">
        <div class="bg-gray-50 dark:bg-gray-700/50 p-4 rounded-lg">
          <p class="text-sm text-gray-500 dark:text-gray-400">目标用户</p>
          <p class="font-bold text-lg">{{ adjustTarget.nickname || adjustTarget.username }}
            <span class="text-sm font-normal text-gray-400 ml-2">ID: {{ adjustTarget.id }}</span>
          </p>
          <p class="text-sm mt-1">当前余额：<span class="text-emerald-600 font-bold">¥{{ Number(adjustTarget.balance || 0).toFixed(2) }}</span></p>
        </div>
        <el-form label-width="80px">
          <el-form-item label="调整金额">
            <el-input-number v-model="adjustAmount" :precision="2" :step="100" placeholder="正数增加，负数扣减" style="width: 100%" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="adjustReason" placeholder="请输入调整原因" />
          </el-form-item>
        </el-form>
        <div v-if="adjustAmount" class="text-center p-3 rounded-lg"
             :class="adjustAmount > 0 ? 'bg-emerald-50 dark:bg-emerald-900/20' : 'bg-red-50 dark:bg-red-900/20'">
          调整后余额：<span class="font-bold text-lg">
            ¥{{ (Number(adjustTarget.balance || 0) + Number(adjustAmount || 0)).toFixed(2) }}
          </span>
        </div>
      </div>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust" :loading="adjustLoading">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminWalletStats,
  getAdminWalletTransactions,
  getAdminUserBalances,
  adjustAdminUserBalance
} from '../../api/admin'
import StatCard from '../../components/StatCard.vue'

const activeTab = ref('balances')
const walletStats = ref({})

const statsCards = computed(() => {
  const s = walletStats.value
  return [
    {
      title: '用户总余额',
      value: `¥${Number(s.totalBalance || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
      icon: 'Wallet',
      color: 'from-emerald-400 to-teal-500'
    },
    {
      title: '累计充值',
      value: `¥${Number(s.totalRecharge || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
      sub: `共 ${s.rechargeCount || 0} 笔`,
      icon: 'Upload',
      color: 'from-blue-400 to-indigo-500'
    },
    {
      title: '累计消费',
      value: `¥${Number(s.totalSpent || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
      sub: `共 ${s.payCount || 0} 笔`,
      icon: 'ShoppingCart',
      color: 'from-purple-400 to-pink-500'
    },
    {
      title: '今日充值',
      value: `¥${Number(s.todayRecharge || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
      sub: `共 ${s.todayRechargeCount || 0} 笔`,
      icon: 'Timer',
      color: 'from-amber-400 to-orange-500'
    }
  ]
})

// 用户余额
const balanceList = ref([])
const balancePage = ref(1)
const balanceTotal = ref(0)
const balanceKeyword = ref('')
const balanceLoading = ref(false)

const loadUserBalances = async (page) => {
  if (page) balancePage.value = page
  balanceLoading.value = true
  try {
    const res = await getAdminUserBalances({
      page: balancePage.value, size: 15, keyword: balanceKeyword.value || undefined
    })
    const d = res || {}
    balanceList.value = d.records || []
    balanceTotal.value = d.total || 0
  } catch (e) { /* ignore */ }
  balanceLoading.value = false
}

// 交易记录
const txList = ref([])
const txPage = ref(1)
const txTotal = ref(0)
const txTypeFilter = ref('')
const txUserIdFilter = ref('')
const txKeyword = ref('')
const txLoading = ref(false)

const loadTransactions = async (page) => {
  if (page) txPage.value = page
  txLoading.value = true
  try {
    const res = await getAdminWalletTransactions({
      page: txPage.value, size: 15,
      type: txTypeFilter.value || undefined,
      userId: txUserIdFilter.value || undefined,
      keyword: txKeyword.value || undefined
    })
    const d = res || {}
    txList.value = d.records || []
    txTotal.value = d.total || 0
  } catch (e) { /* ignore */ }
  txLoading.value = false
}

const txTypeLabel = (type) => {
  const map = { recharge: '充值', pay: '支付', refund: '退款' }
  return map[type] || type
}
const txTypeTag = (type) => {
  const map = { recharge: 'success', pay: 'danger', refund: 'warning' }
  return map[type] || 'info'
}

const viewUserTransactions = (row) => {
  activeTab.value = 'transactions'
  txUserIdFilter.value = String(row.id)
  loadTransactions(1)
}

// 调整余额
const adjustDialogVisible = ref(false)
const adjustTarget = ref({})
const adjustAmount = ref(0)
const adjustReason = ref('')
const adjustLoading = ref(false)

const openAdjustDialog = (row) => {
  adjustTarget.value = { ...row }
  adjustAmount.value = 0
  adjustReason.value = ''
  adjustDialogVisible.value = true
}

const submitAdjust = async () => {
  if (!adjustAmount.value || adjustAmount.value === 0) {
    ElMessage.warning('调整金额不能为0')
    return
  }
  const newBalance = Number(adjustTarget.value.balance || 0) + Number(adjustAmount.value)
  if (newBalance < 0) {
    ElMessage.warning('调整后余额不能为负数')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确认将用户 "${adjustTarget.value.nickname || adjustTarget.value.username}" 的余额${adjustAmount.value > 0 ? '增加' : '扣减'} ¥${Math.abs(adjustAmount.value).toFixed(2)}？`,
      '确认操作',
      { type: 'warning' }
    )
  } catch { return }

  adjustLoading.value = true
  try {
    await adjustAdminUserBalance({
      userId: adjustTarget.value.id,
      amount: adjustAmount.value,
      reason: adjustReason.value || '管理员手动调整'
    })
    ElMessage.success('余额调整成功')
    adjustDialogVisible.value = false
    loadUserBalances()
    loadStats()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '调整失败')
  }
  adjustLoading.value = false
}

const loadStats = async () => {
  try {
    const res = await getAdminWalletStats()
    walletStats.value = res || {}
  } catch (e) { /* ignore */ }
}

onMounted(() => {
  loadStats()
  loadUserBalances(1)
  loadTransactions(1)
})
</script>
