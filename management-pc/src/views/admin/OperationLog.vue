<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <div class="flex flex-wrap gap-4 flex-1 min-w-0 sm:min-w-[300px]">
        <el-input
          v-model="searchQuery"
          placeholder="搜索操作人、模块或操作内容"
          class="w-full sm:max-w-xs !bg-transparent"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-input
          v-model="moduleQuery"
          placeholder="模块名，例如 商品管理"
          class="w-full sm:max-w-xs !bg-transparent"
          clearable
          @keyup.enter="handleSearch"
        />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="w-full lg:!w-[360px] !bg-transparent"
        />
        <el-button @click="handleSearch">查询</el-button>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <el-table
        v-loading="loading"
        :data="logs"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="createTime" label="操作时间" width="180" />
        <el-table-column label="操作人" width="160">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <el-avatar :size="24" class="bg-blue-500 text-xs">{{ (row.username || '?').charAt(0) }}</el-avatar>
              <span class="font-medium">{{ row.username || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag size="small" class="!rounded-full">{{ getLabel(ROLE_MAP, row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="130">
          <template #default="{ row }">
            <el-tag size="small" type="info" class="!rounded">{{ row.module }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="操作内容" min-width="220" />
        <el-table-column prop="ip" label="IP 地址" width="140" />
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ row.costTime || 0 }} ms
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-icon :class="row.status === 1 ? 'text-emerald-500' : 'text-rose-500'" class="text-lg">
              <component :is="row.status === 1 ? 'CircleCheckFilled' : 'CircleCloseFilled'" />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column label="异常信息" min-width="220">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.errorMsg || '-' }}</span>
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
import { getAdminLogs } from '../../api/admin'
import { getLabel, ROLE_MAP } from '../../utils/status'

const loading = ref(false)
const searchQuery = ref('')
const moduleQuery = ref('')
const dateRange = ref([])
const logs = ref([])

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0,
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminLogs({
      page: pagination.page,
      size: pagination.size,
      keyword: searchQuery.value || undefined,
      module: moduleQuery.value || undefined,
      startTime: dateRange.value?.[0] || undefined,
      endTime: dateRange.value?.[1] || undefined,
    })
    logs.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  fetchList()
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
:deep(.el-table tr) {
  background-color: transparent !important;
}
:deep(.el-table td.el-table__cell) {
  border-bottom-color: var(--el-table-border-color);
}
</style>
