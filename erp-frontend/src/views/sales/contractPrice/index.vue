<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Check, Close, Delete, Edit, Plus, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import {
  pageContractPrice,
  createContractPrice,
  updateContractPrice,
  deleteContractPrice,
  approveContractPrice,
  rejectContractPrice,
  requestReviewContractPrice,
  type ContractPrice
} from '@/api/contractPrice'
import { pageProduct, type Product } from '@/api/product'
import { pageCustomer, type Customer } from '@/api/customer'
import { useUserStore } from '@/stores/user'
import CurrencySelect from '@/components/CurrencySelect.vue'

const userStore = useUserStore()
const canAudit = computed(() => userStore.hasPermission('contract_price:approve'))
const canUpdateQuote = computed(() => userStore.hasPermission('contract_price:update'))
const canDeleteQuote = computed(() => userStore.hasPermission('contract_price:delete'))
const loading = ref(false)
const saving = ref(false)
const list = ref<ContractPrice[]>([])
const total = ref(0)
const productOptions = ref<Product[]>([])
const customerOptions = ref<Customer[]>([])
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', auditStatus: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<ContractPrice>(emptyForm())
const now = ref(Date.now())
let countdownTimer: number | undefined

function emptyForm(): ContractPrice {
  return {
    customerId: undefined,
    customerName: '',
    productId: undefined,
    productName: '',
    productModel: '',
    prodCode: '',
    dealUnitPrice: undefined,
    contractPrice: undefined,
    currency: 'USD',
    remark: ''
  }
}

const rules: FormRules<ContractPrice> = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  dealUnitPrice: [{ required: true, message: '请输入成交单价', trigger: 'blur' }]
}

const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' }> = {
  PASS: { text: '通过', type: 'success' },
  PENDING: { text: '待审核', type: 'warning' },
  REJECTED: { text: '驳回', type: 'danger' }
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageContractPrice(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const [products, customers] = await Promise.all([
    pageProduct({ pageNum: 1, pageSize: 1000 }),
    pageCustomer({ pageNum: 1, pageSize: 1000 })
  ])
  productOptions.value = products.data.records.filter((item) => item.id !== undefined && item.id !== null)
  customerOptions.value = customers.data.records.filter((item) => item.id !== undefined && item.id !== null)
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.auditStatus = ''
  onSearch()
}

function openCreate() {
  Object.assign(form, emptyForm())
  form.id = undefined
  dialogTitle.value = '新建销售报价'
  dialogVisible.value = true
}

function openEdit(row: ContractPrice) {
  if (!canEdit(row)) return
  Object.assign(form, emptyForm(), row)
  form.dealUnitPrice = row.dealUnitPrice ?? row.contractPrice
  if (!form.currency) form.currency = 'USD'
  dialogTitle.value = '修改销售报价'
  dialogVisible.value = true
}

function productOptionValue(product: Product): string | number {
  return product.id as string | number
}

function customerOptionValue(customer: Customer): string | number {
  return customer.id as string | number
}

function productOptionLabel(product: Product) {
  return `${product.id} | ${product.productName || product.prodNameCn || ''} | ${product.companyModel || product.prodCode || ''}`
}

function customerOptionLabel(customer: Customer) {
  return `${customer.custCode || customer.id} | ${customer.companyName || customer.custNameEn || customer.custNameCn || ''}`
}

function onProductChange(productId: string | number | undefined) {
  const product = productOptions.value.find((item) => String(item.id) === String(productId))
  if (!product) return
  form.productName = product.productName || product.prodNameCn || product.prodNameEn || ''
  form.productModel = product.companyModel || product.prodCode || product.spec || ''
  form.prodCode = product.prodCode || ''
}

