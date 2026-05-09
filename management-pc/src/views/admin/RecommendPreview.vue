<template>
  <div class="space-y-6 analytics-ui-page recommend-preview-page">
    <section
      v-if="demoContext"
      class="panel-card recommend-preview-guide overflow-hidden"
    >
      <div class="flex flex-wrap items-center justify-between gap-3 p-5">
        <div class="min-w-0 flex-1">
          <div class="recommend-preview-kicker">浏览引导</div>
          <div class="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">
            步骤 {{ demoContext.index + 1 }}/{{ demoContext.total }} · {{ demoContext.step.title }}
          </div>
          <p class="mt-1 text-sm text-slate-600 dark:text-slate-300">{{ demoContext.step.spotlightDescription }}</p>
          <div class="mt-3">
              <div class="text-xs font-medium uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">浏览路径</div>
            <div class="mt-2 flex flex-wrap items-center gap-2">
              <button
                v-for="step in DEFENSE_DEMO_STEPS"
                :key="step.key"
                type="button"
                class="rounded-full border px-3 py-1.5 text-xs font-medium transition"
                :class="step.key === currentDemoStepKey
                  ? 'border-cyan-400 bg-cyan-500 text-white shadow-sm shadow-cyan-200/80 dark:border-cyan-400 dark:bg-cyan-500 dark:text-slate-950'
                  : 'border-slate-200 bg-white/80 text-slate-600 hover:border-cyan-300 hover:text-cyan-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-300 dark:hover:border-cyan-500 dark:hover:text-cyan-300'"
                @click="goDemoStep(step)"
              >
                {{ step.title }}
              </button>
            </div>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-button v-if="demoContext.previous" size="small" @click="goDemoStep(demoContext.previous)">上一步</el-button>
          <el-button v-if="demoContext.next" type="primary" size="small" @click="goDemoStep(demoContext.next)">下一步</el-button>
          <el-button size="small" plain @click="stopDemoGuide">结束引导</el-button>
        </div>
      </div>
    </section>

    <section class="recommend-preview-hero panel-card relative overflow-hidden p-6 md:p-8">
      <div class="recommend-preview-hero__glow recommend-preview-hero__glow--primary"></div>
      <div class="recommend-preview-hero__glow recommend-preview-hero__glow--secondary"></div>
      <div class="relative grid gap-5 xl:grid-cols-[1.25fr_0.75fr]">
        <div>
          <div class="flex flex-wrap items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
            <span class="recommend-preview-pill">推荐运营</span>
            <span class="recommend-preview-pill">算法预览</span>
          </div>
          <h1 class="mt-4 text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100 md:text-4xl">推荐预览</h1>
          <p class="mt-3 max-w-3xl text-sm leading-6 text-slate-600 dark:text-slate-300 md:text-base">
            用户向量 / 权重公式 / Top-N / token。
          </p>
          <div class="mt-5 flex flex-wrap gap-2 text-xs md:text-sm">
            <span v-for="item in previewHeroTags" :key="item" class="recommend-preview-tag">
              {{ item }}
            </span>
          </div>
        </div>
        <div class="grid gap-3">
          <article class="recommend-preview-status-card">
            <div class="recommend-preview-status-card__label">视图</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ activePreviewTabInfo.label }}</div>
            <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-300">{{ activePreviewTabInfo.description }}</p>
          </article>
          <article class="recommend-preview-status-card">
            <div class="recommend-preview-status-card__label">策略组</div>
            <div class="mt-2 text-2xl font-black text-slate-900 dark:text-slate-100">{{ currentGroupMeta.label }}</div>
          <div class="mt-2 text-sm text-slate-600 dark:text-slate-300">实时刷新：{{ autoRefresh ? '开启' : '暂停' }}</div>
            <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ realtimeRefreshLabel }}</div>
          </article>
        </div>
      </div>
    </section>

    <FeatureBrief
      kicker="推荐算法"
      title="判断依据与组成"
      :items="recommendFeatureBrief"
    />

    <div class="panel-card p-5">
      <div class="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200/80 pb-4 dark:border-slate-700/80">
        <div>
          <div class="recommend-preview-kicker">运营查询</div>
          <div class="mt-1 text-base font-semibold text-slate-900 dark:text-slate-100">用户查询</div>
        </div>
        <div class="recommend-preview-refresh-badge">{{ realtimeRefreshLabel }}</div>
      </div>

      <div class="mt-4 flex flex-wrap items-end gap-4">
        <div>
          <label class="mb-1 block text-sm font-medium text-gray-600 dark:text-gray-400">输入用户 ID</label>
          <el-input-number v-model="userId" :min="1" controls-position="right" class="!w-40" />
        </div>
        <div class="recommend-preview-refresh-panel min-w-[240px]">
          <div class="flex items-center justify-between gap-3">
            <span class="text-sm font-medium text-gray-700 dark:text-gray-200">实时刷新</span>
            <el-switch v-model="autoRefresh" />
          </div>
          <div class="mt-2 text-xs text-gray-500 dark:text-gray-400">
            {{ autoRefresh ? '60 秒刷新；隐藏暂停。' : '仅手动刷新。' }}
          </div>
          <div class="mt-1 text-xs text-gray-400">{{ realtimeRefreshLabel }}</div>
        </div>
        <el-button type="primary" :loading="loading" @click="loadAll" class="recommend-preview-primary-btn">
          查询推荐
        </el-button>
        <el-button plain @click="startDemoFromHere" class="recommend-preview-secondary-btn">
          查看引导
        </el-button>
      </div>
    </div>

    <PageSectionTabs
      v-model="activePreviewTab"
      primary-label="管理端"
      page-label="推荐预览"
      title="推荐视图"
      description="画像、公式、结果、对比。"
      :tabs="displayPreviewPageTabs"
    />

    <template v-if="loaded">
      <div v-if="activePreviewTab === 'profile'" class="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <div class="xl:col-span-2 panel-card p-6">
          <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-2">
            <span class="w-1.5 h-5 bg-gradient-to-b from-purple-500 to-pink-500 rounded-full"></span>
            画像向量
          </h3>
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div class="space-y-4">
              <div class="flex items-center gap-3">
                <el-avatar :size="52" class="bg-gradient-to-tr from-blue-500 to-purple-500 text-white text-lg">
                  {{ (profile.username || '用户').charAt(0).toUpperCase() }}
                </el-avatar>
                <div class="min-w-0">
                  <div class="font-bold text-gray-800 dark:text-gray-100 break-words">
                    {{ profile.username || `用户 #${userId}` }}
                  </div>
                  <div class="text-xs text-gray-500 mt-1">
                    ID: {{ userId }} · 实验组:
                    <el-tag size="small" class="!rounded-full">{{ profile.experimentGroupDesc || currentGroupMeta.label }}</el-tag>
                  </div>
                </div>
              </div>

              <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <div class="panel-card--muted p-3">
                  <div class="text-gray-500 dark:text-gray-400">行为商品数</div>
                  <div class="text-xl font-bold text-gray-800 dark:text-gray-100">{{ profile.interactedProducts || 0 }}</div>
                </div>
                <div class="panel-card--muted p-3">
                  <div class="text-gray-500 dark:text-gray-400">向量维度</div>
                  <div class="text-xl font-bold text-gray-800 dark:text-gray-100">{{ profile.vectorDimension || 0 }}</div>
                </div>
              </div>

              <div class="panel-card--muted p-4">
                <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">向量公式</div>
                <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
                  用户向量 = 浏览*1 + 搜索*2 + 加购*2 + 收藏*3 + 购买*8。
                </p>
              </div>
            </div>

            <div>
              <div class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">行为分布</div>
              <div ref="profileBehaviorRef" class="h-44 w-full"></div>
              <div class="panel-card--muted mt-4 p-4">
                <div class="text-sm font-semibold text-gray-700 dark:text-gray-200">行为权重</div>
                <div class="mt-3 grid grid-cols-2 gap-3 text-sm">
                  <div v-for="item in behaviorWeightRules" :key="item.key" class="panel-card--muted px-3 py-3">
                    <div class="text-gray-500 dark:text-gray-400">{{ item.label }}</div>
                    <div class="mt-1 text-base font-semibold text-gray-900 dark:text-gray-100">{{ item.weight }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div class="space-y-4">
              <div>
                <div class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">行为提取标签</div>
                <div class="flex flex-wrap gap-2 max-h-44 overflow-y-auto">
                  <el-tag
                    v-for="tag in userTags"
                    :key="tag"
                    class="!rounded-full"
                    :type="['primary', 'success', 'warning', 'danger', 'info'][Math.abs(hashCode(tag)) % 5]"
                  >
                    {{ tag }}
                  </el-tag>
                  <span v-if="!userTags.length" class="text-sm text-gray-400">暂无标签数据</span>
                </div>
              </div>

              <div class="panel-card--muted p-4">
                <div class="text-sm font-semibold text-gray-700 dark:text-gray-200">行为偏好品类权重</div>
                <div class="mt-3 space-y-3">
                  <div v-for="item in categoryWeightRows" :key="item.key">
                    <div class="flex items-center justify-between gap-3 text-sm">
                      <span class="text-gray-600 dark:text-gray-300">{{ item.label }}</span>
                      <span class="font-semibold text-gray-900 dark:text-gray-100">{{ item.percent }}</span>
                    </div>
                    <el-progress :percentage="item.value" :stroke-width="8" :show-text="false" />
                  </div>
                  <div v-if="!categoryWeightRows.length" class="text-sm text-gray-400">暂无品类权重</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="panel-card p-6">
          <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100 mb-4 flex items-center gap-2">
            <span class="w-1.5 h-5 bg-gradient-to-b from-blue-500 to-cyan-500 rounded-full"></span>
            分群与权重
          </h3>
          <div class="panel-card--muted mb-5 p-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="flex flex-wrap items-center gap-2">
                <el-tag type="success" effect="dark" round>{{ realtimeSegment.segmentName || '待分群' }}</el-tag>
                <el-tag effect="plain" round>{{ realtimeSegment.segmentCode || '待生成' }}</el-tag>
              </div>
              <el-tag type="info" effect="plain" round>{{ realtimeSnapshotDate }}</el-tag>
            </div>
            <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">
              {{ realtimeSegment.personaSummary || realtimeSegment.message || '未命中分群时按默认混合权重。' }}
            </p>
            <div class="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              <div class="panel-card--muted px-3 py-3">
                <div class="text-gray-500 dark:text-gray-400">分群置信度</div>
                <div class="mt-1 text-base font-semibold text-gray-900 dark:text-gray-100">{{ realtimeConfidenceText }}</div>
              </div>
              <div class="panel-card--muted px-3 py-3">
                <div class="text-gray-500 dark:text-gray-400">动作建议</div>
                <div class="mt-1 text-base font-semibold text-gray-900 dark:text-gray-100 line-clamp-2">
                  {{ realtimeSegment.operationSuggestion || '已生成动作建议。' }}
                </div>
              </div>
            </div>
            <div v-if="realtimeFeatureHighlights.length" class="mt-4 flex flex-wrap gap-2">
              <el-tag v-for="item in realtimeFeatureHighlights" :key="item" type="success" effect="plain" round>
                {{ item }}
              </el-tag>
            </div>
            <div v-if="realtimeTopCategories.length || realtimeTopTags.length" class="mt-4 space-y-3">
              <div v-if="realtimeTopCategories.length">
                <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">偏好品类</div>
                <div class="flex flex-wrap gap-2">
                  <el-tag v-for="item in realtimeTopCategories" :key="`cat-${item}`" type="warning" effect="plain" round>{{ item }}</el-tag>
                </div>
              </div>
              <div v-if="realtimeTopTags.length">
                <div class="text-xs text-gray-500 dark:text-gray-400 mb-2">偏好标签</div>
                <div class="flex flex-wrap gap-2">
                  <el-tag v-for="item in realtimeTopTags" :key="`tag-${item}`" type="danger" effect="plain" round>{{ item }}</el-tag>
                </div>
              </div>
            </div>
          </div>
          <div class="rounded-2xl bg-gradient-to-br from-blue-50 to-cyan-50 dark:from-blue-900/20 dark:to-cyan-900/20 border border-blue-100 dark:border-blue-800/40 p-4">
            <div class="flex flex-wrap items-center gap-2">
              <el-tag type="primary" effect="dark" round>{{ currentGroupMeta.label }}</el-tag>
              <el-tag effect="plain" round>流量占比 {{ currentGroupMeta.traffic }}</el-tag>
            </div>
            <p class="mt-3 text-sm leading-6 text-gray-600 dark:text-gray-300">{{ currentGroupMeta.description }}</p>
          </div>

          <div class="mt-5 space-y-4">
            <div v-for="item in weightRows" :key="item.key" class="rounded-2xl bg-gray-50/70 dark:bg-gray-700/30 p-4">
              <div class="flex items-center justify-between gap-3">
                <div>
                  <div class="text-sm font-semibold text-gray-800 dark:text-gray-100">{{ item.label }}</div>
                  <div class="text-xs text-gray-500 dark:text-gray-400 mt-1">{{ item.description }}</div>
                </div>
                <div class="text-base font-bold text-gray-900 dark:text-gray-100">{{ item.percent }}</div>
              </div>
              <el-progress class="mt-3" :percentage="item.value" :stroke-width="10" :show-text="false" />
            </div>
          </div>

          <div class="mt-5 rounded-2xl bg-slate-50/80 dark:bg-slate-800/60 border border-slate-200/70 dark:border-slate-700/70 p-4">
            <div class="text-sm font-semibold text-slate-700 dark:text-slate-200">A/B 分流规则</div>
            <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
              userId 稳定分桶：`control 30%`，`hybrid 40%`，`cf_heavy 30%`。
            </p>
          </div>
        </div>
      </div>

      <div v-else-if="activePreviewTab === 'explain'" class="panel-card p-6">
        <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
          <div class="max-w-3xl">
            <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100 flex items-center gap-2">
              <span class="w-1.5 h-5 bg-gradient-to-b from-emerald-500 to-teal-500 rounded-full"></span>
              算法公式
            </h3>
            <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">
              `User-CF` / `Content-CB` / `Hot` / `Hybrid`
            </p>
          </div>
          <div class="flex flex-wrap gap-2">
            <el-tag effect="plain" round>当前组 {{ currentGroupCode }}</el-tag>
            <el-tag type="success" effect="plain" round>CF {{ weightRows[0]?.percent || '0%' }}</el-tag>
            <el-tag type="warning" effect="plain" round>CB {{ weightRows[1]?.percent || '0%' }}</el-tag>
            <el-tag type="danger" effect="plain" round>热门 {{ weightRows[2]?.percent || '0%' }}</el-tag>
          </div>
        </div>

        <div class="mt-6 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          <div
            v-for="card in algorithmExplainCards"
            :key="card.title"
            class="recommend-preview-algo-card"
            :class="`recommend-preview-algo-card--${card.tone}`"
          >
            <div class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ card.title }}</div>
            <p class="mt-2 text-sm leading-6 text-gray-600 dark:text-gray-300">{{ card.description }}</p>
            <div class="recommend-preview-code-block mt-4">
              {{ card.formula }}
            </div>
          </div>
        </div>
      </div>

      <div
        v-else-if="activePreviewTab === 'results'"
        ref="demoFocusResultRef"
        class="panel-card p-6 transition-all"
        :class="demoContext ? 'ring-2 ring-cyan-300/70 shadow-cyan-100/70 dark:ring-cyan-500/40' : ''"
      >
        <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100 mb-2 flex items-center gap-2">
          <span class="w-1.5 h-5 bg-gradient-to-b from-blue-500 to-cyan-500 rounded-full"></span>
          推荐结果 / 推荐解释
        </h3>
        <div class="text-sm text-gray-500 dark:text-gray-400 mb-4 leading-6">
          先召回，再合分。
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-5">
          <div class="recommend-preview-metric-card recommend-preview-metric-card--blue">
            <div class="text-xs text-gray-500">当前实验组</div>
            <div class="mt-2 text-lg font-bold text-gray-900 dark:text-gray-100">{{ currentGroupMeta.label }}</div>
          </div>
          <div class="recommend-preview-metric-card recommend-preview-metric-card--emerald">
            <div class="text-xs text-gray-500">Hybrid 主策略</div>
            <div class="mt-2 text-lg font-bold text-gray-900 dark:text-gray-100">{{ currentGroupMeta.hybridSummary }}</div>
          </div>
          <div class="recommend-preview-metric-card recommend-preview-metric-card--amber">
            <div class="text-xs text-gray-500">探索上限</div>
            <div class="mt-2 text-lg font-bold text-gray-900 dark:text-gray-100">≤ 0.08</div>
          </div>
          <div class="recommend-preview-metric-card recommend-preview-metric-card--violet">
            <div class="text-xs text-gray-500">热门候选池</div>
            <div class="mt-2 text-lg font-bold text-gray-900 dark:text-gray-100">max(topN * 4, 40)</div>
          </div>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
          <div
            v-for="(product, i) in (preview.products || [])"
            :key="product.id"
            class="recommend-preview-product-card group"
          >
            <div class="recommend-preview-product-card__media relative h-36 overflow-hidden">
              <img
                v-if="product.mainImage"
                :src="product.mainImage"
                class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-300"
              />
              <div class="absolute top-2 left-2 flex flex-wrap gap-2">
                <span
                  class="recommend-preview-reason-pill"
                  :class="reasonColor(explanations[i]?.primaryReason)"
                >
                  {{ reasonLabel(explanations[i]?.primaryReason) }}
                </span>
                <span class="recommend-preview-rank-pill">
                  TOP {{ i + 1 }}
                </span>
              </div>
            </div>
            <div class="p-4">
              <div class="text-sm font-medium text-gray-800 dark:text-gray-100 truncate">{{ product.name }}</div>
              <div class="mt-1 flex items-center justify-between gap-3">
                <div class="text-sm font-bold text-rose-500">￥{{ Number(product.price || 0).toFixed(2) }}</div>
                <div class="text-[11px] text-slate-400 dark:text-slate-500">商品 #{{ product.id }}</div>
              </div>
              <div class="mt-2 text-xs text-gray-500 dark:text-gray-400 line-clamp-2 min-h-[2.5rem]">
                {{ explanations[i]?.reasonText || '缺少解释' }}
              </div>

              <div class="mt-3 flex flex-wrap gap-2">
                <el-tag
                  v-for="signal in productSignalList(product.id)"
                  :key="`${product.id}-${signal.label}`"
                  size="small"
                  effect="plain"
                  :type="signal.type"
                >
                  {{ signal.label }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-if="!(preview.products || []).length" description="该用户暂无推荐结果（可能行为数据不足）" />
      </div>

      <div v-else class="panel-card p-6">
        <h3 class="text-lg font-bold text-gray-800 dark:text-gray-100 mb-2 flex items-center gap-2">
          <span class="w-1.5 h-5 bg-gradient-to-b from-amber-500 to-orange-500 rounded-full"></span>
          算法对比 · 最终上线排序 vs CF vs CB vs 热门
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mb-4 leading-6">
          先看上线排序，再看召回对比。
        </p>
        <div v-if="compareQuality" class="recommend-preview-quality-line mb-4">
          <div>
            <div class="text-xs text-slate-500 dark:text-slate-400">Top 偏好命中率</div>
            <div class="mt-1 text-xl font-black text-slate-900 dark:text-slate-100">
              {{ compareQuality.topCategoryHitRate || '0.00' }}%
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-xs text-slate-500 dark:text-slate-400">偏好品类</div>
            <div class="mt-2 flex flex-wrap gap-2">
              <el-tag v-for="item in (compareQuality.topPreferenceCategories || [])" :key="item" type="warning" effect="plain" round>
                {{ item }}
              </el-tag>
              <span v-if="!(compareQuality.topPreferenceCategories || []).length" class="text-sm text-slate-400">暂无偏好品类</span>
            </div>
          </div>
          <el-tag :type="compareQuality.status === 'PASS' ? 'success' : 'danger'" effect="dark" round>
            {{ compareQuality.status === 'PASS' ? '已匹配' : '待检查' }}
          </el-tag>
        </div>
        <el-tabs v-model="compareTab" type="card" class="recommend-preview-compare-tabs">
          <el-tab-pane v-for="algo in algoTabs" :key="algo.key" :name="algo.key" :label="algo.label">
            <div class="recommend-preview-compare-summary">
              {{ algo.description }}
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4 mt-4">
              <div
                v-for="(product, index) in (compare[algo.key] || [])"
                :key="product.id"
                class="recommend-preview-compare-product"
              >
                <div class="recommend-preview-product-card__media h-28 overflow-hidden">
                  <img v-if="product.mainImage" :src="product.mainImage" class="w-full h-full object-cover" />
                </div>
                <div class="p-4">
                  <div class="flex items-center justify-between gap-3">
                    <div class="text-xs font-medium text-gray-800 dark:text-gray-100 truncate">{{ product.name }}</div>
                    <el-tag size="small" effect="dark" round>#{{
                      index + 1
                    }}</el-tag>
                  </div>
                  <div class="mt-1 text-sm font-bold text-rose-500">￥{{ Number(product.price || 0).toFixed(2) }}</div>
                  <div class="text-[10px] text-gray-400 mt-1">销量 {{ product.salesCount || 0 }}</div>
                </div>
              </div>
            </div>
            <el-empty v-if="!(compare[algo.key] || []).length" description="该算法无推荐结果" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>

    <div v-else class="panel-card px-6 py-10">
      <el-empty description="输入用户 ID 后查询推荐" />
    </div>
  </div>
  <section v-if="compare && (compare.portraitLayers || compare.explainableFormula || compareQuality?.tagComparisons?.length)" class="recommend-preview-explain">
    <div class="recommend-preview-explain-head">
      <div>
        <h3>命中标签对比</h3>
        <p>{{ compare.explainableFormula?.expression || '短期意图 + 长期兴趣 + 协同 + 热门' }}</p>
      </div>
      <span>{{ compare.explainableFormula?.name || '混合推荐' }}</span>
    </div>

    <div v-if="compare.beforeAfterQuality" class="recommend-preview-lift">
      <div>
        <small>{{ compare.beforeAfterQuality.baselineName }}</small>
        <strong>{{ compare.beforeAfterQuality.baseline?.topCategoryHitRate || '0.00' }}%</strong>
        <span>标签 {{ compare.beforeAfterQuality.baseline?.tagMatchRate || '0.00' }}%</span>
      </div>
      <div>
        <small>{{ compare.beforeAfterQuality.optimizedName }}</small>
        <strong>{{ compare.beforeAfterQuality.optimized?.topCategoryHitRate || '0.00' }}%</strong>
        <span>标签 {{ compare.beforeAfterQuality.optimized?.tagMatchRate || '0.00' }}%</span>
      </div>
      <div>
        <small>提升</small>
        <strong>{{ compare.beforeAfterQuality.hitRateLift || '0.00' }}%</strong>
        <span>标签 +{{ compare.beforeAfterQuality.tagMatchLift || '0.00' }}%</span>
      </div>
      <div v-if="compare.beforeAfterQuality.negativeFeedback">
        <small>负反馈降权</small>
        <strong>{{ compare.beforeAfterQuality.negativeFeedback.productHitReduction || 0 }}</strong>
        <span>类目减少 {{ compare.beforeAfterQuality.negativeFeedback.categoryHitReduction || 0 }} 个命中</span>
      </div>
    </div>
    <p v-if="compare.beforeAfterQuality?.negativeFeedback" class="recommend-preview-negative-note">
      {{ compare.beforeAfterQuality.negativeFeedback.conclusion }}
    </p>

    <div v-if="compare.portraitLayers" class="recommend-preview-layers">
      <div v-for="(layer, key) in compare.portraitLayers" :key="key" class="recommend-preview-layer">
        <strong>{{ layer.label }}</strong>
        <div>
          <em v-for="item in layer.values || []" :key="item">{{ item }}</em>
          <em v-if="!(layer.values || []).length">暂无</em>
        </div>
        <p>{{ layer.basis }}</p>
      </div>
    </div>

    <div v-if="compareQuality?.tagComparisons?.length" class="recommend-preview-tag-table">
      <div v-for="item in compareQuality.tagComparisons" :key="item.productId || item.rank" class="recommend-preview-tag-row">
        <span>#{{ item.rank }}</span>
        <b>{{ item.productName }}</b>
        <div>
          <em v-for="tag in item.matchedTags || []" :key="tag">{{ tag }}</em>
          <em v-if="!(item.matchedTags || []).length">探索补位</em>
        </div>
        <small>{{ item.basis }}</small>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getRecommendCompare, getRecommendPreview, getRecommendRealtime, getUserProfile } from '../../api/recommend'
