<template>
  <div class="space-y-6">
    <section class="panel-card overflow-hidden p-6 md:p-7">
      <div class="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
        <div>
          <h1 class="text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100 md:text-4xl">商家经营分析</h1>
        </div>
        <div class="grid gap-3">
          <article class="rounded-2xl border border-blue-100 bg-blue-50/80 p-4 dark:border-blue-900/60 dark:bg-blue-950/30">
            <div class="text-xs uppercase tracking-[0.22em] text-blue-700 dark:text-blue-300">经营摘要</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">￥{{ formatMoney(couponDashboard.summary.paidGmv) }}</div>
            <div class="mt-2 text-sm text-slate-600 dark:text-slate-300">发券成交额，活跃用户 {{ userSummary.activeUsers || 0 }} 人</div>
            <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">窗口 {{ query.days }} 天</div>
          </article>
        </div>
      </div>
    </section>

    <div class="grid grid-cols-1 gap-3 xl:grid-cols-5">
      <StatCard v-for="card in analyticsStatCards" :key="card[0]" :title="card[0]" :value="card[1]" :sub="card[2]" :icon="card[3]" :color="card[4]" />
    </div>

    <PageSectionTabs
      v-model="activeAnalyticsTab"
      primary-label="商家工作台"
      page-label="经营分析"
      :tabs="analyticsTabs"
    />

    <div v-if="activeAnalyticsTab === 'users'" class="panel-card p-5">
      <div class="grid grid-cols-1 gap-3 lg:grid-cols-7">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索用户ID/昵称/手机号"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.segmentCode" clearable placeholder="全部分群">
          <el-option
            v-for="item in segmentOptions"
            :key="item.segmentCode"
            :label="`${item.segmentName} (${item.segmentCode})`"
            :value="item.segmentCode"
          />
        </el-select>
        <el-select v-model="query.days" placeholder="窗口">
          <el-option label="近7天" :value="7" />
          <el-option label="近30天" :value="30" />
          <el-option label="近90天" :value="90" />
          <el-option label="近180天" :value="180" />
        </el-select>
        <el-input-number
          v-model="query.minScore"
          :min="0"
          :max="100"
          :step="1"
          controls-position="right"
          class="!w-full"
          placeholder="最低评分"
        />
        <el-input-number
          v-model="query.minSpend30d"
          :min="0"
          :precision="2"
          :step="50"
          controls-position="right"
          class="!w-full"
          placeholder="最低消费"
        />
        <div class="lg:col-span-2 flex items-center justify-end gap-2">
          <el-button @click="resetFilters">重置</el-button>
          <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        </div>
      </div>
    </div>

    <div v-if="activeAnalyticsTab === 'users'" class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">活跃用户数</div>
        <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ userSummary.activeUsers || 0 }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">窗口行为量</div>
        <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ userSummary.totalBehaviorCount30d || 0 }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">窗口消费额</div>
        <div class="mt-2 text-3xl font-semibold text-emerald-600 dark:text-emerald-400">￥{{ formatMoney(userSummary.totalSpend30d) }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">用户平均评分</div>
        <div class="mt-2 text-3xl font-semibold text-indigo-600 dark:text-indigo-400">{{ formatDecimal(userSummary.avgScore) }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">浏览 UV（店铺/商品）</div>
        <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ userSummary.storeViewUv || 0 }} / {{ userSummary.productViewUv || 0 }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">加购率</div>
        <div class="mt-2 text-3xl font-semibold text-amber-600 dark:text-amber-400">{{ formatRate(userSummary.addToCartRate) }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">下单转化率</div>
        <div class="mt-2 text-3xl font-semibold text-cyan-600 dark:text-cyan-400">{{ formatRate(userSummary.orderConversionRate) }}</div>
      </div>
      <div class="panel-card p-5">
        <div class="text-sm text-slate-500 dark:text-slate-400">复购率</div>
        <div class="mt-2 text-3xl font-semibold text-violet-600 dark:text-violet-400">{{ formatRate(userSummary.repurchaseRate) }}</div>
      </div>
    </div>

    <div v-if="activeAnalyticsTab === 'users'" class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <div class="panel-card p-6">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100 mb-4">本店用户行为分布</h3>
        <div ref="barChartRef" class="h-80 w-full"></div>
      </div>
      <div class="panel-card p-6">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100 mb-4">行为占比</h3>
        <div ref="pieChartRef" class="h-80 w-full"></div>
      </div>
    </div>

    <div v-if="activeAnalyticsTab === 'users'" class="panel-card p-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">用户行为明细</h3>
        <div class="flex items-center gap-2">
          <el-button
            :disabled="!coupons.length"
            @click="openIssueDialogByFilter"
          >
            按筛选一键发券
          </el-button>
          <el-button
            type="primary"
            :disabled="!selectedUserIds.length"
            @click="openIssueDialog(selectedUserIds)"
          >
            给选中用户发券
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="userRows"
        class="!bg-transparent"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="34" :src="row.avatar">{{ (row.nickname || row.username || 'U').slice(0, 1) }}</el-avatar>
              <div class="min-w-0">
                <div class="text-sm font-semibold text-slate-900 dark:text-slate-100 truncate">
                  {{ row.nickname || row.username || `用户${row.userId}` }}
                </div>
                <div class="text-xs text-slate-500 dark:text-slate-400 truncate">
                  ID: {{ row.userId }} {{ row.phone ? `· ${row.phone}` : '' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分群" min-width="140">
          <template #default="{ row }">
            <el-tag effect="plain" round>{{ row.segmentName || '未分群' }}</el-tag>
            <div class="mt-1 text-xs text-slate-500">{{ row.segmentCode || 'UNASSIGNED' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="90" />
        <el-table-column label="近30天消费" min-width="120">
          <template #default="{ row }">￥{{ formatMoney(row.spend30d) }}</template>
        </el-table-column>
        <el-table-column prop="behaviorCount30d" label="行为量" width="90" />
        <el-table-column prop="orderCount30d" label="订单数" width="90" />
        <el-table-column label="最近活跃" min-width="160">
          <template #default="{ row }">{{ row.lastBehaviorTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openIssueDialog([row.userId])">发券</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          background
          layout="total, prev, pager, next"
          :total="total"
          @current-change="fetchUsers"
        />
      </div>
    </div>

    <div v-else-if="activeAnalyticsTab === 'coupons'" class="panel-card p-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">商家发券后转化看板</h3>
        <div class="text-sm text-slate-500 dark:text-slate-400">统计口径：近 {{ query.days }} 天发放的店铺券</div>
      </div>
      <div class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-5">
        <div class="rounded-2xl border border-slate-200/70 dark:border-slate-700 p-4">
          <div class="text-sm text-slate-500 dark:text-slate-400">窗口发券数</div>
          <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ couponDashboard.summary.issuedCount || 0 }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200/70 dark:border-slate-700 p-4">
          <div class="text-sm text-slate-500 dark:text-slate-400">窗口核销数</div>
          <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ couponDashboard.summary.redeemedCount || 0 }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200/70 dark:border-slate-700 p-4">
          <div class="text-sm text-slate-500 dark:text-slate-400">支付订单数</div>
          <div class="mt-2 text-3xl font-semibold text-slate-900 dark:text-slate-100">{{ couponDashboard.summary.paidOrderCount || 0 }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200/70 dark:border-slate-700 p-4">
          <div class="text-sm text-slate-500 dark:text-slate-400">发券带来 GMV</div>
          <div class="mt-2 text-3xl font-semibold text-emerald-600 dark:text-emerald-400">￥{{ formatMoney(couponDashboard.summary.paidGmv) }}</div>
        </div>
        <div class="rounded-2xl border border-slate-200/70 dark:border-slate-700 p-4">
          <div class="text-sm text-slate-500 dark:text-slate-400">支付转化率 / 客单价</div>
          <div class="mt-2 text-2xl font-semibold text-slate-900 dark:text-slate-100">{{ formatRate(couponDashboard.summary.paidOrderRate) }}</div>
          <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">客单价 ￥{{ formatMoney(couponDashboard.summary.avgOrderAmount) }}</div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-2 mt-6">
        <div>
          <div class="mb-3 text-sm font-medium text-slate-700 dark:text-slate-200">发券转化趋势</div>
          <div ref="couponTrendChartRef" class="h-80 w-full"></div>
        </div>
        <div>
          <div class="mb-3 text-sm font-medium text-slate-700 dark:text-slate-200">Top 优惠券 GMV 排行</div>
          <div ref="couponRankChartRef" class="h-80 w-full"></div>
        </div>
      </div>
    </div>

    <div v-else class="panel-card p-6">
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-slate-100">本店优惠券</h3>
        <el-button type="primary" @click="openCouponDialog">创建本店优惠券</el-button>
      </div>
      <el-table
        v-loading="couponLoading"
        :data="coupons"
        class="!bg-transparent"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="name" label="优惠券名称" min-width="180" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ couponTypeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="券值" width="100">
          <template #default="{ row }">{{ couponValueText(row) }}</template>
        </el-table-column>
        <el-table-column label="门槛" width="100">
          <template #default="{ row }">￥{{ formatMoney(row.minAmount) }}</template>
        </el-table-column>
        <el-table-column label="窗口发券数" width="100">
          <template #default="{ row }">{{ row.issuedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="库存" width="100">
          <template #default="{ row }">{{ row.usedCount || 0 }}/{{ row.totalCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="窗口核销数" width="100">
          <template #default="{ row }">{{ row.redeemedCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="支付订单" width="100">
          <template #default="{ row }">{{ row.paidOrderCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="支付GMV" min-width="120">
          <template #default="{ row }">￥{{ formatMoney(row.paidGmv) }}</template>
        </el-table-column>
        <el-table-column label="客单价" min-width="110">
          <template #default="{ row }">￥{{ formatMoney(row.avgOrderAmount) }}</template>
        </el-table-column>
        <el-table-column label="核销率" width="100">
          <template #default="{ row }">{{ formatRate(row.redeemRate) }}</template>
        </el-table-column>
        <el-table-column label="支付转化率" width="120">
          <template #default="{ row }">{{ formatRate(row.paidOrderRate) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="couponStatusTag(row.status)" effect="plain" round>{{ couponStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="220">
          <template #default="{ row }">{{ row.startTime }} 至 {{ row.endTime }}</template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="couponDialogVisible" title="创建本店优惠券" width="min(92vw, 760px)" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="优惠券名称">
          <el-input v-model="couponForm.name" placeholder="例如：店铺满减券" />
        </el-form-item>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="优惠券类型">
            <el-select v-model="couponForm.type" class="w-full">
              <el-option label="满减券" :value="1" />
              <el-option label="折扣券" :value="2" />
              <el-option label="无门槛券" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="couponForm.status" class="w-full">
              <el-option label="未开始" :value="0" />
              <el-option label="进行中" :value="1" />
              <el-option label="已结束" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="券值">
            <el-input-number v-model="couponForm.value" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item label="使用门槛">
            <el-input-number v-model="couponForm.minAmount" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item v-if="couponForm.type === 2" label="最高优惠">
            <el-input-number v-model="couponForm.maxDiscount" :min="0" :precision="2" class="!w-full" />
          </el-form-item>
          <el-form-item label="发行数量">
            <el-input-number v-model="couponForm.totalCount" :min="1" class="!w-full" />
          </el-form-item>
        </div>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="开始时间">
            <el-date-picker v-model="couponForm.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="couponForm.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" class="!w-full" />
          </el-form-item>
        </div>
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
          <el-form-item label="领取范围">
            <el-select v-model="couponForm.audienceType" class="w-full">
              <el-option label="公开券" :value="0" />
              <el-option label="分群定向券" :value="1" />
              <el-option label="指定用户券" :value="2" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="couponForm.audienceNote" maxlength="255" show-word-limit />
          </el-form-item>
        </div>
        <el-form-item v-if="couponForm.audienceType === 1" label="分群编码">
          <el-input v-model="couponForm.targetSegmentCodes" placeholder="例如：S1,S2" />
        </el-form-item>
        <el-form-item v-if="couponForm.audienceType === 2" label="指定用户ID">
          <el-input
            v-model="couponForm.targetUserIds"
            type="textarea"
            :rows="3"
            placeholder="例如：1001,1002,1003"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="couponDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="couponSubmitting" @click="submitCoupon">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="issueDialogVisible" title="定向发放优惠券" width="min(92vw, 480px)" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="目标人数">
          <div class="text-slate-700 dark:text-slate-200">
            <span v-if="issueForm.targetMode === 'users'">{{ issueForm.userIds.length }} 人（手动选择）</span>
            <span v-else>按当前筛选自动匹配（最多 {{ issueForm.maxIssueCount }} 人）</span>
          </div>
        </el-form-item>
        <el-form-item v-if="issueForm.targetMode === 'filter'" label="筛选条件">
          <div class="text-xs text-slate-500 leading-6">
            窗口 {{ query.days }} 天 /
            分群 {{ query.segmentCode || '全部' }} /
            最低评分 {{ query.minScore || 0 }} /
            最低消费 ￥{{ formatMoney(query.minSpend30d || 0) }}
          </div>
        </el-form-item>
        <el-form-item label="选择优惠券">
          <el-select v-model="issueForm.couponId" class="w-full" placeholder="请选择优惠券">
            <el-option
              v-for="item in coupons"
              :key="item.id"
              :label="`${item.name}（剩余${Math.max((item.totalCount || 0) - (item.usedCount || 0), 0)}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="issueDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="issueSubmitting" @click="submitIssue">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  createMerchantCoupon,
  getMerchantBehaviorAnalytics,
  getMerchantBehaviorUsers,
  getMerchantCouponConversionDashboard,
  getMerchantCoupons,
  issueMerchantCoupon,
  issueMerchantCouponByFilter,
} from '../../api/merchant'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import StatCard from '../../components/StatCard.vue'

const loading = ref(false)
const couponLoading = ref(false)
const couponSubmitting = ref(false)
const issueSubmitting = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  days: 30,
  keyword: '',
  segmentCode: '',
  minScore: null,
  minSpend30d: null,
})

const total = ref(0)
const userRows = ref([])
const userSummary = ref({
  activeUsers: 0,
  totalSpend30d: 0,
  totalBehaviorCount30d: 0,
  avgScore: 0,
  storeViewUv: 0,
  productViewUv: 0,
  addToCartRate: 0,
  orderConversionRate: 0,
  repurchaseRate: 0,
})
const behaviorStats = ref([])
const segmentOptions = ref([])
const selectedUserIds = ref([])

const coupons = ref([])
const couponDashboard = ref({
  summary: {
    issuedCount: 0,
    redeemedCount: 0,
    paidOrderCount: 0,
    paidGmv: 0,
    avgOrderAmount: 0,
    paidOrderRate: 0,
  },
  trend: {
    dates: [],
    issuedCounts: [],
    redeemedCounts: [],
    paidOrderCounts: [],
    paidGmvAmounts: [],
  },
  couponBreakdown: [],
})
const couponDialogVisible = ref(false)
const issueDialogVisible = ref(false)
const activeAnalyticsTab = ref('users')

const couponForm = reactive({
  name: '',
  type: 1,
  value: 0,
  minAmount: 0,
  maxDiscount: null,
  totalCount: 1,
  startTime: '',
  endTime: '',
  status: 1,
  audienceType: 0,
  targetSegmentCodes: '',
  targetUserIds: '',
  audienceNote: '',
})

const issueForm = reactive({
  couponId: null,
  userIds: [],
  targetMode: 'users',
  maxIssueCount: 500,
})

const barChartRef = ref(null)
const pieChartRef = ref(null)
const couponTrendChartRef = ref(null)
const couponRankChartRef = ref(null)
let charts = []
let themeObserver = null

const behaviorTypeMap = {
  view: '浏览商品',
  cart: '加入购物车',
  favorite: '收藏',
  purchase: '购买',
  search: '搜索',
}

const parseUserIds = rawValue =>
  String(rawValue || '')
    .split(/[,\uFF0C;\uFF1B\s]+/)
    .map(item => Number(item))
    .filter(item => Number.isInteger(item) && item > 0)

const formatMoney = value => Number(value || 0).toFixed(2)
const formatDecimal = value => Number(value || 0).toFixed(1)
const formatRate = value => `${Number(value || 0).toFixed(2)}%`
const analyticsHeroTags = ['用户行为', '分群筛选', '券后转化', '优惠券管理']
const analyticsTabs = [
  {
    key: 'users',
    label: '用户行为分析',
    hint: '筛选 + 图表 + 明细',
    description: '先看用户画像和行为分布，再下钻到用户明细，是商家端最核心的经营分析视角。',
  },
  {
    key: 'coupons',
    label: '券后转化',
    hint: '发券效果 + GMV',
    description: '单独说明发券后的核销、支付订单和 GMV 变化，强调运营动作与结果的闭环。',
  },
  {
    key: 'library',
    label: '优惠券管理',
    hint: '券库 + 新建优惠券',
    description: '把本店优惠券作为单独的页面内模块，方便展示商家端可以直接配置与投放优惠券。',
  },
]
const activeAnalyticsTabInfo = computed(() => analyticsTabs.find(item => item.key === activeAnalyticsTab.value) || analyticsTabs[0])
const analyticsStatCards = computed(() => ([
  ['活跃用户数', userSummary.value.activeUsers || 0, '当前窗口的有效活跃用户', 'User', 'from-sky-400 to-blue-500'],
  ['窗口行为量', userSummary.value.totalBehaviorCount30d || 0, '浏览 / 加购 / 收藏 / 购买总量', 'Histogram', 'from-cyan-400 to-teal-500'],
  ['窗口消费额', `￥${formatMoney(userSummary.value.totalSpend30d)}`, '近窗口累计消费金额', 'TrendCharts', 'from-emerald-400 to-green-500'],
  ['发券带来 GMV', `￥${formatMoney(couponDashboard.value.summary.paidGmv)}`, '近窗口优惠券带来的成交额', 'Wallet', 'from-amber-400 to-orange-500'],
  ['窗口发券数', couponDashboard.value.summary.issuedCount || 0, '当前店铺券投放规模', 'Ticket', 'from-violet-400 to-fuchsia-500'],
]))

const couponTypeText = type => {
  if (Number(type) === 1) return '满减券'
  if (Number(type) === 2) return '折扣券'
  if (Number(type) === 3) return '无门槛券'
  return '未知'
}

const couponStatusText = status => {
  if (Number(status) === 0) return '未开始'
  if (Number(status) === 1) return '进行中'
  if (Number(status) === 2) return '已结束'
  return '未知'
}

const couponStatusTag = status => {
  if (Number(status) === 1) return 'success'
  if (Number(status) === 2) return 'info'
  return 'warning'
}

const couponValueText = coupon => {
  if (Number(coupon.type) === 2) return `${coupon.value} 折`
  return `￥${formatMoney(coupon.value)}`
}

const resetCouponForm = () => {
  couponForm.name = ''
  couponForm.type = 1
  couponForm.value = 0
  couponForm.minAmount = 0
  couponForm.maxDiscount = null
  couponForm.totalCount = 1
  couponForm.startTime = ''
  couponForm.endTime = ''
  couponForm.status = 1
  couponForm.audienceType = 0
  couponForm.targetSegmentCodes = ''
  couponForm.targetUserIds = ''
  couponForm.audienceNote = ''
}

const fetchBehaviorOverview = async () => {
  const res = await getMerchantBehaviorAnalytics({ days: query.days })
  behaviorStats.value = res?.behaviorStats || []
  segmentOptions.value = res?.segmentDistribution || []
  nextTick(() => initCharts())
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getMerchantBehaviorUsers({
      page: query.page,
      size: query.size,
      days: query.days,
      keyword: query.keyword || undefined,
      segmentCode: query.segmentCode || undefined,
      minScore: query.minScore ?? undefined,
      minSpend30d: query.minSpend30d ?? undefined,
    })
    userRows.value = res?.records || []
    total.value = Number(res?.total || 0)
    userSummary.value = {
      activeUsers: Number(res?.summary?.activeUsers || 0),
      totalSpend30d: Number(res?.summary?.totalSpend30d || 0),
      totalBehaviorCount30d: Number(res?.summary?.totalBehaviorCount30d || 0),
      avgScore: Number(res?.summary?.avgScore || 0),
      storeViewUv: Number(res?.summary?.storeViewUv || 0),
      productViewUv: Number(res?.summary?.productViewUv || 0),
      addToCartRate: Number(res?.summary?.addToCartRate || 0),
      orderConversionRate: Number(res?.summary?.orderConversionRate || 0),
      repurchaseRate: Number(res?.summary?.repurchaseRate || 0),
    }
  } finally {
    loading.value = false
  }
}

const fetchCoupons = async () => {
  couponLoading.value = true
  try {
    const res = await getMerchantCoupons({ page: 1, size: 100, days: query.days })
    coupons.value = res?.records || []
  } finally {
    couponLoading.value = false
  }
}

const fetchCouponDashboard = async () => {
  const res = await getMerchantCouponConversionDashboard({ days: query.days })
  couponDashboard.value = {
    summary: {
      issuedCount: Number(res?.summary?.issuedCount || 0),
      redeemedCount: Number(res?.summary?.redeemedCount || 0),
      paidOrderCount: Number(res?.summary?.paidOrderCount || 0),
      paidGmv: Number(res?.summary?.paidGmv || 0),
      avgOrderAmount: Number(res?.summary?.avgOrderAmount || 0),
      paidOrderRate: Number(res?.summary?.paidOrderRate || 0),
    },
    trend: {
      dates: res?.trend?.dates || [],
      issuedCounts: res?.trend?.issuedCounts || [],
      redeemedCounts: res?.trend?.redeemedCounts || [],
      paidOrderCounts: res?.trend?.paidOrderCounts || [],
      paidGmvAmounts: (res?.trend?.paidGmvAmounts || []).map(item => Number(item || 0)),
    },
    couponBreakdown: res?.couponBreakdown || [],
  }
  nextTick(() => initCharts())
}

const handleSearch = async () => {
  query.page = 1
  await Promise.all([fetchBehaviorOverview(), fetchUsers(), fetchCoupons(), fetchCouponDashboard()])
}

const resetFilters = async () => {
  query.keyword = ''
  query.segmentCode = ''
  query.days = 30
  query.minScore = null
  query.minSpend30d = null
  query.page = 1
  await Promise.all([fetchBehaviorOverview(), fetchUsers(), fetchCoupons(), fetchCouponDashboard()])
}

const handleSelectionChange = rows => {
  selectedUserIds.value = (rows || []).map(item => item.userId).filter(Boolean)
}

const openCouponDialog = () => {
  resetCouponForm()
  couponDialogVisible.value = true
}

const submitCoupon = async () => {
  if (couponSubmitting.value) return
  if (!couponForm.name?.trim()) {
    ElMessage.warning('请输入优惠券名称')
    return
  }
  if (!couponForm.startTime || !couponForm.endTime) {
    ElMessage.warning('请设置优惠券有效期')
    return
  }
  if (couponForm.audienceType === 1 && !couponForm.targetSegmentCodes?.trim()) {
    ElMessage.warning('请填写分群编码')
    return
  }
  if (couponForm.audienceType === 2 && !parseUserIds(couponForm.targetUserIds).length) {
    ElMessage.warning('请填写有效的用户ID')
    return
  }

  couponSubmitting.value = true
  try {
    await createMerchantCoupon({
      name: couponForm.name.trim(),
      type: couponForm.type,
      value: couponForm.value,
      minAmount: couponForm.minAmount,
      maxDiscount: couponForm.type === 2 ? couponForm.maxDiscount : null,
      totalCount: couponForm.totalCount,
      startTime: couponForm.startTime,
      endTime: couponForm.endTime,
      status: couponForm.status,
      audienceType: couponForm.audienceType,
      targetSegmentCodes: couponForm.audienceType === 1 ? couponForm.targetSegmentCodes : '',
      targetUserIds: couponForm.audienceType === 2 ? parseUserIds(couponForm.targetUserIds).join(',') : '',
      audienceNote: couponForm.audienceNote || '',
    })
    ElMessage.success('本店优惠券创建成功')
    couponDialogVisible.value = false
    await Promise.all([fetchCoupons(), fetchCouponDashboard()])
  } finally {
    couponSubmitting.value = false
  }
}

const openIssueDialog = userIds => {
  if (!userIds?.length) {
    ElMessage.warning('请先选择目标用户')
    return
  }
  if (!coupons.value.length) {
    ElMessage.warning('请先创建本店优惠券')
    return
  }
  issueForm.userIds = [...new Set(userIds)]
  issueForm.targetMode = 'users'
  issueForm.couponId = coupons.value[0]?.id || null
  issueDialogVisible.value = true
}

const openIssueDialogByFilter = () => {
  if (!coupons.value.length) {
    ElMessage.warning('请先创建本店优惠券')
    return
  }
  issueForm.userIds = []
  issueForm.targetMode = 'filter'
  issueForm.maxIssueCount = 500
  issueForm.couponId = coupons.value[0]?.id || null
  issueDialogVisible.value = true
}

const submitIssue = async () => {
  if (issueSubmitting.value) return
  if (!issueForm.couponId) {
    ElMessage.warning('请选择优惠券')
    return
  }
  if (issueForm.targetMode === 'users' && !issueForm.userIds.length) {
    ElMessage.warning('请选择目标用户')
    return
  }
  issueSubmitting.value = true
  try {
    let res
    if (issueForm.targetMode === 'filter') {
      res = await issueMerchantCouponByFilter(issueForm.couponId, {
        days: query.days,
        keyword: query.keyword || '',
        segmentCode: query.segmentCode || '',
        minScore: query.minScore ?? undefined,
        minSpend30d: query.minSpend30d ?? undefined,
        maxIssueCount: issueForm.maxIssueCount,
      })
      ElMessage.success(`发放完成：匹配 ${res?.matchedCount || 0}，成功 ${res?.issuedCount || 0}，跳过 ${res?.skippedCount || 0}`)
    } else {
      res = await issueMerchantCoupon(issueForm.couponId, { userIds: issueForm.userIds })
      ElMessage.success(`发放完成：成功 ${res?.issuedCount || 0}，跳过 ${res?.skippedCount || 0}`)
    }
    issueDialogVisible.value = false
    selectedUserIds.value = []
    await Promise.all([fetchUsers(), fetchCoupons(), fetchCouponDashboard()])
  } finally {
    issueSubmitting.value = false
  }
}

const initCharts = () => {
  charts.forEach(item => item?.dispose())
  charts = []

  if (!behaviorStats.value.length) return

  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#94a3b8' : '#64748b'
  const splitLineColor = isDark ? '#334155' : '#e2e8f0'
  const colors = ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981']

  const labels = behaviorStats.value.map(item => behaviorTypeMap[item.behaviorType] || item.behaviorType || '未知')
  const values = behaviorStats.value.map(item => Number(item.count || 0))

  if (barChartRef.value) {
    const barChart = echarts.init(barChartRef.value)
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: '4%', right: '4%', bottom: 40, top: 16, containLabel: true },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { color: textColor, interval: 0 },
      },
      yAxis: {
        type: 'value',
        axisLabel: { color: textColor },
        splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
      },
      series: [{
        type: 'bar',
        barMaxWidth: 36,
        data: values,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: params => colors[params.dataIndex % colors.length],
        },
      }],
    })
    charts.push(barChart)
  }

  if (pieChartRef.value) {
    const pieChart = echarts.init(pieChartRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 8, textStyle: { color: textColor }, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['36%', '66%'],
        center: ['50%', '42%'],
        data: behaviorStats.value.map((item, index) => ({
          value: Number(item.count || 0),
          name: behaviorTypeMap[item.behaviorType] || item.behaviorType || '未知',
          itemStyle: { color: colors[index % colors.length] },
        })),
        label: { show: false },
        itemStyle: {
          borderColor: isDark ? '#0f172a' : '#ffffff',
          borderWidth: 2,
          borderRadius: 10,
        },
      }],
    })
    charts.push(pieChart)
  }

  const trendDates = couponDashboard.value?.trend?.dates || []
  if (couponTrendChartRef.value && trendDates.length) {
    const trendChart = echarts.init(couponTrendChartRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: {
        top: 0,
        textStyle: { color: textColor },
      },
      grid: { left: '4%', right: '4%', bottom: 30, top: 50, containLabel: true },
      xAxis: {
        type: 'category',
        data: trendDates,
        axisLabel: { color: textColor },
      },
      yAxis: [
        {
          type: 'value',
          axisLabel: { color: textColor },
          splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
        },
        {
          type: 'value',
          axisLabel: { color: textColor, formatter: value => `￥${value}` },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: '发券数',
          type: 'line',
          smooth: true,
          data: couponDashboard.value?.trend?.issuedCounts || [],
          lineStyle: { color: '#334155' },
          itemStyle: { color: '#334155' },
        },
        {
          name: '核销数',
          type: 'line',
          smooth: true,
          data: couponDashboard.value?.trend?.redeemedCounts || [],
          lineStyle: { color: '#8b5cf6' },
          itemStyle: { color: '#8b5cf6' },
        },
        {
          name: '支付订单',
          type: 'line',
          smooth: true,
          data: couponDashboard.value?.trend?.paidOrderCounts || [],
          lineStyle: { color: '#10b981' },
          itemStyle: { color: '#10b981' },
        },
        {
          name: 'GMV',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 20,
          data: couponDashboard.value?.trend?.paidGmvAmounts || [],
          itemStyle: { color: '#0f172a', borderRadius: [6, 6, 0, 0] },
        },
      ],
    })
    charts.push(trendChart)
  }

  const rankRows = [...(couponDashboard.value?.couponBreakdown || [])].slice(0, 8).reverse()
  if (couponRankChartRef.value && rankRows.length) {
    const rankChart = echarts.init(couponRankChartRef.value)
    rankChart.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '4%', right: '8%', bottom: 12, top: 8, containLabel: true },
      xAxis: {
        type: 'value',
        axisLabel: { color: textColor, formatter: value => `￥${value}` },
        splitLine: { lineStyle: { color: splitLineColor, type: 'dashed' } },
      },
      yAxis: {
        type: 'category',
        data: rankRows.map(item => item.name || `券${item.couponId}`),
        axisLabel: { color: textColor },
      },
      series: [{
        type: 'bar',
        data: rankRows.map(item => Number(item.paidGmv || 0)),
        barMaxWidth: 20,
        itemStyle: {
          color: '#0f172a',
          borderRadius: [0, 6, 6, 0],
        },
        label: {
          show: true,
          position: 'right',
          color: textColor,
          formatter: params => `￥${Number(params.value || 0).toFixed(0)}`,
        },
      }],
    })
    charts.push(rankChart)
  }
}

const handleResize = () => {
  charts.forEach(chart => chart?.resize())
}

onMounted(async () => {
  await Promise.all([fetchBehaviorOverview(), fetchUsers(), fetchCoupons(), fetchCouponDashboard()])
  window.addEventListener('resize', handleResize)
  themeObserver = new MutationObserver(mutations => {
    mutations.forEach(mutation => {
      if (mutation.attributeName === 'class') {
        initCharts()
      }
    })
  })
  themeObserver.observe(document.documentElement, { attributes: true })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(chart => chart?.dispose())
  charts = []
  themeObserver?.disconnect()
  themeObserver = null
})
</script>
