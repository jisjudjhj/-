<template>
  <section class="defense-hero">
    <div class="defense-hero__top">
      <div class="min-w-0">
        <div v-if="kicker" class="defense-hero__kicker">{{ kicker }}</div>
        <h1 class="defense-hero__title">{{ title }}</h1>
        <p v-if="description && !conciseCopy" class="defense-hero__desc">{{ description }}</p>
      </div>
      <span v-if="badge" class="defense-hero__badge">{{ badge }}</span>
    </div>

    <div class="defense-hero__body">
      <div class="defense-hero__main">
        <div class="defense-hero__section">
          <div v-if="leftEyebrow" class="defense-hero__eyebrow">{{ leftEyebrow }}</div>
          <div v-if="leftTitle" class="defense-hero__section-title">{{ leftTitle }}</div>
        </div>

        <div class="defense-hero__rows">
          <div
            v-for="(row, index) in leftRows"
            :key="row.key || `${row.label}-${index}`"
            class="defense-hero__row"
          >
            <div class="defense-hero__label">{{ row.label }}</div>
            <p class="defense-hero__value">{{ row.value }}</p>
          </div>
        </div>

        <slot name="left-extra" />
      </div>

      <div class="defense-hero__rail">
        <div class="defense-hero__section defense-hero__section--rail">
          <div v-if="rightEyebrow" class="defense-hero__eyebrow defense-hero__eyebrow--rail">{{ rightEyebrow }}</div>
          <div v-if="rightTitle" class="defense-hero__section-title defense-hero__section-title--rail">{{ rightTitle }}</div>
        </div>

        <div class="defense-hero__rows defense-hero__rows--rail">
          <div
            v-for="(row, index) in rightRows"
            :key="row.key || `${row.label}-${index}`"
            class="defense-hero__row defense-hero__row--rail"
          >
            <div class="defense-hero__label defense-hero__label--rail">{{ row.label }}</div>
            <p class="defense-hero__value defense-hero__value--rail">{{ row.value }}</p>
          </div>
        </div>

        <slot name="right-extra" />
      </div>
    </div>

    <slot />
  </section>
</template>

<script setup>
const conciseCopy = import.meta.env.VITE_CONCISE_COPY !== 'false'

defineProps({
  kicker: {
    type: String,
    default: '',
  },
  title: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  badge: {
    type: String,
    default: '',
  },
  leftEyebrow: {
    type: String,
    default: '',
  },
  leftTitle: {
    type: String,
    default: '',
  },
  rightEyebrow: {
    type: String,
    default: '',
  },
  rightTitle: {
    type: String,
    default: '',
  },
  leftRows: {
    type: Array,
    default: () => [],
  },
  rightRows: {
    type: Array,
    default: () => [],
  },
})
</script>

<style scoped>
.defense-hero {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 2px 0 0;
}

.defense-hero::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border: 1px solid rgba(203, 213, 225, 0.9);
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.98) 0%, rgba(255, 255, 255, 0.98) 70%, rgba(239, 246, 255, 0.96) 70%, rgba(239, 246, 255, 0.96) 100%);
  border-radius: 28px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.dark .defense-hero::before {
  border-color: rgba(71, 85, 105, 0.56);
  background:
    linear-gradient(90deg, rgba(15, 23, 42, 0.96) 0%, rgba(15, 23, 42, 0.96) 70%, rgba(30, 41, 59, 0.96) 70%, rgba(30, 41, 59, 0.96) 100%);
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.24);
}

.defense-hero > * {
  position: relative;
  z-index: 1;
}

.defense-hero__top {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  padding: 0 26px;
}

.defense-hero__kicker {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.24em;
  text-transform: uppercase;
  color: #1d4ed8;
}

.dark .defense-hero__kicker {
  color: #93c5fd;
}

.defense-hero__title {
  margin-top: 8px;
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 800;
  line-height: 1.12;
  color: #020617;
}

.dark .defense-hero__title {
  color: #f8fafc;
}

