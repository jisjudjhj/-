<template>
  <div v-loading="loading" class="space-y-6">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      <div v-for="(stat, index) in statsCards" :key="index"
           class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg hover:shadow-xl hover:-translate-y-1 transition-all duration-300">
        <div class="flex items-center justify-between">
          <div>
            <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ stat.title }}</p>
            <p class="text-3xl font-bold text-gray-800 dark:text-gray-100 mt-2">{{ stat.value }}</p>
          </div>
          <div :class="`p-3 rounded-xl bg-gradient-to-br ${stat.color} text-white shadow-lg`">
            <el-icon class="text-2xl"><component :is="stat.icon" /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">热销商品 TOP5</h3>
        <div v-if="topProducts.length" class="space-y-3">
          <div v-for="(p, i) in topProducts" :key="p.id"
               class="flex items-center gap-4 p-3 rounded-xl bg-gray-50/50 dark:bg-gray-700/30 hover:bg-gray-100/50 dark:hover:bg-gray-700/50 transition-colors">
            <span class="w-8 h-8 flex items-center justify-center rounded-full text-sm font-bold"
                  :class="i < 3 ? 'bg-gradient-to-br from-amber-400 to-orange-500 text-white' : 'bg-gray-200 dark:bg-gray-600 text-gray-600 dark:text-gray-300'">
              {{ i + 1 }}
            </span>
            <img v-if="p.mainImage" :src="p.mainImage" class="w-10 h-10 rounded-lg object-cover" />
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ p.name }}</div>
              <div class="text-xs text-gray-500 dark:text-gray-400">销量: {{ p.salesCount }} | ￥{{ Number(p.price || 0).toFixed(2) }}</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="80" />
      </div>

      <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">库存预警</h3>
        <div v-if="lowStockProducts.length" class="space-y-3 max-h-80 overflow-y-auto">
          <div v-for="p in lowStockProducts" :key="p.id"
               class="flex items-center justify-between p-3 rounded-xl bg-red-50/50 dark:bg-red-900/10 border border-red-200/30 dark:border-red-800/20">
            <div class="flex-1 min-w-0">
              <div class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ p.name }}</div>
            </div>
            <el-tag type="danger" size="small" class="!rounded-full ml-2">
              库存: {{ p.stock }}
            </el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无库存预警" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getMerchantDashboard } from '../../api/merchant'

const loading = ref(false)
const dashData = ref({})

const topProducts = computed(() => dashData.value.topProducts || [])
const lowStockProducts = computed(() => dashData.value.lowStockProducts || [])

const statsCards = computed(() => {
  const d = dashData.value
  return [
    {
      title: '总收入',
      value: `￥${Number(d.totalRevenue || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}`,
      icon: 'Money',
      color: 'from-emerald-400 to-teal-500',
    },
    {
      title: '总销量',
      value: Number(d.totalSales || 0).toLocaleString(),
      icon: 'ShoppingCart',
      color: 'from-blue-400 to-indigo-500',
    },
    {
      title: '在售商品',
      value: Number(d.onShelfCount || 0).toLocaleString(),
      icon: 'Goods',
      color: 'from-purple-400 to-pink-500',
    },
    {
      title: '已下架',
      value: Number(d.offShelfCount || 0).toLocaleString(),
      icon: 'Box',
      color: 'from-amber-400 to-orange-500',
    },
  ]
})

const fetchDashboard = async () => {
  loading.value = true
  try {
    const res = await getMerchantDashboard()
    dashData.value = res || {}
  } finally {
    loading.value = false
  }
}

onMounted(fetchDashboard)
</script>
