<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Document, Download, Edit, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import CurrencySelect from '@/components/CurrencySelect.vue'
import {
  pageStandardPrice,
  createStandardPrice,
  updateStandardPrice,
  deleteStandardPrice,
  importStandardPrice,
  exportStandardPrice,
  downloadStandardPriceTemplate,
  type StandardPrice
} from '@/api/standardPrice'
import { pageProduct, type Product } from '@/api/product'

const loading = ref(false)
const saving = ref(false)
const list = ref<StandardPrice[]>([])
const total = ref(0)
const productOptions = ref<Product[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<StandardPrice>(emptyForm())
const importVisible = ref(false)

function emptyForm(): StandardPrice {
  return {
    productId: undefined,
    productName: '',
    companyModel: '',
    remark: '',
    standardUnitPrice: undefined,
    discountUnitPrice: undefined,
    floorPrice: undefined,
    currency: 'USD'
  }
}

const rules: FormRules<StandardPrice> = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  standardUnitPrice: [{ required: true, message: '请输入标准单价', trigger: 'blur' }],
  discountUnitPrice: [{ required: true, message: '请输入折扣单价', trigger: 'blur' }],
  currency: [{ max: 8, message: '币种最多 8 个字符', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageStandardPrice(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadProducts() {
  const res = await pageProduct({ pageNum: 1, pageSize: 1000 })
  productOptions.value = res.data.records.filter((item) => item.id !== undefined && item.id !== null)
}

function productOptionValue(product: Product): string | number {
  return product.id as string | number
}

function productOptionLabel(product: Product) {
  return `${product.id} | ${product.productName || ''} | ${product.companyModel || ''}`
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
  dialogTitle.value = '新建产品报价'
  dialogVisible.value = true
}

function openEdit(row: StandardPrice) {
  Object.assign(form, emptyForm(), row)
  if (!form.standardUnitPrice) form.standardUnitPrice = form.price
  if (!form.floorPrice) form.floorPrice = form.discountUnitPrice
  if (!form.currency) form.currency = 'USD'
  dialogTitle.value = '修改产品报价'
  dialogVisible.value = true
}

function onProductChange(productId: string | number | undefined) {
  const product = productOptions.value.find((item) => String(item.id) === String(productId))
  if (!product) return
  form.productName = product.productName || product.prodNameCn || ''
  form.companyModel = product.companyModel || product.prodCode || ''
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const payload: StandardPrice = {
    ...form,
    currency: form.currency?.trim() || 'USD',
    floorPrice: form.floorPrice ?? form.discountUnitPrice,
    price: form.standardUnitPrice
  }
  saving.value = true
  try {
    if (payload.id) {
      await updateStandardPrice(payload)
      ElMessage.success('产品报价已保存')
    } else {
      await createStandardPrice(payload)
      ElMessage.success('产品报价已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: StandardPrice) {
  await ElMessageBox.confirm(`确认删除产品报价「${row.productName || row.productId}」？`, '提示', { type: 'warning' })
  if (!row.id) return
  await deleteStandardPrice(row.id)
  ElMessage.success('产品报价已删除')
  await loadData()
}

function onExport() {
  exportStandardPrice(query.keyword)
}

function formatMoney(value?: number) {
  if (value === undefined || value === null) return ''
  return Number(value).toFixed(4)
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
            placeholder="产品名称 / 公司产品型号 / 备注"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'standard_price:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
        <el-button v-permission="'standard_price:import'" :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button v-permission="'standard_price:export'" :icon="Document" @click="downloadStandardPriceTemplate">
          模板
        </el-button>
        <el-button v-permission="'standard_price:export'" :icon="Download" @click="onExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="productId" label="产品ID" width="120" />
        <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="companyModel" label="公司产品型号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="标准单价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.standardUnitPrice || row.price) }}</template>
        </el-table-column>
        <el-table-column label="折扣单价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.discountUnitPrice) }}</template>
        </el-table-column>
        <el-table-column label="底价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.floorPrice || row.discountUnitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'standard_price:update'" link type="primary" :icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'standard_price:delete'" link type="danger" :icon="Delete" @click="onDelete(row)">
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="form.productId"
                filterable
                clearable
                placeholder="选择产品ID / 产品名称 / 型号"
                style="width: 100%"
                @change="onProductChange"
              >
                <el-option
                  v-for="product in productOptions"
                  :key="String(product.id)"
                  :label="productOptionLabel(product)"
                  :value="productOptionValue(product)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品名称">
              <el-input v-model="form.productName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="公司产品型号">
              <el-input v-model="form.companyModel" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标准单价" prop="standardUnitPrice">
              <el-input-number
                v-model="form.standardUnitPrice"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="折扣单价" prop="discountUnitPrice">
              <el-input-number
                v-model="form.discountUnitPrice"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="底价">
              <el-input-number
                v-model="form.floorPrice"
                :min="0"
                :precision="4"
                :controls="false"
                placeholder="默认折扣单价"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <CurrencySelect v-model="form.currency" placeholder="请选择或输入币种" />
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
      title="导入产品报价"
      :import-api="importStandardPrice"
      :download-template="downloadStandardPriceTemplate"
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
</style>
