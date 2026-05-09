<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import GlassCard from '../../components/GlassCard.vue'
import {
  getAdminCurrentRolePermissions,
  getAdminRolePermissions,
  resetAdminRolePermissions,
  updateAdminRolePermissions,
} from '../../api/admin'

const loading = ref(false)
const saving = ref(false)
const currentRoleInfo = ref({ role: '', permissions: [] })
const overview = ref({
  roles: [],
  catalog: [],
  protectedPermissions: [],
  totalPermissionCount: 0,
})
const activeRole = ref('')
const draftPermissions = ref([])

const roleList = computed(() => Array.isArray(overview.value.roles) ? overview.value.roles : [])
const permissionCatalog = computed(() => Array.isArray(overview.value.catalog) ? overview.value.catalog : [])
const protectedPermissions = computed(() => Array.isArray(overview.value.protectedPermissions) ? overview.value.protectedPermissions : [])

const activeRoleMeta = computed(() => roleList.value.find(item => item.role === activeRole.value) || null)
const roleCount = computed(() => roleList.value.length)
const totalPermissionCount = computed(() => Number(overview.value.totalPermissionCount || permissionCatalog.value.length || 0))
const activeGrantedCount = computed(() => draftPermissions.value.length)
const canWriteRolePermission = computed(() => {
  const permissions = Array.isArray(currentRoleInfo.value.permissions) ? currentRoleInfo.value.permissions : []
  return permissions.includes('system.role.write')
})

const groupedCatalog = computed(() => {
  const groups = new Map()
  permissionCatalog.value.forEach(item => {
    const group = item.group || 'other'
    if (!groups.has(group)) {
      groups.set(group, {
        group,
        groupName: item.groupName || group,
        items: [],
      })
    }
    groups.get(group).items.push(item)
  })
  return Array.from(groups.values())
})

const isDirty = computed(() => {
  if (!activeRoleMeta.value) return false
  const original = new Set(activeRoleMeta.value.permissions || [])
  if (original.size !== draftPermissions.value.length) {
    return true
  }
  return draftPermissions.value.some(item => !original.has(item))
})

function isChecked(permissionKey) {
  return draftPermissions.value.includes(permissionKey)
}

function isProtected(permissionKey) {
  return activeRole.value === 'admin' && protectedPermissions.value.includes(permissionKey)
}

function setDraftFromActiveRole() {
  if (!activeRoleMeta.value) {
    draftPermissions.value = []
    return
  }
  draftPermissions.value = [...(activeRoleMeta.value.permissions || [])]
}

function togglePermission(permissionKey, enabled) {
  if (!permissionKey) return
  if (isProtected(permissionKey) && !enabled) {
    return
  }
  const next = new Set(draftPermissions.value)
  if (enabled) {
    next.add(permissionKey)
  } else {
    next.delete(permissionKey)
  }
  draftPermissions.value = Array.from(next)
}

function applyGroup(group, enabled) {
  const items = group?.items || []
  const next = new Set(draftPermissions.value)
  items.forEach(item => {
    if (!item?.key) return
    if (!enabled && isProtected(item.key)) {
      return
    }
    if (enabled) {
      next.add(item.key)
    } else {
      next.delete(item.key)
    }
  })
  draftPermissions.value = Array.from(next)
}

function groupEnabledCount(group) {
  return (group?.items || []).filter(item => isChecked(item.key)).length
}

function riskType(level) {
  if (level === 'critical') return 'danger'
  if (level === 'high') return 'warning'
  if (level === 'medium') return ''
  return 'success'
}

async function loadOverview() {
  loading.value = true
  try {
    const [roleOverview, me] = await Promise.all([
      getAdminRolePermissions(),
      getAdminCurrentRolePermissions().catch(() => ({ role: 'admin', permissions: [] })),
    ])
    overview.value = roleOverview || { roles: [], catalog: [] }
    currentRoleInfo.value = me || { role: '', permissions: [] }

    const roleValues = roleList.value.map(item => item.role)
    if (!activeRole.value || !roleValues.includes(activeRole.value)) {
      activeRole.value = roleValues.includes(currentRoleInfo.value.role)
        ? currentRoleInfo.value.role
        : (roleValues[0] || '')
    }
    setDraftFromActiveRole()
  } catch (error) {
    ElMessage.error(error?.message || '加载角色权限失败')
  } finally {
    loading.value = false
  }
}

async function savePermissions() {
  if (!activeRole.value || !activeRoleMeta.value) return
  if (!canWriteRolePermission.value) {
    ElMessage.warning('当前账号没有角色权限修改能力')
    return
  }
  saving.value = true
  try {
    const result = await updateAdminRolePermissions(activeRole.value, draftPermissions.value)
    const nextRoles = roleList.value.map(item => (
      item.role === activeRole.value
        ? { ...item, ...(result || {}), permissions: result?.permissions || [] }
        : item
    ))
    overview.value = { ...overview.value, roles: nextRoles }
    setDraftFromActiveRole()
    ElMessage.success('角色权限更新成功')
  } catch (error) {
    ElMessage.error(error?.message || '角色权限更新失败')
  } finally {
    saving.value = false
  }
}

