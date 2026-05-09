<template>
  <div class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-2">账号安全</h3>
      <p class="text-sm text-gray-500 dark:text-gray-400">这里可以修改登录密码，也可以快速跳转到与你身份相关的管理设置。</p>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-[minmax(0,1fr)_320px] gap-6">
      <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
        <el-form :model="form" label-width="100px" class="max-w-2xl">
          <el-form-item label="当前密码">
            <el-input v-model="form.oldPassword" type="password" show-password placeholder="请输入当前密码" />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="changePassword">更新密码</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="space-y-6">
        <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
          <h4 class="text-base font-semibold text-gray-800 dark:text-gray-100">快捷入口</h4>
          <div class="mt-4 space-y-3">
            <el-button
              v-if="userRole === 'merchant'"
              class="!ml-0 !w-full"
              @click="router.push('/merchant/store')"
            >
              前往店铺设置
            </el-button>
            <el-button
              v-if="userRole === 'admin'"
              class="!ml-0 !w-full"
              @click="router.push('/admin/system/modules')"
            >
              前往功能开关
            </el-button>
            <el-button
              v-if="userRole === 'admin'"
              class="!ml-0 !w-full"
              @click="router.push('/admin/system/logs')"
            >
              查看操作日志
            </el-button>
          </div>
        </div>

        <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
          <h4 class="text-base font-semibold text-gray-800 dark:text-gray-100">安全提示</h4>
          <div class="mt-4 text-sm leading-7 text-gray-500 dark:text-gray-400">
            <div>修改密码后建议重新登录一次，确认新密码已经生效。</div>
            <div>请为后台账号设置独立强密码，并定期更新。</div>
            <div>如果资料修改处于待审核状态，不影响这里的密码修改。</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword as updatePassword } from '../../api/auth'
import { useUserStore } from '../../store/user'

const router = useRouter()
const userStore = useUserStore()

const submitting = ref(false)
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const userRole = computed(() => userStore.role)

async function changePassword() {
  if (!form.oldPassword || !form.newPassword || !form.confirmPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (form.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  submitting.value = true
  try {
    await updatePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    })
    ElMessage.success('密码修改成功')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
  } catch (error) {
    ElMessage.error(error?.message || '密码修改失败')
  } finally {
    submitting.value = false
  }
}
</script>
