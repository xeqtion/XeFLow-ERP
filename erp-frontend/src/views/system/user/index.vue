<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  pageUser,
  createUser,
  updateUser,
  deleteUser,
  changeUserStatus,
  resetPassword,
  assignableRoles,
  pageInvitationCodes,
  createInvitationCode,
  deleteInvitationCode,
  type SysUser,
  type Role,
  type InvitationCode
} from '@/api/system'

const userStore = useUserStore()
const ADMIN_ID = 1
const SUPER_ADMIN_ROLE_ID = 0
/** id 现以字符串下发(防雪花精度丢失), 比较一律按字符串 */
const sameId = (a: unknown, b: unknown) => a != null && b != null && String(a) === String(b)

const loading = ref(false)
const list = ref<SysUser[]>([])
const total = ref(0)
const roles = ref<Role[]>([])
const activeTab = ref('users')
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; status: number | null }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  status: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const isEdit = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SysUser>(emptyForm())
const editingSuperAdmin = computed(() => isEdit.value && sameId(form.id, ADMIN_ID))

const pwdVisible = ref(false)
const pwdForm = reactive({ userId: 0, username: '', newPassword: '' })
const pwdFormRef = ref<FormInstance>()

const inviteLoading = ref(false)
const invitationList = ref<InvitationCode[]>([])
const invitationTotal = ref(0)
const invitationQuery = reactive({ pageNum: 1, pageSize: 10 })
const invitationVisible = ref(false)
const invitationFormRef = ref<FormInstance>()
const invitationForm = reactive({ expireMode: 'timed' as 'timed' | 'permanent', expireTime: defaultExpireTime(), maxUses: 1 })

function emptyForm(): SysUser {
  return {
    id: undefined,
    username: '',
    password: '',
    realName: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1,
    remark: '',
    roleIds: []
  }
}

function defaultExpireTime() {
  const value = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:00`
}

function hasRoleKey(roleIds: Array<number | string>, roleKey: string) {
  const role = roles.value.find((item) => item.roleKey === roleKey)
  return !!role && roleIds.some((id) => sameId(id, role.id))
}

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [
    {
      validator: (_r, v, cb) => {
        if (!isEdit.value && !v) cb(new Error('请输入初始密码'))
        else if (v && (v.length < 6 || v.length > 32)) cb(new Error('密码长度 6-32 位'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  roleIds: [
    {
      validator: (_r, value: Array<number | string>, cb) => {
        const selected = value || []
        if (selected.length > 3) cb(new Error('每个用户最多可同时分配 3 个角色'))
        else if (hasRoleKey(selected, 'readonly') && selected.length > 1) cb(new Error('只读权限不能与其他角色共存'))
        else cb()
      },
      trigger: 'change'
    }
  ]
}

const invitationRules: FormRules = {
  expireTime: [
    {
      validator: (_rule, value, callback) => {
        if (invitationForm.expireMode === 'timed' && !value) callback(new Error('请选择有效期限'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  maxUses: [{ required: true, message: '请填写可用次数', trigger: 'blur' }]
}

const pwdRules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6-32 位', trigger: 'blur' }
  ]
}

function isSelf(row: SysUser) {
  return sameId(row.id, userStore.userId)
}
function isProtected(row: SysUser) {
  return sameId(row.id, ADMIN_ID)
}

function onRoleChange(value: number[]) {
  const admin = roles.value.find((role) => role.roleKey === 'admin')
  if (admin && value.some((id) => sameId(id, admin.id))) {
    form.roleIds = [admin.id!]
    return
  }
  form.roleIds = [...value]
}

function roleOptionDisabled(role: Role) {
  const selected = form.roleIds || []
  if (hasRoleKey(selected, 'admin')) return role.roleKey !== 'admin'
  if (hasRoleKey(selected, 'readonly')) return role.roleKey !== 'readonly'
  if (role.roleKey === 'readonly') return selected.length > 0
  return false
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageUser(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}
function onReset() {
  query.keyword = ''
  query.status = null
  onSearch()
}

function openCreate() {
  Object.assign(form, emptyForm())
  isEdit.value = false
  dialogTitle.value = '新建用户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: SysUser) {
  const roleIds = [...(row.roleIds || [])]
  const admin = roles.value.find((role) => role.roleKey === 'admin')
  const normalizedRoleIds = isProtected(row)
    ? [SUPER_ADMIN_ROLE_ID]
    : admin && roleIds.some((id) => sameId(id, admin.id))
      ? [admin.id!]
      : roleIds
  Object.assign(form, emptyForm(), row, { password: '', roleIds: normalizedRoleIds })
  isEdit.value = true
  dialogTitle.value = '修改用户'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const payload = { ...form }
    if (sameId(payload.id, ADMIN_ID)) {
      payload.roleIds = [SUPER_ADMIN_ROLE_ID]
      payload.status = 1
    }
    if (isEdit.value && !payload.password) delete payload.password
    if (isEdit.value) {
      await updateUser(payload)
      ElMessage.success('修改成功')
    } else {
      await createUser(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onToggleStatus(row: SysUser) {
  const next = row.status === 1 ? 0 : 1
  const action = next === 1 ? '启用' : '停用'
  try {
    await ElMessageBox.confirm(`确认${action}用户「${row.realName || row.username}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await changeUserStatus(row.id!, next)
    row.status = next
    ElMessage.success(`${action}成功`)
  } catch {
    /* 拦截器已提示 */
  }
}