import PageSectionTabs from '../../components/PageSectionTabs.vue'
import FeatureBrief from '../../components/FeatureBrief.vue'
import {
  DEFENSE_DEMO_STEPS,
  buildDefenseDemoRoute,
  getDefenseDemoContext,
  markDefenseDemoStep,
  readDefenseDemoState,
  startDefenseDemo,
  stopDefenseDemo,
} from '../../utils/workflowGuide'

const route = useRoute()
const router = useRouter()

const EXPERIMENT_GROUP_META = {
  control: {
    label: '热门对照组',
    description: 'score = hot',
    traffic: '30%',
    hybridSummary: '1.00Hot',
  },
  hybrid: {
    label: '标准混合组',
    description: 'score = 0.40CF + 0.30CB + 0.30Hot',
    traffic: '40%',
    hybridSummary: '0.40CF+0.30CB+0.30Hot',
  },
  cf_heavy: {
    label: 'CF 强化组',
    description: 'score = 0.55CF + 0.20CB + 0.25Hot',
    traffic: '30%',
    hybridSummary: '0.55CF+0.20CB+0.25Hot',
  },
  disabled: {
    label: '默认混合策略',
    description: '未分流时按默认混合权重排序',
    traffic: '100%',
    hybridSummary: 'Default Hybrid',
  },
}

