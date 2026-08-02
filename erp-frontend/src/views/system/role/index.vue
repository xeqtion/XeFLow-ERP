<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules, type ElTree } from 'element-plus'
import {
  pageRole,
  createRole,
  updateRole,
  deleteRole,
  listPermissions,
  rolePermissionIds,
  assignRolePermissions,
  type Role,
  type Permission
} from '@/api/system'

const SUPER_ADMIN_ROLE_ID = 0
const ADMIN_ROLE_ID = 1
const loading = ref(false)
const list = ref<Role[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Role>(emptyForm())

function emptyForm(): Role {
  return { id: undefined, roleName: '', roleKey: '', status: 1, remark: '' }
}
const rules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleKey: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageRole(query)
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

function openCreate() {
  Object.assign(form, emptyForm())
  dialogTitle.value = '新建角色'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}
function openEdit(row: Role) {
  Object.assign(form, row)
  dialogTitle.value = '修改角色'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}
function isBuiltinAdminRole(row: Role | { id?: number }) {
  return row.id === SUPER_ADMIN_ROLE_ID || row.id === ADMIN_ROLE_ID
}
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id !== undefined && form.id !== null) {
      await updateRole(form)
      ElMessage.success('修改成功')
    } else {
      await createRole(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  })
}
async function onDelete(row: Role) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '提示', { type: 'warning' })
  await deleteRole(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

/* 分配权限 */
const permVisible = ref(false)
const permTree = ref<InstanceType<typeof ElTree>>()
const treeData = ref<Permission[]>([])
const treeProps = { label: 'permName', children: 'children' }
const assigningRole = ref<Role | null>(null)

function buildTree(flat: Permission[]): Permission[] {
  const byId = new Map<number, Permission>()
  flat.forEach((p) => byId.set(p.id!, { ...p, children: [] }))
  const roots: Permission[] = []
  byId.forEach((n) => {
    const parent = byId.get(n.parentId)
    if (parent) parent.children!.push(n)
    else roots.push(n)
  })
  byId.forEach((n) => {
    if (n.children && n.children.length === 0) delete n.children
  })
  return roots
}

async function openAssign(row: Role) {
  assigningRole.value = row
  const [permsRes, idsRes] = await Promise.all([listPermissions(), rolePermissionIds(row.id!)])
  treeData.value = buildTree(permsRes.data)
  permVisible.value = true
  await nextTick()
  // 仅勾选叶子, 半选父级由 el-tree 自动推导
  const leafIds = leafCheckedIds(permsRes.data, idsRes.data)
  permTree.value?.setCheckedKeys(leafIds, false)
}

function leafCheckedIds(all: Permission[], checked: number[]): number[] {
  const hasChild = new Set(all.map((p) => p.parentId))
  return checked.filter((id) => !hasChild.has(id))
}

async function submitAssign() {
  if (!assigningRole.value || !permTree.value) return
  const checked = permTree.value.getCheckedKeys(false) as number[]
  const half = permTree.value.getHalfCheckedKeys() as number[]
  await assignRolePermissions(assigningRole.value.id!, [...checked, ...half])
  ElMessage.success('权限已保存')
  permVisible.value = false
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="名称 / 标识" clearable style="width: 180px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="query.keyword = ''; onSearch()">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'system:role:create'" type="primary" @click="openCreate">新建角色</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="角色标识" min-width="160" />
        <el-table-column label="状态" width="90" align="left">
          <template #default="{ row }: any">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'system:role:update'" link type="primary" @click="openAssign(row)">分配权限</el-button>
            <el-button v-permission="'system:role:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-permission="'system:role:delete'"
              link
              type="danger"
              :disabled="isBuiltinAdminRole(row)"
              @click="onDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="onSearch"
      />
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色标识" prop="roleKey">
          <el-input v-model="form.roleKey" :disabled="isBuiltinAdminRole(form)" placeholder="如 salesman" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" :disabled="isBuiltinAdminRole(form)" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="permVisible" :title="`分配权限 - ${assigningRole?.roleName}`" width="460px">
      <el-alert
        v-if="assigningRole && isBuiltinAdminRole(assigningRole)"
        type="warning"
        :closable="false"
        show-icon
        title="超级管理员始终拥有全部权限(登录时授予通配)，此处调整不影响其越权。"
        style="margin-bottom: 12px"
      />
      <el-scrollbar max-height="50vh">
        <el-tree
          ref="permTree"
          :data="treeData"
          :props="treeProps"
          node-key="id"
          show-checkbox
          default-expand-all
        />
      </el-scrollbar>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAssign">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-top: 4px;
}
</style>
