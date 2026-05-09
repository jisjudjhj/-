<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg flex flex-wrap gap-4 justify-between items-center">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">轮播图管理</h3>
      <el-button type="primary" @click="openDialog(null)">
        <el-icon class="mr-1"><Plus /></el-icon>新增轮播图
      </el-button>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <el-table v-loading="loading" :data="banners" class="!bg-transparent custom-table"
                :header-cell-style="{ background: 'transparent', color: 'inherit' }"
                :row-style="{ background: 'transparent' }">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="图片" width="200">
          <template #default="{ row }">
            <el-image
              :src="resolveBannerImage(row.title, row.image)"
              fit="cover"
              class="w-40 h-20 rounded-lg"
              :preview-src-list="[resolveBannerImage(row.title, row.image)]"
            />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" />
        <el-table-column label="链接类型" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ linkTypeMap[row.linkType] || row.linkType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="linkValue" label="链接值" min-width="140" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)"
                       active-text="启用" inactive-text="停用" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingBanner ? '编辑轮播图' : '新增轮播图'" width="min(92vw, 500px)">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="输入标题" />
        </el-form-item>
        <el-form-item label="图片URL">
          <el-input v-model="form.image" placeholder="输入图片链接" />
        </el-form-item>
        <el-form-item label="链接类型">
          <el-select v-model="form.linkType" placeholder="选择类型">
            <el-option label="商品详情" value="product" />
            <el-option label="分类页" value="category" />
            <el-option label="外部链接" value="url" />
            <el-option label="无跳转" value="none" />
          </el-select>
        </el-form-item>
        <el-form-item label="链接值">
          <el-input v-model="form.linkValue" placeholder="商品ID / 分类ID / URL" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.statusBool" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getAdminBanners,
  createAdminBanner,
  updateAdminBanner,
} from '../../api/admin'
import { resolveBannerImage } from '../../utils/bannerImage'

const loading = ref(false)
const saving = ref(false)
const banners = ref([])
const dialogVisible = ref(false)
const editingBanner = ref(null)
const linkTypeMap = { product: '商品详情', category: '分类页', url: '外部链接', none: '无跳转' }

const form = ref({ title: '', image: '', linkType: 'product', linkValue: '', sortOrder: 0, statusBool: true })

async function fetchBanners() {
  loading.value = true
  try {
    const res = await getAdminBanners()
    banners.value = Array.isArray(res) ? res : []
  } catch (e) { console.error(e) }
  loading.value = false
}

function openDialog(banner) {
  editingBanner.value = banner
  if (banner) {
    form.value = { title: banner.title, image: banner.image, linkType: banner.linkType || 'none',
      linkValue: banner.linkValue || '', sortOrder: banner.sortOrder || 0, statusBool: banner.status === 1 }
  } else {
    form.value = { title: '', image: '', linkType: 'product', linkValue: '', sortOrder: 0, statusBool: true }
  }
  dialogVisible.value = true
}

async function handleSave() {
  saving.value = true
  const payload = { ...form.value, status: form.value.statusBool ? 1 : 0 }
  delete payload.statusBool
  try {
    if (editingBanner.value) {
      await updateAdminBanner(editingBanner.value.id, payload)
      ElMessage.success('更新成功')
    } else {
      await createAdminBanner(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchBanners()
  } catch (e) { ElMessage.error('操作失败') }
  saving.value = false
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  await updateAdminBanner(row.id, { ...row, status: newStatus })
  ElMessage.success(newStatus === 1 ? '已启用' : '已停用')
  fetchBanners()
}

onMounted(fetchBanners)
</script>