const behaviorWeightRules = [
  { key: 'purchase', label: '购买', weight: '8' },
  { key: 'favorite', label: '收藏', weight: '3' },
  { key: 'cart', label: '加购', weight: '2' },
  { key: 'search', label: '搜索', weight: '2' },
  { key: 'view', label: '浏览', weight: '1' },
]

const algoTabs = [
  {
    key: 'online',
    label: '最终上线排序',
    description: '最终展示结果 = 召回分 + 规则分 + 重排分。',
  },
  {
    key: 'hybrid',
    label: '混合推荐 (Hybrid)',
    description: '三路候选合表后按实验组权重求和。',
  },
  {
    key: 'cf',
    label: '协同过滤 (CF)',
    description: '依据相似用户行为召回。',
  },
  {
    key: 'cb',
    label: '基于内容 (CB)',
    description: '依据标签、品类、价格带匹配召回。',
  },
  {
    key: 'hot',
    label: '热门推荐',
    description: '依据全站热度和销量补冷启动。',
  },
]

const previewHeroTags = ['用户向量', '实验分流', '权重公式', 'Top-N']

const recommendFeatureBrief = [
  { label: '判断依据', value: 'view/search/cart/favorite/purchase', text: '行为向量 + 分群 + 热度。' },
  { label: '功能组成', value: 'CF + CB + Hot + Hybrid', text: '多路召回 + 加权重排。' },
  { label: '输出结果', value: 'Top-N + token + reason', text: '保留来源、分数、归因。' },
]

