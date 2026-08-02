<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, Share, Promotion, Select, Upload } from '@element-plus/icons-vue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import CurrencySelect from '@/components/CurrencySelect.vue'
import { encodeEntityRef, getEntityTypeConfig } from '@/utils/entityLink'
import { listContacts, sendMessage, type Contact } from '@/api/message'
import {
  pageProduct,
  createProduct,
  updateProduct,
  deleteProduct,
  importProduct,
  exportProduct,
  downloadProductTemplate,
  type Product
} from '@/api/product'

const loading = ref(false)
const saving = ref(false)
const list = ref<Product[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Product>(emptyForm())
const originalId = ref<number | string>()
const importVisible = ref(false)

/* -------- 分享到消息 -------- */
const shareDialogVisible = ref(false)
const shareContacts = ref<Contact[]>([])
const shareContactKeyword = ref('')
const shareSelectedContact = ref<Contact | null>(null)
const shareSending = ref(false)
const shareTarget = ref<Product | null>(null)
const shareRemark = ref('')
const shareFilteredContacts = computed(() => {
  const k = shareContactKeyword.value.trim().toLowerCase()
  if (!k) return shareContacts.value
  return shareContacts.value.filter(
    (c) => c.name.toLowerCase().includes(k) || (c.username || '').toLowerCase().includes(k)
  )
})
async function openShare(row: Product) {
  shareTarget.value = row
  shareSelectedContact.value = null
  shareContactKeyword.value = ''
  shareRemark.value = ''
  try {
    shareContacts.value = (await listContacts()).data
  } catch { /* ignore */ }
  shareDialogVisible.value = true
}
async function confirmShare() {
  if (!shareTarget.value || !shareSelectedContact.value) return
  // 校验实体 ID
  if (shareTarget.value.id == null) {
    ElMessage.warning('该产品数据 ID 无效，无法分享')
    return
  }
  // 校验联系人
  if (!shareSelectedContact.value) {
    ElMessage.warning('请选择联系人')
    return
  }
  shareSending.value = true
  try {
    const config = getEntityTypeConfig('product')
    if (!config) return
    const entity = shareTarget.value
    const extra: Record<string, unknown> = {}
    if (config.detailFields) {
      for (const f of config.detailFields) {
        if ((entity as any)[f.key] != null) extra[f.key] = (entity as any)[f.key]
      }
    }
    const remarkText = shareRemark.value.trim()
    if (remarkText) extra.remark = remarkText
    if (entity.id == null) {
      ElMessage.warning('产品 ID 无效，无法分享')
      return
    }
    const entityJson = encodeEntityRef({ type: 'product', id: entity.id, name: config.getDisplayName(entity.id, entity), extra })
    await sendMessage(shareSelectedContact.value.userId, entityJson, [])
    ElMessage.success('产品已分享')
    shareDialogVisible.value = false
  } catch { /* interceptor handled */ }
  finally { shareSending.value = false }
}

function emptyForm(): Product {
  return {
    productName: '',
    companyModel: '',
    factoryModel: '',
    remark: '',
    factoryPrice: undefined,
    currency: 'CNY'
  }
}

const rules: FormRules<Product> = {
  id: [
    {
      validator: (_rule, value, callback) => {
        if (!value && !originalId.value) {
          callback()
          return
        }
        if (!value) {
          callback(new Error('请输入产品ID'))
          return
        }
        if (!/^\d{6,9}$/.test(String(value))) {
          callback(new Error('产品ID必须是6到9位数字'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  productName: [{ max: 255, message: '产品名称最多 255 个字符', trigger: 'blur' }],
  companyModel: [{ max: 255, message: '公司产品型号最多 255 个字符', trigger: 'blur' }],
  factoryModel: [{ max: 255, message: '工厂产品型号最多 255 个字符', trigger: 'blur' }],
  currency: [{ max: 8, message: '币种最多 8 个字符', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageProduct(query)
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
  onSearch()
}

function openCreate() {
  Object.assign(form, emptyForm())
  form.id = undefined
  originalId.value = undefined
  dialogTitle.value = '新建产品资料'
  dialogVisible.value = true
}

function openEdit(row: Product) {
  Object.assign(form, emptyForm(), row)
  originalId.value = row.id
  dialogTitle.value = '修改产品资料'
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (originalId.value) {
      await updateProduct(form, originalId.value)
      ElMessage.success('产品资料已保存')
    } else {
      await createProduct(form)
      ElMessage.success('产品资料已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Product) {
  await ElMessageBox.confirm(`确认删除产品「${row.productName || row.id}」？`, '提示', { type: 'warning' })
  if (!row.id) return
  await deleteProduct(row.id)
  ElMessage.success('产品资料已删除')
  await loadData()
}

function onExport() {
  exportProduct(query.keyword)
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="产品ID / 产品名称 / 型号"
            clearable
            style="width: 260px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'product:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
        <el-button v-permission="'product:import'" :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button v-permission="'product:export'" :icon="Document" @click="downloadProductTemplate">模板</el-button>
        <el-button v-permission="'product:export'" :icon="Download" @click="onExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="id" label="产品ID" width="180" />
        <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="companyModel" label="公司产品型号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="factoryModel" label="工厂产品型号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column prop="factoryPrice" label="出厂价" width="120" align="left" />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="操作" width="200" align="left" fixed="right">
          <template #default="{ row }: any">
            <div class="table-actions">
              <el-button
                v-permission="'product:update'"
                class="table-action-btn"
                link
                type="primary"
                :icon="Edit"
                @click="openEdit(row)"
              >
                编辑
              </el-button>
              <el-button
                class="table-action-btn"
                link
                type="info"
                :icon="Share"
                @click="openShare(row)"
              >
                分享
              </el-button>
              <el-button
                v-permission="'product:delete'"
                class="table-action-btn"
                link
                type="danger"
                :icon="Delete"
                @click="onDelete(row)"
              >
                删除
              </el-button>
            </div>
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="产品ID" prop="id">
              <el-input
                v-model="form.id"
                :disabled="!originalId"
                placeholder="保存后自动生成"
                inputmode="numeric"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <CurrencySelect v-model="form.currency" placeholder="请选择或输入币种" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称" prop="productName">
              <el-input v-model="form.productName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出厂价">
              <el-input-number v-model="form.factoryPrice" :min="0" :precision="4" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司产品型号" prop="companyModel">
              <el-input v-model="form.companyModel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工厂产品型号" prop="factoryModel">
              <el-input v-model="form.factoryModel" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="3" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <ImportExcelDialog
      v-model="importVisible"
      title="导入产品资料"
      :import-api="importProduct"
      :download-template="downloadProductTemplate"
      @success="loadData"
    />

    <!-- 分享到消息 -->
    <el-dialog v-model="shareDialogVisible" title="分享产品到消息" width="420px" append-to-body>
      <p style="margin-bottom: 12px; color: #606266; font-size: 13px;">
        选择联系人，将该产品信息发送给对方
      </p>
      <el-input
        v-model="shareContactKeyword"
        placeholder="搜索联系人..."
        clearable
        :prefix-icon="Search"
        style="margin-bottom: 12px"
      />
      <el-scrollbar max-height="260px">
        <div
          v-for="c in shareFilteredContacts"
          :key="c.userId"
          class="share-contact-row"
          :class="{ active: shareSelectedContact?.userId === c.userId }"
          @click="shareSelectedContact = c"
        >
          <el-avatar :size="28" class="sc-avatar">{{ (c.name || 'U').charAt(0) }}</el-avatar>
          <div class="sc-meta">
            <div class="sc-name">{{ c.name }}</div>
            <div class="sc-sub">{{ c.username }}</div>
          </div>
          <el-icon v-if="shareSelectedContact?.userId === c.userId" class="sc-check"><Select /></el-icon>
        </div>
        <div v-if="!shareFilteredContacts.length" class="sc-empty">无联系人</div>
      </el-scrollbar>
      <el-input
        v-model="shareRemark"
        type="textarea"
        :rows="2"
        placeholder="附言（可选）"
        style="margin-top: 8px"
      />
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" :icon="Promotion" :disabled="!shareSelectedContact" :loading="shareSending" @click="confirmShare">
          发送
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.table-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  white-space: nowrap;
}

.table-action-btn {
  height: 28px;
  margin-left: 0;
  padding: 0 2px;
  font-weight: 500;
}

.table-action-btn + .table-action-btn {
  margin-left: 0;
}

.table-action-btn :deep(.el-icon) {
  margin-right: 4px;
}

/* 分享对话框联系人列表 */
.share-contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.share-contact-row:hover {
  background: #f5f7fa;
}
.share-contact-row.active {
  background: #ecf5ff;
}
.sc-avatar {
  background: linear-gradient(135deg, #1f3a93, #2c82c9);
  color: #fff;
  flex-shrink: 0;
}
.sc-meta {
  flex: 1;
  min-width: 0;
}
.sc-name {
  font-size: 14px;
  color: #303133;
}
.sc-sub {
  font-size: 12px;
  color: #909399;
}
.sc-check {
  color: #409eff;
  font-size: 16px;
}
.sc-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
}
</style>
