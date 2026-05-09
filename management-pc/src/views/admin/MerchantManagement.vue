<template>
  <div class="space-y-6">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <StatCard title="商家总数" :value="stats.totalMerchants || 0" icon="Shop" color="from-blue-400 to-indigo-500" />
      <StatCard title="活跃商家" :value="stats.activeMerchants || 0" icon="Check" color="from-emerald-400 to-teal-500" />
      <StatCard title="商品总数" :value="formatNum(stats.totalProducts || 0)" icon="Goods" color="from-purple-400 to-pink-500" />
      <StatCard title="总营收" :value="'￥' + formatNum(stats.totalRevenue || 0)" icon="Money" color="from-amber-400 to-orange-500" />
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <div class="p-6 pb-4 flex flex-wrap gap-4 items-center">
        <el-input
          v-model="keyword"
          placeholder="搜索商家用户名、昵称或手机号"
          clearable
          class="w-full sm:w-72"
          @keyup.enter="loadMerchants(1)"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="statusFilter" clearable placeholder="状态" class="w-full sm:w-32">
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadMerchants(1)">查询</el-button>
        <el-button type="success" plain @click="openCreateDialog">新增商家</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="merchants"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="商家信息" min-width="220">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="40" :src="row.avatar">
                {{ (row.nickname || row.username || '?').charAt(0) }}
              </el-avatar>
              <div class="min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">{{ row.nickname || row.username }}</div>
                <div class="text-xs text-gray-500">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="商品数" width="90" align="center">
          <template #default="{ row }">
            <span class="font-semibold">{{ row.productCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总销量" width="100" align="center">
          <template #default="{ row }">
            <span class="text-blue-600 font-semibold">{{ formatNum(row.totalSales || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="总营收" width="130" align="right">
          <template #default="{ row }">
            <span class="text-emerald-600 font-semibold">￥{{ formatNum(row.totalRevenue || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="160">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.createTime?.substring(0, 16) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleMerchantStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="p-4 flex justify-center" v-if="total > pageSize">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="loadMerchants"
        />
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新增商家" width="min(92vw, 520px)" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" maxlength="11" placeholder="请输入商家手机号" />
        </el-form-item>
        <el-form-item label="登录密码">
          <el-input v-model="createForm.password" type="password" show-password placeholder="请输入6-20位密码" />
        </el-form-item>
        <el-form-item label="商家昵称">
          <el-input v-model="createForm.nickname" maxlength="30" placeholder="可选，不填则自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="商家详情" width="min(92vw, 600px)">
      <div v-if="currentMerchant" class="space-y-4">
        <div class="flex items-center gap-4 p-4 bg-gray-50 dark:bg-gray-700/50 rounded-xl">
          <el-avatar :size="64" :src="currentMerchant.avatar">
            {{ (currentMerchant.nickname || currentMerchant.username || '?').charAt(0) }}
          </el-avatar>
          <div class="min-w-0">
            <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 break-words">{{ currentMerchant.nickname || currentMerchant.username }}</h3>
            <p class="text-sm text-gray-500">{{ currentMerchant.phone }}</p>
          </div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div class="p-4 bg-blue-50 dark:bg-blue-900/30 rounded-xl text-center">
            <div class="text-2xl font-bold text-blue-600">{{ currentMerchant.productCount || 0 }}</div>
            <div class="text-xs text-gray-500 mt-1">商品数量</div>
          </div>
          <div class="p-4 bg-green-50 dark:bg-green-900/30 rounded-xl text-center">
            <div class="text-2xl font-bold text-green-600">{{ formatNum(currentMerchant.totalSales || 0) }}</div>
            <div class="text-xs text-gray-500 mt-1">总销量</div>
          </div>
          <div class="p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl text-center">
            <div class="text-2xl font-bold text-purple-600">￥{{ formatNum(currentMerchant.totalRevenue || 0) }}</div>
            <div class="text-xs text-gray-500 mt-1">总营收</div>
          </div>
          <div class="p-4 bg-amber-50 dark:bg-amber-900/30 rounded-xl text-center">
            <div class="text-2xl font-bold text-amber-600">{{ currentMerchant.orderCount || 0 }}</div>
            <div class="text-xs text-gray-500 mt-1">关联订单</div>
          </div>
        </div>
        <div class="text-sm text-gray-500">
          <p>注册时间: {{ currentMerchant.createTime }}</p>
          <p>
            账号状态:
            <el-tag size="small" :type="currentMerchant.status === 1 ? 'success' : 'danger'">
              {{ currentMerchant.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createAdminMerchant,
  getAdminMerchantStats,
  getAdminMerchants,
  updateAdminUserStatus,
} from '../../api/admin'
import StatCard from '../../components/StatCard.vue'

const loading = ref(false)
const merchants = ref([])
const stats = ref({})
const keyword = ref('')
const statusFilter = ref(null)
const currentPage = ref(1)
const pageSize = 10
const total = ref(0)
const detailVisible = ref(false)
const currentMerchant = ref(null)

const createVisible = ref(false)
const creating = ref(false)
const createForm = reactive({
  phone: '',
  password: '',
  nickname: ''
})

function formatNum(n) {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN')
}

function resetCreateForm() {
  createForm.phone = ''
  createForm.password = ''
  createForm.nickname = ''
}

async function loadStats() {
  try {
    stats.value = await getAdminMerchantStats() || {}
  } catch (e) {
    console.error(e)
  }
}

async function loadMerchants(page = 1) {
  loading.value = true
  currentPage.value = page
  try {
    const params = { page, size: pageSize }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value != null) params.status = statusFilter.value
    const res = await getAdminMerchants(params)
    merchants.value = res?.records || []
    total.value = res?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  resetCreateForm()
  createVisible.value = true
}

async function submitCreate() {
  if (!/^1[3-9]\d{9}$/.test(createForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!createForm.password || createForm.password.length < 6 || createForm.password.length > 20) {
    ElMessage.warning('请输入6-20位登录密码')
    return
  }
  creating.value = true
  try {
    await createAdminMerchant({
      phone: createForm.phone,
      password: createForm.password,
      nickname: createForm.nickname || undefined
    })
    ElMessage.success('商家创建成功')
    createVisible.value = false
    await Promise.all([loadStats(), loadMerchants(1)])
  } catch (e) {
    console.error(e)
  } finally {
    creating.value = false
  }
}

async function toggleMerchantStatus(row) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}商家 ${row.nickname || row.username} 吗？`, '提示', { type: 'warning' })
  const newStatus = row.status === 1 ? 0 : 1
  await updateAdminUserStatus(row.id, newStatus)
  ElMessage.success(`${action}成功`)
  await Promise.all([loadMerchants(currentPage.value), loadStats()])
}

function viewDetail(row) {
  currentMerchant.value = row
  detailVisible.value = true
}

onMounted(() => {
  loadStats()
  loadMerchants()
})
</script>