const previewPageTabs = [
  {
    key: 'profile',
    label: '实例画像与分群',
    hint: '用户偏好 + 实验权重',
    description: '用户向量、分群、权重。',
  },
  {
    key: 'explain',
    label: '算法链路',
    hint: 'CF / CB / 热门 / 混合',
    description: '召回、相似度、混合公式。',
  },
  {
    key: 'results',
    label: '推荐结果',
    hint: '排序结果 + 命中信号',
    description: 'Top-N、命中通道、归因 token。',
  },
  {
    key: 'compare',
    label: '算法对比',
    hint: '同用户多策略对照',
    description: '同用户多策略结果对照。',
  },
]

function normalizeUserId(value, fallback = 4) {
  const number = Number(value)
  return Number.isFinite(number) && number > 0 ? Math.floor(number) : fallback
}

const userId = ref(4)
const loading = ref(false)
const loaded = ref(false)
const profile = ref({})
const preview = ref({})
const explanations = ref([])
const compare = ref({})
const realtimeDashboard = ref({})
const autoRefresh = ref(true)
const lastRefreshAt = ref('')
const competitionMode = import.meta.env.VITE_COMPETITION_MODE !== 'false'
const activePreviewTab = ref(competitionMode ? 'results' : 'profile')
const compareTab = ref('online')
const profileBehaviorRef = ref(null)
const demoFocusResultRef = ref(null)
const demoState = ref(readDefenseDemoState())
let chart = null
let autoRefreshTimer = null
const AUTO_REFRESH_INTERVAL_MS = 60000

