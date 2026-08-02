<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import {
  pageBom,
  getBom,
  createBom,
  updateBom,
  deleteBom,
  importBom,
  exportBom,
  downloadBomTemplate,
  type Bom
} from '@/api/bom'
import { pageProduct, type Product } from '@/api/product'
import CurrencySelect from '@/components/CurrencySelect.vue'

const loading = ref(false)
const saving = ref(false)
const list = ref<Bom[]>([])
const total = ref(0)
const productOptions = ref<Product[]>([])
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  productModel: '',
  universalOnly: false
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Bom>(emptyForm())
const selectedModels = ref<string[]>([])
const universalChecked = ref(false)
const importVisible = ref(false)

const dialogTotalPrice = computed(() => {
  const qty = Number(form.qty || 0)
  const unitPrice = Number(form.unitPrice || 0)
  if (!qty || !unitPrice) return '0.0000'
  return (qty * unitPrice).toFixed(4)
})

function emptyForm(): Bom {
  return {
    bomCode: '',
    nameCn: '',
    supportModels: '',
    universal: 0,
    spec: '',
    qty: undefined,
    supplier: '',
    supplierPhone: '',
    supplierContact: '',
    unitPrice: undefined,
    currency: 'CNY',
    remark: ''
  }
}

const rules: FormRules<Bom> = {
  bomCode: [
    {
      validator: (_rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        if (!/^MT\d{4,}$/.test(String(value).toUpperCase())) {
          callback(new Error('物料编码格式为 MT0001'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  nameCn: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  supplierPhone: [{ max: 64, message: '联系电话最多 64 个字符', trigger: 'blur' }],
  currency: [{ max: 8, message: '币种最多 8 个字符', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageBom(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  const res = await pageProduct({ pageNum: 1, pageSize: 1000 })
  const seen = new Set<string>()
  productOptions.value = res.data.records.filter((product) => {
    const model = product.companyModel?.trim()
    if (!model || seen.has(model)) return false
    seen.add(model)
    return true
  })
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.productModel = ''
  query.universalOnly = false
  onSearch()
}

function openCreate() {
  Object.assign(form, emptyForm())
  form.id = undefined
  selectedModels.value = []
  universalChecked.value = false
  dialogTitle.value = '新建产品配件资料'
  dialogVisible.value = true
}

async function openEdit(row: Bom) {
  const res = await getBom(row.id!)
  Object.assign(form, emptyForm(), res.data)
  if (!form.currency) form.currency = 'CNY'
  universalChecked.value = isUniversal(res.data)
  selectedModels.value = universalChecked.value ? [] : parseSupportModels(res.data.supportModels)
  dialogTitle.value = '修改产品配件资料'
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: Bom = {
    ...form,
    bomCode: form.bomCode?.trim(),
    currency: form.currency?.trim() || 'CNY',
    supportModels: universalChecked.value ? '所有' : selectedModels.value.join(','),
    universal: universalChecked.value ? 1 : 0
  }
  saving.value = true
  try {
    if (payload.id) {
      await updateBom(payload)
      ElMessage.success('产品配件资料已保存')
    } else {
      await createBom(payload)
      ElMessage.success('产品配件资料已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: Bom) {
  await ElMessageBox.confirm(`确认删除配件「${row.nameCn || row.bomCode}」？`, '提示', { type: 'warning' })
  if (!row.id) return
  await deleteBom(row.id)
  ElMessage.success('产品配件资料已删除')
  await loadData()
}

function parseSupportModels(models?: string) {
  if (!models || models === '所有') return []
  return models
    .split(/[,，、\n\r]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function isUniversal(row: Bom) {
  return row.universal === 1 || row.universal === true || row.supportModels === '所有'
}

function formatMoney(value?: number) {
  if (value === undefined || value === null) return ''
  return Number(value).toFixed(4)
}

function onExport() {
  exportBom(query.keyword)
}

onMounted(() => {
  loadProducts()
  loadData()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="物料编码 / 名称 / 规格 / 供应商"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="产品型号">
          <el-select
            v-model="query.productModel"
            filterable
            clearable
            allow-create
            default-first-option
            placeholder="公司产品型号"
            style="width: 260px"
            @change="onSearch"
          >
            <el-option
              v-for="product in productOptions"
              :key="String(product.id) + product.companyModel"
              :label="product.companyModel || ''"
              :value="product.companyModel || ''"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.universalOnly" @change="onSearch">通用</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'bom:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
        <el-button v-permission="'bom:import'" :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button v-permission="'bom:export'" :icon="Document" @click="downloadBomTemplate">模板</el-button>
        <el-button v-permission="'bom:export'" :icon="Download" @click="onExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="bomCode" label="物料编码" width="120" />
        <el-table-column prop="nameCn" label="名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="通用" width="80" align="left">
          <template #default="{ row }: any">
            <el-tag v-if="isUniversal(row)" type="success" size="small">通用</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="supportModels" label="支持产品型号" min-width="220" show-overflow-tooltip />
        <el-table-column prop="spec" label="规格" min-width="150" show-overflow-tooltip />
        <el-table-column prop="qty" label="用量" width="110" align="left" />
        <el-table-column prop="supplier" label="供应商" min-width="150" show-overflow-tooltip />
        <el-table-column prop="supplierPhone" label="联系电话" width="140" show-overflow-tooltip />
        <el-table-column prop="supplierContact" label="联系人" width="110" show-overflow-tooltip />
        <el-table-column label="单价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="总价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.totalPrice) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'bom:update'" link type="primary" :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'bom:delete'" link type="danger" :icon="Delete" @click="onDelete(row)">
              删除
            </el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="物料编码" prop="bomCode">
              <el-input v-model="form.bomCode" placeholder="保存后自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="nameCn">
              <el-input v-model="form.nameCn" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="支持产品型号">
              <div class="support-row">
                <el-checkbox v-model="universalChecked">所有</el-checkbox>
                <el-select
                  v-model="selectedModels"
                  multiple
                  filterable
                  allow-create
                  default-first-option
                  collapse-tags
                  collapse-tags-tooltip
                  :disabled="universalChecked"
                  placeholder="公司产品型号"
                  class="model-select"
                >
                  <el-option
                    v-for="product in productOptions"
                    :key="'dialog-' + String(product.id) + product.companyModel"
                    :label="product.companyModel || ''"
                    :value="product.companyModel || ''"
                  />
                </el-select>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格">
              <el-input v-model="form.spec" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用量">
              <el-input-number v-model="form.qty" :min="0" :precision="4" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商">
              <el-input v-model="form.supplier" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="supplierPhone">
              <el-input v-model="form.supplierPhone" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="form.supplierContact" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单价">
              <el-input-number v-model="form.unitPrice" :min="0" :precision="4" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <CurrencySelect v-model="form.currency" placeholder="请选择或输入币种" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总价">
              <el-input :model-value="dialogTotalPrice" disabled />
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
      title="导入产品配件资料"
      :import-api="importBom"
      :download-template="downloadBomTemplate"
      identifier-label="物料编码"
      name-label="配件名称"
      error-file-name="产品配件导入错误清单.csv"
      @success="loadData"
    />
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.support-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.model-select {
  flex: 1;
}
</style>
