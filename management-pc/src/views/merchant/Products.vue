<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索商品名称"
            class="w-56 !bg-transparent"
            clearable
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-select
            v-model="filters.status"
            placeholder="商品状态"
            class="w-32"
            clearable
            @change="handleSearch"
          >
            <el-option label="在售" :value="1" />
            <el-option label="已下架" :value="0" />
          </el-select>
          <el-button @click="handleSearch">搜索</el-button>
        </div>
        <div class="flex flex-wrap gap-3">
          <el-button plain class="!rounded-xl" @click="openMerchantAi">AI 商家助手</el-button>
          <el-button type="primary" class="!rounded-xl shadow-lg shadow-blue-500/30" @click="openCreateDialog">
            <el-icon class="mr-2"><Plus /></el-icon>
            添加商品
          </el-button>
        </div>
      </div>

      <div class="mt-4 rounded-2xl border border-emerald-100 bg-emerald-50/80 px-4 py-3 dark:border-emerald-800/30 dark:bg-emerald-900/10">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">AI 商家助手</div>
            <div class="mt-1 text-xs leading-6 text-gray-500 dark:text-gray-400">
              基于当前商品草稿，生成中文上架标题、卖点、详情描述、标签和客服说明。
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg p-6">
      <div class="mb-4 text-sm text-gray-500 dark:text-gray-400">
        当前按 ID 倒序显示，首条为最新商品。
      </div>

      <div class="desktop-table-scroll">
        <el-table
          v-loading="loading"
          :data="products"
          class="!bg-transparent"
          :header-cell-style="{ background: 'transparent', color: 'inherit' }"
          :row-style="{ background: 'transparent' }"
        >
        <el-table-column label="商品" min-width="260">
          <template #default="{ row, $index }">
            <div class="flex items-center gap-3">
              <div class="flex h-12 w-12 items-center justify-center overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-700">
                <img
                  v-if="row.mainImage || row.image"
                  :src="row.mainImage || row.image"
                  class="h-full w-full object-cover"
                />
                <el-icon v-else class="text-gray-400"><Picture /></el-icon>
              </div>
              <div class="min-w-0">
                <div class="flex min-w-0 items-center gap-2">
                  <div class="max-w-[220px] truncate text-sm font-medium text-gray-800 dark:text-gray-100">
                    {{ row.name }}
                  </div>
                  <el-tag v-if="isLatestRow($index)" type="danger" effect="dark" size="small">最新商品</el-tag>
                </div>
                <div class="text-xs text-gray-500 dark:text-gray-400">ID: {{ row.id }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="120">
          <template #default="{ row }">¥{{ formatMoney(row.price) }}</template>
        </el-table-column>

        <el-table-column label="库存" width="100">
          <template #default="{ row }">
            <span :class="row.stock < 50 ? 'font-medium text-red-500' : ''">{{ row.stock }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="salesCount" label="销量" width="100" />

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" class="!rounded-full">
              {{ row.status === 1 ? '在售' : '已下架' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :width="isCompactDesktop ? 170 : 210" label="操作" align="right">
          <template #default="{ row }">
            <div class="product-row-actions">
              <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button
                size="small"
                :type="row.status === 1 ? 'warning' : 'success'"
                plain
                @click="handleToggleStatus(row)"
              >
                {{ row.status === 1 ? '下架' : '上架' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
        </el-table>
      </div>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="pagination.page"
          background
          layout="total, prev, pager, next"
          :total="pagination.total"
          :page-size="pagination.size"
          @current-change="fetchList"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '添加商品' : '编辑商品'"
      width="680px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <div class="mb-4 rounded-2xl border border-slate-200/70 bg-slate-50/80 px-4 py-4 dark:border-slate-700/60 dark:bg-slate-800/40">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">AI 上架文案助手</div>
              <div class="mt-1 text-xs leading-6 text-gray-500 dark:text-gray-400">
                基于你当前填写的商品草稿，生成标题、卖点、描述和标签。
              </div>
            </div>
            <div class="flex flex-wrap gap-2">
              <el-button size="small" type="primary" round :loading="aiCopyLoading" @click="handleGenerateProductCopy">
                一键生成上架文案
              </el-button>
            </div>
          </div>
          <div class="mt-3 text-xs leading-6 text-gray-400">当前草稿：{{ currentDraftSummary }}</div>
        </div>

        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入商品名称" />
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <el-cascader
            v-model="form.categoryId"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', checkStrictly: true, emitPath: false }"
            class="w-full"
            clearable
            placeholder="请选择分类"
          />
        </el-form-item>

        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" class="!w-full" />
        </el-form-item>

        <el-form-item label="原价">
          <el-input-number v-model="form.originalPrice" :min="0" :precision="2" class="!w-full" />
        </el-form-item>

        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="0" class="!w-full" />
        </el-form-item>

        <el-form-item label="主图 URL">
          <el-input v-model="form.mainImage" placeholder="请输入主图地址" />
        </el-form-item>

        <el-form-item label="详情图">
          <el-input
            v-model="form.detailImages"
            type="textarea"
            :rows="3"
            placeholder="多张图片 URL 用逗号分隔"
          />
        </el-form-item>

        <el-form-item label="商品标签">
          <el-input
            v-model="form.tagsText"
            placeholder="多个标签用逗号分隔，例如：通勤,轻薄,高性价比"
          />
        </el-form-item>

        <el-form-item label="商品描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="请输入商品描述"
          />
        </el-form-item>

        <el-form-item v-if="aiCopyResult" label="AI 结果">
          <div class="w-full rounded-2xl border border-emerald-100 bg-emerald-50/70 px-4 py-4 dark:border-emerald-800/30 dark:bg-emerald-900/10">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                  {{ aiCopyResult.productName || 'AI 文案草稿' }}
                </div>
                <div v-if="aiCopyResult.productSubtitle" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  {{ aiCopyResult.productSubtitle }}
                </div>
              </div>
              <el-button size="small" type="success" plain @click="applyGeneratedCopy(aiCopyResult)">
                重新套用这版文案
              </el-button>
            </div>

            <div v-if="aiCopyResult.sellingPoints.length" class="mt-4">
              <div class="text-xs font-semibold text-gray-600 dark:text-gray-300">核心卖点</div>
              <div class="mt-2 flex flex-wrap gap-2">
                <el-tag
                  v-for="point in aiCopyResult.sellingPoints"
                  :key="point"
                  size="small"
                  type="success"
                  effect="plain"
                  round
                >
                  {{ point }}
                </el-tag>
              </div>
            </div>

            <div v-if="aiCopyResult.tags.length" class="mt-4">
              <div class="text-xs font-semibold text-gray-600 dark:text-gray-300">建议标签</div>
              <div class="mt-2 flex flex-wrap gap-2">
                <el-tag v-for="tag in aiCopyResult.tags" :key="tag" size="small" effect="plain" round>
                  {{ tag }}
                </el-tag>
              </div>
            </div>

            <div v-if="aiCopyResult.recommendedAudience" class="mt-4 text-xs leading-6 text-gray-600 dark:text-gray-300">
              适合人群：{{ aiCopyResult.recommendedAudience }}
            </div>
            <div v-if="aiCopyResult.customerPitch" class="mt-2 text-xs leading-6 text-gray-600 dark:text-gray-300">
              运营建议：{{ aiCopyResult.customerPitch }}
            </div>
            <div v-if="aiCopyResult.liveScript" class="mt-2 text-xs leading-6 text-gray-600 dark:text-gray-300">
              直播口播：{{ aiCopyResult.liveScript }}
            </div>
            <div v-if="aiCopyResult.serviceReply" class="mt-2 text-xs leading-6 text-gray-500 dark:text-gray-400">
              客服说明：{{ aiCopyResult.serviceReply }}
            </div>
            <div v-if="aiCopyResult.searchKeywords.length" class="mt-2 text-xs leading-6 text-gray-500 dark:text-gray-400">
              搜索关键词：{{ aiCopyResult.searchKeywords.join('、') }}
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="aiDrawerVisible" title="AI 商家助手" size="min(92vw, 520px)">
      <div class="flex h-full min-h-0 flex-col">
        <div class="rounded-xl border border-slate-200/70 bg-slate-50/80 px-3.5 py-3 dark:border-slate-700/60 dark:bg-slate-800/40">
          <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">当前商品草稿</div>
          <div class="mt-2 text-xs leading-6 text-gray-500 dark:text-gray-400">{{ currentDraftSummary }}</div>
        </div>

        <div class="mt-3 flex flex-wrap gap-2">
          <el-button
            v-for="prompt in merchantAiQuickPrompts"
            :key="prompt"
            size="small"
            round
            plain
            @click="handleMerchantAiChat(prompt)"
          >
            {{ prompt }}
          </el-button>
        </div>

        <div class="mt-3 flex-1 overflow-y-auto space-y-3 pr-1">
          <div
            v-if="!merchantAiMessages.length"
            class="rounded-xl border border-dashed border-gray-200 px-4 py-6 text-center text-sm text-gray-400 dark:border-gray-700"
          >
            输入需求后，AI 可以帮你优化标题、补充卖点、生成标签和客服回复。
          </div>

          <div
            v-for="item in merchantAiMessages"
            :key="item.id"
            class="flex"
            :class="item.role === 'user' ? 'justify-end' : 'justify-start'"
          >
            <div class="max-w-[84%] space-y-2.5">
              <div
                class="whitespace-pre-wrap rounded-xl px-3.5 py-2.5 text-sm leading-6"
                :class="item.role === 'user'
                  ? 'bg-blue-500 text-white'
                  : 'border border-gray-200 bg-gray-50 text-gray-700 dark:border-gray-700 dark:bg-gray-800/60 dark:text-gray-200'"
              >
                {{ item.content }}
              </div>

              <div
                v-if="item.draftSummary && item.role !== 'user'"
                class="rounded-lg border border-slate-200/70 bg-slate-50/80 px-3 py-2 text-xs leading-6 text-gray-500 dark:border-slate-700/60 dark:bg-slate-800/40 dark:text-gray-400"
              >
                {{ item.draftSummary }}
              </div>

              <div
                v-if="item.generatedCopy && item.role !== 'user'"
                class="rounded-xl border border-emerald-100 bg-emerald-50/70 px-3.5 py-3 dark:border-emerald-800/30 dark:bg-emerald-900/10"
              >
                <div class="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <div class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                      {{ item.generatedCopy.productName || 'AI 文案草稿' }}
                    </div>
                    <div v-if="item.generatedCopy.productSubtitle" class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                      {{ item.generatedCopy.productSubtitle }}
                    </div>
                  </div>
                  <el-button size="small" type="success" plain @click="applyGeneratedCopy(item.generatedCopy)">
                    套用到表单
                  </el-button>
                </div>

                <div v-if="item.generatedCopy.sellingPoints.length" class="mt-3">
                  <div class="text-xs font-semibold text-gray-600 dark:text-gray-300">核心卖点</div>
                  <div class="mt-2 flex flex-wrap gap-2">
                    <el-tag
                      v-for="point in item.generatedCopy.sellingPoints"
                      :key="point"
                      size="small"
                      type="success"
                      effect="plain"
                      round
                    >
                      {{ point }}
                    </el-tag>
                  </div>
                </div>

                <div v-if="item.generatedCopy.tags.length" class="mt-3">
                  <div class="text-xs font-semibold text-gray-600 dark:text-gray-300">建议标签</div>
                  <div class="mt-2 flex flex-wrap gap-2">
                    <el-tag v-for="tag in item.generatedCopy.tags" :key="tag" size="small" effect="plain" round>
                      {{ tag }}
                    </el-tag>
                  </div>
                </div>

                <div
                  v-if="item.generatedCopy.description"
                  class="mt-3 whitespace-pre-wrap text-xs leading-6 text-gray-600 dark:text-gray-300"
                >
                  {{ item.generatedCopy.description }}
                </div>

                <div
                  v-if="item.generatedCopy.liveScript"
                  class="mt-3 whitespace-pre-wrap text-xs leading-6 text-gray-600 dark:text-gray-300"
                >
                  直播口播：{{ item.generatedCopy.liveScript }}
                </div>

                <div
                  v-if="item.generatedCopy.serviceReply"
                  class="mt-3 whitespace-pre-wrap text-xs leading-6 text-gray-500 dark:text-gray-400"
                >
                  客服说明：{{ item.generatedCopy.serviceReply }}
                </div>

                <div
                  v-if="item.generatedCopy.searchKeywords.length"
                  class="mt-3 text-xs leading-6 text-gray-500 dark:text-gray-400"
                >
                  搜索关键词：{{ item.generatedCopy.searchKeywords.join('、') }}
                </div>
              </div>

            </div>
          </div>

          <div v-if="aiChatLoading" class="flex justify-start">
            <div class="rounded-xl border border-gray-200 bg-gray-50 px-3.5 py-2.5 text-sm text-gray-500 dark:border-gray-700 dark:bg-gray-800/60">
              AI 商家助手正在整理建议...
            </div>
          </div>
        </div>

        <div class="mt-3 border-t border-gray-200/70 pt-3 dark:border-gray-700/60">
          <el-input
            v-model="merchantAiInput"
            placeholder="例如：帮我把标题改得更像电商详情页"
            clearable
            @keyup.enter="handleMerchantAiChat()"
          />
          <div class="mt-2.5 flex items-center justify-between gap-3">
            <span class="text-xs text-gray-400">支持生成标题、卖点、标签和客服说明</span>
            <div class="flex gap-2">
              <el-button @click="resetMerchantAiMessages">重置对话</el-button>
              <el-button
                type="primary"
                :loading="aiChatLoading"
                :disabled="!merchantAiInput.trim()"
                @click="handleMerchantAiChat()"
              >
                发送
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createMerchantProduct,
  generateMerchantAiProductCopy,
  getMerchantProducts,
  sendMerchantAiChat,
  updateMerchantProduct,
  updateMerchantProductStatus,
} from '../../api/merchant'
import { getProductCategories } from '../../api/products'

const merchantAiQuickPrompts = [
  '帮我生成一版上架文案',
  '帮我把标题改得更有吸引力',
  '给我 4 个适合搜索的商品标签',
]

const defaultAiFollowUps = [
  '帮我把标题改得更有吸引力',
  '再补 4 个搜索标签',
  '把描述改得更像详情页',
]

const loading = ref(false)
const submitting = ref(false)
const products = ref([])
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const categoryOptions = ref([])
const compactDesktopBreakpoint = 1180
const isCompactDesktop = ref(typeof window !== 'undefined' ? window.innerWidth < compactDesktopBreakpoint : false)
const aiDrawerVisible = ref(false)
const aiChatLoading = ref(false)
const aiCopyLoading = ref(false)
const merchantAiInput = ref('')
const merchantAiMessages = ref([])
const aiCopyResult = ref(null)

const filters = reactive({
  keyword: '',
  status: null,
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const form = reactive({
  id: null,
  name: '',
  categoryId: null,
  price: 0,
  originalPrice: 0,
  stock: 0,
  mainImage: '',
  detailImages: '',
  tagsText: '',
  description: '',
})

const rules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'change' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'change' }],
}

const categoryNameMap = computed(() => {
  const map = new Map()
  const walk = list => {
    ;(list || []).forEach(item => {
      if (item?.id != null) {
        map.set(String(item.id), item.name || '')
      }
      if (Array.isArray(item?.children) && item.children.length) {
        walk(item.children)
      }
    })
  }
  walk(categoryOptions.value)
  return map
})

const syncCompactDesktop = () => {
  isCompactDesktop.value = window.innerWidth < compactDesktopBreakpoint
}

const currentDraftSummary = computed(() => buildMerchantDraftSummary(buildMerchantDraftPayload()))

const handleSearch = () => {
  pagination.page = 1
  fetchList()
}

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getMerchantProducts({
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword || undefined,
      status: filters.status ?? undefined,
    })
    const data = res || {}
    products.value = data.records || []
    pagination.total = data.total || 0
  } finally {
    loading.value = false
  }
}

const fetchCategories = async () => {
  try {
    const res = await getProductCategories()
    categoryOptions.value = res || []
  } catch (error) {
    categoryOptions.value = []
    console.error('加载分类失败', error)
  }
}

const isLatestRow = index => pagination.page === 1 && index === 0

function formatMoney(value) {
  const amount = Number(value || 0)
  return Number.isFinite(amount) ? amount.toFixed(2) : '0.00'
}

function normalizeStringList(value, limit = Infinity) {
  const source = Array.isArray(value)
    ? value
    : String(value || '')
        .split(/[\n,，;；]/)
        .map(item => item.trim())
        .filter(Boolean)

  return source
    .map(item => String(item || '').trim())
    .filter(Boolean)
    .slice(0, limit)
}

function parseTagList(value) {
  return normalizeStringList(value, 8)
}

function parseDetailImages(value) {
  return normalizeStringList(value, 8)
}

function toTagsText(value) {
  return parseTagList(value).join(', ')
}

function toDetailImagesText(value) {
  if (Array.isArray(value)) {
    return value
      .map(item => String(item || '').trim())
      .filter(Boolean)
      .join(', ')
  }
  return String(value || '').trim()
}

function findCategoryNameById(categoryId, list = categoryOptions.value) {
  if (categoryId == null) {
    return ''
  }

  const key = String(categoryId)
  if (categoryNameMap.value.has(key)) {
    return categoryNameMap.value.get(key) || ''
  }

  for (const item of list || []) {
    if (String(item?.id) === key) {
      return item.name || ''
    }
    if (Array.isArray(item?.children) && item.children.length) {
      const nested = findCategoryNameById(categoryId, item.children)
      if (nested) {
        return nested
      }
    }
  }

  return ''
}

function buildMerchantDraftPayload() {
  const name = String(form.name || '').trim()
  const description = String(form.description || '').trim()
  const mainImage = String(form.mainImage || '').trim()
  const detailImages = parseDetailImages(form.detailImages)
  const tags = parseTagList(form.tagsText)
  const categoryName = findCategoryNameById(form.categoryId)

  return {
    name: name || undefined,
    categoryId: form.categoryId || undefined,
    categoryName: categoryName || undefined,
    price: Number(form.price || 0) > 0 ? Number(form.price) : undefined,
    originalPrice: Number(form.originalPrice || 0) > 0 ? Number(form.originalPrice) : undefined,
    stock: Number(form.stock || 0),
    mainImage: mainImage || undefined,
    detailImages,
    tags,
    description: description || undefined,
  }
}

function buildMerchantDraftSummary(draft) {
  const parts = []

  if (draft.name) {
    parts.push(`商品名：${draft.name}`)
  }
  if (draft.categoryName) {
    parts.push(`分类：${draft.categoryName}`)
  }
  if (draft.price) {
    parts.push(`价格：¥${formatMoney(draft.price)}`)
  }
  if (draft.tags?.length) {
    parts.push(`标签：${draft.tags.join(' / ')}`)
  }

  return parts.length
    ? parts.join(' | ')
    : '当前还没有完整草稿，建议至少填写商品名称、分类或价格。'
}

function buildSubmitPayload() {
  return {
    name: String(form.name || '').trim(),
    categoryId: form.categoryId,
    price: Number(form.price || 0),
    originalPrice: Number(form.originalPrice || 0) || null,
    stock: Number(form.stock || 0),
    mainImage: String(form.mainImage || '').trim() || null,
    detailImages: toDetailImagesText(form.detailImages) || null,
    tags: parseTagList(form.tagsText),
    description: String(form.description || '').trim() || null,
  }
}

function normalizeGeneratedCopy(payload) {
  if (!payload || typeof payload !== 'object') {
    return null
  }

  return {
    productName: String(payload.productName || '').trim(),
    productSubtitle: String(payload.productSubtitle || '').trim(),
    sellingPoints: normalizeStringList(payload.sellingPoints, 4),
    description: String(payload.description || '').trim(),
    tags: normalizeStringList(payload.tags, 6),
    recommendedAudience: String(payload.recommendedAudience || '').trim(),
    customerPitch: String(payload.customerPitch || '').trim(),
    liveScript: String(payload.liveScript || '').trim(),
    serviceReply: String(payload.serviceReply || '').trim(),
    searchKeywords: normalizeStringList(payload.searchKeywords, 6),
    marketingHighlights: normalizeStringList(payload.marketingHighlights, 4),
    draftSummary: String(payload.draftSummary || '').trim(),
    source: String(payload.source || '').trim(),
    generatedAt: payload.generatedAt || '',
  }
}

function buildDescriptionFromCopy(copy) {
  if (!copy) {
    return ''
  }

  const sections = []
  if (copy.productSubtitle) {
    sections.push(copy.productSubtitle)
  }
  if (copy.sellingPoints.length) {
    sections.push(`核心卖点：\n${copy.sellingPoints.map((item, index) => `${index + 1}. ${item}`).join('\n')}`)
  }
  if (copy.description) {
    sections.push(copy.description)
  }
  if (copy.recommendedAudience) {
    sections.push(`适合人群：${copy.recommendedAudience}`)
  }

  return sections.join('\n\n').trim()
}

function buildEnhancedDescriptionFromCopy(copy) {
  if (!copy) {
    return ''
  }

  const sections = []
  if (copy.productSubtitle) {
    sections.push(copy.productSubtitle)
  }
  if (copy.sellingPoints?.length) {
    sections.push(`核心卖点：\n${copy.sellingPoints.map((item, index) => `${index + 1}. ${item}`).join('\n')}`)
  }
  if (copy.description) {
    sections.push(copy.description)
  }
  if (copy.recommendedAudience) {
    sections.push(`适合人群：${copy.recommendedAudience}`)
  }
  if (copy.customerPitch) {
    sections.push(`运营建议：${copy.customerPitch}`)
  }
  if (copy.liveScript) {
    sections.push(`直播口播：${copy.liveScript}`)
  }
  if (copy.serviceReply) {
    sections.push(`客服说明：${copy.serviceReply}`)
  }

  return sections.join('\n\n').trim()
}

function createMerchantAiMessage(role, content, extra = {}) {
  return {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    role,
    content: String(content || '').trim(),
    draftSummary: String(extra.draftSummary || '').trim(),
    suggestedActions: normalizeStringList(extra.suggestedActions, 4),
    generatedCopy: normalizeGeneratedCopy(extra.generatedCopy),
    preset: !!extra.preset,
  }
}

function createMerchantAiWelcomeMessage() {
  return createMerchantAiMessage(
    'assistant',
    '我是 AI 商家助手，可以帮你生成上架文案、优化标题、补充标签和客服说明。',
    {
      suggestedActions: merchantAiQuickPrompts,
      preset: true,
    }
  )
}

function resetMerchantAiMessages() {
  merchantAiInput.value = ''
  merchantAiMessages.value = [createMerchantAiWelcomeMessage()]
}

function resetAiDraftState() {
  aiCopyResult.value = null
  aiDrawerVisible.value = false
  resetMerchantAiMessages()
}

function ensureMerchantAiMessages() {
  if (!merchantAiMessages.value.length) {
    resetMerchantAiMessages()
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.categoryId = null
  form.price = 0
  form.originalPrice = 0
  form.stock = 0
  form.mainImage = ''
  form.detailImages = ''
  form.tagsText = ''
  form.description = ''
}

function openCreateDialog() {
  dialogMode.value = 'create'
  resetForm()
  resetAiDraftState()
  dialogVisible.value = true
}

function openEditDialog(row) {
  dialogMode.value = 'edit'
  form.id = row.id
  form.name = row.name || ''
  form.categoryId = row.categoryId ?? null
  form.price = Number(row.price || 0)
  form.originalPrice = Number(row.originalPrice || 0)
  form.stock = Number(row.stock || 0)
  form.mainImage = row.mainImage || row.image || ''
  form.detailImages = toDetailImagesText(row.detailImages || row.images)
  form.tagsText = toTagsText(row.tags)
  form.description = row.description || ''
  resetAiDraftState()
  dialogVisible.value = true
}

function openMerchantAi() {
  aiDrawerVisible.value = true
  ensureMerchantAiMessages()
}

async function applyGeneratedCopy(copy, options = {}) {
  const normalized = normalizeGeneratedCopy(copy)
  if (!normalized) {
    ElMessage.warning('当前没有可套用的 AI 文案')
    return false
  }

  const askConfirm = options.askConfirm !== false
  const hasExistingContent = [form.name, form.description, form.tagsText].some(value => String(value || '').trim())

  if (askConfirm && hasExistingContent) {
    try {
      await ElMessageBox.confirm('AI 结果将更新当前商品名称、描述和标签，是否继续？', '应用 AI 文案', {
        type: 'warning',
        confirmButtonText: '应用',
        cancelButtonText: '取消',
      })
    } catch {
      aiCopyResult.value = normalized
      return false
    }
  }

  if (normalized.productName) {
    form.name = normalized.productName
  }

  const generatedDescription = buildEnhancedDescriptionFromCopy(normalized)
  if (generatedDescription) {
    form.description = generatedDescription
  }

  if (normalized.tags.length) {
    form.tagsText = normalized.tags.join(', ')
  } else if (normalized.searchKeywords.length) {
    form.tagsText = normalized.searchKeywords.join(', ')
  }

  aiCopyResult.value = normalized
  ElMessage.success('AI 文案已套用到当前表单')
  return true
}

async function handleGenerateProductCopy() {
  if (aiCopyLoading.value) {
    return
  }

  const draft = buildMerchantDraftPayload()
  if (!draft.name && !draft.categoryId && !draft.description) {
    ElMessage.warning('建议先填写商品名称、分类或描述，再生成上架文案')
    return
  }

  aiCopyLoading.value = true
  try {
    const res = await generateMerchantAiProductCopy(draft)
    const copy = normalizeGeneratedCopy(res)
    if (!copy) {
      throw new Error('AI 未返回可用文案')
    }

    aiCopyResult.value = copy
    const applied = await applyGeneratedCopy(copy)

    ensureMerchantAiMessages()
    merchantAiMessages.value.push(
      createMerchantAiMessage(
        'assistant',
        applied
          ? '已根据当前草稿生成并套用一版上架文案，你可以继续微调后保存。'
          : '已根据当前草稿生成一版上架文案，你可以先预览，再决定是否套用。',
        {
          draftSummary: res?.draftSummary || currentDraftSummary.value,
          generatedCopy: copy,
          suggestedActions: defaultAiFollowUps,
        }
      )
    )
    aiDrawerVisible.value = true
  } catch (error) {
    ElMessage.error(error?.message || 'AI 生成失败，请稍后再试')
  } finally {
    aiCopyLoading.value = false
  }
}

function buildMerchantAiHistory() {
  return merchantAiMessages.value
    .filter(item => !item.preset)
    .slice(-10)
    .map(item => ({
      role: item.role,
      content: item.content,
    }))
}

async function handleMerchantAiChat(message = merchantAiInput.value) {
  const text = String(message || '').trim()
  if (!text || aiChatLoading.value) {
    return
  }

  ensureMerchantAiMessages()
  const history = buildMerchantAiHistory()
  merchantAiMessages.value.push(createMerchantAiMessage('user', text))
  merchantAiInput.value = ''
  aiChatLoading.value = true

  try {
    const res = await sendMerchantAiChat(text, history, buildMerchantDraftPayload())
    const copy = normalizeGeneratedCopy(res?.generatedCopy)
    if (copy) {
      aiCopyResult.value = copy
    }

    merchantAiMessages.value.push(
      createMerchantAiMessage(
        'assistant',
        res?.reply || '已收到，我会结合当前草稿继续给你建议。',
        {
          draftSummary: res?.draftSummary || currentDraftSummary.value,
          suggestedActions: res?.suggestedActions || defaultAiFollowUps,
          generatedCopy: copy,
        }
      )
    )
  } catch (error) {
    console.error('商家 AI 对话失败', error)
    merchantAiMessages.value.push(
      createMerchantAiMessage(
        'assistant',
        'AI 商家助手暂时繁忙，请稍后再试。你也可以直接点击“一键生成上架文案”。'
      )
    )
  } finally {
    aiChatLoading.value = false
  }
}

const submitForm = async () => {
  if (!formRef.value || submitting.value) {
    return
  }

  await formRef.value.validate()
  submitting.value = true

  try {
    const payload = buildSubmitPayload()
    if (dialogMode.value === 'create') {
      await createMerchantProduct(payload)
      ElMessage.success('商品创建成功')
    } else {
      await updateMerchantProduct(form.id, payload)
      ElMessage.success('商品更新成功')
    }

    dialogVisible.value = false
    await fetchList()
  } finally {
    submitting.value = false
  }
}

const handleToggleStatus = async row => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await updateMerchantProductStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '商品已上架' : '商品已下架')
    await fetchList()
  } catch (error) {
    console.error('更新商品状态失败', error)
  }
}

onMounted(() => {
  syncCompactDesktop()
  window.addEventListener('resize', syncCompactDesktop)
  fetchList()
  fetchCategories()
  resetMerchantAiMessages()
})

onUnmounted(() => {
  window.removeEventListener('resize', syncCompactDesktop)
})
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

.product-row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
