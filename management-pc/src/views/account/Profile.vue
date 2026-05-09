<template>
  <div v-loading="loading" class="space-y-6">
    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <div class="flex flex-col gap-6 lg:flex-row lg:items-start">
        <div class="flex items-center gap-4 min-w-0">
          <el-avatar :size="72" :src="form.avatarUrl || profile.avatar">
            {{ displayName.charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="min-w-0">
            <div class="text-xl font-semibold text-gray-800 dark:text-gray-100 truncate">{{ displayName }}</div>
            <div class="text-sm text-gray-500 dark:text-gray-400 mt-1">账号：{{ profile.username || '-' }}</div>
            <div class="mt-3 flex flex-wrap gap-2">
              <el-tag size="small" :type="profile.role === 'admin' ? 'danger' : 'success'">
                {{ profile.role === 'admin' ? '管理员' : '商家' }}
              </el-tag>
              <el-tag v-if="changeStatus?.latestRequest?.status === 0" size="small" type="warning">资料修改待审核</el-tag>
              <el-tag v-else size="small" type="info">资料修改需管理员审核</el-tag>
            </div>
          </div>
        </div>

        <div class="flex-1 grid grid-cols-1 md:grid-cols-3 gap-4">
          <div class="rounded-xl bg-gray-50/60 dark:bg-gray-700/30 p-4">
            <div class="text-sm text-gray-500 dark:text-gray-400">手机号</div>
            <div class="mt-2 text-base font-medium text-gray-800 dark:text-gray-100">{{ profile.phone || '-' }}</div>
          </div>
          <div class="rounded-xl bg-gray-50/60 dark:bg-gray-700/30 p-4">
            <div class="text-sm text-gray-500 dark:text-gray-400">角色</div>
            <div class="mt-2 text-base font-medium text-gray-800 dark:text-gray-100">
              {{ profile.role === 'admin' ? '管理员' : '商家' }}
            </div>
          </div>
          <div class="rounded-xl bg-gray-50/60 dark:bg-gray-700/30 p-4">
            <div class="text-sm text-gray-500 dark:text-gray-400">最近修改</div>
            <div class="mt-2 text-base font-medium text-gray-800 dark:text-gray-100">{{ formatDate(changeStatus?.lastChange) }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="bg-white/60 dark:bg-gray-800/60 backdrop-blur-xl p-6 rounded-2xl border border-white/20 dark:border-gray-700/30 shadow-lg">
      <div class="flex items-start justify-between gap-4 mb-6">
        <div>
          <h3 class="text-lg font-semibold text-gray-800 dark:text-gray-100">资料修改</h3>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">这里只支持修改昵称和头像，提交后会进入审核流程，7 天内只能提交一次。</p>
        </div>
        <el-button text @click="loadAll">刷新</el-button>
      </div>

      <el-alert
        v-if="changeStatus?.reason"
        :title="changeStatus.reason"
        type="warning"
        :closable="false"
        class="mb-5"
      />

      <el-form :model="form" label-width="100px" class="max-w-2xl">
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="20" show-word-limit placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="头像 URL">
          <el-input v-model="form.avatarUrl" placeholder="请输入头像地址" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input :model-value="profile.phone || '-'" disabled />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submitProfile">提交修改</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getCurrentUser, getProfileChangeStatus, updateProfile } from '../../api/auth'

const userStore = useUserStore()

const loading = ref(false)
const saving = ref(false)
const profile = ref({})
const changeStatus = ref(null)
const form = reactive({
  nickname: '',
  avatarUrl: '',
})

const displayName = computed(() => form.nickname || profile.value.nickname || profile.value.username || '用户')
const canModify = computed(() => changeStatus.value?.canModify !== false)
const hasChanges = computed(() =>
  (form.nickname || '') !== (profile.value.nickname || '')
  || (form.avatarUrl || '') !== (profile.value.avatar || '')
)
const canSubmit = computed(() => canModify.value && hasChanges.value)

function formatDate(value) {
  if (!value) return '暂无'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadAll() {
  loading.value = true
  try {
    const [me, status] = await Promise.all([
      getCurrentUser(),
      getProfileChangeStatus(),
    ])
    profile.value = me || {}
    changeStatus.value = status || null
    form.nickname = me?.nickname || ''
    form.avatarUrl = me?.avatar || ''
    userStore.setUserInfo(me || {})
  } catch {
    ElMessage.error('获取个人资料失败')
  } finally {
    loading.value = false
  }
}

async function submitProfile() {
  if (!hasChanges.value) {
    ElMessage.warning('资料没有变化')
    return
  }
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname || null,
      avatar: form.avatarUrl || null,
    })
    ElMessage.success('资料修改申请已提交')
    await loadAll()
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadAll)
</script>
