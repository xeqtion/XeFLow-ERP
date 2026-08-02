<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import {
  ArrowDown,
  Bell,
  HomeFilled,
  Key,
  Message,
  SwitchButton,
  User,
  WarningFilled
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  changeProfilePassword,
  deactivateAccount,
  getProfile,
  resetSystem,
  updateProfile,
  uploadAvatar,
  type Profile
} from '@/api/profile'
import { unreadCount, pageNotification, markAllRead, type Notification } from '@/api/notification'
import { messageUnreadCount } from '@/api/message'
import SidebarItem from './components/SidebarItem.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (route.meta?.title as string) || '')

const unread = ref(0)
const recent = ref<Notification[]>([])
const msgUnread = ref(0)
const TYPE_LABEL: Record<string, string> = { SYSTEM: '系统', REPORT: '报表', ANNOUNCE: '公告' }

async function loadNotificationUnread() {
  try {
    unread.value = (await unreadCount()).data.count
  } catch {
    /* ignore */
  }
}

async function loadMessageUnread() {
  try {
    msgUnread.value = (await messageUnreadCount()).data.count
  } catch {
    /* ignore */
  }
}

async function loadUnread() {
  await Promise.all([loadNotificationUnread(), loadMessageUnread()])
}

async function loadRecent() {
  try {
    recent.value = (await pageNotification({ pageNum: 1, pageSize: 6 })).data.records
  } catch {
    /* ignore */
  }
}

function goMessages() {
  router.push('/message')
}

function goNotifications() {
  router.push({ path: '/message', query: { tab: 'notice' } })
}

async function onMarkAllHeader() {
  await markAllRead()
  await Promise.all([loadUnread(), loadRecent()])
}

let timer: number | undefined
const refreshUnread = () => loadUnread()
const refreshOnVisible = () => {
  if (!document.hidden) loadUnread()
}
onMounted(() => {
  loadUnread()
  timer = window.setInterval(loadUnread, 10000)
  window.addEventListener('notify-refresh', refreshUnread)
  window.addEventListener('message-refresh', refreshUnread)
  window.addEventListener('focus', refreshUnread)
  document.addEventListener('visibilitychange', refreshOnVisible)
})
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('notify-refresh', refreshUnread)
  window.removeEventListener('message-refresh', refreshUnread)
  window.removeEventListener('focus', refreshUnread)
  document.removeEventListener('visibilitychange', refreshOnVisible)
})

const ROLE_LABELS: Record<string, string> = {
  super_admin: '超级管理员',
  admin: '管理员',
  salesman: '业务员',
  sales_manager: '销售主管',
  finance: '财务',
  readonly: '只读权限'
}

const displayName = computed(() => userStore.nickname || userStore.realName || userStore.username)
const avatarText = computed(() => (displayName.value || 'U').charAt(0).toUpperCase())
const headerAvatarFailed = ref(false)
const profileAvatarFailed = ref(false)
const headerAvatarSrc = computed(() => (headerAvatarFailed.value ? undefined : userStore.avatar || undefined))
const profileDisplayName = computed(() => profile.value?.nickname || profile.value?.realName || profile.value?.username || displayName.value)
const profileAvatarText = computed(() => (profileDisplayName.value || 'U').charAt(0).toUpperCase())
const profileAvatarSrc = computed(() => {
  if (profileAvatarFailed.value) return undefined
  return profile.value?.avatar || userStore.avatar || undefined
})
const roleText = computed(() =>
  userStore.roles.length ? userStore.roles.map((r) => ROLE_LABELS[r] || r).join(' / ') : '已登录用户'
)
const isSuperAdmin = computed(() => userStore.roles.includes('super_admin'))

function onHeaderAvatarError() {
  headerAvatarFailed.value = true
}

function onProfileAvatarError() {
  profileAvatarFailed.value = true
}

function handleCommand(command: string) {
  if (command === 'profile') openProfile()
  else if (command === 'logout') handleLogout()
}

const profileDrawer = ref(false)
const profileLoading = ref(false)
const profile = ref<Profile | null>(null)
const isBuiltInSuperAdmin = computed(() => isSuperAdmin.value && String(profile.value?.userId ?? '') === '1')
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({ realName: '', nickname: '', phone: '' })
const profileRules: FormRules = {
  nickname: [{ max: 64, message: '昵称不能超过64个字符', trigger: 'blur' }],
  realName: [{ max: 64, message: '姓名不能超过64个字符', trigger: 'blur' }],
  phone: [{ max: 64, message: '电话不能超过64个字符', trigger: 'blur' }]
}

