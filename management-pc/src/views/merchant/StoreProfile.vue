<template>
  <div v-loading="loading" class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-6">店铺资料</h3>
      <el-form :model="profile" label-width="100px" class="max-w-xl">
        <el-form-item label="店铺头像">
          <div class="flex items-center gap-4">
            <el-avatar :size="72" :src="profile.avatar">
              {{ (profile.nickname || profile.username || '?').charAt(0) }}
            </el-avatar>
            <el-input v-model="profile.avatarUrl" placeholder="输入头像 URL" class="flex-1" />
          </div>
        </el-form-item>
        <el-form-item label="店铺名称">
          <el-input v-model="profile.nickname" placeholder="输入店铺名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="profile.phone" disabled>
            <template #append>
              <el-tag type="info" size="small">不可修改</el-tag>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="handleSave">保存修改</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">经营概览</h3>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div class="text-center p-4 bg-blue-50 dark:bg-blue-900/30 rounded-xl">
          <div class="text-2xl font-bold text-blue-600">{{ formatNum(bizStats.totalProducts) }}</div>
          <div class="text-xs text-gray-500 mt-1">在售商品</div>
        </div>
        <div class="text-center p-4 bg-green-50 dark:bg-green-900/30 rounded-xl">
          <div class="text-2xl font-bold text-green-600">{{ formatNum(bizStats.totalSales) }}</div>
          <div class="text-xs text-gray-500 mt-1">累计销量</div>
        </div>
        <div class="text-center p-4 bg-purple-50 dark:bg-purple-900/30 rounded-xl">
          <div class="text-2xl font-bold text-purple-600">￥{{ formatNum(bizStats.totalRevenue) }}</div>
          <div class="text-xs text-gray-500 mt-1">累计营收</div>
        </div>
        <div class="text-center p-4 bg-amber-50 dark:bg-amber-900/30 rounded-xl">
          <div class="text-2xl font-bold text-amber-600">{{ bizStats.lowStockCount || 0 }}</div>
          <div class="text-xs text-gray-500 mt-1">库存预警</div>
        </div>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100 mb-4">账号安全</h3>
      <el-form label-width="100px" class="max-w-xl">
        <el-form-item label="当前密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="输入新密码，至少 6 位" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" :loading="changingPwd" @click="handleChangePassword">修改密码</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getCurrentUser, updateProfile, changePassword } from '../../api/auth'
import { getMerchantDashboard } from '../../api/merchant'

const loading = ref(false)
const saving = ref(false)
const changingPwd = ref(false)
const profile = ref({ nickname: '', phone: '', avatar: '', avatarUrl: '', username: '' })
const bizStats = ref({ totalProducts: 0, totalSales: 0, totalRevenue: 0, lowStockCount: 0 })
const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const userStore = useUserStore()

function formatNum(value) {
  return value == null ? '0' : Number(value).toLocaleString('zh-CN')
}

async function loadProfile() {
  loading.value = true
  try {
    const res = await getCurrentUser()
    if (res) {
      profile.value = { ...res, avatarUrl: res.avatar || '' }
      userStore.setUserInfo(res)
    }
  } catch {
    ElMessage.error('获取店铺资料失败')
  } finally {
    loading.value = false
  }
}

async function loadBizStats() {
  try {
    const res = await getMerchantDashboard()
    if (res) {
      bizStats.value = {
        totalProducts: res.totalProducts || 0,
        totalSales: res.totalSales || 0,
        totalRevenue: res.totalRevenue || 0,
        lowStockCount: (res.lowStockProducts || []).length,
      }
    }
  } catch {
    ElMessage.error('获取经营数据失败')
  }
}

async function handleSave() {
  saving.value = true
  try {
    await updateProfile({
      nickname: profile.value.nickname,
      avatar: profile.value.avatarUrl,
    })
    ElMessage.success('资料修改申请已提交')
    await loadProfile()
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    saving.value = false
  }
}

async function handleChangePassword() {
  if (!pwdForm.value.oldPassword || !pwdForm.value.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (pwdForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }

  changingPwd.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword,
    })
    ElMessage.success('密码修改成功')
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (error) {
    ElMessage.error(error?.message || '修改失败')
  } finally {
    changingPwd.value = false
  }
}

onMounted(() => {
  loadProfile()
  loadBizStats()
})
</script>
