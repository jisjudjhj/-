<script setup>
const conciseCopy = import.meta.env.VITE_CONCISE_COPY !== 'false'

defineProps({
  title: { type: String, required: true },
  value: { type: [String, Number], required: true },
  sub: { type: String, default: '' },
  icon: { type: String, required: true },
  color: { type: String, default: '' },
})
</script>

<template>
  <article class="panel-card ios-stat px-4 py-4">
    <div class="flex items-start justify-between gap-4">
      <div class="min-w-0">
        <p class="text-[13px] font-medium text-slate-500 dark:text-slate-400">{{ title }}</p>
        <p class="mt-1.5 text-[26px] font-semibold leading-[1.15] text-slate-950 dark:text-slate-100">{{ value }}</p>
        <p v-if="sub && !conciseCopy" class="mt-2 text-xs leading-5 text-slate-500 dark:text-slate-400">{{ sub }}</p>
      </div>
      <div
        class="ios-stat__icon flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
      >
        <el-icon class="text-base"><component :is="icon" /></el-icon>
      </div>
    </div>
  </article>
</template>