async function openProfile() {
  profileDrawer.value = true
  profileLoading.value = true
  profileAvatarFailed.value = false
  Object.assign(systemResetForm, {
    preserveUsers: true,
    password: ''
  })
  try {
    profile.value = (await getProfile()).data
    Object.assign(profileForm, {
      realName: profile.value.realName || '',
      nickname: profile.value.nickname || '',
      phone: profile.value.phone || ''
    })
  } finally {
    profileLoading.value = false
  }
}

async function submitProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profile.value = (await updateProfile(profileForm)).data
    await userStore.loadUserInfo()
    ElMessage.success('资料已更新')
  })
}

async function onAvatarChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像不能超过5MB')
    return
  }
  if (!raw.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    return
  }
  const res = await uploadAvatar(raw)
  if (profile.value) profile.value.avatar = res.data.avatar
  headerAvatarFailed.value = false
  profileAvatarFailed.value = false
  await userStore.loadUserInfo()
  ElMessage.success('头像已更新')
}

const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === pwdForm.newPassword ? callback() : callback(new Error('两次输入的密码不一致')),
      trigger: 'blur'
    }
  ]
}

async function submitChangePassword() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    await changeProfilePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改，请重新登录')
    await userStore.logout()
    router.replace('/login')
    setTimeout(() => location.reload(), 0)
  })
}

const deactivateFormRef = ref<FormInstance>()
const deactivateForm = reactive({ password: '' })
const deactivateRules: FormRules = {
  password: [{ required: true, message: '请输入当前密码确认注销', trigger: 'blur' }]
}

async function submitDeactivate() {
  if (!deactivateFormRef.value) return
  await deactivateFormRef.value.validate(async (valid) => {
    if (!valid) return
    await ElMessageBox.confirm('注销后该账号将被停用，需要管理员重新启用。确认继续吗？', '注销账号', {
      type: 'warning',
      confirmButtonText: '确认注销',
      cancelButtonText: '取消'
    })
    await deactivateAccount(deactivateForm.password)
    userStore.reset()
    router.replace('/login')
    ElMessage.success('账号已注销')
    setTimeout(() => location.reload(), 0)
  })
}

const systemResetFormRef = ref<FormInstance>()
const systemResetLoading = ref(false)
const systemResetForm = reactive({
  preserveUsers: true,
  password: ''
})
const systemResetRules: FormRules = {
  password: [{ required: true, message: '请输入超级管理员密码', trigger: 'blur' }]
}

