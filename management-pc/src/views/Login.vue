<template>
  <div class="login-page min-h-screen bg-[#f5f6f8] dark:bg-gray-900 flex items-center justify-center relative overflow-hidden transition-colors duration-300">
    <div class="login-shell w-full max-w-[430px] px-6 z-10">
      <div class="login-panel relative px-2 py-9">
        <div class="relative text-center mb-9 flex flex-col items-center">
          <BrandLogo compact class="mb-5" />
          <div class="space-y-2">
            <p class="text-[28px] font-semibold text-slate-950 dark:text-white">数智优购</p>
          </div>
          <div class="login-divider mt-6 h-px w-20"></div>
          <p class="text-slate-600 dark:text-slate-200 mt-6 text-[15px] font-medium">管理端</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="rules"
          class="login-form"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              placeholder="用户名"
              :prefix-icon="User"
              size="large"
              class="!bg-transparent"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              placeholder="密码"
              :prefix-icon="Lock"
              show-password
              size="large"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item prop="captchaCode">
            <div class="captcha-row flex gap-3 w-full">
              <el-input
                v-model="loginForm.captchaCode"
                placeholder="验证码"
                :prefix-icon="Key"
                size="large"
                class="!bg-transparent min-w-0 flex-1"
                @keyup.enter="handleLogin"
              />
              <div
                class="captcha-box h-14 w-[132px] overflow-hidden cursor-pointer flex-shrink-0"
                @click="refreshCaptcha"
              >
                <img
                  v-if="captchaImage"
                  :src="captchaImage"
                  class="w-full h-full object-cover"
                  alt="验证码"
                />
                <div v-else class="w-full h-full flex items-center justify-center text-xs text-gray-400">
                  获取
                </div>
              </div>
            </div>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              class="login-submit w-full !rounded-2xl !h-14 text-lg font-semibold shadow-lg shadow-blue-500/20 transition-all duration-300"
              :loading="loading"
              @click="handleLogin"
            >
              登录
            </el-button>
          </el-form-item>
        </el-form>

        <div v-if="competitionMode" class="competition-login">
          <div class="competition-login__divider">
            <span>快捷入口</span>
          </div>
          <button type="button" class="competition-login__button" @click="handleCompetitionDemo">
            <span>进入运营工作台</span>
            <strong>推荐 · 分群 · 订单 · 风控</strong>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCaptcha, loginByPassword } from '../api/auth'
import { useUserStore } from '../store/user'
import BrandLogo from '../components/BrandLogo.vue'
import { competitionMode, demoAdminUser, demoToken } from '../utils/competitionDemoData'

const router = useRouter()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  captchaCode: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

const refreshCaptcha = async () => {
  try {
    const res = await getCaptcha()
    const data = res || {}
    captchaKey.value = data.captchaKey || ''
    captchaImage.value = data.captchaImage || ''
  } catch (error) {
    // 静默处理，用户可以点击重新获取
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value || loading.value) return

  try {
    await loginFormRef.value.validate()
    loading.value = true

    const res = await loginByPassword({
      username: loginForm.username,
      password: loginForm.password,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode,
    })

    const loginData = res || {}
    const user = loginData.user || {}
    const role = user.role

    if (role !== 'admin' && role !== 'merchant') {
      userStore.logout()
      ElMessage.error('当前账号不是管理端可用账号')
      return
    }

    userStore.setLoginData({
      token: loginData.token,
      user,
    })

    ElMessage.success(res.message || '登录成功')
    router.push(role === 'admin' ? '/admin/dashboard' : '/merchant/dashboard')
  } catch (error) {
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const handleCompetitionDemo = () => {
  userStore.setLoginData({
    token: demoToken,
    user: demoAdminUser,
  })
  ElMessage.success('已进入运营工作台')
  router.push('/admin/dashboard')
}

onMounted(refreshCaptcha)
</script>

<style scoped>
:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.login-shell {
  position: relative;
}

.login-shell::before {
  content: '';
  position: absolute;
  left: 24px;
  right: 24px;
  top: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 122, 255, 0.42), transparent);
}