function openResetPwd(row: SysUser) {
  pwdForm.userId = row.id!
  pwdForm.username = row.username
  pwdForm.newPassword = ''
  pwdVisible.value = true
  pwdFormRef.value?.clearValidate()
}

async function submitResetPwd() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    await resetPassword(pwdForm.userId, pwdForm.newPassword)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  })
}

async function onDelete(row: SysUser) {
  await ElMessageBox.confirm(`确认删除用户「${row.realName || row.username}」？`, '提示', { type: 'warning' })
  await deleteUser(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

async function loadInvitations() {
  inviteLoading.value = true
  try {
    const res = await pageInvitationCodes(invitationQuery)
    invitationList.value = res.data.records
    invitationTotal.value = res.data.total
  } finally {
    inviteLoading.value = false
  }
}

function openCreateInvitation() {
  invitationForm.expireMode = 'timed'
  invitationForm.expireTime = defaultExpireTime()
  invitationForm.maxUses = 1
  invitationVisible.value = true
  invitationFormRef.value?.clearValidate()
}

async function submitInvitation() {
  if (!invitationFormRef.value) return
  await invitationFormRef.value.validate(async (valid) => {
    if (!valid) return
    const res = await createInvitationCode({
      expireTime: invitationForm.expireMode === 'permanent' ? null : invitationForm.expireTime,
      maxUses: invitationForm.maxUses
    })
    invitationVisible.value = false
    ElMessage.success(`邀请码已生成：${res.data.inviteCode}`)
    invitationQuery.pageNum = 1
    loadInvitations()
  })
}

async function copyInvitation(code: string) {
  await navigator.clipboard.writeText(code)
  ElMessage.success('邀请码已复制')
}

async function onDeleteInvitation(row: InvitationCode) {
  await ElMessageBox.confirm(`确认删除邀请码「${row.inviteCode}」？删除后将无法用于注册。`, '提示', { type: 'warning' })
  await deleteInvitationCode(row.id)
  ElMessage.success('邀请码已删除')
  loadInvitations()
}

function invitationStatus(row: InvitationCode) {
  if (row.status === 'VALID') return { label: '有效', type: 'success' as const }
  if (row.status === 'EXPIRED') return { label: '已过期', type: 'info' as const }
  return { label: '次数已用完', type: 'warning' as const }
}

function onTabChange(name: string | number) {
  if (name === 'invitations' && invitationList.value.length === 0) loadInvitations()
}

onMounted(async () => {
  const res = await assignableRoles()
  roles.value = res.data
  loadData()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="用户列表" name="users">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 姓名 / 邮箱"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'system:user:create'" type="primary" @click="openCreate">新建用户</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column prop="realName" label="姓名" width="110" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }: any">
            <el-tag v-for="name in row.roleNames" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
            <span v-if="!row.roleNames || !row.roleNames.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="left">
          <template #default="{ row }: any">
            <el-switch
              :model-value="row.status === 1"
              :disabled="isProtected(row) || isSelf(row) || !userStore.hasPermission('system:user:update')"
              @click="onToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'system:user:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:user:resetPwd'"
              link
              type="warning"
              @click="openResetPwd(row)"
            >重置密码</el-button>
            <el-button
              v-permission="'system:user:delete'"
              link
              type="danger"
              :disabled="isProtected(row) || isSelf(row)"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="onSearch"
      />
        </el-tab-pane>

        <el-tab-pane
          v-if="userStore.hasPermission('system:user:invite:list')"
          label="邀请码"
          name="invitations"
        >
          <div class="invite-header">
            <div>
              <div class="invite-title">注册邀请码</div>
              <div class="invite-description">邀请码由系统自动生成，可永久有效，也可限制有效期限和注册次数。</div>
            </div>
            <el-button
              v-permission="'system:user:invite:create'"
              type="primary"
              @click="openCreateInvitation"
            >生成邀请码</el-button>
          </div>

          <el-table v-loading="inviteLoading" :data="invitationList" border stripe>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column label="邀请码" min-width="210">
              <template #default="{ row }: any">
                <span class="invite-code">{{ row.inviteCode }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }: any">
                <el-tag :type="invitationStatus(row).type" size="small">
                  {{ invitationStatus(row).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="使用情况" width="140">
              <template #default="{ row }: any">{{ row.usedCount }} / {{ row.maxUses }}</template>
            </el-table-column>
            <el-table-column label="有效期至" width="180">
              <template #default="{ row }: any">{{ row.expireTime || '永久' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="180" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }: any">
                <el-button link type="primary" @click="copyInvitation(row.inviteCode)">复制</el-button>
                <el-button
                  v-permission="'system:user:invite:delete'"
                  link
                  type="danger"
                  @click="onDeleteInvitation(row)"
                >删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="invitationQuery.pageNum"
            v-model:page-size="invitationQuery.pageSize"
            :total="invitationTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            style="margin-top: 12px; justify-content: flex-end"
            @current-change="loadInvitations"
            @size-change="loadInvitations"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                :placeholder="isEdit ? '留空表示不修改' : '初始密码 6-32 位'"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="姓名"><el-input v-model="form.realName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="角色" prop="roleIds">
              <el-input v-if="editingSuperAdmin" model-value="超级管理员" disabled />
              <el-select
                v-else
                v-model="form.roleIds"
                multiple
                collapse-tags
                :multiple-limit="3"
                placeholder="最多选择 3 个角色"
                style="width: 100%"
                @change="onRoleChange"
              >
                <el-option
                  v-for="r in roles"
                  :key="r.id"
                  :label="r.roleName"
                  :value="r.id!"
                  :disabled="roleOptionDisabled(r)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :disabled="sameId(form.id, ADMIN_ID)" />
              <span class="status-text">{{ form.status === 1 ? '启用' : '停用' }}</span>
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="用户">
          <el-input :model-value="pwdForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="6-32 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="invitationVisible" title="生成邀请码" width="460px">
      <el-form
        ref="invitationFormRef"
        :model="invitationForm"
        :rules="invitationRules"
        label-width="90px"
      >
        <el-form-item label="有效方式">
          <el-radio-group v-model="invitationForm.expireMode">
            <el-radio-button value="timed">指定时间</el-radio-button>
            <el-radio-button value="permanent">永久有效</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="invitationForm.expireMode === 'timed'" label="有效期至" prop="expireTime">
          <el-date-picker
            v-model="invitationForm.expireTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            placeholder="选择失效时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="可用次数" prop="maxUses">
          <el-input-number v-model="invitationForm.maxUses" :min="1" :max="10000" style="width: 100%" />
        </el-form-item>
        <el-alert
          title="邀请码将由系统自动生成，注册成功一次即扣减一次。"
          type="info"
          :closable="false"
          show-icon
        />
      </el-form>
      <template #footer>
        <el-button @click="invitationVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInvitation">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-top: 4px;
}
.muted {
  color: #c0c4cc;
}
.status-text {
  margin-left: 8px;
  color: #606266;
}
.invite-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.invite-title {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}
.invite-description {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.invite-code {
  color: #1f4f8f;
  font-family: Consolas, monospace;
  font-weight: 600;
}
</style>
