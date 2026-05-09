<template>
  <el-config-provider :size="size" :z-index="zIndex">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import { ref } from 'vue'

const size = ref('default')
const zIndex = ref(3000)
</script>
