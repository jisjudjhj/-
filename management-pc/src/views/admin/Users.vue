<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <div class="flex flex-wrap gap-4">
        <el-input
          v-model="searchQuery"
          placeholder="搜索用户名、昵称或手机号"
          class="w-80 !bg-transparent"
          clearable
          @keyup.enter="fetchList"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="roleFilter" clearable placeholder="角色" class="w-32">
          <el-option label="管理员" value="admin" />
          <el-option label="商家" value="merchant" />
          <el-option label="普通用户" value="user" />
        </el-select>
        <el-button @click="fetchList">查询</el-button>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <el-table
        v-loading="loading"
        :data="users"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="用户" min-width="240">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="42" :src="row.avatar">
                {{ (row.nickname || row.username || '?').charAt(0) }}
              </el-avatar>
              <div class="min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ row.nickname || row.username }}
                </div>
                <div class="text-sm text-gray-500 truncate">@{{ row.username }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="160" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag class="!rounded-full">
              {{ getLabel(ROLE_MAP, row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" class="!rounded-full">
              {{ getLabel(USER_STATUS_MAP, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="180" />
        <el-table-column label="操作" width="120" align="right" fixed="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <el-button size="small" @click="handleToggleStatus(row)">
                {{ row.status === 1 ? '禁用' : '恢复' }}
              </el-button>
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
import { ElMessage } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus } from '../../api/admin'
import { getLabel, ROLE_MAP, USER_STATUS_MAP } from '../../utils/status'

const loading = ref(false)
const searchQuery = ref('')
const roleFilter = ref('')
const users = ref([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminUsers({
      page: pagination.page,
      size: pagination.size,
      keyword: searchQuery.value || undefined,
      role: roleFilter.value || undefined,
    })
    users.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const handleToggleStatus = async row => {
  const nextStatus = row.status === 1 ? 0 : 1
  await updateAdminUserStatus(row.id, nextStatus)
  ElMessage.success(`用户已${nextStatus === 1 ? '恢复' : '禁用'}`)
  await fetchList()
}

onMounted(fetchList)
</script>
