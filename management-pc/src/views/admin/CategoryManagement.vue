<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <el-input
        v-model="searchQuery"
        placeholder="搜索分类名称"
        class="w-full sm:max-w-xs !bg-transparent"
        clearable
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" class="!rounded-xl shadow-lg shadow-blue-500/30" @click="openCreateDialog()">
        <el-icon class="mr-2"><Plus /></el-icon>
        添加分类
      </el-button>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg p-6">
      <el-table
        v-loading="loading"
        :data="filteredCategories"
        row-key="id"
        border
        default-expand-all
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="name" label="分类名称" min-width="220" />
        <el-table-column label="层级" width="120">
          <template #default="{ row }">
            <el-tag :type="isTopLevel(row) ? 'primary' : 'success'" class="!rounded-full">
              {{ isTopLevel(row) ? '一级分类' : '二级分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="图标" min-width="160">
          <template #default="{ row }">
            {{ row.icon || '未设置' }}
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序值" width="120" />
        <el-table-column label="子分类数" width="120">
          <template #default="{ row }">
            {{ row.children?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="right">
          <template #default="{ row }">
            <div class="flex justify-end gap-2">
              <el-button size="small" @click="openCreateDialog(row)">新增子分类</el-button>
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新增分类' : '编辑分类'"
      width="min(92vw, 560px)"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" />
        </el-form-item>
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" class="w-full" clearable placeholder="不选择则为一级分类">
            <el-option label="一级分类" :value="0" />
            <el-option
              v-for="item in parentOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" placeholder="请输入图标名称或 URL" />
        </el-form-item>
        <el-form-item label="排序值">
          <el-input-number v-model="form.sortOrder" :min="0" class="!w-full" />
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAdminCategory, updateAdminCategory } from '../../api/admin'
import { getProductCategories } from '../../api/products'

const loading = ref(false)
const submitting = ref(false)
const searchQuery = ref('')
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const categories = ref([])

const form = reactive({
  id: null,
  name: '',
  parentId: 0,
  icon: '',
  sortOrder: 0,
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

const isTopLevel = row => !row.parentId || row.parentId === 0

const parentOptions = computed(() => {
  return categories.value
    .filter(item => isTopLevel(item))
    .map(item => ({
      label: item.name,
      value: item.id,
    }))
})

const filteredCategories = computed(() => {
  if (!searchQuery.value) return categories.value

  const keyword = searchQuery.value.trim()
  const filterTree = list => {
    return list
      .map(item => {
        const children = filterTree(item.children || [])
        if (item.name.includes(keyword) || children.length) {
          return {
            ...item,
            children,
          }
        }
        return null
      })
      .filter(Boolean)
  }

  return filterTree(categories.value)
})

const resetForm = () => {
  form.id = null
  form.name = ''
  form.parentId = 0
  form.icon = ''
  form.sortOrder = 0
}

const fetchCategories = async () => {
  loading.value = true
  try {
    const res = await getProductCategories()
    categories.value = res || []
  } finally {
    loading.value = false
  }
}

const openCreateDialog = parent => {
  dialogMode.value = 'create'
  resetForm()
  if (parent?.id) {
    form.parentId = parent.id
  }
  dialogVisible.value = true
}

const openEditDialog = row => {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name || ''
  form.parentId = row.parentId || 0
  form.icon = row.icon || ''
  form.sortOrder = row.sortOrder || 0
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value || submitting.value) return

  await formRef.value.validate()
  submitting.value = true

  try {
    const payload = {
      name: form.name,
      parentId: form.parentId || 0,
      icon: form.icon,
      sortOrder: form.sortOrder,
    }

    if (dialogMode.value === 'create') {
      await createAdminCategory(payload)
      ElMessage.success('分类创建成功')
    } else {
      await updateAdminCategory(form.id, payload)
      ElMessage.success('分类更新成功')
    }

    dialogVisible.value = false
    await fetchCategories()
  } finally {
    submitting.value = false
  }
}

onMounted(fetchCategories)
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
:deep(.el-table--border .el-table__inner-wrapper::after),
:deep(.el-table--border::after),
:deep(.el-table--border::before),
:deep(.el-table__inner-wrapper::before) {
  background-color: var(--el-table-border-color);
}
</style>
