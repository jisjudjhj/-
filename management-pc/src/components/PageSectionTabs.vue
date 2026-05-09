<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: String, required: true },
  tabs: {
    type: Array,
    default: () => [],
  },
  primaryLabel: { type: String, default: '' },
  pageLabel: { type: String, default: '' },
  title: { type: String, default: '功能分区' },
  description: { type: String, default: '' },
  activeLabel: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])
const conciseCopy = import.meta.env.VITE_CONCISE_COPY !== 'false'

const currentTab = computed(() => props.tabs.find(tab => tab.key === props.modelValue) || props.tabs[0] || null)

const setTab = key => {
  if (!key || key === props.modelValue) return
  emit('update:modelValue', key)
}
</script>

<template>
  <section class="page-section-tabs">
    <div class="page-section-tabs__meta">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div v-if="primaryLabel || pageLabel" class="page-section-tabs__breadcrumb">
            <span v-if="primaryLabel">{{ primaryLabel }}</span>
            <template v-if="pageLabel">
              <span v-if="primaryLabel" class="page-section-tabs__dot"></span>
              <span>{{ pageLabel }}</span>
            </template>
          </div>
          <h2 class="page-section-tabs__title">{{ title }}</h2>
          <p v-if="description && !conciseCopy" class="page-section-tabs__desc">{{ description }}</p>
        </div>
      </div>
    </div>

    <div class="page-section-tabs__body">
      <div class="overflow-x-auto pb-1">
        <div class="page-section-tabs__tab-list">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            type="button"
            class="page-section-tabs__tab"
            :class="modelValue === tab.key
              ? 'page-section-tabs__tab--active'
              : 'page-section-tabs__tab--idle'"
            @click="setTab(tab.key)"
          >
            <div class="page-section-tabs__tab-label">{{ tab.label }}</div>
            <div v-if="tab.hint && !conciseCopy" class="page-section-tabs__tab-hint">{{ tab.hint }}</div>
          </button>
        </div>
      </div>

      <div
        v-if="currentTab?.description && !conciseCopy"
        class="page-section-tabs__active-desc"
      >
        {{ currentTab.description }}
      </div>

      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-section-tabs {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 0 0;
}

.page-section-tabs__meta {
  padding: 0 4px 12px;
  border-bottom: 1px solid rgba(203, 213, 225, 0.9);
}

.dark .page-section-tabs__meta {
  border-bottom-color: rgba(71, 85, 105, 0.5);
}

.page-section-tabs__breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #64748b;
}

.dark .page-section-tabs__breadcrumb {
  color: #94a3b8;
}

.page-section-tabs__dot {
  width: 4px;
  height: 4px;
  border-radius: 999px;
  background: #cbd5e1;
}

.dark .page-section-tabs__dot {
  background: #475569;
}

.page-section-tabs__title {
  margin-top: 10px;
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
}

.dark .page-section-tabs__title {
  color: #f8fafc;
}

.page-section-tabs__desc {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.7;
  color: #64748b;
}

.dark .page-section-tabs__desc {
  color: #94a3b8;
}

.page-section-tabs__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.page-section-tabs__tab-list {
  display: flex;
  min-width: max-content;
  gap: 8px;
  padding-bottom: 4px;
}

.page-section-tabs__tab {
  min-width: 124px;
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.85);
  padding: 12px 16px 10px;
  text-align: left;
  transition: all 0.2s ease;
}

.page-section-tabs__tab--active {
  border-color: rgba(37, 99, 235, 0.22);
  background: rgba(239, 246, 255, 0.95);
  color: #0f172a;
}

.page-section-tabs__tab--idle {
  background: transparent;
  color: #526172;
}

.page-section-tabs__tab--idle:hover {
  border-color: rgba(37, 99, 235, 0.18);
  color: #0f172a;
}

.dark .page-section-tabs__tab {
  border-color: rgba(71, 85, 105, 0.5);
}

.dark .page-section-tabs__tab--active {
  border-color: rgba(96, 165, 250, 0.24);
  background: rgba(30, 41, 59, 0.96);
  color: #eff6ff;
}

.dark .page-section-tabs__tab--idle {
  color: #cbd5e1;
}

.dark .page-section-tabs__tab--idle:hover {
  border-color: rgba(148, 163, 184, 0.72);
  color: #f8fafc;
}

.page-section-tabs__tab-label {
  font-size: 14px;
  font-weight: 700;
}

.page-section-tabs__tab-hint {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.5;
  opacity: 0.72;
}

.page-section-tabs__active-desc {
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
}

.dark .page-section-tabs__active-desc {
  color: #cbd5e1;
}
</style>