async function resetPermissions() {
  if (!activeRole.value || !activeRoleMeta.value) return
  if (!canWriteRolePermission.value) {
    ElMessage.warning('当前账号没有角色权限修改能力')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定将角色「${activeRoleMeta.value.roleName || activeRole.value}」恢复为默认权限吗？`,
      '恢复默认权限',
      { type: 'warning', confirmButtonText: '恢复默认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }

  saving.value = true
  try {
    const result = await resetAdminRolePermissions(activeRole.value)
    const nextRoles = roleList.value.map(item => (
      item.role === activeRole.value
        ? { ...item, ...(result || {}), permissions: result?.permissions || [] }
        : item
    ))
    overview.value = { ...overview.value, roles: nextRoles }
    setDraftFromActiveRole()
    ElMessage.success('已恢复默认权限')
  } catch (error) {
    ElMessage.error(error?.message || '恢复默认失败')
  } finally {
    saving.value = false
  }
}

watch(activeRole, () => {
  setDraftFromActiveRole()
})

onMounted(() => {
  loadOverview()
})
</script>

<template>
  <div class="space-y-6" v-loading="loading">
    <div class="permission-head">
      <div>
        <h2 class="text-2xl font-bold text-slate-900 dark:text-slate-100">角色权限管理</h2>
        <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">配置角色可访问的功能范围，降低误操作风险</p>
      </div>
      <div class="permission-head__actions">
        <el-button @click="loadOverview">刷新</el-button>
        <el-button :disabled="!isDirty || !activeRoleMeta?.editable || !canWriteRolePermission" @click="resetPermissions">恢复默认</el-button>
        <el-button type="primary" :loading="saving" :disabled="!isDirty || !activeRoleMeta?.editable || !canWriteRolePermission" @click="savePermissions">
          保存变更
        </el-button>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-4">
      <div class="permission-stat">
        <div class="label">角色数量</div>
        <div class="value">{{ roleCount }}</div>
      </div>
      <div class="permission-stat">
        <div class="label">权限点总数</div>
        <div class="value">{{ totalPermissionCount }}</div>
      </div>
      <div class="permission-stat is-ok">
        <div class="label">当前角色已授权</div>
        <div class="value">{{ activeGrantedCount }}</div>
      </div>
      <div class="permission-stat" :class="canWriteRolePermission ? 'is-ok' : 'is-risk'">
        <div class="label">当前账号修改权限</div>
        <div class="value">{{ canWriteRolePermission ? '可修改' : '只读' }}</div>
      </div>
    </div>

    <el-alert
      v-if="!canWriteRolePermission"
      type="warning"
      :closable="false"
      show-icon
      title="当前账号仅有查看权限，无法执行保存和恢复默认操作。"
    />

    <GlassCard>
      <template #header>
        <div class="text-base font-semibold text-slate-900 dark:text-slate-100">角色选择</div>
      </template>
      <div class="role-list">
        <button
          v-for="role in roleList"
          :key="role.role"
          class="role-chip"
          :class="activeRole === role.role ? 'is-active' : ''"
          @click="activeRole = role.role"
        >
          <div class="name">{{ role.roleName }}</div>
          <div class="meta">{{ role.grantedCount || 0 }} / {{ role.totalCount || totalPermissionCount }}</div>
        </button>
      </div>
    </GlassCard>

    <GlassCard v-for="group in groupedCatalog" :key="group.group">
      <template #header>
        <div class="flex items-center gap-3">
          <div class="text-base font-semibold text-slate-900 dark:text-slate-100">{{ group.groupName }}</div>
          <el-tag size="small" effect="plain">{{ groupEnabledCount(group) }} / {{ group.items.length }}</el-tag>
        </div>
        <div class="flex items-center gap-2">
          <el-button size="small" text @click="applyGroup(group, true)">全选</el-button>
          <el-button size="small" text @click="applyGroup(group, false)">清空</el-button>
        </div>
      </template>

      <div class="permission-list">
        <div v-for="item in group.items" :key="item.key" class="permission-item">
          <div class="main">
            <div class="title-row">
              <div class="title">{{ item.name }}</div>
              <el-tag size="small" effect="plain" :type="riskType(item.riskLevel)">
                {{ item.riskLevel || 'low' }}
              </el-tag>
              <el-tag v-if="isProtected(item.key)" size="small" type="danger" effect="light">保护</el-tag>
            </div>
            <div class="desc">{{ item.description }}</div>
            <div class="key">{{ item.key }}</div>
          </div>
          <el-switch
            :model-value="isChecked(item.key)"
            :disabled="!activeRoleMeta?.editable || (isProtected(item.key) && isChecked(item.key))"
            @change="val => togglePermission(item.key, val)"
          />
        </div>
      </div>
    </GlassCard>
  </div>
</template>

<style scoped>
.permission-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.permission-head__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.permission-stat {
  border: 1px solid rgba(148, 163, 184, 0.26);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
  padding: 16px;
}

.permission-stat .label {
  font-size: 12px;
  color: #64748b;
}

.permission-stat .value {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.permission-stat.is-ok .value {
  color: #059669;
}

.permission-stat.is-risk .value {
  color: #dc2626;
}

.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.role-chip {
  border: 1px solid rgba(148, 163, 184, 0.3);
  background: rgba(248, 250, 252, 0.8);
  border-radius: 12px;
  padding: 10px 14px;
  min-width: 130px;
  text-align: left;
  transition: border-color .2s ease, box-shadow .2s ease, background .2s ease;
}

.role-chip .name {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.role-chip .meta {
  margin-top: 4px;
  font-size: 12px;
  color: #64748b;
}

.role-chip.is-active {
  border-color: rgba(59, 130, 246, 0.45);
  background: rgba(219, 234, 254, 0.7);
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.12);
}

.permission-list {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 12px;
}

.permission-item {
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.75);
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.permission-item .main {
  min-width: 0;
}

.permission-item .title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.permission-item .title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.permission-item .desc {
  margin-top: 4px;
  font-size: 12px;
  color: #475569;
}

.permission-item .key {
  margin-top: 5px;
  font-size: 11px;
  color: #94a3b8;
}

@media (max-width: 980px) {
  .permission-head {
    flex-direction: column;
  }
}
</style>
