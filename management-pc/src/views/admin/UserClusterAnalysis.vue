<template>
  <div v-loading="pageLoading" class="space-y-6 analytics-ui-page defense-page">
    <section class="analysis-page-header">
      <h1 class="analysis-page-header__title">用户分群</h1>
      <div class="analysis-page-header__meta">KMeans · 画像 · 推荐联动</div>
    </section>

    <FeatureBrief
      kicker="用户分群"
      title="判断依据与组成"
      :items="clusterFeatureBrief"
    />

    <section class="defense-metric-strip">
      <article v-for="item in clusterSummaryCards" :key="item.label" class="defense-metric-strip__item">
        <span class="defense-metric-strip__label">{{ item.label }}</span>
        <strong class="defense-metric-strip__value">{{ item.value }}</strong>
        <span class="defense-metric-strip__sub">{{ item.sub }}</span>
      </article>
    </section>

    <section class="defense-evidence-list defense-evidence-list--two">
      <article
        v-for="item in clusterEvidenceTracks"
        :key="item.title"
        class="defense-evidence-item"
      >
        <div class="defense-evidence-item__head">
          <div>
            <div class="defense-evidence-item__title">{{ item.title }}</div>
            <p class="mt-2 defense-evidence-item__text">{{ item.instance }}</p>
          </div>
          <span class="defense-inline-tag">{{ item.tag }}</span>
        </div>
        <div class="defense-evidence-item__note">{{ item.evidence }}</div>
        <div class="mt-4">
          <el-button size="small" plain @click="openClusterEvidence(item)">{{ item.action }}</el-button>
        </div>
      </article>
    </section>

    <div v-if="clusterActiveTab === 'principle'" class="defense-surface p-6">
      <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
        <div class="max-w-4xl">
          <h2 class="text-xl font-bold text-gray-800 dark:text-gray-100">分群公式</h2>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-tag type="primary" effect="dark" round>{{ clusterModeLabel }}</el-tag>
          <el-tag type="success" effect="plain" round>实际 K = {{ currentActualClusterCount }}</el-tag>
          <el-tag type="warning" effect="plain" round>冷启动: 90天订单=0 且 30天行为&lt;3</el-tag>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
        <div
          v-for="item in clusterExplainCards"
          :key="item.title"
          class="cluster-inline-block bg-gradient-to-br p-5"
          :class="item.bg"
        >
          <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ item.title }}</div>
          <div class="mt-4 rounded-xl bg-slate-900 px-4 py-3 text-[11px] leading-6 text-slate-100 font-mono whitespace-pre-line overflow-x-auto">
            {{ item.formula }}
          </div>
        </div>
      </div>

      <div class="mt-6 grid grid-cols-1 xl:grid-cols-3 gap-4">
        <div class="xl:col-span-2 cluster-inline-block cluster-inline-block--muted p-5">
          <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">分群区分条件</div>
          <div class="mt-4 overflow-hidden cluster-inline-block cluster-inline-block--plain">
            <div
              v-for="rule in segmentDecisionRows"
              :key="rule.name"
              class="grid gap-3 border-t border-slate-200/80 px-4 py-4 first:border-t-0 dark:border-slate-700/70 md:grid-cols-[0.92fr_1.16fr_1fr]"
            >
              <div>
                <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ rule.name }}</div>
                <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">{{ rule.scope }}</div>
              </div>
              <div class="cluster-trigger-chip px-3 py-2 font-mono text-[11px] leading-5 text-slate-100">{{ rule.trigger }}</div>
            </div>
          </div>
        </div>

        <div class="cluster-inline-block cluster-inline-block--accent p-5">
          <div class="text-sm font-semibold text-blue-700 dark:text-blue-300">本批次实际参数</div>
          <div class="mt-4 space-y-4">
            <div v-for="item in kmeansCurrentFacts" :key="item.label" class="cluster-inline-block cluster-inline-block--plain px-4 py-4">
              <div class="text-xs text-gray-500">{{ item.label }}</div>
              <div class="mt-1 text-base font-semibold text-gray-900 dark:text-gray-100">{{ item.value }}</div>
              <div class="mt-2 text-xs leading-5 text-gray-500 dark:text-gray-400">{{ item.sub }}</div>
            </div>
          </div>
        </div>
      </div>

      <PageSectionTabs
        v-model="clusterActiveTab"
        primary-label="管理端"
        page-label="用户分群"
        title="专题切换"
        description=""
        :tabs="clusterTabs"
        :active-label="clusterActiveTabInfo.label"
      />
    </div>

    <template v-if="clusterActiveTab === 'tasks'">
      <PageSectionTabs
        v-model="clusterActiveTab"
        primary-label="管理端"
        page-label="用户分群"
        title="专题切换"
        description=""
        :tabs="clusterTabs"
        :active-label="clusterActiveTabInfo.label"
      />
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      <div class="xl:col-span-2 defense-surface p-6">
        <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div>
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-100">分群任务</h2>
          </div>
          <div class="flex flex-wrap gap-2">
            <el-tag :type="taskRuntime.runningInDatabase || taskRuntime.triggerInProgress ? 'warning' : 'success'" effect="light">
              {{ taskRuntime.runningInDatabase || taskRuntime.triggerInProgress ? '有任务运行中' : '当前空闲' }}
            </el-tag>
            <el-tag type="info" effect="plain">脚本 {{ taskRuntime.scriptReady ? '已就绪' : '未找到' }}</el-tag>
          </div>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-5 gap-3 mb-5">
          <el-date-picker
            v-model="triggerForm.snapshotDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择快照日期"
            class="!w-full lg:col-span-2"
          />
          <el-input-number v-model="triggerForm.k" :min="2" :max="8" class="!w-full" />
          <el-input-number v-model="triggerForm.minK" :min="2" :max="8" class="!w-full" />
          <el-input-number v-model="triggerForm.maxK" :min="2" :max="8" class="!w-full" />
        </div>

        <div class="flex flex-wrap items-center justify-between gap-3 mb-5">
          <div class="flex flex-wrap items-center gap-3">
            <span class="text-sm text-gray-500">自动选 K</span>
            <el-switch v-model="triggerForm.autoK" />
            <span class="text-xs text-gray-400">固定 K=3 · 可扩展自动选 K</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <el-button @click="loadTaskHistory(1)">刷新历史</el-button>
            <el-button type="primary" :loading="triggerSubmitting" @click="triggerTask">手动执行分群</el-button>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-3 mb-5">
          <div class="cluster-inline-block cluster-inline-block--muted p-4">
            <div class="text-xs text-gray-500">最近一次手动触发</div>
            <div class="mt-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
              {{ formatDateTime(taskRuntime.lastManualLaunch?.triggeredAt || taskRuntime.lastManualLaunch?.startedAt) }}
            </div>
            <div class="mt-1 text-xs text-gray-500">状态：{{ taskRuntime.lastManualLaunch?.status || '--' }}</div>
          </div>
          <div class="cluster-inline-block cluster-inline-block--muted p-4">
            <div class="text-xs text-gray-500">执行脚本</div>
            <div class="mt-2 text-sm font-semibold text-gray-800 dark:text-gray-100 break-all">
              {{ taskRuntime.scriptPath || '--' }}
            </div>
          </div>
          <div class="cluster-inline-block cluster-inline-block--muted p-4">
            <div class="text-xs text-gray-500">命令预览</div>
            <div class="mt-2 text-sm font-semibold text-gray-800 dark:text-gray-100 break-all">
              {{ taskRuntime.lastManualLaunch?.commandPreview || '--' }}
            </div>
          </div>
        </div>

        <el-table
          v-loading="taskHistoryLoading"
          :data="taskHistoryRecords"
          size="small"
          border
          class="!bg-transparent"
        >
          <el-table-column prop="batchNo" label="批次号" min-width="170" />
          <el-table-column prop="snapshotDate" label="快照日期" width="120" />
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.finalStatus)" effect="light">{{ row.finalStatus || '--' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="120" align="center">
            <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
          </el-table-column>
          <el-table-column label="聚类用户" width="110" align="center">
            <template #default="{ row }">{{ formatNumber(row.clusteredUserCount) }}</template>
          </el-table-column>
          <el-table-column label="轮廓系数" width="120" align="center">
            <template #default="{ row }">{{ formatDecimal(row.silhouetteScore, 4) }}</template>
          </el-table-column>
          <el-table-column label="开始时间" min-width="170">
            <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
          </el-table-column>
          <el-table-column label="数据来源" width="100" align="center">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ row.dataSource || 'mysql' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="pt-4 flex justify-end">
          <el-pagination
            v-model:current-page="taskHistoryPagination.page"
            v-model:page-size="taskHistoryPagination.size"
            background
            layout="total, prev, pager, next"
            :total="taskHistoryPagination.total"
            @current-change="loadTaskHistory"
          />
        </div>
      </div>

      <div class="defense-surface p-6">
        <h2 class="text-xl font-bold text-gray-800 dark:text-gray-100 mb-4">数据新鲜度</h2>
        <div class="space-y-4">
          <div class="cluster-inline-block cluster-inline-block--accent p-4">
            <div class="text-sm font-semibold text-blue-700 dark:text-blue-300">当前读取来源</div>
            <div class="mt-2 text-2xl font-bold text-gray-800 dark:text-gray-100">{{ latestDataSource }}</div>
            <div class="mt-1 text-xs text-gray-500">快照日期 {{ freshness.snapshotDate || '--' }}</div>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div class="cluster-inline-block cluster-inline-block--muted p-4">
              <div class="text-xs text-gray-500">最近任务时间</div>
              <div class="mt-2 text-sm font-semibold text-gray-800 dark:text-gray-100">
                {{ formatDateTime(freshness.lastTaskTime) }}
              </div>
            </div>
            <div class="cluster-inline-block cluster-inline-block--muted p-4">
              <div class="text-xs text-gray-500">缓存策略</div>
              <div class="mt-2 text-sm font-semibold text-gray-800 dark:text-gray-100 break-all">
                {{ freshness.cacheStrategy || '--' }}
              </div>
            </div>
          </div>

          <div class="cluster-inline-block cluster-inline-block--success p-4">
            <div class="text-sm font-semibold text-emerald-700 dark:text-emerald-300">MySQL / Redis</div>
            <div class="mt-3 text-xs text-gray-500">MySQL</div>
            <div class="mt-2 flex flex-wrap gap-2">
              <el-tag
                v-for="item in storageBoundary.mysql || []"
                :key="`mysql-${item}`"
                type="success"
                effect="plain"
              >
                {{ item }}
              </el-tag>
            </div>
            <div class="mt-4 text-xs text-gray-500">Redis</div>
            <div class="mt-2 flex flex-wrap gap-2">
              <el-tag
                v-for="item in storageBoundary.redis || []"
                :key="`redis-${item}`"
                type="warning"
                effect="plain"
              >
                {{ item }}
              </el-tag>
            </div>
            <div class="mt-4 text-xs text-gray-500">规则：{{ storageBoundary.rule || '--' }}</div>
          </div>
        </div>
      </div>
    </div>
    </template>

    <template v-if="clusterActiveTab === 'personas'">
      <PageSectionTabs
        v-model="clusterActiveTab"
        primary-label="管理端"
        page-label="用户分群"
        title="专题切换"
        description=""
        :tabs="clusterTabs"
        :active-label="clusterActiveTabInfo.label"
      />
    <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
      <div class="defense-surface p-6">
        <div class="flex items-center justify-between gap-3 mb-4">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">分群用户分布</h3>
          <el-tag type="info" effect="plain">共 {{ segments.length }} 个分群</el-tag>
        </div>
        <div v-if="segments.length" ref="distributionChartRef" class="h-80 w-full"></div>
        <el-empty v-else description="暂无分群数据" />
      </div>

      <div class="defense-surface p-6">
        <div class="flex items-center justify-between gap-3 mb-4">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">分群价值对比</h3>
          <el-tag type="success" effect="plain">按人数与客单价对照</el-tag>
        </div>
        <div v-if="segments.length" ref="valueChartRef" class="h-80 w-full"></div>
        <el-empty v-else description="暂无对比数据" />
      </div>
    </div>
    <div class="space-y-4">
      <div class="flex items-center justify-between gap-4">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">分群画像</h3>
        <el-button @click="loadOverview">刷新分群结果</el-button>
      </div>

      <div v-if="segments.length" class="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <button
          v-for="segment in segments"
          :key="segment.segmentCode"
          type="button"
          class="w-full text-left defense-surface p-6 transition-colors duration-200"
          :class="segmentCardClass(segment)"
          @click="selectSegment(segment.segmentCode)"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <div class="flex flex-wrap items-center gap-2">
                <h4 class="text-lg font-bold text-gray-800 dark:text-gray-100">{{ segment.segmentName || segment.segmentCode }}</h4>
                <el-tag size="small" effect="plain">{{ segment.segmentCode }}</el-tag>
                <el-tag v-if="summaryMetrics.bestSegmentCode === segment.segmentCode" size="small" type="success">最佳分群</el-tag>
                <el-tag v-if="selectedSegmentCode === segment.segmentCode" size="small" type="primary">查看中</el-tag>
              </div>
            </div>
            <div class="text-right min-w-0 sm:min-w-[96px]">
              <div class="text-2xl font-bold text-gray-800 dark:text-gray-100">{{ formatNumber(segment.userCount) }}</div>
              <div class="text-xs text-gray-500 mt-1">用户数 / {{ formatPercent(segment.percentage) }}</div>
            </div>
          </div>

          <div class="grid grid-cols-2 md:grid-cols-4 gap-3 mt-5">
            <div class="p-3 rounded-xl bg-blue-50 dark:bg-blue-900/20">
              <div class="text-xs text-gray-500">90天订单金额</div>
              <div class="mt-1 font-semibold text-blue-600">{{ formatCurrency(segment.avgOrderAmount90d) }}</div>
            </div>
            <div class="p-3 rounded-xl bg-emerald-50 dark:bg-emerald-900/20">
              <div class="text-xs text-gray-500">90天订单数</div>
              <div class="mt-1 font-semibold text-emerald-600">{{ formatDecimal(segment.avgOrderCount90d) }}</div>
            </div>
            <div class="p-3 rounded-xl bg-amber-50 dark:bg-amber-900/20">
              <div class="text-xs text-gray-500">30天行为数</div>
              <div class="mt-1 font-semibold text-amber-600">{{ formatDecimal(segment.avgBehaviorCount30d) }}</div>
            </div>
            <div class="p-3 rounded-xl bg-purple-50 dark:bg-purple-900/20">
              <div class="text-xs text-gray-500">最近下单距今天数</div>
              <div class="mt-1 font-semibold text-purple-600">{{ formatDecimal(segment.avgRecencyDays) }}</div>
            </div>
          </div>

          <div class="mt-5 grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <div class="text-sm font-medium text-gray-600 dark:text-gray-300 mb-2">偏好品类</div>
              <div class="flex flex-wrap gap-2">
                <el-tag
                  v-for="item in segment.topCategories || []"
                  :key="`${segment.segmentCode}-category-${item}`"
                  size="small"
                  type="info"
                  effect="plain"
                >
                  {{ item }}
                </el-tag>
                <span v-if="!(segment.topCategories || []).length" class="text-xs text-gray-400">暂无偏好品类</span>
              </div>
            </div>
            <div>
              <div class="text-sm font-medium text-gray-600 dark:text-gray-300 mb-2">偏好标签</div>
              <div class="flex flex-wrap gap-2">
                <el-tag
                  v-for="item in segment.topTags || []"
                  :key="`${segment.segmentCode}-tag-${item}`"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  {{ item }}
                </el-tag>
                <span v-if="!(segment.topTags || []).length" class="text-xs text-gray-400">暂无偏好标签</span>
              </div>
            </div>
          </div>

          <div class="panel-card--muted mt-5 p-4">
            <div class="flex flex-wrap items-center justify-between gap-3 mb-3">
              <div class="text-sm font-semibold text-gray-700 dark:text-gray-200">分群初始特征数据</div>
              <el-tag size="small" type="info" effect="plain">
                共 {{ segmentFeatureRows(segment).length }} 项
              </el-tag>
            </div>

            <div v-if="segmentFeatureRows(segment).length" class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div
                v-for="item in segmentFeatureRows(segment)"
                :key="`${segment.segmentCode}-feature-${item.key}`"
                class="panel-card--muted px-4 py-3"
              >
                <div class="text-xs text-gray-500">{{ item.label }}</div>
                <div class="mt-1 text-sm font-semibold text-gray-800 dark:text-gray-100">
                  {{ formatSegmentFeatureValue(item.key, item.value) }}
                </div>
              </div>
            </div>

            <div v-else class="text-xs text-gray-400">暂无该分群的初始特征数据</div>
          </div>

          <div class="panel-card--muted mt-5 p-4">
            <div class="text-sm font-semibold text-gray-700 dark:text-gray-200 mb-2">动作建议</div>
          </div>

          <div v-if="(segment.activationPlan?.actions || []).length" class="panel-card--muted mt-5 p-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="text-sm font-semibold text-gray-700 dark:text-gray-200">落地动作</div>
              </div>
              <el-tag :type="segment.activationPlan?.priority === 'high' ? 'danger' : 'warning'" effect="light">
                {{ segment.activationPlan?.priority === 'high' ? '优先落地' : '常规落地' }}
              </el-tag>
            </div>

            <div class="mt-4 grid grid-cols-1 md:grid-cols-3 gap-3">
              <div
                v-for="action in segment.activationPlan.actions || []"
                :key="`${segment.segmentCode}-${action.type}`"
                class="panel-card--muted p-4"
              >
                <div class="text-sm font-semibold text-gray-800 dark:text-gray-100">{{ action.title }}</div>
                <div class="mt-3 text-xs text-gray-500">关注指标：{{ action.metric }}</div>
              </div>
            </div>
          </div>
        </button>
      </div>
      <div v-else class="defense-surface p-6">
        <el-empty description="暂无分群数据" />
      </div>
    </div>
    </template>
    <div v-if="clusterActiveTab === 'personas'" class="defense-surface overflow-hidden">
      <div class="p-6 pb-4 flex flex-wrap gap-4 items-center justify-between">
        <div class="flex flex-wrap gap-4 items-center">
          <el-select v-model="selectedSegmentCode" class="w-full sm:w-72" placeholder="选择分群" clearable @change="handleSegmentChange">
            <el-option label="全部分群" value="" />
            <el-option
              v-for="segment in segments"
              :key="segment.segmentCode"
              :label="`${segment.segmentName} (${segment.segmentCode})`"
              :value="segment.segmentCode"
            />
          </el-select>
          <el-button type="primary" @click="loadUsers(1)">刷新用户</el-button>
        </div>
        <div class="text-sm text-gray-500">
          样本 {{ formatNumber(pagination.total) }}
        </div>
      </div>

      <div v-if="currentSegment" class="panel-card--muted mx-6 mb-4 p-4">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="flex-1 min-w-0 sm:min-w-[280px]">
            <div class="flex flex-wrap items-center gap-2">
              <div class="text-base font-semibold text-gray-800 dark:text-gray-100">{{ currentSegment.segmentName }}</div>
              <el-tag size="small" effect="plain">{{ currentSegment.segmentCode }}</el-tag>
            </div>
          </div>
          <div class="flex flex-wrap gap-2">
            <el-tag type="primary">人数 {{ formatNumber(currentSegment.userCount) }}</el-tag>
            <el-tag type="success">占比 {{ formatPercent(currentSegment.percentage) }}</el-tag>
            <el-tag type="warning">30天行为 {{ formatDecimal(currentSegment.avgBehaviorCount30d) }}</el-tag>
          </div>
        </div>
      </div>

      <el-table
        v-loading="tableLoading"
        :data="users"
        class="!bg-transparent custom-table"
        :header-cell-style="{ background: 'transparent', color: 'inherit' }"
        :row-style="{ background: 'transparent' }"
      >
        <el-table-column prop="userId" label="用户ID" width="90" />
        <el-table-column label="用户信息" min-width="220">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <el-avatar :size="40" :src="row.avatar">
                {{ (row.nickname || row.username || '?').charAt(0) }}
              </el-avatar>
              <div class="min-w-0">
                <div class="font-medium text-gray-900 dark:text-gray-100 truncate">
                  {{ row.nickname || row.username || `用户${row.userId}` }}
                </div>
                <div class="text-xs text-gray-500 truncate">
                  @{{ row.username || '--' }} · {{ row.phone || '未绑定手机号' }}
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="所属分群" width="170">
          <template #default="{ row }">
            <div class="space-y-1">
              <el-tag size="small" :type="row.isColdStart ? 'warning' : 'primary'">{{ row.segmentName || row.segmentCode || '--' }}</el-tag>
              <div class="text-xs text-gray-400">{{ row.segmentCode || '--' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="toPercent(row.confidenceScore)"
              :status="row.isColdStart ? 'warning' : undefined"
              :stroke-width="8"
              :show-text="false"
            />
            <div class="text-xs text-gray-500 mt-1">{{ formatPercent(row.confidenceScore) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="90天订单金额" width="130" align="right">
          <template #default="{ row }">{{ formatCurrency(row.orderAmount90d) }}</template>
        </el-table-column>
        <el-table-column label="30天行为数" prop="behaviorCount30d" width="120" align="center" />
        <el-table-column label="最近行为距今天数" prop="recencyBehaviorDays" width="150" align="center" />
        <el-table-column label="画像摘要" min-width="280">
          <template #default="{ row }">
            <span class="text-sm text-gray-600 dark:text-gray-300">{{ row.personaSummary || '--' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openUserDetail(row)">查看画像</el-button>
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
          @current-change="loadUsers"
        />
      </div>
    </div>
    <el-drawer v-model="detailVisible" title="用户分群详情" size="min(92vw, 640px)">
      <div v-loading="detailLoading" class="space-y-6">
        <div v-if="detail.userId" class="space-y-6">
          <div class="flex items-center gap-4 p-4 rounded-2xl bg-gray-50 dark:bg-gray-700/40">
            <el-avatar :size="64" :src="detail.avatar">
              {{ (detail.nickname || detail.username || '?').charAt(0) }}
            </el-avatar>
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">
                  {{ detail.nickname || detail.username || `用户${detail.userId}` }}
                </h3>
                <el-tag size="small" :type="detail.isColdStart ? 'warning' : 'primary'">
                  {{ detail.segmentName || detail.segmentCode || '--' }}
                </el-tag>
              </div>
              <p class="text-sm text-gray-500 mt-1">
                @{{ detail.username || '--' }} · {{ detail.phone || '未绑定手机号' }}
              </p>
              <p class="text-sm text-gray-500 mt-1">
                注册时间 {{ formatDateTime(detail.userCreateTime) }}
              </p>
            </div>
          </div>

          <div class="rounded-2xl bg-blue-50/80 dark:bg-blue-900/20 border border-blue-100 dark:border-blue-800/40 p-4">
            <div class="text-sm font-semibold text-blue-700 dark:text-blue-300">为什么这个用户会被分到当前分群</div>
            <p v-if="Number(detail.isColdStart) === 1" class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
              该用户满足冷启动规则：近90天订单数为 0，且近30天行为数小于 3，因此不会进入 KMeans 计算，而是直接归入 `COLD_START` 观察人群。
            </p>
            <p v-else class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
              系统先对订单、行为和注册时长等特征做 `StandardScaler` 标准化，再计算用户与每个簇中心的距离，距离最小的簇就是当前分群。该用户当前距离中心
              {{ formatDecimal(detail.distanceToCenter, 6) }}，分群置信度 {{ formatPercent(detail.confidenceScore) }}。
            </p>
          </div>

          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="p-4 rounded-2xl bg-blue-50 dark:bg-blue-900/20 text-center">
              <div class="text-xl font-bold text-blue-600">{{ formatPercent(detail.confidenceScore) }}</div>
              <div class="text-xs text-gray-500 mt-1">分群置信度</div>
            </div>
            <div class="p-4 rounded-2xl bg-emerald-50 dark:bg-emerald-900/20 text-center">
              <div class="text-xl font-bold text-emerald-600">{{ formatCurrency(detail.orderAmount90d) }}</div>
              <div class="text-xs text-gray-500 mt-1">90天订单金额</div>
            </div>
            <div class="p-4 rounded-2xl bg-amber-50 dark:bg-amber-900/20 text-center">
              <div class="text-xl font-bold text-amber-600">{{ formatNumber(detail.behaviorCount30d) }}</div>
              <div class="text-xs text-gray-500 mt-1">30天行为数</div>
            </div>
            <div class="p-4 rounded-2xl bg-purple-50 dark:bg-purple-900/20 text-center">
              <div class="text-xl font-bold text-purple-600">{{ formatNumber(detail.activeDays30d) }}</div>
              <div class="text-xs text-gray-500 mt-1">30天活跃天数</div>
            </div>
          </div>

          <div class="p-4 rounded-2xl bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700">
            <div class="text-sm font-semibold text-gray-700 dark:text-gray-200 mb-2">用户画像摘要</div>
            <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">
              {{ detail.personaSummary || '暂无画像摘要' }}
            </p>
          </div>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="90天订单数">{{ formatNumber(detail.orderCount90d) }}</el-descriptions-item>
            <el-descriptions-item label="90天平均客单价">{{ formatCurrency(detail.avgOrderAmount90d) }}</el-descriptions-item>
            <el-descriptions-item label="90天品类数">{{ formatNumber(detail.distinctCategoryCount90d) }}</el-descriptions-item>
            <el-descriptions-item label="30天浏览数">{{ formatNumber(detail.viewCount30d) }}</el-descriptions-item>
            <el-descriptions-item label="30天加购数">{{ formatNumber(detail.cartCount30d) }}</el-descriptions-item>
            <el-descriptions-item label="30天收藏数">{{ formatNumber(detail.favoriteCount30d) }}</el-descriptions-item>
            <el-descriptions-item label="30天购买行为数">{{ formatNumber(detail.purchaseBehaviorCount30d) }}</el-descriptions-item>
            <el-descriptions-item label="平均停留时长">{{ formatDecimal(detail.avgDuration30d) }}</el-descriptions-item>
            <el-descriptions-item label="最近下单距今天数">{{ formatNumber(detail.recencyOrderDays) }}</el-descriptions-item>
            <el-descriptions-item label="最近行为距今天数">{{ formatNumber(detail.recencyBehaviorDays) }}</el-descriptions-item>
            <el-descriptions-item label="注册时长">{{ formatNumber(detail.tenureDays) }}</el-descriptions-item>
            <el-descriptions-item label="批次号">{{ detail.task?.batchNo || '--' }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.segment" class="p-4 rounded-2xl bg-cyan-50 dark:bg-cyan-900/20 border border-cyan-100 dark:border-cyan-800/40">
            <div class="flex flex-wrap items-center gap-2 mb-2">
              <div class="text-sm font-semibold text-cyan-700 dark:text-cyan-300">所属分群策略</div>
              <el-tag size="small" type="info" effect="plain">{{ detail.segment.segmentCode }}</el-tag>
            </div>
            <p class="text-sm leading-6 text-gray-600 dark:text-gray-300">
              {{ detail.segment.operationSuggestion || detail.segment.segmentDescription || '暂无策略说明' }}
            </p>
          </div>

          <div v-if="detail.segment?.activationPlan?.actions?.length" class="rounded-2xl bg-slate-50/90 dark:bg-slate-900/40 border border-slate-200/70 dark:border-slate-700/60 p-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="text-sm font-semibold text-gray-700 dark:text-gray-200">该分群的落地动作</div>
                <p class="mt-1 text-sm leading-6 text-gray-600 dark:text-gray-300">
                  {{ detail.segment.activationPlan.summary }}
                </p>
              </div>
              <el-tag :type="detail.segment.activationPlan.priority === 'high' ? 'danger' : 'warning'" effect="light">
                {{ detail.segment.activationPlan.priority === 'high' ? '优先执行' : '常规执行' }}
              </el-tag>
            </div>

            <div class="mt-4 grid grid-cols-1 lg:grid-cols-3 gap-3">
              <div
                v-for="action in detail.segment.activationPlan.actions || []"
                :key="`detail-action-${action.type}`"
                class="rounded-2xl bg-white/90 dark:bg-gray-800/80 border border-white/70 dark:border-gray-700/60 p-4"
              >
                <div class="text-sm font-semibold text-gray-800 dark:text-gray-100">{{ action.title }}</div>
                <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">{{ action.description }}</p>
                <div class="mt-3 text-xs text-gray-500">关注指标：{{ action.metric }}</div>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div class="rounded-2xl border border-gray-100 dark:border-gray-700 overflow-hidden">
              <div class="px-4 py-3 bg-gray-50 dark:bg-gray-800/60 text-sm font-semibold text-gray-700 dark:text-gray-200">
                原始特征
              </div>
              <el-table :data="rawFeatureRows" size="small" max-height="280">
                <el-table-column prop="label" label="特征" min-width="160" />
                <el-table-column prop="value" label="值" min-width="120" align="right">
                  <template #default="{ row }">{{ formatLooseValue(row.value) }}</template>
                </el-table-column>
              </el-table>
            </div>

            <div class="rounded-2xl border border-gray-100 dark:border-gray-700 overflow-hidden">
              <div class="px-4 py-3 bg-gray-50 dark:bg-gray-800/60 text-sm font-semibold text-gray-700 dark:text-gray-200">
                标准化特征
              </div>
              <el-table :data="normalizedFeatureRows" size="small" max-height="280">
                <el-table-column prop="label" label="特征" min-width="160" />
                <el-table-column prop="value" label="值" min-width="120" align="right">
                  <template #default="{ row }">{{ formatLooseValue(row.value) }}</template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>

        <el-empty v-else description="暂无用户画像数据" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'
import {
  getAdminKmeansLatestTask,
  getAdminKmeansSegments,
  getAdminKmeansSummary,
  getAdminKmeansTaskHistory,
  getAdminKmeansUserDetail,
  getAdminKmeansUsers,
  triggerAdminKmeansTask,
} from '../../api/admin'

const route = useRoute()
const router = useRouter()
const clusterFeatureBrief = [
  { label: '判断依据', value: '订单、行为、注册时长', text: '特征进入向量。' },
  { label: '功能组成', value: '标准化 + KMeans + 冷启动', text: '标准化后聚类。' },
  { label: '输出结果', value: '人群编码与运营动作', text: '分群进入推荐与触达。' },
]
const SEGMENT_COLORS = ['#3b82f6', '#8b5cf6', '#f59e0b', '#10b981', '#ef4444', '#06b6d4']
const FIELD_LABELS = {
  order_count_90d: '90天订单数',
  order_amount_90d: '90天订单金额',
  avg_order_amount_90d: '90天平均客单价',
  distinct_category_count_90d: '90天品类数',
  behavior_count_30d: '30天行为数',
  view_count_30d: '30天浏览数',
  cart_count_30d: '30天加购数',
  favorite_count_30d: '30天收藏数',
  purchase_behavior_count_30d: '30天购买行为数',
  active_days_30d: '30天活跃天数',
  avg_duration_30d: '30天平均停留时长',
  recency_order_days: '最近下单距今天数',
  recency_behavior_days: '最近行为距今天数',
  tenure_days: '注册时长',
}

const clusterTabs = [
  { key: 'principle', label: '分群原理', hint: '建模', description: '' },
  { key: 'tasks', label: '任务管理', hint: '运维', description: '' },
  { key: 'personas', label: '分群画像', hint: '应用', description: '' },
]
const clusterHeroTags = ['14 个特征', '冷启动剔除', 'KMeans 距离', '业务阈值命名']
const clusterTabKeySet = new Set(clusterTabs.map(item => item.key))
const normalizeClusterTab = (value) => {
  const tab = String(value || '').trim()
  return clusterTabKeySet.has(tab) ? tab : clusterTabs[0].key
}

const pageLoading = ref(false)
const tableLoading = ref(false)
const detailLoading = ref(false)
const taskHistoryLoading = ref(false)
const triggerSubmitting = ref(false)
const latestTask = ref({})
const summary = ref({})
const segments = ref([])
const users = ref([])
const taskHistoryState = ref({})
const selectedSegmentCode = ref('')
const clusterActiveTab = ref(normalizeClusterTab(route.query.tab))
const detailVisible = ref(false)
const detail = ref({})
const distributionChartRef = ref(null)
const valueChartRef = ref(null)

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

const taskHistoryPagination = reactive({
  page: 1,
  size: 5,
  total: 0,
})

const triggerForm = reactive({
  snapshotDate: '',
  k: 3,
  autoK: true,
  minK: 2,
  maxK: 6,
})

let distributionChart = null
let valueChart = null
let themeObserver = null
let taskHistoryTimer = null
let echartsModule = null

const summaryMetrics = computed(() => summary.value.summary || {})
const featureColumns = computed(() => summary.value.featureColumns || latestTask.value.featureColumns || [])
const segmentCount = computed(() => summary.value.segmentCount || latestTask.value.clusterCount || segments.value.length || 0)
const requestedClusterCount = computed(() => summaryMetrics.value.requestedClusterCount || segmentCount.value)
const currentActualClusterCount = computed(() =>
  Number(summaryMetrics.value.actualClusterCount || latestTask.value.clusterCount || segments.value.filter(item => item.segmentCode !== 'COLD_START').length || 0)
)
const sampleUserCount = computed(() => summaryMetrics.value.sampleUserCount || latestTask.value.sampleUserCount || 0)
const clusteredUserCount = computed(() => summaryMetrics.value.clusteredUserCount || latestTask.value.clusteredUserCount || 0)
const coldStartUserCount = computed(() => summaryMetrics.value.coldStartUserCount || latestTask.value.coldStartUserCount || 0)
const clusterSummaryCards = computed(() => [
  {
    label: '分群数量',
    value: formatNumber(segmentCount.value),
    sub: '人群簇',
  },
  {
    label: '样本用户数',
    value: formatNumber(sampleUserCount.value),
    sub: '建模样本',
  },
  {
    label: '进入聚类用户',
    value: formatNumber(clusteredUserCount.value),
    sub: 'KMeans 样本',
  },
  {
    label: '冷启动观察用户',
    value: formatNumber(coldStartUserCount.value),
    sub: '单独观察',
  },
  {
    label: '轮廓系数',
    value: formatDecimal(latestTask.value.silhouetteScore, 4),
    sub: '质量指标',
  },
])
const clusterSelection = computed(() => summaryMetrics.value.clusterSelection || {})
const clusterModeLabel = computed(() => clusterSelection.value.mode === 'auto_silhouette' ? '轮廓系数自动选 K' : '固定 K')
const clusteredRate = computed(() => {
  const sample = Number(sampleUserCount.value || 0)
  const clustered = Number(clusteredUserCount.value || 0)
  return sample ? clustered / sample : 0
})
const bestSegment = computed(() =>
  segments.value.find(item => item.segmentCode === summaryMetrics.value.bestSegmentCode) || null
)
const currentSegment = computed(() =>
  segments.value.find(item => item.segmentCode === selectedSegmentCode.value) || null
)
const rawFeatureRows = computed(() => mapObjectRows(detail.value.rawFeatures))
const normalizedFeatureRows = computed(() => mapObjectRows(detail.value.normalizedFeatures))
const taskHistoryRecords = computed(() => taskHistoryState.value.records || [])
const freshness = computed(() => summary.value.freshness || latestTask.value.freshness || {})
const storageBoundary = computed(() => summary.value.storageBoundary || latestTask.value.storageBoundary || {})
const latestDataSource = computed(() => summary.value.dataSource || latestTask.value.dataSource || '--')
const taskRuntime = computed(() => taskHistoryState.value.runtime || {})
const clusterActiveTabInfo = computed(() => clusterTabs.find(item => item.key === clusterActiveTab.value) || clusterTabs[0])
const clusterExplainCards = computed(() => [
  {
    title: '1. 特征工程',
    description: '订单 + 行为 + 注册',
    formula: `featureCount=${featureColumns.value.length}\n90d: order_count, order_amount,\navg_order_amount, category_count,\nrecency_order_days\n30d: behavior/view/cart/favorite/\npurchase/active_days/duration\nbase: tenure_days`,
    bg: 'from-blue-50 to-cyan-50 dark:from-blue-900/20 dark:to-cyan-900/20',
  },
  {
    title: '2. 冷启动筛选',
    description: '低样本剔除',
    formula: 'if order_count_90d = 0\nand behavior_count_30d < 3\n=> segment = COLD_START',
    bg: 'from-amber-50 to-orange-50 dark:from-amber-900/20 dark:to-orange-900/20',
  },
  {
    title: '3. 标准化 + KMeans',
    description: '标准化 + 最近中心',
    formula: 'z = StandardScaler(x)\nlabel = argmin || z - center_k ||\nKMeans(random_state=42, n_init=10)\n当前实际 K = ' + currentActualClusterCount.value,
    bg: 'from-emerald-50 to-teal-50 dark:from-emerald-900/20 dark:to-teal-900/20',
  },
  {
    title: '4. 置信度与最佳分群',
    description: '距离越近，置信越高',
    formula: 'confidence = 1 - distance / maxDistance\nbestSegment sort by:\navg_order_amount desc,\navg_behavior_count desc,\navg_recency_days asc',
    bg: 'from-purple-50 to-pink-50 dark:from-purple-900/20 dark:to-pink-900/20',
  },
])
const segmentNamingRules = [
  {
    name: '高价值活跃用户',
    trigger: 'avg_amount >= 500 && avg_orders >= 2 && avg_recency <= 30',
    description: '消费高、复购高、近期活跃，适合重点做会员权益、专属券和新品首发。',
  },
  {
    name: '高意向待转化用户',
    trigger: 'avg_behavior >= 12 && avg_amount < 300',
    description: '浏览、加购、收藏较多，但订单金额偏低，说明转化空间很明显。',
  },
  {
    name: '沉睡低活跃用户',
    trigger: 'avg_recency >= 60 && avg_behavior < 8',
    description: '近期访问和购买都偏弱，已经出现沉睡趋势，适合做召回券和唤醒活动。',
  },
  {
    name: '稳定消费用户',
    trigger: 'else',
    description: '消费和活跃度处于中间水平，适合通过常规促活和交叉销售持续提升价值。',
  },
]
const segmentDecisionRows = [
  {
    name: '冷启动观察用户',
    scope: '先于 KMeans 执行',
    trigger: 'order_count_90d <= 0\n&& behavior_count_30d < 3',
    description: '样本少，不参与聚类。',
  },
  {
    name: '高价值活跃用户',
    scope: '聚类后按簇均值命名',
    trigger: 'avg_amount >= 500\n&& avg_orders >= 2\n&& avg_recency <= 30',
    description: '高客单、高频、近期活跃。',
  },
  {
    name: '高意向待转化用户',
    scope: '聚类后按簇均值命名',
    trigger: 'avg_behavior >= 12\n&& avg_amount < 300',
    description: '高行为、低消费。',
  },
  {
    name: '沉睡低活跃用户',
    scope: '聚类后按簇均值命名',
    trigger: 'avg_recency >= 60\n&& avg_behavior < 8',
    description: '低活跃、待召回。',
  },
  {
    name: '稳定消费用户',
    scope: '兜底命名',
    trigger: 'else',
    description: '中位用户。',
  },
]
const kmeansCurrentFacts = computed(() => [
  {
    label: '聚类模式',
    value: clusterModeLabel.value,
    sub: clusterSelection.value.mode === 'auto_silhouette'
      ? '候选 K'
      : '固定 K',
  },
  {
    label: '请求 / 实际 K',
    value: `${formatNumber(requestedClusterCount.value)} / ${formatNumber(currentActualClusterCount.value)}`,
    sub: '任务参数',
  },
  {
    label: '轮廓系数 / 惯性',
    value: `${formatDecimal(latestTask.value.silhouetteScore, 4)} / ${formatDecimal(latestTask.value.inertiaScore, 6)}`,
    sub: '质量',
  },
  {
    label: '最佳分群',
    value: bestSegment.value ? `${bestSegment.value.segmentName} (${bestSegment.value.segmentCode})` : (summaryMetrics.value.bestSegmentCode || '--'),
    sub: '运营重点',
  },
])
const clusterEvidenceTracks = computed(() => [
  {
    title: '分群建模实例',
    tag: '建模',
    type: 'primary',
    instance: `K=${formatNumber(currentActualClusterCount.value)} · 轮廓 ${formatDecimal(latestTask.value.silhouetteScore, 4)}`,
    evidence: '真实特征聚类',
    action: '查看分群原理',
    tab: 'principle',
  },
  {
    title: '任务运维实例',
    tag: taskRuntime.value.runningInDatabase || taskRuntime.value.triggerInProgress ? '运行中' : '运维',
    type: taskRuntime.value.runningInDatabase || taskRuntime.value.triggerInProgress ? 'warning' : 'success',
    instance: taskHistoryRecords.value.length
      ? `${taskHistoryRecords.value[0].batchNo || '--'} · ${taskHistoryRecords.value[0].finalStatus || '--'}`
      : '暂无历史',
    evidence: '可触发 / 可追踪',
    action: '查看任务管理',
    tab: 'tasks',
  },
  {
    title: '画像运营实例',
    tag: bestSegment.value ? '人群' : '待加载',
    type: bestSegment.value ? 'danger' : 'info',
    instance: bestSegment.value
      ? `${bestSegment.value.segmentName} · 优先触达`
      : '等待分群',
    evidence: bestSegment.value?.operationSuggestion ? '运营动作已生成' : '待生成动作',
    action: '查看分群画像',
    tab: 'personas',
  },
  {
    title: '推荐联动实例',
    tag: '联动',
    type: 'warning',
    instance: bestSegment.value
      ? `${bestSegment.value.segmentName} · 推荐联动`
      : '等待联动',
    evidence: '推荐闭环',
    action: '查看推荐分析',
    route: '/admin/analytics/recommend',
  },
])
const clusterHeroEvidence = computed(() => {
  const activeEvidence = clusterEvidenceTracks.value.find(item => item.tab === clusterActiveTab.value)
  return activeEvidence?.instance || clusterEvidenceTracks.value[0]?.instance || '等待分群证据回流'
})
const clusterIntroRows = computed(() => [
  {
    label: '传统做法',
    value: '统一推荐',
  },
  {
    label: '数据短板',
    value: '缺少画像',
  },
  {
    label: '冷启动难题',
    value: `冷启动 ${formatNumber(coldStartUserCount.value)}`,
  },
])
const clusterSolutionRows = computed(() => [
  {
    label: '14维特征',
    value: '订单 + 行为 + 时效',
  },
  {
    label: 'KMeans聚类',
    value: `K=${formatNumber(currentActualClusterCount.value)} · ${formatDecimal(latestTask.value.silhouetteScore, 4)}`,
  },
  {
    label: '推荐联动',
    value: bestSegment.value
      ? `${bestSegment.value.segmentName} 回流推荐`
      : '等待回流',
  },
])

function evidenceCardClass(type) {
  if (type === 'primary') return 'border-blue-200 bg-blue-50/70 dark:border-blue-800/40 dark:bg-blue-900/10'
  if (type === 'success') return 'border-emerald-200 bg-emerald-50/70 dark:border-emerald-800/40 dark:bg-emerald-900/10'
  if (type === 'warning') return 'border-amber-200 bg-amber-50/70 dark:border-amber-800/40 dark:bg-amber-900/10'
  if (type === 'danger') return 'border-rose-200 bg-rose-50/70 dark:border-rose-800/40 dark:bg-rose-900/10'
  return 'border-slate-200 bg-slate-50/80 dark:border-slate-700 dark:bg-slate-900/20'
}

function formatFieldLabel(key) {
  if (!key) return '--'
  if (FIELD_LABELS[key]) {
    return FIELD_LABELS[key]
  }
  return String(key)
    .replace(/_/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/\b\w/g, match => match.toUpperCase())
}

function formatNumber(value) {
  if (value == null || value === '') return '0'
  const number = Number(value)
  if (Number.isNaN(number)) return String(value)
  return number.toLocaleString('zh-CN')
}

function formatDecimal(value, digits = 2) {
  if (value == null || value === '') return '--'
  const number = Number(value)
  if (Number.isNaN(number)) return String(value)
  return number.toFixed(digits)
}

function formatPercent(value) {
  if (value == null || value === '') return '--'
  const number = Number(value)
  if (Number.isNaN(number)) return '--'
  const percent = number > 1 ? number : number * 100
  return `${percent.toFixed(percent >= 10 ? 1 : 2)}%`
}

function toPercent(value) {
  if (value == null || value === '') return 0
  const number = Number(value)
  if (Number.isNaN(number)) return 0
  return Math.max(0, Math.min(100, Math.round((number > 1 ? number : number * 100) * 100) / 100))
}

function formatCurrency(value) {
  if (value == null || value === '') return '¥0.00'
  const number = Number(value)
  if (Number.isNaN(number)) return '¥0.00'
  return `¥${number.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

function formatDuration(seconds) {
  if (seconds == null || seconds === '') return '--'
  const total = Number(seconds)
  if (Number.isNaN(total)) return '--'
  if (total < 60) return `${total} 秒`
  const minutes = Math.floor(total / 60)
  const remainSeconds = total % 60
  if (minutes < 60) {
    return `${minutes} 分 ${remainSeconds} 秒`
  }
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `${hours} 小时 ${remainMinutes} 分`
}

function statusTagType(status) {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'danger'
  if (status === 'running') return 'warning'
  if (status === 'accepted') return 'info'
  return 'info'
}

function formatLooseValue(value) {
  if (value == null || value === '') return '--'
  if (typeof value === 'number') {
    return Number.isInteger(value) ? formatNumber(value) : formatDecimal(value)
  }
  const number = Number(value)
  if (!Number.isNaN(number) && String(value).trim() !== '') {
    return Number.isInteger(number) ? formatNumber(number) : formatDecimal(number)
  }
  return String(value)
}

function normalizeObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) {
    return value
  }
  if (typeof value !== 'string') {
    return {}
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

function mapObjectRows(source) {
  const object = normalizeObject(source)
  return Object.entries(object).map(([key, value]) => ({
    key,
    label: formatFieldLabel(key),
    value,
  }))
}

function segmentFeatureRows(segment) {
  return mapObjectRows(segment?.featureCenter)
}

function formatSegmentFeatureValue(key, value) {
  const normalizedKey = String(key || '').toLowerCase()
  if (normalizedKey.includes('amount') || normalizedKey.includes('price')) {
    return formatCurrency(value)
  }
  if (normalizedKey.includes('score') || normalizedKey.includes('ratio')) {
    return formatDecimal(value, 4)
  }
  return formatLooseValue(value)
}

function normalizeSegments(payload) {
  if (Array.isArray(payload)) {
    return payload
  }
  if (Array.isArray(payload?.records)) {
    return payload.records
  }
  return []
}

function getSegmentColor(segmentCode) {
  const index = segments.value.findIndex(item => item.segmentCode === segmentCode)
  return SEGMENT_COLORS[index >= 0 ? index % SEGMENT_COLORS.length : 0]
}

function segmentCardClass(segment) {
  if (selectedSegmentCode.value === segment.segmentCode) {
    return 'border-blue-400/70 dark:border-blue-500/60 ring-2 ring-blue-400/30'
  }
  return 'border-white/20 dark:border-gray-700/30'
}

function selectSegment(segmentCode) {
  selectedSegmentCode.value = segmentCode || ''
  loadUsers(1)
}

function openClusterEvidence(item) {
  if (item?.route) {
    router.push(item.route)
    return
  }
  if (item?.tab) {
    clusterActiveTab.value = item.tab
  }
}

async function getEcharts() {
  if (!echartsModule) {
    echartsModule = await import('echarts')
  }
  return echartsModule
}

function disposeCharts() {
  distributionChart?.dispose()
  valueChart?.dispose()
  distributionChart = null
  valueChart = null
}

async function renderCharts() {
  if (!distributionChartRef.value || !valueChartRef.value || !segments.value.length) {
    disposeCharts()
    return
  }

  disposeCharts()

  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#d1d5db' : '#4b5563'
  const splitLine = isDark ? '#374151' : '#e5e7eb'
  const echarts = await getEcharts()

  distributionChart = echarts.init(distributionChartRef.value)
  distributionChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: params => `${params.name}<br/>用户数: ${formatNumber(params.value)}<br/>占比: ${params.percent}%`,
    },
    legend: {
      bottom: 8,
      type: 'scroll',
      textStyle: { color: textColor },
    },
    series: [{
      type: 'pie',
      radius: ['40%', '72%'],
      center: ['50%', '40%'],
      itemStyle: {
        borderRadius: 10,
        borderColor: isDark ? '#1f2937' : '#ffffff',
        borderWidth: 2,
      },
      label: { show: false },
      emphasis: { label: { show: true, color: textColor, formatter: '{b}\n{d}%' } },
      data: segments.value.map(segment => ({
        value: Number(segment.userCount || 0),
        name: segment.segmentName || segment.segmentCode,
        itemStyle: { color: getSegmentColor(segment.segmentCode) },
      })),
    }],
  })

  valueChart = echarts.init(valueChartRef.value)
  valueChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      data: ['用户数', '90天订单金额'],
      textStyle: { color: textColor },
    },
    grid: {
      left: '4%',
      right: '4%',
      top: 44,
      bottom: 72,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: segments.value.map(segment => segment.segmentName || segment.segmentCode),
      axisLabel: {
        color: textColor,
        interval: 0,
        rotate: segments.value.some(segment => String(segment.segmentName || segment.segmentCode || '').length > 6) ? 18 : 0,
        formatter: value => String(value || '').replace(/\s+/g, '\n'),
      },
      axisLine: { lineStyle: { color: splitLine } },
    },
    yAxis: [
      {
        type: 'value',
        name: '用户数',
        axisLabel: { color: textColor },
        splitLine: { lineStyle: { color: splitLine, type: 'dashed' } },
      },
      {
        type: 'value',
        name: '订单金额',
        axisLabel: {
          color: textColor,
          formatter: value => `¥${value}`,
        },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '用户数',
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: params => getSegmentColor(segments.value[params.dataIndex]?.segmentCode),
        },
        data: segments.value.map(segment => Number(segment.userCount || 0)),
      },
      {
        name: '90天订单金额',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        lineStyle: {
          width: 3,
          color: '#ef4444',
        },
        itemStyle: { color: '#ef4444' },
        data: segments.value.map(segment => Number(segment.avgOrderAmount90d || 0)),
      },
    ],
  })
}

function handleResize() {
  distributionChart?.resize()
  valueChart?.resize()
}

async function loadOverview() {
  pageLoading.value = true
  try {
    const [taskResult, summaryResult, segmentResult] = await Promise.allSettled([
      getAdminKmeansLatestTask(),
      getAdminKmeansSummary(),
      getAdminKmeansSegments(),
    ])

    latestTask.value = taskResult.status === 'fulfilled' ? taskResult.value || {} : {}
    summary.value = summaryResult.status === 'fulfilled' ? summaryResult.value || {} : {}
    segments.value = segmentResult.status === 'fulfilled'
      ? normalizeSegments(segmentResult.value)
      : []

    const segmentCodes = segments.value.map(item => item.segmentCode)
    if (selectedSegmentCode.value && !segmentCodes.includes(selectedSegmentCode.value)) {
      selectedSegmentCode.value = ''
    }
    if (!selectedSegmentCode.value && summaryMetrics.value.bestSegmentCode && segmentCodes.includes(summaryMetrics.value.bestSegmentCode)) {
      selectedSegmentCode.value = summaryMetrics.value.bestSegmentCode
    }
    if (!selectedSegmentCode.value && segmentCodes.length) {
      selectedSegmentCode.value = segmentCodes[0]
    }

    await nextTick()
    await renderCharts()
  } finally {
    pageLoading.value = false
  }
}

async function loadTaskHistory(page = taskHistoryPagination.page) {
  taskHistoryLoading.value = true
  taskHistoryPagination.page = page
  try {
    const res = await getAdminKmeansTaskHistory({
      page,
      size: taskHistoryPagination.size,
    })
    taskHistoryState.value = res || {}
    taskHistoryPagination.total = Number(res?.total || 0)
    taskHistoryPagination.page = Number(res?.current || page)
  } finally {
    taskHistoryLoading.value = false
  }
}

async function triggerTask() {
  triggerSubmitting.value = true
  try {
    await triggerAdminKmeansTask({
      snapshotDate: triggerForm.snapshotDate || undefined,
      k: triggerForm.k,
      autoK: triggerForm.autoK,
      minK: triggerForm.minK,
      maxK: triggerForm.maxK,
    })
    ElMessage.success('分群任务已提交，稍后会写入任务历史')
    await loadTaskHistory(1)
  } finally {
    triggerSubmitting.value = false
  }
}

function startTaskHistoryPolling() {
  stopTaskHistoryPolling()
  taskHistoryTimer = window.setInterval(() => {
    if (taskRuntime.value?.triggerInProgress || taskRuntime.value?.runningInDatabase) {
      loadTaskHistory(taskHistoryPagination.page)
      loadOverview()
    }
  }, 15000)
}

function stopTaskHistoryPolling() {
  if (taskHistoryTimer) {
    window.clearInterval(taskHistoryTimer)
    taskHistoryTimer = null
  }
}

async function loadUsers(page = pagination.page) {
  tableLoading.value = true
  pagination.page = page
  try {
    const res = await getAdminKmeansUsers({
      page,
      size: pagination.size,
      segmentCode: selectedSegmentCode.value || undefined,
    })
    users.value = res?.records || []
    pagination.total = Number(res?.total || 0)
    pagination.page = Number(res?.current || page)
  } finally {
    tableLoading.value = false
  }
}

function handleSegmentChange(value) {
  selectedSegmentCode.value = value || ''
  loadUsers(1)
}

async function openUserDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = {}
  try {
    const res = await getAdminKmeansUserDetail(row.userId)
    detail.value = {
      ...res,
      rawFeatures: normalizeObject(res?.rawFeatures),
      normalizedFeatures: normalizeObject(res?.normalizedFeatures),
    }
  } finally {
    detailLoading.value = false
  }
}

onMounted(async () => {
  await loadOverview()
  await loadTaskHistory(1)
  await loadUsers(1)
  startTaskHistoryPolling()
  window.addEventListener('resize', handleResize)
  themeObserver = new MutationObserver(mutations => {
    if (mutations.some(item => item.attributeName === 'class')) {
      nextTick(() => {
        void renderCharts()
      })
    }
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
})

watch(
  () => route.query.tab,
  value => {
    const nextTab = normalizeClusterTab(value)
    if (nextTab !== clusterActiveTab.value) {
      clusterActiveTab.value = nextTab
    }
  }
)

watch(clusterActiveTab, value => {
  const nextTab = normalizeClusterTab(value)
  const currentTab = normalizeClusterTab(route.query.tab)
  if (nextTab !== currentTab) {
    router.replace({
      query: {
        ...route.query,
        tab: nextTab === clusterTabs[0].key ? undefined : nextTab,
      },
    })
  }
  nextTick(() => {
    void renderCharts()
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  themeObserver?.disconnect()
  stopTaskHistoryPolling()
  disposeCharts()
})
</script>

<style scoped>
.cluster-inline-block {
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  border-left: 0;
  border-right: 0;
  background: transparent;
}

.cluster-inline-block--plain {
  background: transparent;
}

.cluster-inline-block--muted {
  background: transparent;
}

.cluster-inline-block--accent {
  border-color: rgba(59, 130, 246, 0.18);
  background: transparent;
}

.cluster-inline-block--success {
  border-color: rgba(16, 185, 129, 0.18);
  background: transparent;
}

.cluster-trigger-chip {
  border-radius: 0;
  background: #0f172a;
}

.dark .cluster-inline-block {
  border-color: rgba(71, 85, 105, 0.52);
  background: transparent;
}

.dark .cluster-inline-block--plain {
  background: transparent;
}

.dark .cluster-inline-block--muted {
  background: transparent;
}

.dark .cluster-inline-block--accent {
  border-color: rgba(59, 130, 246, 0.26);
  background: transparent;
}

.dark .cluster-inline-block--success {
  border-color: rgba(16, 185, 129, 0.24);
  background: transparent;
}
</style>
