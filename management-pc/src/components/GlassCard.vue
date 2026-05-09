<script setup>
defineProps({
  title: { type: String, default: '' },
})
</script>

<template>
  <section class="panel-card overflow-hidden">
    <header
      v-if="title || $slots.header"
      class="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3.5 dark:border-slate-700"
    >
      <h3 v-if="title" class="m-0 text-base font-semibold text-slate-900 dark:text-slate-100">{{ title }}</h3>
      <slot name="header" />
    </header>
    <div class="p-5" :class="{ 'pt-4': !(title || $slots.header) }">
      <slot />
    </div>
  </section>
</template>