async function submitSystemReset() {
  if (!systemResetFormRef.value) return
  await systemResetFormRef.value.validate(async (valid) => {
    if (!valid) return
    await ElMessageBox.confirm(
      systemResetForm.preserveUsers
        ? '将清空全部业务数据和业务附件，但保留现有用户。所有账号都需要重新登录。'
        : '将清空全部业务数据、业务附件及其他用户，仅保留当前超级管理员。此操作无法恢复。',
      '确认系统重置',
      {
        type: 'error',
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
    systemResetLoading.value = true
    try {
      await resetSystem({
        preserveUsers: systemResetForm.preserveUsers,
        password: systemResetForm.password
      })
      ElMessage.success('系统数据已重置，请重新登录')
      userStore.reset()
      router.replace('/login')
      setTimeout(() => location.reload(), 0)
    } finally {
      systemResetLoading.value = false
    }
  })
}

async function handleLogout() {
  try {
    await ElMessageBox({
      title: '退出登录',
      message: h('div', { class: 'logout-confirm__body' }, [
        h('div', { class: 'logout-confirm__name' }, `${displayName.value}，${roleText.value}`),
        h('div', { class: 'logout-confirm__tip' }, '退出后需要重新登录才能继续使用，确定要退出吗？')
      ]),
      icon: WarningFilled,
      showCancelButton: true,
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning',
      center: true,
      roundButton: true,
      confirmButtonClass: 'el-button--danger',
      customClass: 'logout-confirm'
    })
  } catch {
    return
  }
  await userStore.logout()
  router.replace('/login')
  setTimeout(() => location.reload(), 0)
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">XeFlow</div>
      <el-scrollbar class="sidebar-scrollbar">
        <el-menu
          :default-active="activeMenu"
          router
          background-color="#1f2d3d"
          text-color="#bfcbd9"
          active-text-color="#409eff"
        >
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <sidebar-item v-for="menu in userStore.menus" :key="menu.id" :item="menu" />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item v-if="currentTitle && currentTitle !== '首页'">{{ currentTitle }}</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="header-right">
          <el-badge is-dot :hidden="msgUnread === 0" class="header-badge header-dot">
            <el-button :icon="Message" circle text title="消息中心" @click="goMessages" />
          </el-badge>

          <el-popover placement="bottom" :width="320" trigger="click" @show="loadRecent">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" :max="99" class="header-badge">
                <el-button :icon="Bell" circle text title="通知" />
              </el-badge>
            </template>
            <div class="notify-pop">
              <div class="notify-pop__head">
                <span>通知</span>
                <el-link type="primary" :underline="false" @click="onMarkAllHeader">全部已读</el-link>
              </div>
              <el-scrollbar max-height="280px">
                <div v-if="!recent.length" class="notify-empty">暂无通知</div>
                <div
                  v-for="n in recent"
                  :key="n.id"
                  class="notify-item"
                  :class="{ unread: n.isRead === 0 }"
                  @click="goNotifications"
                >
                  <div class="notify-item__title">
                    <el-tag size="small" effect="plain">{{ TYPE_LABEL[n.type] || n.type }}</el-tag>
                    <span class="notify-item__text">{{ n.title }}</span>
                  </div>
                  <div class="notify-item__time">{{ n.createTime }}</div>
                </div>
              </el-scrollbar>
              <div class="notify-pop__foot">
                <el-link type="primary" :underline="false" @click="goNotifications">查看全部</el-link>
              </div>
            </div>
          </el-popover>

          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="headerAvatarSrc" class="user-avatar" @error="onHeaderAvatarError">
                {{ avatarText }}
              </el-avatar>
              <span class="user-name">{{ displayName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="user-card">
                  <el-avatar :size="42" :src="headerAvatarSrc" class="user-avatar" @error="onHeaderAvatarError">
                    {{ avatarText }}
                  </el-avatar>
                  <div class="user-card__meta">
                    <div class="user-card__name">{{ displayName }}</div>
                    <el-tag size="small" type="info" effect="plain" round>{{ roleText }}</el-tag>
                  </div>
                </div>
                <el-dropdown-item divided command="profile">
                  <el-icon><User /></el-icon>
                  <span>个人中心</span>
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" :key="$route.fullPath" />
          </transition>
        </router-view>
      </el-main>
    </el-container>

    <el-drawer v-model="profileDrawer" title="个人中心" size="460px" append-to-body>
      <template #default>
        <div v-loading="profileLoading" class="profile-panel">
          <div class="profile-head">
            <el-upload
              :show-file-list="false"
              :auto-upload="false"
              accept="image/*"
              :on-change="onAvatarChange"
            >
              <el-avatar :size="72" :src="profileAvatarSrc" class="profile-avatar" @error="onProfileAvatarError">
                {{ profileAvatarText }}
              </el-avatar>
            </el-upload>
            <div>
              <div class="profile-name">{{ profileDisplayName }}</div>
              <div class="profile-sub">{{ profile?.username }} · {{ profile?.email || '未绑定邮箱' }}</div>
              <div class="profile-tip">点击头像上传，图片不超过5MB</div>
            </div>
          </div>

          <el-tabs>
            <el-tab-pane label="资料">
              <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="72px">
                <el-form-item label="账号">
                  <el-input :model-value="profile?.username" disabled />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input :model-value="profile?.email" disabled />
                </el-form-item>
                <el-form-item label="昵称" prop="nickname">
                  <el-input v-model="profileForm.nickname" placeholder="用于系统内显示" />
                </el-form-item>
                <el-form-item label="姓名" prop="realName">
                  <el-input v-model="profileForm.realName" />
                </el-form-item>
                <el-form-item label="电话" prop="phone">
                  <el-input v-model="profileForm.phone" />
                </el-form-item>
                <el-form-item label="角色">
                  <el-tag v-for="r in profile?.roleNames || []" :key="r" size="small" effect="plain">{{ r }}</el-tag>
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="submitProfile">保存资料</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="安全">
              <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
                <el-form-item label="当前密码" prop="oldPassword">
                  <el-input v-model="pwdForm.oldPassword" type="password" show-password />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input v-model="pwdForm.confirmPassword" type="password" show-password @keyup.enter="submitChangePassword" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :icon="Key" @click="submitChangePassword">修改密码</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane v-if="isSuperAdmin" label="系统重置">
              <div class="danger-zone">
                <div class="danger-title">系统重置</div>
                <el-alert
                  class="reset-warning"
                  type="error"
                  :closable="false"
                  show-icon
                  title="仅超级管理员可执行，重置后无法恢复"
                />
                <p>清空客户、产品、订单、出货、库存、财务、消息、审计日志等业务数据，同时保留系统角色和权限配置。</p>
                <el-form
                  ref="systemResetFormRef"
                  :model="systemResetForm"
                  :rules="systemResetRules"
                  label-width="104px"
                >
                  <el-form-item label="保留用户">
                    <el-switch
                      v-model="systemResetForm.preserveUsers"
                      inline-prompt
                      active-text="保留"
                      inactive-text="清除"
                    />
                  </el-form-item>
                  <el-form-item label="管理员密码" prop="password">
                    <el-input
                      v-model="systemResetForm.password"
                      type="password"
                      show-password
                      @keyup.enter="submitSystemReset"
                    />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="danger" :loading="systemResetLoading" @click="submitSystemReset">
                      系统重置
                    </el-button>
                  </el-form-item>
                </el-form>
              </div>
            </el-tab-pane>

            <el-tab-pane v-if="!isBuiltInSuperAdmin" label="注销">
              <div class="danger-zone">
                <div class="danger-title">注销账号</div>
                <p>注销后账号会被停用，无法继续登录；业务数据不会被删除，需要管理员重新启用后才能恢复使用。</p>
                <el-form ref="deactivateFormRef" :model="deactivateForm" :rules="deactivateRules" label-width="88px">
                  <el-form-item label="当前密码" prop="password">
                    <el-input v-model="deactivateForm.password" type="password" show-password />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="danger" @click="submitDeactivate">确认注销</el-button>
                  </el-form-item>
                </el-form>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </template>
    </el-drawer>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}
.sidebar {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #1f2d3d;
  overflow: hidden;
}
.logo {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 1.2px;
  background-color: #18222c;
}
.sidebar :deep(.el-menu) {
  border-right: none;
  min-height: 100%;
}
.sidebar-scrollbar {
  flex: 1;
  min-height: 0;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-badge :deep(.el-button) {
  font-size: 18px;
  color: #606266;
}
.header-badge :deep(.el-badge__content) {
  transform: translateY(-4px) translateX(50%);
}
.header-dot :deep(.el-badge__content.is-dot) {
  width: 8px;
  height: 8px;
  transform: translateY(2px) translateX(2px);
}
.notify-pop__head,
.notify-pop__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.notify-pop__head {
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}
.notify-pop__foot {
  justify-content: center;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}
.notify-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
}
.notify-item {
  padding: 8px 6px;
  border-radius: 6px;
  cursor: pointer;
}
.notify-item:hover {
  background-color: #f5f7fa;
}
.notify-item.unread .notify-item__text {
  font-weight: 600;
  color: #303133;
}
.notify-item:not(.unread) {
  opacity: 0.82;
}
.notify-item__title {
  display: flex;
  align-items: center;
  gap: 6px;
}
.notify-item__text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notify-item__time {
  color: #909399;
  font-size: 12px;
  margin-top: 2px;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #303133;
  padding: 4px 10px;
  border-radius: 18px;
  transition: background-color 0.2s;
}
.user-info:hover {
  background-color: #f2f3f5;
}
.user-avatar,
.profile-avatar {
  background: linear-gradient(135deg, #1f3a93 0%, #2c82c9 100%);
  color: #fff;
  font-weight: 600;
}
.user-name {
  font-size: 14px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-arrow {
  font-size: 12px;
  color: #909399;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px 10px;
  min-width: 220px;
}
.user-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.user-card__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.main {
  background-color: #f0f2f5;
}
.profile-panel {
  min-height: 420px;
}
.profile-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.profile-avatar {
  cursor: pointer;
}
.profile-name {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.profile-sub {
  margin-top: 4px;
  font-size: 13px;
  color: #606266;
}
.profile-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.danger-zone {
  padding: 12px 0;
}
.danger-title {
  font-weight: 700;
  color: #f56c6c;
  margin-bottom: 8px;
}
.danger-zone p {
  color: #606266;
  line-height: 1.7;
  margin: 0 0 16px;
}
.reset-warning {
  margin-bottom: 12px;
}
:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 6px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
