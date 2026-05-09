<template>
  <div class="brand-logo" :class="{ 'brand-logo--compact': compact, 'brand-logo--dark': dark }">
    <div class="brand-logo__icon" aria-hidden="true">
      <span class="brand-logo__surface"></span>
      <span class="brand-logo__node brand-logo__node--a"></span>
      <span class="brand-logo__node brand-logo__node--b"></span>
      <span class="brand-logo__node brand-logo__node--c"></span>
      <span class="brand-logo__line brand-logo__line--a"></span>
      <span class="brand-logo__line brand-logo__line--b"></span>
    </div>
    <div v-if="!compact" class="brand-logo__copy">
      <div class="brand-logo__title">{{ title }}</div>
      <div v-if="subtitle" class="brand-logo__subtitle">{{ subtitle }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    default: '数智优购',
  },
  subtitle: {
    type: String,
    default: '智能电商平台',
  },
  compact: {
    type: Boolean,
    default: false,
  },
  dark: {
    type: Boolean,
    default: false,
  },
})
</script>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.brand-logo__icon {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 14px;
  overflow: hidden;
  flex: none;
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.16);
}

.brand-logo__surface {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #153e75 0%, #1d4ed8 58%, #60a5fa 100%);
}

.brand-logo__node,
.brand-logo__line {
  position: absolute;
  background: rgba(255, 255, 255, 0.94);
}

.brand-logo__node {
  border-radius: 999px;
}

.brand-logo__node--a {
  width: 9px;
  height: 9px;
  left: 8px;
  top: 10px;
}

.brand-logo__node--b {
  width: 11px;
  height: 11px;
  right: 7px;
  top: 7px;
}

.brand-logo__node--c {
  width: 12px;
  height: 12px;
  left: 13px;
  bottom: 7px;
}

.brand-logo__line {
  height: 3px;
  border-radius: 999px;
  transform-origin: left center;
}

.brand-logo__line--a {
  width: 16px;
  left: 15px;
  top: 14px;
  transform: rotate(-14deg);
}

.brand-logo__line--b {
  width: 12px;
  left: 15px;
  top: 22px;
  transform: rotate(44deg);
}

.brand-logo__title {
  font-size: 15px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.brand-logo__subtitle {
  margin-top: 4px;
  font-size: 11px;
  line-height: 1.2;
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

.brand-logo--compact .brand-logo__icon {
  width: 34px;
  height: 34px;
  border-radius: 12px;
}

.brand-logo--dark .brand-logo__title {
  color: #fff;
}

.brand-logo--dark .brand-logo__subtitle {
  color: rgba(255, 255, 255, 0.72);
}
</style>
