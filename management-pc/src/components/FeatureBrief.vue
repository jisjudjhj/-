<template>
  <section class="feature-brief" :aria-label="title || '功能说明'">
    <div class="feature-brief__head">
      <span class="feature-brief__kicker">{{ kicker }}</span>
      <strong v-if="title" class="feature-brief__title">{{ title }}</strong>
    </div>
    <div class="feature-brief__grid">
      <article v-for="item in items" :key="item.label" class="feature-brief__item">
        <span class="feature-brief__label">{{ item.label }}</span>
        <strong class="feature-brief__value">{{ item.value }}</strong>
        <p class="feature-brief__text">{{ item.text }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
defineProps({
  kicker: {
    type: String,
    default: '功能说明',
  },
  title: {
    type: String,
    default: '',
  },
  items: {
    type: Array,
    default: () => [],
  },
})
</script>

<style scoped>
.feature-brief {
  border-top: 1px solid rgba(226, 232, 240, 0.92);
  border-bottom: 1px solid rgba(226, 232, 240, 0.92);
  background: transparent;
  padding: 14px 0;
}

.feature-brief__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.feature-brief__kicker {
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.feature-brief__title {
  color: #0f172a;
  font-size: 14px;
}

.feature-brief__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.feature-brief__item {
  min-width: 0;
  border-left: 1px solid rgba(226, 232, 240, 0.9);
  padding: 2px 16px;
}

.feature-brief__item:first-child {
  border-left: 0;
  padding-left: 0;
}

.feature-brief__label {
  display: block;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.feature-brief__value {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
}

.feature-brief__text {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.65;
}

:global(.dark) .feature-brief {
  border-color: rgba(51, 65, 85, 0.86);
}

:global(.dark) .feature-brief__title,
:global(.dark) .feature-brief__value {
  color: #f8fafc;
}

:global(.dark) .feature-brief__item {
  border-left-color: rgba(51, 65, 85, 0.86);
}

:global(.dark) .feature-brief__label,
:global(.dark) .feature-brief__text {
  color: #94a3b8;
}

@media (max-width: 900px) {
  .feature-brief__grid {
    grid-template-columns: 1fr;
  }

  .feature-brief__item {
    border-left: 0;
    border-top: 1px solid rgba(226, 232, 240, 0.9);
    padding: 12px 0 0;
  }

  .feature-brief__item:first-child {
    border-top: 0;
    padding-top: 0;
  }
}
</style>
