<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg p-6">
      <el-table v-loading="loading" :data="reviews" class="!bg-transparent custom-table"
                :header-cell-style="{ background: 'transparent', color: 'inherit' }" :row-style="{ background: 'transparent' }">
        <el-table-column label="商品ID" prop="productId" width="100" />
        <el-table-column label="评分" width="140">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled :max="5" />
          </template>
        </el-table-column>
        <el-table-column label="评论内容" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div>{{ row.content || '-' }}</div>
            <div v-if="row.images && parseImages(row.images).length" class="flex gap-1 mt-1">
              <el-image v-for="(img, i) in parseImages(row.images)" :key="i"
                        :src="img" :preview-src-list="parseImages(row.images)"
                        class="w-8 h-8 rounded" fit="cover" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="回复" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reply" class="text-green-600 dark:text-green-400">{{ row.reply }}</span>
            <span v-else class="text-gray-400">未回复</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="right">
          <template #default="{ row }">
            <el-button v-if="!row.reply" size="small" type="primary" @click="openReplyDialog(row)">回复</el-button>
            <el-tag v-else type="success" size="small" class="!rounded-full">已回复</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination background layout="total, prev, pager, next"
                       :total="pagination.total" :page-size="pagination.size" v-model:current-page="pagination.page" @current-change="fetchList" />
      </div>
    </div>

    <el-dialog v-model="replyDialogVisible" title="回复评论" width="min(92vw, 500px)" destroy-on-close>
      <div v-if="currentReview" class="mb-4 p-4 bg-gray-50/50 dark:bg-gray-700/30 rounded-xl">
        <div class="text-sm text-gray-600 dark:text-gray-300">{{ currentReview.content }}</div>
        <div class="mt-2"><el-rate :model-value="currentReview.rating" disabled :max="5" /></div>
      </div>
      <el-input v-model="replyContent" type="textarea" :rows="4" placeholder="请输入回复内容" />
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMerchantReviews, replyMerchantReview } from '../../api/merchant'

const loading = ref(false)
const submitting = ref(false)
const reviews = ref([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const replyDialogVisible = ref(false)
const currentReview = ref(null)
const replyContent = ref('')

const parseImages = (images) => {
  if (!images) return []
  if (Array.isArray(images)) return images.filter(Boolean)
  if (typeof images === 'string') {
    try { const parsed = JSON.parse(images); if (Array.isArray(parsed)) return parsed.filter(Boolean) } catch {}
    return images.split(',').map(s => s.trim()).filter(Boolean)
  }
  return []
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMerchantReviews({
      page: pagination.page,
      size: pagination.size,
    })
    const data = res || {}
    reviews.value = data.records || []
    pagination.total = data.total || 0
  } finally {
    loading.value = false
  }
}

const openReplyDialog = row => {
  currentReview.value = row
  replyContent.value = ''
  replyDialogVisible.value = true
}

const submitReply = async () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('回复内容不能为空')
    return
  }
  submitting.value = true
  try {
    await replyMerchantReview(currentReview.value.id, replyContent.value.trim())
    ElMessage.success('回复成功')
    replyDialogVisible.value = false
    await fetchList()
    window.dispatchEvent(new Event('merchant-workbench-refresh'))
  } finally {
    submitting.value = false
  }
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
:deep(.el-table th.el-table__cell) { background-color: var(--el-table-header-bg-color) !important; backdrop-filter: blur(10px); }
:deep(.el-table tr) { background-color: transparent !important; }
:deep(.el-table td.el-table__cell) { border-bottom-color: var(--el-table-border-color); }
</style>
