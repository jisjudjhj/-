<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <div class="flex flex-wrap gap-4 flex-1 min-w-0 sm:min-w-[300px]">
        <el-input
          v-model="searchQuery"
          placeholder="搜索商品名称或描述"
          class="w-full sm:max-w-xs !bg-transparent"
          clearable
          @keyup.enter="fetchList"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态" class="w-full sm:w-32" clearable>
          <el-option
            v-for="item in PRODUCT_STATUS_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-select v-model="categoryFilter" placeholder="分类" class="w-full sm:w-44" clearable>
          <el-option
            v-for="item in categoryOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-button @click="fetchList">查询</el-button>
      </div>
      <el-button type="primary" class="!rounded-xl shadow-lg shadow-blue-500/30" @click="openCreateDialog">
        <el-icon class="mr-2"><Plus /></el-icon>
        添加商品
      </el-button>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-visible">
      <div class="px-6 pt-4 text-sm text-gray-500 dark:text-gray-400">
        当前按 ID 倒序显示，首条为最新商品
      </div>
      <div class="desktop-table-scroll px-4 pb-2">
        <el-table
          v-loading="loading"
          :data="tableData"
          style="width: 100%"
          class="!bg-transparent custom-table"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column label="商品信息" :min-width="isCompactDesktop ? 190 : 280">
          <template #default="{ row, $index }">
            <div class="flex items-center gap-4">
              <div class="w-14 h-14 rounded-xl bg-gray-200 dark:bg-gray-700 overflow-hidden flex items-center justify-center">
                <img v-if="row.image" :src="row.image" class="w-full h-full object-cover" />
                <el-icon v-else class="text-gray-400 text-xl"><Picture /></el-icon>
              </div>
              <div class="min-w-0">
                <div class="flex items-center gap-2 min-w-0">
                  <div class="font-medium text-gray-900 dark:text-gray-100 truncate">{{ row.name }}</div>
                  <el-tag v-if="isLatestRow($index)" type="danger" effect="dark" size="small">最新商品</el-tag>
                </div>
                <div class="text-sm text-gray-500 dark:text-gray-400 truncate">{{ row.description || '暂无描述' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" :min-width="isCompactDesktop ? 84 : 140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ categoryNameMap[row.categoryId] || row.categoryName || '未分类' }}
          </template>
        </el-table-column>
        <el-table-column label="价格" :width="isCompactDesktop ? 90 : 120">
          <template #default="{ row }">
            ￥{{ Number(row.price || 0).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column label="库存" :width="isCompactDesktop ? 72 : 100" prop="stock" />
        <el-table-column label="销量" :width="isCompactDesktop ? 72 : 100" prop="salesCount" />
        <el-table-column label="状态" :width="isCompactDesktop ? 84 : 120">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" class="!rounded-full">
              {{ getLabel(PRODUCT_STATUS_MAP, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          :width="isCompactDesktop ? 96 : 160"
          align="right"
          :fixed="isCompactDesktop ? false : 'right'"
        >
          <template #default="{ row }">
            <div class="product-row-actions">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button size="small" @click="toggleStatus(row)">
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>
      </div>

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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增商品' : '编辑商品'"
      width="min(92vw, 720px)"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入商品描述" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类" class="w-full">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <el-form-item label="销售价" prop="price">
            <el-input-number v-model="form.price" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item label="原价" prop="originalPrice">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item label="库存" prop="stock">
            <el-input-number v-model="form.stock" :min="0" class="!w-full" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="form.status" class="w-full">
              <el-option
                v-for="item in PRODUCT_STATUS_OPTIONS"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="form.tagsText" placeholder="多个标签请用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="主图">
          <div class="flex flex-col gap-4 md:flex-row md:items-center">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              accept="image/*"
            >
              <el-button>上传图片</el-button>
            </el-upload>
            <el-input v-model="form.image" placeholder="或直接粘贴图片 URL" />
          </div>
          <div v-if="form.image" class="mt-3">
            <img :src="form.image" class="w-24 h-24 rounded-xl object-cover border border-gray-200 dark:border-gray-700" />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdminProducts,
  createAdminProduct,
  updateAdminProduct,
  updateAdminProductStatus,
} from '../../api/admin'
import { getProductCategories } from '../../api/products'
import { uploadImage } from '../../api/upload'
import { getLabel, PRODUCT_STATUS_MAP, PRODUCT_STATUS_OPTIONS } from '../../utils/status'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)

const searchQuery = ref('')
const statusFilter = ref(null)
const categoryFilter = ref(null)
const tableData = ref([])
const categories = ref([])
const compactDesktopBreakpoint = 1180
const isCompactDesktop = ref(typeof window !== 'undefined' ? window.innerWidth < compactDesktopBreakpoint : false)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const form = reactive({
  id: null,
  name: '',
  description: '',
  categoryId: null,
  price: 0,
  originalPrice: 0,
  stock: 0,
  status: 1,
  image: '',
  tagsText: '',
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入商品描述', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入销售价', trigger: 'change' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'change' }],
}

const categoryOptions = computed(() => {
  const flat = []
  const walk = (list, prefix = '') => {
    list.forEach(item => {
      flat.push({
        label: `${prefix}${item.name}`,
        value: item.id,
      })
      if (item.children?.length) {
        walk(item.children, `${prefix}${item.name} / `)
      }
    })
  }
  walk(categories.value)
  return flat
})

const categoryNameMap = computed(() => {
  return categoryOptions.value.reduce((acc, item) => {
    acc[item.value] = item.label
    return acc
  }, {})
})

const syncCompactDesktop = () => {
  isCompactDesktop.value = window.innerWidth < compactDesktopBreakpoint
}

const resetForm = () => {
  form.id = null
  form.name = ''
  form.description = ''
  form.categoryId = null
  form.price = 0
  form.originalPrice = 0
  form.stock = 0
  form.status = 1
  form.image = ''
  form.tagsText = ''
}

const fetchCategories = async () => {
  try {
    const res = await getProductCategories()
    categories.value = res || []
  } catch {
    categories.value = []
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getAdminProducts({
      page: pagination.page,
      size: pagination.size,
      keyword: searchQuery.value || undefined,
      categoryId: categoryFilter.value || undefined,
      status: statusFilter.value ?? undefined,
    })
    tableData.value = res?.records || []
    pagination.total = res?.total || 0
  } finally {
    loading.value = false
  }
}

const isLatestRow = index => pagination.page === 1 && index === 0

const openCreateDialog = () => {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}

const openEditDialog = row => {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name || ''
  form.description = row.description || ''
  form.categoryId = row.categoryId || null
  form.price = Number(row.price || 0)
  form.originalPrice = Number(row.originalPrice || 0)
  form.stock = row.stock ?? 0
  form.status = row.status ?? 1
  form.image = row.image || ''
  form.tagsText = Array.isArray(row.tags) ? row.tags.join(',') : ''
  dialogVisible.value = true
}

const buildPayload = () => {
  const tags = form.tagsText
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)

  return {
    name: form.name,
    description: form.description,
    categoryId: form.categoryId,
    price: form.price,
    originalPrice: form.originalPrice || form.price,
    stock: form.stock,
    image: form.image,
    images: form.image ? [form.image] : [],
    tags,
    status: form.status,
  }
}

const submitForm = async () => {
  if (!formRef.value || submitting.value) return

  await formRef.value.validate()
  submitting.value = true

  try {
    const payload = buildPayload()
    if (dialogMode.value === 'create') {
      const res = await createAdminProduct(payload)
      const newId = res?.id
      if (newId && form.status !== 1) {
        await updateAdminProductStatus(newId, form.status)
      }
      ElMessage.success('商品创建成功')
    } else {
      await updateAdminProduct(form.id, payload)
      ElMessage.success('商品更新成功')
    }
    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async row => {
  const targetStatus = row.status === 1 ? 0 : 1
  await updateAdminProductStatus(row.id, targetStatus)
  ElMessage.success(`商品已${targetStatus === 1 ? '上架' : '下架'}`)
  await fetchList()
}

const handleUpload = async option => {
  try {
    const res = await uploadImage(option.file)
    form.image = res?.url || ''
    option.onSuccess(res)
  } catch (error) {
    option.onError(error)
  }
}

onMounted(async () => {
  syncCompactDesktop()
  window.addEventListener('resize', syncCompactDesktop)
  await fetchCategories()
  await fetchList()
})

onUnmounted(() => {
  window.removeEventListener('resize', syncCompactDesktop)
})
</script>

<style scoped>
/* Glassmorphism Table Overrides */
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

.product-row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
