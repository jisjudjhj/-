<template>
  <div class="not-found">
    <div class="content">
      <h1 class="code">404</h1>
      <p class="title">页面未找到</p>
      <p class="desc">抱歉，您访问的页面不存在或已被移除</p>
      <el-button type="primary" @click="goBack">
        返回上一页
      </el-button>
      <el-button @click="goHome">
        回到首页
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/dashboard')
  }
}

const goHome = () => {
  router.push('/dashboard')
}
</script>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f5f7fa;
}

.content {
  text-align: center;
}

.code {
  font-size: 120px;
  font-weight: 700;
  color: #409eff;
  line-height: 1;
  margin: 0 0 16px;
}

.title {
  font-size: 24px;
  color: #303133;
  margin: 0 0 8px;
}

.desc {
  font-size: 14px;
  color: #909399;
  margin: 0 0 32px;
}
</style>
