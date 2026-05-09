<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h2 class="text-xl font-bold text-gray-800 dark:text-gray-100 mb-6">消息推送</h2>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" class="max-w-3xl">
        <el-form-item label="消息类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio-button value="system">系统公告</el-radio-button>
            <el-radio-button value="promotion">营销活动</el-radio-button>
            <el-radio-button value="order">订单通知</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="消息标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入消息标题" class="!bg-transparent" />
        </el-form-item>

        <el-form-item label="消息内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="6"
            placeholder="请输入消息内容"
            class="!bg-transparent custom-textarea"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="!rounded-xl shadow-lg shadow-blue-500/30 px-8"
            :loading="submitting"
            @click="handleSubmit"
          >
            <el-icon class="mr-2"><Position /></el-icon>
            立即群发
          </el-button>
          <span class="ml-4 text-sm text-gray-500">当前接口会向所有正常状态用户发送站内消息。</span>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">最近一次发送结果</h3>
      <div v-if="lastResult" class="bg-white/40 dark:bg-gray-800/40 p-5 rounded-xl border border-gray-200/50 dark:border-gray-700/50 space-y-2">
        <div class="flex items-center justify-between gap-4">
          <div class="font-medium text-gray-900 dark:text-gray-100">{{ lastResult.title }}</div>
          <el-tag class="!rounded-full">{{ typeTextMap[lastResult.type] }}</el-tag>
        </div>
        <div class="text-sm text-gray-500 leading-6">{{ lastResult.content }}</div>
        <div class="text-sm text-emerald-500">{{ lastResult.message }}</div>
        <div class="text-xs text-gray-400">{{ lastResult.time }}</div>
      </div>
      <el-empty v-else description="暂无发送记录" />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { broadcastAdminMessage } from '../../api/admin'

const formRef = ref(null)
const submitting = ref(false)
const lastResult = ref(null)

const form = reactive({
  type: 'system',
  title: '',
  content: '',
})

const rules = {
  type: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入消息标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入消息内容', trigger: 'blur' }],
}

const typeTextMap = {
  system: '系统公告',
  promotion: '营销活动',
  order: '订单通知',
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return

  await formRef.value.validate()
  submitting.value = true

  try {
    const res = await broadcastAdminMessage({
      title: form.title,
      content: form.content,
      type: form.type,
    })
    ElMessage.success(res.message || '消息发送成功')
    lastResult.value = {
      title: form.title,
      content: form.content,
      type: form.type,
      message: res.message || '消息发送成功',
      time: new Date().toLocaleString(),
    }
    form.title = ''
    form.content = ''
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
:deep(.custom-textarea .el-textarea__inner) {
  background-color: rgba(255, 255, 255, 0.5) !important;
  backdrop-filter: blur(10px);
  border-radius: 0.75rem;
}
.dark :deep(.custom-textarea .el-textarea__inner) {
  background-color: rgba(31, 41, 55, 0.5) !important;
}
:deep(.el-timeline-item__timestamp) {
  color: var(--el-text-color-secondary);
}
</style>