const currentGroupCode = computed(() =>
  compare.value.experimentGroup || preview.value.experimentGroup || profile.value.experimentGroup || 'disabled'
)

const currentGroupMeta = computed(() => EXPERIMENT_GROUP_META[currentGroupCode.value] || EXPERIMENT_GROUP_META.disabled)
const displayPreviewPageTabs = computed(() =>
  competitionMode
    ? previewPageTabs.filter(item => ['results', 'compare', 'profile'].includes(item.key))
    : previewPageTabs
)
const activePreviewTabInfo = computed(() =>
  displayPreviewPageTabs.value.find(item => item.key === activePreviewTab.value) || displayPreviewPageTabs.value[0]
)
const currentDemoStepKey = computed(() => demoContext.value?.step?.key || demoState.value.stepKey || '')
const demoContext = computed(() => {
  if (!demoState.value.active) {
    return null
  }
  return getDefenseDemoContext(route.path)
})

const realtimeSegment = computed(() => realtimeDashboard.value.segment || {})

const realtimeSnapshotDate = computed(() =>
  realtimeSegment.value.snapshotDate || realtimeDashboard.value.dataSource?.segmentSnapshotDate || '--'
)

const realtimeConfidenceText = computed(() => {
  const value = Number(realtimeSegment.value.confidenceScore || 0)
  return value > 0 ? `${(value * 100).toFixed(1)}%` : '待计算'
})

const realtimeFeatureHighlights = computed(() =>
  Array.isArray(realtimeSegment.value.featureHighlights) ? realtimeSegment.value.featureHighlights : []
)

const realtimeTopCategories = computed(() =>
  Array.isArray(realtimeSegment.value.topCategories) ? realtimeSegment.value.topCategories : []
)

const realtimeTopTags = computed(() =>
  Array.isArray(realtimeSegment.value.topTags) ? realtimeSegment.value.topTags : []
)

const realtimeRefreshLabel = computed(() =>
  lastRefreshAt.value ? `最近刷新 ${formatDateTime(lastRefreshAt.value)}` : '尚未刷新'
)

const currentWeights = computed(() => {
  const weights = compare.value.weights || preview.value.algorithmWeights || {}
  return {
    collaborative: Number(weights.collaborative || 0),
    content: Number(weights.content || 0),
    popularity: Number(weights.popularity || 0),
  }
})

const weightRows = computed(() => [
  {
    key: 'collaborative',
    label: '协同过滤权重',
    description: 'similar-user',
    value: toPercent(currentWeights.value.collaborative),
    percent: formatPercent(currentWeights.value.collaborative),
  },
  {
    key: 'content',
    label: '内容推荐权重',
    description: 'tag/category/price',
    value: toPercent(currentWeights.value.content),
    percent: formatPercent(currentWeights.value.content),
  },
  {
    key: 'popularity',
    label: '热门候选权重',
    description: 'global hot',
    value: toPercent(currentWeights.value.popularity),
    percent: formatPercent(currentWeights.value.popularity),
  },
])

const userTags = computed(() => {
  const tags = profile.value.userTags
  const normalized = Array.isArray(tags) ? tags : Array.from(tags || [])
  return normalized.slice(0, 18)
})

const categoryWeightRows = computed(() =>
  {
    const rows = Object.entries(profile.value.categoryWeights || {})
      .map(([key, value]) => ({
        key,
        label: formatCategoryLabel(key),
        rawValue: Number(value || 0),
      }))
      .filter(item => item.rawValue > 0)
      .sort((a, b) => b.rawValue - a.rawValue)
      .slice(0, 6)

    const maxValue = rows.reduce((max, item) => Math.max(max, item.rawValue), 0)

    return rows.map(item => ({
      ...item,
      value: toRelativePercent(item.rawValue, maxValue),
      percent: formatRelativePercent(item.rawValue, maxValue),
    }))
  }
)

const algorithmExplainCards = computed(() => [
  {
    title: 'User-CF 协同过滤',
    description: '构建 user-item 矩阵后计算余弦相似度。',
    formula: 'purchase=8, favorite=3, cart=2, search=2, view=1\nsim(u,v)=R_u·R_v / (||R_u|| x ||R_v||)\nproductScore=Σ(similarity x behaviorWeight)',
    tone: 'blue',
  },
  {
    title: 'Content-CB 内容推荐',
    description: '标签、品类、价格带组成内容相似度。',
    formula: 'J(A,B)=|A∩B| / |A∪B|\ncontentScore=0.30*tagSim\n           +0.45*categoryWeight\n           +0.10*priceMatch\n           +0.15*quality',
    tone: 'violet',
  },
  {
    title: '热门候选',
    description: '冷启动和低画像样本走热榜补位。',
    formula: 'hotCandidatePool=max(topN*4, 40)\n按类目轮转补位，限制同类连续出现\nscore=max(热度归一化, 排名分*0.6) * popularityWeight',
    tone: 'amber',
  },
  {
    title: 'Hybrid 混合加权',
    description: '三路候选合并后按实验组公式求最终分。',
    formula: `finalScore=cfRankScore*${formatWeight(currentWeights.value.collaborative)}\n         +cbRankScore*${formatWeight(currentWeights.value.content)}\n         +hotRankScore*${formatWeight(currentWeights.value.popularity)}\n         +exploreScore(0~0.08*baseScore)`,
    tone: 'emerald',
  },
])

