<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <div class="flex flex-wrap gap-4 items-center">
        <el-select
          v-model="statusFilter"
          class="w-36"
          clearable
          placeholder="审核状态"
          @change="handleFilterChange"
        >
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
        <el-button @click="fetchList">刷新</el-button>
      </div>
      <div class="text-sm text-gray-500 dark:text-gray-400">
        审核用户提交的昵称和头像修改申请
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <el-table
        v-loading="loading"
        :data="records"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="id" label="申请ID" width="100" />
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="text-sm text-gray-800 dark:text-gray-100">
              <div class="font-medium">用户 #{{ row.userId }}</div>
              <div class="text-gray-500 dark:text-gray-400 mt-1">提交时间：{{ formatDate(row.createTime) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前资料" min-width="260">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="44" :src="row.currentAvatar">
                {{ (row.currentNickname || '?').charAt(0) }}
              </el-avatar>
              <div class="min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ row.currentNickname || '未设置昵称' }}
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {{ row.currentAvatar || '未设置头像' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="申请修改为" min-width="260">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="44" :src="row.newAvatar || row.currentAvatar">
                {{ (row.newNickname || row.currentNickname || '?').charAt(0) }}
              </el-avatar>
              <div class="min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ row.newNickname || row.currentNickname || '未设置昵称' }}
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {{ row.newAvatar || '头像未修改' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" class="!rounded-full">
              {{ getLabel(PROFILE_CHANGE_STATUS_MAP, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理结果" min-width="220">
          <template #default="{ row }">
            <div class="text-sm text-gray-700 dark:text-gray-300 space-y-1">
              <div>审核时间：{{ formatDate(row.reviewTime) }}</div>
              <div v-if="row.rejectReason" class="text-red-500 dark:text-red-400 break-words">
                拒绝原因：{{ row.rejectReason }}
              </div>
              <div v-else class="text-gray-400 dark:text-gray-500">
                {{ row.status === 0 ? '等待管理员处理' : '无补充说明' }}
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <template v-if="row.status === 0">
                <el-button size="small" type="primary" @click="handleApprove(row)">通过</el-button>
                <el-button size="small" type="danger" plain @click="handleReject(row)">拒绝</el-button>
              </template>
              <span v-else class="text-sm text-gray-400 dark:text-gray-500">已处理</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="p-4 border-t border-gray-200/50 dark:border-gray-700/50 flex justify-end">
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
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  approveAdminProfileChange,
  getAdminProfileChanges,
  rejectAdminProfileChange,
} from '../../api/admin'
import { getLabel, PROFILE_CHANGE_STATUS_MAP } from '../../utils/status'

const loading = ref(false)
const statusFilter = ref(null)
const records = ref([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const getStatusType = status => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'warning'
}

const formatDate = value => {
  if (!value) return '暂无'
  return String(value).replace('T', ' ').slice(0, 16)
}

const notifyPendingRefresh = () => {
  window.dispatchEvent(new Event('profile-change-pending-refresh'))
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminProfileChanges({
      page: pagination.page,
      size: pagination.size,
      status: statusFilter.value ?? undefined,
    })
    records.value = res?.records || []
    pagination.total = Number(res?.total || 0)
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchList()
}

const handleApprove = async row => {
  try {
    await ElMessageBox.confirm(
      `确认通过用户 #${row.userId} 的资料修改申请吗？`,
      '通过确认',
      { type: 'warning' }
    )
    await approveAdminProfileChange(row.id)
    ElMessage.success('资料修改已通过')
    await fetchList()
    notifyPendingRefresh()
  } catch (error) {
    // 用户取消时不提示
  }
}

const handleReject = async row => {
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入拒绝用户 #${row.userId} 资料修改的原因`,
      '拒绝申请',
      {
        confirmButtonText: '确认拒绝',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：头像不合规、昵称包含敏感词',
      }
    )
    await rejectAdminProfileChange(row.id, value || '不符合规范')
    ElMessage.success('资料修改已拒绝')
    await fetchList()
    notifyPendingRefresh()
  } catch (error) {
    // 用户取消时不提示
  }
}

onMounted(fetchList)
</script>
