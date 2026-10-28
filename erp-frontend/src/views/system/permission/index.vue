<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { idKey, type EntityId } from '@/utils/identifier'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  listPermissions,
  createPermission,
  updatePermission,
  deletePermission,
  type Permission
} from '@/api/system'

const TYPE_LABEL: Record<string, string> = { M: '目录', C: '菜单', B: '按钮' }
const TYPE_TAG: Record<string, 'primary' | 'success' | 'warning'> = { M: 'primary', C: 'success', B: 'warning' }

const loading = ref(false)
const flat = ref<Permission[]>([])
const tree = ref<Permission[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Permission>(emptyForm())

function emptyForm(): Permission {
  return { id: undefined, parentId: '0', permName: '', permKey: '', type: 'C', path: '', component: '', icon: '', sort: 0, status: 1 }
}
const rules: FormRules = {
  permName: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

// 父级候选: 顶级(0) + 所有 目录/菜单
const parentOptions = computed(() => {
  const opts: { id: EntityId; label: string }[] = [{ id: '0', label: '顶级' }]
  flat.value.filter((p) => p.type === 'M' || p.type === 'C').forEach((p) => {
    opts.push({ id: p.id!, label: `${p.permName} (${TYPE_LABEL[p.type]})` })
  })
  return opts
})

function buildTree(items: Permission[]): Permission[] {
  const byId = new Map<string, Permission>()
  items.forEach((p) => byId.set(idKey(p.id), { ...p, children: [] }))
  const roots: Permission[] = []
  byId.forEach((n) => {
    const parent = byId.get(idKey(n.parentId))
    if (parent) parent.children!.push(n)
    else roots.push(n)
  })
  byId.forEach((n) => {
    if (n.children && n.children.length === 0) delete n.children
  })
  return roots
}

async function loadData() {
  loading.value = true
  try {
    flat.value = (await listPermissions()).data
    tree.value = buildTree(flat.value)
  } finally {
    loading.value = false
  }
}

function openCreate(parent?: Permission) {
  Object.assign(form, emptyForm())
  if (parent) {
    form.parentId = parent.id!
    form.type = parent.type === 'M' ? 'C' : 'B'
  }
  dialogTitle.value = '新建权限'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}
function openEdit(row: Permission) {
  Object.assign(form, emptyForm(), row)
  dialogTitle.value = '修改权限'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) {
      await updatePermission(form)
      ElMessage.success('修改成功')
    } else {
      await createPermission(form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  })
}
async function onDelete(row: Permission) {
  await ElMessageBox.confirm(`确认删除「${row.permName}」？`, '提示', { type: 'warning' })
  await deletePermission(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <div class="toolbar">
        <el-button v-permission="'system:permission:create'" type="primary" @click="openCreate()">新建顶级</el-button>
        <span class="tip">提示：删除需先移除其下级；菜单的 path/component 对应前端路由</span>
      </div>

      <el-table
        v-loading="loading"
        :data="tree"
        row-key="id"
        border
        default-expand-all
        :tree-props="{ children: 'children' }"
        style="margin-top: 12px"
      >
        <el-table-column prop="permName" label="名称" min-width="200" />
        <el-table-column label="类型" width="90" align="left">
          <template #default="{ row }: any">
            <el-tag :type="TYPE_TAG[row.type]" size="small">{{ TYPE_LABEL[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permKey" label="权限标识" min-width="170">
          <template #default="{ row }: any">
            <span v-if="row.permKey" class="perm-key">{{ row.permKey }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="70" align="left" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }: any">
            <el-button v-if="row.type !== 'B'" v-permission="'system:permission:create'" link type="primary" @click="openCreate(row)">加下级</el-button>
            <el-button v-permission="'system:permission:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:permission:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="上级">
              <el-select v-model="form.parentId" filterable style="width: 100%">
                <el-option v-for="o in parentOptions" :key="o.id" :label="o.label" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="目录" value="M" />
                <el-option label="菜单" value="C" />
                <el-option label="按钮" value="B" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="名称" prop="permName"><el-input v-model="form.permName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="权限标识"><el-input v-model="form.permKey" placeholder="如 customer:list" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="路由"><el-input v-model="form.path" placeholder="/basedata/xxx" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="组件"><el-input v-model="form.component" placeholder="basedata/xxx/index" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" :controls="false" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
}
.perm-key {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #409eff;
}
.muted {
  color: #c0c4cc;
}
</style>
