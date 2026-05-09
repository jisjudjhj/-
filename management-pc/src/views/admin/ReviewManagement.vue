<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex justify-between items-center">
      <div class="flex gap-4">
        <el-select v-model="statusFilter" placeholder="审核状态" class="w-36" clearable @change="handleFilterChange">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
      </div>
    </div>

    <div v-loading="loading" class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <div
        v-for="review in reviews"
        :key="review.id"
        class="bg-white/40 dark:bg-gray-800/40 rounded-2xl p-6 border border-gray-200/50 dark:border-gray-700/50 hover:shadow-md transition-shadow"
      >
        <div class="flex justify-between items-start gap-4 mb-4">
          <div class="flex items-center gap-3">
            <el-avatar :size="42" :src="review.avatar">
              {{ (review.username || '?').charAt(0) }}
            </el-avatar>
            <div>
              <div class="font-medium text-gray-900 dark:text-gray-100">{{ review.username || '匿名用户' }}</div>
              <div class="text-xs text-gray-500">{{ review.createTime }}</div>
            </div>
          </div>
          <el-tag
            :type="review.status === 1 ? 'success' : review.status === 2 ? 'danger' : 'warning'"
            class="!rounded-full"
          >
            {{ getLabel(REVIEW_STATUS_MAP, review.status) }}
          </el-tag>
        </div>

        <div class="mb-3">
          <el-rate :model-value="review.rating" disabled text-color="#f59e0b" />
        </div>

        <p class="text-gray-700 dark:text-gray-300 text-sm leading-6 mb-4">
          {{ review.content || '暂无评价内容' }}
        </p>

        <div class="flex flex-wrap gap-3 mb-4" v-if="review.images?.length">
          <el-image
            v-for="(img, index) in review.images"
            :key="`${review.id}-${index}`"
            :src="img"
            :preview-src-list="review.images"
            fit="cover"
            class="w-16 h-16 rounded-xl overflow-hidden border border-gray-200 dark:border-gray-700"
          />
        </div>

        <div class="bg-gray-50/50 dark:bg-gray-900/50 rounded-lg p-3 text-sm flex items-center gap-3">
          <div class="w-10 h-10 rounded bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
            <el-icon class="text-gray-400"><Goods /></el-icon>
          </div>
          <div class="flex-1 min-w-0 truncate text-gray-600 dark:text-gray-400">
            {{ review.productName || '未知商品' }}
          </div>
        </div>

        <div class="mt-4 pt-4 border-t border-gray-200/50 dark:border-gray-700/50 flex justify-end gap-3">
          <template v-if="review.status === 0">
            <el-button size="small" @click="handleUpdateStatus(review, 1)">通过</el-button>
            <el-button size="small" type="warning" plain @click="handleUpdateStatus(review, 2)">拒绝</el-button>
          </template>
        </div>
      </div>
    </div>

    <div class="flex justify-end">
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
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getAdminReviews, updateAdminReviewStatus } from '../../api/admin'
import { getLabel, REVIEW_STATUS_MAP } from '../../utils/status'

const loading = ref(false)
const statusFilter = ref(null)
const reviews = ref([])

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminReviews({
      page: pagination.page,
      size: pagination.size,
      status: statusFilter.value ?? undefined,
    })
    reviews.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  pagination.page = 1
  fetchList()
}

const handleUpdateStatus = async (review, status) => {
  await updateAdminReviewStatus(review.id, status)
  ElMessage.success(`评价已${status === 1 ? '通过' : '拒绝'}`)
  await fetchList()
  window.dispatchEvent(new Event('admin-workbench-refresh'))
}

onMounted(fetchList)
</script>