const compareRankMaps = computed(() => {
  const buildMap = list =>
    new Map((list || []).map((item, index) => [String(item.id), index + 1]))

  return {
    online: buildMap(compare.value.online),
    hybrid: buildMap(compare.value.hybrid),
    cf: buildMap(compare.value.cf),
    cb: buildMap(compare.value.cb),
    hot: buildMap(compare.value.hot),
  }
})

const compareQuality = computed(() => compare.value.quality || null)

const reasonColor = r => ({
  COLLABORATIVE: 'recommend-preview-reason-pill--blue',
  CONTENT_TAG: 'recommend-preview-reason-pill--violet',
  CONTENT_CATEGORY: 'recommend-preview-reason-pill--indigo',
  HOT_SELLING: 'recommend-preview-reason-pill--amber',
  COLD_START: 'recommend-preview-reason-pill--slate',
}[r] || 'recommend-preview-reason-pill--slate')

const reasonLabel = r => ({
  COLLABORATIVE: 'CF 协同',
  CONTENT_TAG: 'CB 标签',
  CONTENT_CATEGORY: 'CB 品类',
  HOT_SELLING: '热门',
  COLD_START: '精选',
}[r] || '推荐')

const hashCode = s => {
  let h = 0
  for (let i = 0; i < s.length; i++) h = ((h << 5) - h + s.charCodeAt(i)) | 0
  return h
}

function formatCategoryLabel(key) {
  return /^\d+$/.test(String(key)) ? `分类 ID ${key}` : String(key)
}

function formatPercent(value) {
  return `${(Number(value || 0) * 100).toFixed(0)}%`
}

function formatWeight(value) {
  return Number(value || 0).toFixed(1)
}

function toPercent(value) {
  return Math.max(0, Math.min(100, Number((Number(value || 0) * 100).toFixed(2))))
}

function formatRelativePercent(value, maxValue) {
  if (!Number.isFinite(value) || value <= 0 || !Number.isFinite(maxValue) || maxValue <= 0) {
    return '0%'
  }
  return `${((value / maxValue) * 100).toFixed(0)}%`
}

function toRelativePercent(value, maxValue) {
  if (!Number.isFinite(value) || value <= 0 || !Number.isFinite(maxValue) || maxValue <= 0) {
    return 0
  }
  return Math.max(0, Math.min(100, Number((((value / maxValue) * 100)).toFixed(2))))
}

function productSignalList(productId) {
  const productKey = String(productId)
  const signals = []

  if (compareRankMaps.value.online.has(productKey)) {
    signals.push({ label: `上线 #${compareRankMaps.value.online.get(productKey)}`, type: 'primary' })
  }
  if (compareRankMaps.value.hybrid.has(productKey)) {
    signals.push({ label: `混合 #${compareRankMaps.value.hybrid.get(productKey)}`, type: 'primary' })
  }
  if (compareRankMaps.value.cf.has(productKey)) {
    signals.push({ label: `协同 #${compareRankMaps.value.cf.get(productKey)}`, type: 'success' })
  }
  if (compareRankMaps.value.cb.has(productKey)) {
    signals.push({ label: `内容 #${compareRankMaps.value.cb.get(productKey)}`, type: 'warning' })
  }
  if (compareRankMaps.value.hot.has(productKey)) {
    signals.push({ label: `热门 #${compareRankMaps.value.hot.get(productKey)}`, type: 'danger' })
  }

  return signals
}

function cleanDemoQuery() {
  const query = { ...route.query }
  delete query.workflowGuide
  delete query.workflowStep
  return query
}

function syncDemoStateForRoute() {
  const latest = readDefenseDemoState()
  const context = getDefenseDemoContext(route.path)
  if (context && route.query.workflowGuide === '1') {
    demoState.value = markDefenseDemoStep(context.step.key)
    return
  }
  if (!latest.active || !context) {
    demoState.value = latest
    return
  }
  demoState.value = latest.stepKey === context.step.key
    ? latest
    : markDefenseDemoStep(context.step.key)
}

function goDemoStep(step) {
  if (!step) {
    return
  }
  demoState.value = markDefenseDemoStep(step.key)
  router.push(buildDefenseDemoRoute(step, cleanDemoQuery()))
}

function startDemoFromHere() {
  const context = getDefenseDemoContext(route.path)
  const step = context?.step || DEFENSE_DEMO_STEPS[0]
  demoState.value = startDefenseDemo(step.key)
  router.push(buildDefenseDemoRoute(step, cleanDemoQuery()))
}

function stopDemoGuide() {
  demoState.value = stopDefenseDemo()
  if (route.query.workflowGuide || route.query.workflowStep) {
    router.replace({ path: route.path, query: cleanDemoQuery() })
  }
}

const loadAll = async (preserveView = false) => {
  if (typeof preserveView !== 'boolean') {
    preserveView = false
  }
  if (!userId.value || isNaN(userId.value)) return
  loading.value = true
  if (!preserveView) {
    loaded.value = false
  }
  try {
    const [realtimeRes, compareRes] = await Promise.all([
      getRecommendRealtime(userId.value).catch(() => null),
      getRecommendCompare(userId.value).catch(() => null),
    ])
    let previewData = {}
    if (realtimeRes) {
      realtimeDashboard.value = realtimeRes || {}
      profile.value = realtimeDashboard.value.profile || {}
      previewData = realtimeDashboard.value.recommendation || {}
      preview.value = previewData
      explanations.value = previewData.explanations || []
      lastRefreshAt.value = realtimeDashboard.value.generatedAt || new Date().toISOString()
    } else {
      const [profileRes, previewRes] = await Promise.all([
        getUserProfile(userId.value).catch(() => null),
        getRecommendPreview(userId.value).catch(() => null),
      ])
      realtimeDashboard.value = {}
      profile.value = profileRes || {}
      previewData = previewRes || {}
      preview.value = previewData
      explanations.value = previewData.explanations || []
      lastRefreshAt.value = new Date().toISOString()
    }
    preview.value = previewData
    compare.value = compareRes || {}
    loaded.value = true

    setTimeout(initProfileChart, 100)
    startAutoRefresh()
  } finally {
    loading.value = false
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (!autoRefresh.value || document.visibilityState === 'hidden') return
  autoRefreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible' && !loading.value && loaded.value && userId.value) {
      loadAll(true)
    }
  }, AUTO_REFRESH_INTERVAL_MS)
}

const stopAutoRefresh = () => {
  if (autoRefreshTimer) {
    window.clearInterval(autoRefreshTimer)
    autoRefreshTimer = null
  }
}