.login-panel {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

:deep(.el-input__wrapper) {
  min-height: 56px;
  padding: 0 18px 0 24px;
  background-color: #f8fafc !important;
  border: 1px solid #e4e9f1;
  box-shadow: none;
  border-radius: 1rem;
  transition: transform 180ms ease, box-shadow 180ms ease, border-color 180ms ease, background-color 180ms ease;
}

:deep(.el-input__prefix) {
  margin-right: 16px;
}

:deep(.el-input__inner) {
  padding-left: 12px;
}

:deep(.el-input__prefix-inner) {
  margin-right: 4px;
}

.dark :deep(.el-input__wrapper) {
  background-color: rgba(31, 41, 55, 0.72) !important;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  border-color: rgba(71, 85, 105, 0.45);
}

:deep(.el-input__wrapper:hover) {
  transform: translateY(-1px);
  border-color: rgba(0, 122, 255, 0.36);
  box-shadow: 0 10px 24px rgba(0, 122, 255, 0.06);
}

:deep(.is-focus .el-input__wrapper) {
  transform: translateY(-1px);
  border-color: rgba(0, 122, 255, 0.72);
  box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.12), 0 14px 28px rgba(0, 122, 255, 0.12);
}

:deep(.el-input__prefix),
:deep(.el-input__suffix),
:deep(.el-input__prefix-inner),
:deep(.el-input__suffix-inner) {
  display: flex;
  align-items: center;
}

:deep(.el-input__inner) {
  color: inherit;
  font-size: 16px;
  line-height: 1;
}

:deep(.el-input__inner::placeholder) {
  color: rgb(148 163 184);
}

:deep(.el-button) {
  margin-top: 6px;
}

.competition-login {
  margin-top: 22px;
}

.competition-login__divider {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.competition-login__divider::before,
.competition-login__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}

.competition-login__button {
  width: 100%;
  margin-top: 14px;
  min-height: 62px;
  border: 0;
  border-top: 1px solid rgba(0, 122, 255, 0.18);
  border-bottom: 1px solid rgba(0, 122, 255, 0.18);
  border-radius: 0;
  background: transparent;
  color: #0f172a;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: transform 180ms ease, border-color 180ms ease, background 180ms ease;
}

.competition-login__button:hover,
.competition-login__button:focus-visible {
  transform: translateY(-1px);
  border-color: rgba(0, 122, 255, 0.42);
  background: rgba(0, 122, 255, 0.04);
  outline: none;
}

.competition-login__button:active {
  transform: scale(0.99);
}

.competition-login__button span {
  font-size: 16px;
  font-weight: 800;
}

.competition-login__button strong {
  color: #007aff;
  font-size: 12px;
  font-weight: 700;
}

.captcha-box {
  position: relative;
  border: 1px solid #dde4ee;
  border-radius: 14px;
  background: #ffffff;
}

.captcha-box::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.login-badge {
  border-color: rgba(0, 122, 255, 0.18);
  color: #007aff;
}

.login-badge span {
  background: #007aff;
}

.login-divider {
  background: linear-gradient(90deg, transparent, rgba(0, 122, 255, 0.42), transparent);
}

:deep(.login-submit.el-button--primary) {
  border: none;
  background: #007aff;
  box-shadow: none;
}

:deep(.login-submit.el-button--primary:hover),
:deep(.login-submit.el-button--primary:focus-visible) {
  background: #006fe6;
  box-shadow: none;
  transform: translateY(-1px);
}

:deep(.login-submit.el-button--primary:active) {
  transform: translateY(0);
  box-shadow: none;
}

.login-page {
  background: linear-gradient(180deg, #ffffff 0%, #f5f5f7 100%) !important;
}

.login-page > .absolute:nth-child(1) {
  background: #007aff;
}

.login-page > .absolute:nth-child(2) {
  background: #111827;
}

.login-page > .absolute:nth-child(3) {
  background: #c8ccd4;
}

:deep(.el-input__wrapper:hover) {
  border-color: rgba(0, 122, 255, 0.32);
  box-shadow: 0 10px 24px rgba(17, 24, 39, 0.06);
}

:deep(.is-focus .el-input__wrapper) {
  border-color: rgba(0, 122, 255, 0.5);
  box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.1), 0 12px 24px rgba(17, 24, 39, 0.08);
}

@media (max-width: 480px) {
  .login-page > .z-10 {
    padding-left: 24px;
    padding-right: 24px;
  }

  .login-page > .z-10 > div {
    padding: 34px 0 36px;
    border-radius: 0;
  }

  .captcha-row {
    gap: 10px;
  }

  .captcha-box {
    width: 116px !important;
  }

  :deep(.el-input__wrapper) {
    padding-left: 18px;
    padding-right: 14px;
  }

  :deep(.el-input__prefix) {
    margin-right: 10px;
  }

  :deep(.el-input__inner) {
    padding-left: 6px;
    font-size: 15px;
  }
}
</style>