.defense-hero__desc {
  margin-top: 10px;
  max-width: 720px;
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.dark .defense-hero__desc {
  color: #cbd5e1;
}

.defense-hero__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(37, 99, 235, 0.16);
  border-radius: 999px;
  padding: 9px 14px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #1d4ed8;
  white-space: nowrap;
  background: rgba(239, 246, 255, 0.9);
}

.dark .defense-hero__badge {
  border-color: rgba(96, 165, 250, 0.24);
  color: #bfdbfe;
  background: rgba(30, 41, 59, 0.92);
}

.defense-hero__body {
  display: grid;
  grid-template-columns: minmax(0, 1.72fr) minmax(300px, 0.82fr);
  gap: 0;
}

.defense-hero__main,
.defense-hero__rail {
  padding: 18px 26px 20px;
}

.defense-hero__rail {
  border-left: 1px solid rgba(191, 219, 254, 0.9);
  background: rgba(248, 250, 252, 0.74);
}

.dark .defense-hero__rail {
  border-left-color: rgba(71, 85, 105, 0.56);
  background: rgba(15, 23, 42, 0.18);
}

.defense-hero__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.defense-hero__eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #64748b;
}

.defense-hero__eyebrow--rail {
  color: #1d4ed8;
}

.dark .defense-hero__eyebrow {
  color: #94a3b8;
}

.dark .defense-hero__eyebrow--rail {
  color: #bfdbfe;
}

.defense-hero__section-title {
  font-size: 20px;
  font-weight: 800;
  line-height: 1.2;
  color: #0f172a;
}

.defense-hero__section-title--rail {
  color: #0f172a;
}

.dark .defense-hero__section-title {
  color: #f8fafc;
}

.dark .defense-hero__section-title--rail {
  color: #f8fafc;
}

.defense-hero__rows {
  margin-top: 14px;
}

.defense-hero__row {
  display: grid;
  grid-template-columns: minmax(110px, 150px) minmax(0, 1fr);
  gap: 16px;
  padding: 12px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
}

.defense-hero__row:first-child {
  border-top: none;
  padding-top: 0;
}

.defense-hero__row--rail {
  grid-template-columns: 1fr;
  gap: 8px;
  border-top-color: rgba(191, 219, 254, 0.9);
}

.dark .defense-hero__row {
  border-top-color: rgba(71, 85, 105, 0.44);
}

.dark .defense-hero__row--rail {
  border-top-color: rgba(71, 85, 105, 0.5);
}

.defense-hero__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
}

.defense-hero__label--rail {
  color: #1d4ed8;
}

.dark .defense-hero__label {
  color: #94a3b8;
}

.dark .defense-hero__label--rail {
  color: #93c5fd;
}

.defense-hero__value {
  margin: 0;
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
}

.defense-hero__value--rail {
  color: #334155;
}

.dark .defense-hero__value {
  color: #cbd5e1;
}

.dark .defense-hero__value--rail {
  color: #cbd5e1;
}

@media (max-width: 1279px) {
  .defense-hero__top {
    align-items: start;
    flex-direction: column;
  }

  .defense-hero__body {
    grid-template-columns: 1fr;
  }

  .defense-hero__rail {
    border-left: none;
    border-top: 1px solid rgba(191, 219, 254, 0.9);
  }
}

@media (max-width: 768px) {
  .defense-hero::before {
    border-radius: 24px;
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, rgba(255, 255, 255, 0.98) 58%, rgba(239, 246, 255, 0.96) 58%, rgba(239, 246, 255, 0.96) 100%);
  }

  .dark .defense-hero::before {
    background:
      linear-gradient(180deg, rgba(15, 23, 42, 0.96) 0%, rgba(15, 23, 42, 0.96) 58%, rgba(30, 41, 59, 0.96) 58%, rgba(30, 41, 59, 0.96) 100%);
  }

  .defense-hero__top,
  .defense-hero__main,
  .defense-hero__rail {
    padding-left: 18px;
    padding-right: 18px;
  }

  .defense-hero__title {
    font-size: 26px;
  }

  .defense-hero__row {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