watch(autoRefresh, enabled => {
  if (enabled) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

watch(() => route.query.userId, async value => {
  const nextUserId = normalizeUserId(value, 4)
  const shouldReload = nextUserId !== userId.value || !loaded.value
  if (nextUserId !== userId.value) {
    userId.value = nextUserId
  }
  if (!shouldReload || loading.value) {
    return
  }
  try {
    await loadAll()
  } catch {
    // keep the page interactive and let manual retry handle transient failures
  }
}, { immediate: true })

watch(() => route.fullPath, () => {
  syncDemoStateForRoute()
}, { immediate: true })

watch(demoContext, async (context) => {
  if (!context) {
    return
  }
  if (!loaded.value && !loading.value) {
    try {
      await loadAll()
    } catch {
      return
    }
  }
  await nextTick()
  if (demoFocusResultRef.value && typeof demoFocusResultRef.value.scrollIntoView === 'function') {
    demoFocusResultRef.value.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}, { immediate: true })

const handleVisibilityChange = () => {
  if (document.visibilityState === 'hidden') {
    stopAutoRefresh()
    return
  }
  if (autoRefresh.value) {
    startAutoRefresh()
  }
}

const initProfileChart = () => {
  chart?.dispose()
  if (!profileBehaviorRef.value) return
  const isDark = document.documentElement.classList.contains('dark')
  const textColor = isDark ? '#9ca3af' : '#6b7280'
  const colors = ['#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981']
  const behaviorTypeMap = { view: '浏览', cart: '加购', favorite: '收藏', purchase: '购买', search: '搜索' }
  const stats = Array.isArray(profile.value.behaviorStats) ? profile.value.behaviorStats : []
  const distribution = stats.map((item, index) => {
    const type = item.behaviorType || item.behavior_type || ''
    const value = Number(item.count || 0)
    return {
      value,
      name: behaviorTypeMap[type] || type || '未知',
      itemStyle: { color: colors[index % colors.length] },
    }
  })
  const hasBehaviorData = distribution.some(item => item.value > 0)

  chart = echarts.init(profileBehaviorRef.value)
  chart.setOption({
    tooltip: hasBehaviorData
      ? { trigger: 'item', formatter: '{b}: {c} 次 ({d}%)' }
      : { show: false },
    graphic: hasBehaviorData
      ? []
      : [
          {
            type: 'text',
            left: 'center',
            top: '43%',
            style: {
              text: '暂无行为数据',
              fill: isDark ? '#d1d5db' : '#6b7280',
              fontSize: 13,
              fontWeight: 600,
            },
          },
          {
            type: 'text',
            left: 'center',
            top: '57%',
            style: {
              text: '先产生浏览/加购/收藏/购买行为',
              fill: isDark ? '#6b7280' : '#9ca3af',
              fontSize: 11,
            },
          },
        ],
    series: [{
      type: 'pie',
      radius: ['40%', '75%'],
      itemStyle: { borderRadius: 6, borderColor: isDark ? '#1f2937' : '#fff', borderWidth: 2 },
      label: hasBehaviorData
        ? { show: true, color: textColor, fontSize: 11, formatter: '{b}\n{c}' }
        : { show: false },
      data: hasBehaviorData
        ? distribution
        : [{ value: 1, name: '暂无数据', itemStyle: { color: isDark ? '#374151' : '#e5e7eb' } }],
    }],
  })
}

function formatDateTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = num => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

onUnmounted(() => {
  chart?.dispose()
  stopAutoRefresh()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
  if (!loaded.value && !loading.value) {
    loadAll().catch(() => {})
  }
})
</script>

<style scoped>
.recommend-preview-guide {
  position: relative;
  background: transparent;
  border-radius: 0 !important;
  border-left: 0 !important;
  border-right: 0 !important;
}

.recommend-preview-hero {
  background: transparent;
  border-color: rgba(203, 213, 225, 0.9);
  border-radius: 0 !important;
  border-left: 0 !important;
  border-right: 0 !important;
}

.dark .recommend-preview-hero {
  background: transparent;
  border-color: rgba(71, 85, 105, 0.52);
}

.recommend-preview-hero__glow {
  display: none;
}

.recommend-preview-hero__glow--primary {
  top: -42px;
  left: -22px;
  width: 220px;
  height: 220px;
  background: rgba(59, 130, 246, 0.1);
}

.recommend-preview-hero__glow--secondary {
  right: 24px;
  bottom: -72px;
  width: 280px;
  height: 280px;
  background: rgba(16, 185, 129, 0.08);
}

.recommend-preview-kicker,
.recommend-preview-status-card__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: #1d4ed8;
}

.dark .recommend-preview-kicker,
.dark .recommend-preview-status-card__label {
  color: #93c5fd;
}

.recommend-preview-pill,
.recommend-preview-tag,
.recommend-preview-refresh-badge {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: transparent;
  color: #475569;
  border-radius: 999px;
  box-shadow: none;
}

.dark .recommend-preview-pill,
.dark .recommend-preview-tag,
.dark .recommend-preview-refresh-badge {
  border-color: rgba(148, 163, 184, 0.18);
  background: transparent;
  color: #cbd5e1;
  box-shadow: none;
}

.recommend-preview-pill {
  padding: 6px 12px;
}

.recommend-preview-tag {
  padding: 8px 14px;
  font-weight: 500;
}

.recommend-preview-status-card {
  padding: 16px;
  border-radius: 0;
  border-top: 1px solid rgba(148, 163, 184, 0.2);
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  border-left: 0;
  border-right: 0;
  background: transparent;
  box-shadow: none;
}

.recommend-preview-page > .panel-card {
  border-radius: 0 !important;
  border-left: 0 !important;
  border-right: 0 !important;
}

.dark .recommend-preview-status-card {
  border-color: rgba(71, 85, 105, 0.55);
  background: transparent;
  box-shadow: none;
}

.recommend-preview-refresh-badge {
  padding: 8px 14px;
  font-size: 12px;
}

.recommend-preview-refresh-panel {
  padding: 14px 16px;
  border-radius: 0;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  border-left: 0;
  border-right: 0;
  background: transparent;
}

.dark .recommend-preview-refresh-panel {
  border-color: rgba(71, 85, 105, 0.55);
  background: transparent;
}

:deep(.recommend-preview-primary-btn) {
  border-radius: 12px;
  box-shadow: none !important;
}

:deep(.recommend-preview-secondary-btn) {
  border-radius: 12px;
  border-color: rgba(148, 163, 184, 0.35);
  color: #334155;
}

.dark :deep(.recommend-preview-secondary-btn) {
  border-color: rgba(71, 85, 105, 0.7);
  color: #e2e8f0;
  background: rgba(15, 23, 42, 0.28);
}

.recommend-preview-algo-card,
.recommend-preview-metric-card,
.recommend-preview-product-card,
.recommend-preview-compare-product,
.recommend-preview-compare-summary {
  position: relative;
  overflow: hidden;
  border-radius: 0;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
  border-left: 0;
  border-right: 0;
  background: transparent;
}

.dark .recommend-preview-algo-card,
.dark .recommend-preview-metric-card,
.dark .recommend-preview-product-card,
.dark .recommend-preview-compare-product,
.dark .recommend-preview-compare-summary {
  border-color: rgba(71, 85, 105, 0.55);
  background: transparent;
}

.recommend-preview-algo-card {
  padding: 20px;
}

.recommend-preview-algo-card::before,
.recommend-preview-metric-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 999px;
}

.recommend-preview-algo-card--blue::before,
.recommend-preview-metric-card--blue::before {
  background: linear-gradient(180deg, #3b82f6, #06b6d4);
}

.recommend-preview-algo-card--violet::before,
.recommend-preview-metric-card--violet::before {
  background: linear-gradient(180deg, #8b5cf6, #c084fc);
}

.recommend-preview-algo-card--amber::before,
.recommend-preview-metric-card--amber::before {
  background: linear-gradient(180deg, #f59e0b, #fb7185);
}

.recommend-preview-algo-card--emerald::before,
.recommend-preview-metric-card--emerald::before {
  background: linear-gradient(180deg, #10b981, #14b8a6);
}

.recommend-preview-code-block {
  border-radius: 12px;
  background: #0f172a;
  padding: 14px 16px;
  color: #e2e8f0;
  font-size: 11px;
  line-height: 1.7;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  white-space: pre-line;
  overflow-x: auto;
}

.recommend-preview-metric-card {
  padding: 16px 18px 16px 20px;
}

.recommend-preview-product-card,
.recommend-preview-compare-product {
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease;
}

.recommend-preview-product-card:hover,
.recommend-preview-compare-product:hover {
  transform: none;
  border-color: rgba(96, 165, 250, 0.32);
  box-shadow: none;
}

.dark .recommend-preview-product-card:hover,
.dark .recommend-preview-compare-product:hover {
  box-shadow: none;
}

.recommend-preview-product-card__media {
  background: linear-gradient(180deg, rgba(241, 245, 249, 0.95), rgba(226, 232, 240, 0.8));
}

.dark .recommend-preview-product-card__media {
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.86), rgba(30, 41, 59, 0.72));
}

.recommend-preview-reason-pill,
.recommend-preview-rank-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  backdrop-filter: blur(8px);
}

.recommend-preview-rank-pill {
  background: rgba(15, 23, 42, 0.84);
  color: #fff;
}

.recommend-preview-reason-pill--blue,
.recommend-preview-reason-pill--violet,
.recommend-preview-reason-pill--indigo,
.recommend-preview-reason-pill--amber,
.recommend-preview-reason-pill--slate {
  color: #fff;
}

.recommend-preview-reason-pill--blue {
  background: #2563eb;
}

.recommend-preview-reason-pill--violet {
  background: #6d28d9;
}

.recommend-preview-reason-pill--indigo {
  background: #4f46e5;
}

.recommend-preview-reason-pill--amber {
  background: #d97706;
}

.recommend-preview-reason-pill--slate {
  background: #475569;
}

.recommend-preview-compare-summary {
  padding: 14px 16px;
  color: #475569;
  line-height: 1.7;
}

.dark .recommend-preview-compare-summary {
  color: #cbd5e1;
}

.recommend-preview-quality-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 18px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(250, 204, 21, 0.28);
  background: linear-gradient(135deg, rgba(254, 252, 232, 0.92), rgba(255, 247, 237, 0.9));
  padding: 16px 18px;
}

.dark .recommend-preview-quality-line {
  border-color: rgba(180, 83, 9, 0.36);
  background: linear-gradient(135deg, rgba(69, 26, 3, 0.28), rgba(15, 23, 42, 0.46));
}

:deep(.recommend-preview-compare-tabs .el-tabs__header) {
  margin-bottom: 16px;
}

:deep(.recommend-preview-compare-tabs .el-tabs__nav-wrap::after) {
  background-color: rgba(226, 232, 240, 0.9);
}

:deep(.recommend-preview-compare-tabs .el-tabs__item) {
  height: auto;
  padding: 10px 16px;
  border-radius: 12px 12px 0 0;
  color: #64748b;
}

:deep(.recommend-preview-compare-tabs .el-tabs__item.is-active) {
  color: #0f172a;
  font-weight: 700;
}

.dark :deep(.recommend-preview-compare-tabs .el-tabs__item) {
  color: #94a3b8;
}

.dark :deep(.recommend-preview-compare-tabs .el-tabs__item.is-active) {
  color: #f8fafc;
}
.recommend-preview-explain {
  margin-top: 18px;
  padding: 18px 0 4px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
}

.recommend-preview-explain-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.recommend-preview-explain-head h3 {
  margin: 0;
  color: #111827;
  font-size: 18px;
  font-weight: 800;
}

.recommend-preview-explain-head p,
.recommend-preview-layer p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.recommend-preview-explain-head span {
  flex: 0 0 auto;
  color: #0f766e;
  font-size: 13px;
  font-weight: 700;
}

.recommend-preview-layers {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.recommend-preview-lift {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 0 0 14px;
  padding: 12px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.recommend-preview-lift div {
  min-width: 0;
}

.recommend-preview-lift small,
.recommend-preview-lift span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.recommend-preview-lift strong {
  display: block;
  margin: 2px 0;
  color: #111827;
  font-size: 20px;
  font-weight: 900;
}

.recommend-preview-negative-note {
  margin: -4px 0 14px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.recommend-preview-layer {
  min-width: 0;
  padding: 0 12px 0 0;
  border-right: 1px solid rgba(148, 163, 184, 0.2);
}

.recommend-preview-layer:last-child {
  border-right: 0;
}

.recommend-preview-layer strong {
  display: block;
  margin-bottom: 8px;
  color: #111827;
  font-size: 14px;
}

.recommend-preview-layer div,
.recommend-preview-tag-row div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.recommend-preview-layer em,
.recommend-preview-tag-row em {
  max-width: 100%;
  padding: 3px 7px;
  border-radius: 6px;
  background: rgba(15, 118, 110, 0.08);
  color: #0f766e;
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
}

.recommend-preview-tag-table {
  display: grid;
  gap: 8px;
}

.recommend-preview-tag-row {
  display: grid;
  grid-template-columns: 44px minmax(160px, 1fr) minmax(220px, 1.2fr) minmax(180px, 0.9fr);
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
}

.recommend-preview-tag-row span {
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.recommend-preview-tag-row b {
  overflow: hidden;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-preview-tag-row small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .recommend-preview-layers {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recommend-preview-lift {
    grid-template-columns: 1fr;
  }

  .recommend-preview-tag-row {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .recommend-preview-tag-row div,
  .recommend-preview-tag-row small {
    grid-column: 2;
  }
}
</style>
