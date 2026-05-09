<template>
  <div v-loading="loading" class="space-y-6">
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <StatCard title="累计营收" :value="'¥' + formatMoney(financeStats.totalRevenue)" icon="Money" color="from-emerald-400 to-teal-500" />
      <StatCard title="本月营收" :value="'¥' + formatMoney(financeStats.monthRevenue)" icon="TrendCharts" color="from-blue-400 to-indigo-500" />
      <StatCard title="累计订单" :value="formatNum(financeStats.totalOrders)" icon="List" color="from-purple-400 to-pink-500" />
      <StatCard title="平均客单价" :value="'¥' + formatMoney(financeStats.avgOrderValue)" icon="PriceTag" color="from-amber-400 to-orange-500" />
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">营收趋势（近 30 天）</h3>
      <div ref="chartRef" class="h-80 w-full"></div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg overflow-hidden">
      <div class="p-6 pb-2">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">收入明细</h3>
      </div>
      <el-table
        :data="revenueDetails"
        class="!bg-transparent"
        stripe
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="orderNo" label="订单号" min-width="180" />
        <el-table-column label="用户" width="120">
          <template #default="{ row }">{{ row.username || '-' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <span class="text-emerald-600 font-semibold">¥{{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商品数" width="80" align="center">
          <template #default="{ row }">{{ row.itemCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.createTime?.substring(0, 16) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="p-4 flex justify-center" v-if="revTotal > revPageSize">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="revTotal"
          :page-size="revPageSize"
          :current-page="revPage"
          @current-change="loadRevenueDetails"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import StatCard from '../../components/StatCard.vue'
import {
  getMerchantFinanceDetails,
  getMerchantFinanceStats,
  getMerchantFinanceTrend,
} from '../../api/merchant'

const loading = ref(false)
const financeStats = ref({ totalRevenue: 0, monthRevenue: 0, totalOrders: 0, avgOrderValue: 0 })
const revenueDetails = ref([])
const revPage = ref(1)
const revPageSize = 15
const revTotal = ref(0)
const chartRef = ref(null)
let chart = null

function formatMoney(v) {
  return v == null ? '0.00' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function formatNum(n) { return n == null ? '0' : Number(n).toLocaleString('zh-CN') }
function statusText(s) { return { 0: '待付款', 1: '已付款', 2: '已发货', 3: '已完成', 4: '已取消', 5: '已退款' }[s] || '-' }
function statusType(s) { return { 0: 'warning', 1: 'primary', 2: 'info', 3: 'success', 4: 'danger', 5: 'danger' }[s] || 'info' }

async function loadFinanceData() {
  loading.value = true
  try {
    const res = await getMerchantFinanceStats()
    if (res) financeStats.value = res
  } catch (e) {
    console.error(e)
  }
  loading.value = false
}

async function loadRevenueDetails(page = 1) {
  revPage.value = page
  try {
    const res = await getMerchantFinanceDetails({ page, size: revPageSize })
    revenueDetails.value = res?.records || []
    revTotal.value = res?.total || 0
  } catch (e) {
    console.error(e)
  }
}

async function loadChart() {
  try {
    const res = await getMerchantFinanceTrend()
    await nextTick()
    if (!chartRef.value) return
    const isDark = document.documentElement.classList.contains('dark')
    chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '4%', right: '4%', bottom: 72, top: 24, containLabel: true },
      xAxis: {
        type: 'category',
        data: res?.dates || [],
        axisLabel: { color: isDark ? '#9ca3af' : '#6b7280', rotate: 45, fontSize: 10, margin: 14 },
      },
      yAxis: {
        type: 'value',
        name: '金额 (¥)',
        splitLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb', type: 'dashed' } },
        axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' },
      },
      series: [{
        name: '日营收',
        type: 'bar',
        barMaxWidth: 20,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(16,185,129,0.8)' },
            { offset: 1, color: 'rgba(16,185,129,0.2)' },
          ]),
          borderRadius: [4, 4, 0, 0],
        },
        data: res?.revenues || [],
      }, {
        name: '订单数',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        lineStyle: { width: 2, color: '#3b82f6', type: 'dashed' },
        itemStyle: { color: '#3b82f6' },
        showSymbol: false,
        data: res?.orderCounts || [],
      }],
    })
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => { loadFinanceData(); loadRevenueDetails(); loadChart() })
onUnmounted(() => chart?.dispose())
</script>