function onCustomerChange(customerId: string | number | undefined) {
  const customer = customerOptions.value.find((item) => String(item.id) === String(customerId))
  if (!customer) return
  form.customerName = customer.companyName || customer.custNameEn || customer.custNameCn || customer.custCode || ''
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const dealUnitPrice = form.dealUnitPrice ?? form.contractPrice
  const payload: ContractPrice = {
    ...form,
    dealUnitPrice,
    contractPrice: dealUnitPrice,
    currency: form.currency?.trim() || 'USD'
  }
  saving.value = true
  try {
    if (payload.id) {
      const resubmittingRejected = form.auditStatus === 'REJECTED'
      await updateContractPrice(payload)
      ElMessage.success(resubmittingRejected ? '销售报价已重新提交审核' : '销售报价已保存')
    } else {
      await createContractPrice(payload)
      ElMessage.success('销售报价已创建')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onApprove(row: ContractPrice) {
  if (!row.id || !canAudit.value) return
  await ElMessageBox.confirm('确认通过该销售报价？', '审批', { type: 'warning' })
  await approveContractPrice(row.id)
  ElMessage.success('已通过')
  await loadData()
}

async function onReject(row: ContractPrice) {
  if (!row.id || !canAudit.value) return
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回', { inputType: 'textarea' })
  await rejectContractPrice(row.id, value || '')
  ElMessage.success('已驳回')
  await loadData()
}

async function onRequestReview(row: ContractPrice) {
  if (!row.id || !canAudit.value) return
  await ElMessageBox.confirm('确认将该销售报价重新置为待审核？', '重新审核', { type: 'warning' })
  await requestReviewContractPrice(row.id)
  ElMessage.success('已重新进入待审核')
  await loadData()
}

async function onDelete(row: ContractPrice) {
  if (!row.id || !canCancel(row)) return
  const action = removeActionText()
  await ElMessageBox.confirm(`确认${action}该销售报价？`, '提示', { type: 'warning' })
  await deleteContractPrice(row.id)
  ElMessage.success(`已${action}`)
  await loadData()
}

function formatMoney(value?: number) {
  if (value === undefined || value === null) return ''
  return Number(value).toFixed(4)
}

function deviationValue(row: ContractPrice) {
  return row.unitPriceDeviation ?? row.deviation
}

function formatDeviationRate(value?: number) {
  if (value === undefined || value === null) return ''
  const percent = Number(value) * 100
  const sign = percent > 0 ? '+' : ''
  return `${sign}${percent.toFixed(2)}%`
}

function dealValue(row: ContractPrice) {
  return row.dealUnitPrice ?? row.contractPrice
}

function isOwner(row: ContractPrice) {
  return String(row.createBy || '') === String(userStore.userId || '')
}

function rejectedExpireMs(row: ContractPrice) {
  if (row.auditStatus !== 'REJECTED' || !row.rejectedExpireTime) return 0
  return new Date(row.rejectedExpireTime.replace(' ', 'T')).getTime()
}

function rejectedRemainingSeconds(row: ContractPrice) {
  const expireMs = rejectedExpireMs(row)
  if (!expireMs) return row.rejectedRemainingSeconds ?? null
  return Math.max(Math.floor((expireMs - now.value) / 1000), 0)
}

function rejectedCountdown(row: ContractPrice) {
  const seconds = rejectedRemainingSeconds(row)
  if (seconds === null || seconds === undefined) return ''
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function rejectedExpireMessage(row: ContractPrice) {
  if (row.auditStatus !== 'REJECTED') return ''
  return rejectedEditable(row) ? `报价将在${rejectedCountdown(row)}后自动撤销` : '报价已超时，系统将自动撤销'
}

function rejectedEditable(row: ContractPrice) {
  return row.auditStatus === 'REJECTED' && (rejectedRemainingSeconds(row) ?? 0) > 0
}

function customerDisplayId(row: ContractPrice) {
  return row.customerCode || row.customerId || ''
}

function editButtonText(row: ContractPrice) {
  return row.auditStatus === 'REJECTED' ? '重新编辑' : '编辑'
}

function removeActionText() {
  return canAudit.value ? '删除' : '撤销'
}

function canEdit(row: ContractPrice) {
  if (!canUpdateQuote.value) return false
  if (row.auditStatus === 'PASS') return false
  if (row.auditStatus === 'REJECTED' && !rejectedEditable(row)) return false
  return canAudit.value || isOwner(row)
}

function canCancel(row: ContractPrice) {
  if (!canDeleteQuote.value) return false
  return canAudit.value || isOwner(row)
}

onMounted(() => {
  loadOptions()
  loadData()
  countdownTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="产品 / 型号 / 客户 / 备注"
            clearable
            style="width: 260px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 140px">
            <el-option label="待审核" value="PENDING" />
            <el-option label="通过" value="PASS" />
            <el-option label="驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'contract_price:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="productId" label="产品ID" width="120" />
        <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="productModel" label="产品型号" min-width="150" show-overflow-tooltip />
        <el-table-column label="客户ID" width="120">
          <template #default="{ row }: any">{{ customerDisplayId(row) }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        <el-table-column label="成交单价" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(dealValue(row)) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="偏差率" width="120" align="left">
          <template #default="{ row }: any">
            <span :class="{ danger: row.auditStatus === 'REJECTED', warning: row.auditStatus === 'PENDING' }">
              {{ formatDeviationRate(deviationValue(row)) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="100">
          <template #default="{ row }: any">
            <el-tag :type="statusMeta[row.auditStatus]?.type">{{ statusMeta[row.auditStatus]?.text || row.auditStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }: any">
            <div class="operation-cell">
              <div v-if="row.auditStatus === 'REJECTED'" class="expire-tip">
                {{ rejectedExpireMessage(row) }}
              </div>
              <div class="operation-buttons">
                <el-button
                  v-if="canAudit && row.auditStatus === 'PENDING'"
                  link
                  type="success"
                  :icon="Check"
                  @click="onApprove(row)"
                >
                  通过
                </el-button>
                <el-button
                  v-if="canAudit && row.auditStatus !== 'REJECTED'"
                  link
                  type="warning"
                  :icon="Close"
                  @click="onReject(row)"
                >
                  驳回
                </el-button>
                <el-button
                  v-if="canAudit && row.auditStatus !== 'PENDING'"
                  link
                  type="primary"
                  :icon="RefreshRight"
                  @click="onRequestReview(row)"
                >
                  重新审核
                </el-button>
                <el-button v-if="canEdit(row)" link type="primary" :icon="Edit" @click="openEdit(row)">
                  {{ editButtonText(row) }}
                </el-button>
                <el-button v-if="canCancel(row)" link type="danger" :icon="Delete" @click="onDelete(row)">
                  {{ removeActionText() }}
                </el-button>
              </div>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
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
            <el-form-item label="产品型号">
              <el-input v-model="form.productModel" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                filterable
                clearable
                placeholder="选择客户ID / 客户名称"
                style="width: 100%"
                @change="onCustomerChange"
              >
                <el-option
                  v-for="customer in customerOptions"
                  :key="String(customer.id)"
                  :label="customerOptionLabel(customer)"
                  :value="customerOptionValue(customer)"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成交单价" prop="dealUnitPrice">
              <el-input-number
                v-model="form.dealUnitPrice"
                :min="0"
                :precision="4"
                :controls="false"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种">
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
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.warning {
  color: #e6a23c;
}

.danger {
  color: #f56c6c;
}

.operation-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-height: 32px;
}

.operation-buttons {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.expire-tip {
  color: #f56c6c;
  font-size: 12px;
  line-height: 18px;
  white-space: normal;
}
</style>
