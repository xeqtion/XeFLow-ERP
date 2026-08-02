<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Edit, Plus, Search, View } from '@element-plus/icons-vue'
import {
  pageSalesPi,
  getSalesPi,
  createSalesPi,
  updateSalesPi,
  deleteSalesPi,
  downloadPiDoc,
  downloadCiDoc,
  confirmSalesPi,
  shipSalesPi,
  type SalesPi,
  type SalesPiItem
} from '@/api/salesPi'
import { pageCustomer, type Customer } from '@/api/customer'
import CurrencySelect from '@/components/CurrencySelect.vue'
import { pageProduct, type Product } from '@/api/product'
import { pageStandardPrice, type StandardPrice } from '@/api/standardPrice'
import { pageContractPrice, type ContractPrice } from '@/api/contractPrice'
import { traceOwners, type TraceOwnerOption } from '@/api/trace'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const statusMap: Record<string, { text: string; type: 'info' | 'primary' | 'success' | 'warning' }> = {
  DRAFT: { text: '草稿', type: 'info' },
  CONFIRMED: { text: '已确认', type: 'primary' },
  ORDERED: { text: '已下单', type: 'primary' },
  SHIPPED: { text: '已出货', type: 'success' },
  COMPLETED: { text: '已完成', type: 'success' },
  CLOSED: { text: '已完成', type: 'success' }
}

const loading = ref(false)
const saving = ref(false)
const list = ref<SalesPi[]>([])
const total = ref(0)
const customerOptions = ref<Customer[]>([])
const productOptions = ref<Product[]>([])
const standardPriceOptions = ref<StandardPrice[]>([])
const approvedQuoteOptions = ref<ContractPrice[]>([])
const ownerOptions = ref<TraceOwnerOption[]>([])
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; ownerId?: number | string }>({ pageNum: 1, pageSize: 10, keyword: '', ownerId: undefined })
const now = ref(Date.now())
let countdownTimer: number | undefined

const dialogVisible = ref(false)
const detailEditing = ref(false)
const isCreateMode = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SalesPi>(emptyForm())
const items = ref<SalesPiItem[]>([])
const dialogTitle = computed(() => {
  if (isCreateMode.value) return '新建销售合同PI'
  return detailEditing.value ? '编辑销售合同PI' : '销售合同PI详情'
})
const editable = computed(() => isCreateMode.value || detailEditing.value)

function emptyForm(): SalesPi {
  return {
    piNo: '',
    customerId: undefined,
    customerCode: '',
    customerName: '',
    currency: 'USD',
    depositAmount: 0,
    status: 'DRAFT',
    signDate: '',
    deliveryDate: '',
    paymentMethod: '',
    remark: ''
  }
}

const rules: FormRules<SalesPi> = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  signDate: [{ required: true, message: '请选择签约日期', trigger: 'change' }]
}

const computedTotal = computed(() =>
  items.value.reduce((sum, it) => sum + lineAmount(it), 0)
)

async function loadData() {
  loading.value = true
  try {
    const res = await pageSalesPi(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadOptions() {
  const standardPriceRequest = userStore.hasPermission('standard_price:list')
    ? pageStandardPrice({ pageNum: 1, pageSize: 1000 })
    : Promise.resolve(null)
  const [customerRes, productRes, standardPriceRes] = await Promise.all([
    pageCustomer({ pageNum: 1, pageSize: 1000 }),
    pageProduct({ pageNum: 1, pageSize: 1000 }),
    standardPriceRequest
  ])
  customerOptions.value = customerRes.data.records.filter((item) => item.id !== undefined && item.id !== null)
  productOptions.value = productRes.data.records.filter((item) => item.id !== undefined && item.id !== null)
  standardPriceOptions.value = standardPriceRes?.data.records.filter((item) => item.productId !== undefined && item.productId !== null) ?? []
}
async function loadOwnerOptions() {
  try { ownerOptions.value = (await traceOwners()).data } catch { ownerOptions.value = [] }
}

async function loadApprovedQuotes(customerId: number | string | undefined) {
  approvedQuoteOptions.value = []
  if (!customerId) return
  const res = await pageContractPrice({
    pageNum: 1,
    pageSize: 1000,
    customerId,
    auditStatus: 'PASS'
  })
  approvedQuoteOptions.value = res.data.records
}

function onSearch() {
  query.pageNum = 1
  loadData()
}

function onReset() {
  query.keyword = ''
  query.ownerId = undefined
  onSearch()
}

async function openCreate() {
  await loadOptions()
  Object.assign(form, emptyForm())
  form.id = undefined
  form.signDate = today()
  items.value = []
  isCreateMode.value = true
  detailEditing.value = true
  dialogVisible.value = true
}

async function openDetail(row: SalesPi) {
  const res = await getSalesPi(row.id!)
  Object.assign(form, emptyForm(), res.data)
  items.value = res.data.items || []
  isCreateMode.value = false
  detailEditing.value = false
  dialogVisible.value = true
}

async function beginEdit() {
  await Promise.all([loadOptions(), loadApprovedQuotes(form.customerId)])
  detailEditing.value = true
}

function addItem() {
  if (!editable.value) return
  items.value.push({
    productId: undefined,
    prodCode: '',
    productName: '',
    productModel: '',
    qty: undefined,
    unitPrice: undefined,
    amount: undefined,
    quoteSource: ''
  })
}

function removeItem(index: number) {
  if (!editable.value) return
  items.value.splice(index, 1)
}

function customerOptionLabel(customer: Customer) {
  return `${customer.custCode || customer.id} | ${customer.companyName || customer.custNameEn || customer.custNameCn || ''}`
}

function customerOptionValue(customer: Customer): string | number {
  return customer.id as string | number
}

async function onCustomerChange(customerId: number | string | undefined) {
  const customer = customerOptions.value.find((item) => String(item.id) === String(customerId))
  if (!customer) {
    approvedQuoteOptions.value = []
    return
  }
  form.customerCode = customer.custCode
  form.customerName = customer.companyName || customer.custNameEn || customer.custNameCn || ''
  if (!form.currency && customer.currency) {
    form.currency = customer.currency
  }
  await loadApprovedQuotes(customerId)
  items.value.forEach((item) => {
    if (item.productId) {
      applyProductAndPrice(item)
    }
  })
}

function productOptionLabel(product: Product) {
  return `${product.id} | ${product.productName || product.prodNameCn || product.prodNameEn || ''} | ${product.companyModel || product.prodCode || product.spec || ''}`
}

function productOptionValue(product: Product): string | number {
  return product.id as string | number
}

function onProductChange(row: SalesPiItem) {
  if (!editable.value) return
  applyProductAndPrice(row)
}

function applyProductAndPrice(row: SalesPiItem) {
  const product = productOptions.value.find((item) => String(item.id) === String(row.productId))
  if (!product) return

  row.prodCode = firstText(product.prodCode, row.prodCode)
  row.productName = firstText(product.productName, product.prodNameCn, product.prodNameEn, row.productName)
  row.productModel = firstText(product.companyModel, product.prodCode, product.spec, row.productModel)
  row.factoryModel = firstText(product.factoryModel, row.factoryModel)
  row.unit = firstQuantityUnit(product.unit, row.unit)

  const productQuote = findProductQuote(row.productId)
  if (productQuote) {
    row.standardPrice = productQuote.standardUnitPrice ?? productQuote.price
    row.discountUnitPrice = productQuote.discountUnitPrice
    row.floorPrice = productQuote.floorPrice
    if (productQuote.currency) form.currency = productQuote.currency
  }

  const salesQuote = approvedQuoteOptions.value.find((quote) => String(quote.productId) === String(row.productId))
  if (salesQuote) {
    row.quoteId = salesQuote.id
    row.quoteSource = 'SALES_QUOTE'
    row.productName = firstText(salesQuote.productName, row.productName)
    row.productModel = firstText(salesQuote.productModel, row.productModel)
    row.standardPrice = salesQuote.standardPrice
    applyUnitPrice(row, salesQuote.contractPrice ?? salesQuote.dealUnitPrice)
    if (salesQuote.currency) form.currency = salesQuote.currency
    return
  }

  row.quoteId = undefined
  row.quoteSource = productQuote ? 'DISCOUNT_PRICE' : ''
  if (productQuote) {
    applyQuoteSource(row, row.quoteSource)
  }
}

function applyUnitPrice(row: SalesPiItem, value?: number) {
  if (value === undefined || value === null) return
  const nextValue = Number(value)
  if (Number.isFinite(nextValue)) {
    row.unitPrice = nextValue
  }
}

function onUnitPriceChange(row: SalesPiItem) {
  row.quoteSource = ''
  row.quoteId = undefined
}

function onQuoteSourceChange(row: SalesPiItem, value: string) {
  applyQuoteSource(row, value)
}

function applyQuoteSource(row: SalesPiItem, value?: string) {
  row.quoteSource = ''
  row.quoteId = undefined
  if (!value) return

  if (value.startsWith('SALES_QUOTE:')) {
    const quoteId = value.slice('SALES_QUOTE:'.length)
    const quote = approvedQuoteOptions.value.find((item) => String(item.id) === quoteId)
    if (!quote) return
    row.quoteSource = 'SALES_QUOTE'
    row.quoteId = quote.id
    row.productName = firstText(quote.productName, row.productName)
    row.productModel = firstText(quote.productModel, row.productModel)
    row.prodCode = firstText(quote.prodCode, row.prodCode)
    row.standardPrice = quote.standardPrice
    applyUnitPrice(row, quote.contractPrice ?? quote.dealUnitPrice)
    if (quote.currency) form.currency = quote.currency
    return
  }

  const productQuote = findProductQuote(row.productId)
  if (!productQuote) return
  row.standardPrice = productQuote.standardUnitPrice ?? productQuote.price
  row.discountUnitPrice = productQuote.discountUnitPrice
  row.floorPrice = productQuote.floorPrice
  row.quoteSource = value
  if (value === 'STANDARD_PRICE') {
    applyUnitPrice(row, productQuote.standardUnitPrice ?? productQuote.price)
  } else if (value === 'DISCOUNT_PRICE') {
    applyUnitPrice(row, productQuote.discountUnitPrice)
  } else if (value === 'FLOOR_PRICE') {
    applyUnitPrice(row, productQuote.floorPrice ?? productQuote.discountUnitPrice)
  } else {
    row.quoteSource = ''
  }
  if (productQuote.currency) form.currency = productQuote.currency
}

async function submitForm() {
  if (!formRef.value || !editable.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const payload: SalesPi = { ...form, totalAmount: computedTotal.value, items: items.value }
  saving.value = true
  try {
    if (form.id) {
      await updateSalesPi(payload)
      ElMessage.success('保存成功')
    } else {
      await createSalesPi(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: SalesPi) {
  await ElMessageBox.confirm(`确认删除 PI「${row.piNo}」？`, '提示', { type: 'warning' })
  await deleteSalesPi(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

function onDoc(row: SalesPi) {
  downloadPiDoc(row.id!, row.piNo)
}

function onCiDoc(row: SalesPi) {
  downloadCiDoc(row.id!, row.piNo)
}

async function onConfirm(row: SalesPi) {
  const res = await getSalesPi(row.id!)
  const detailItems = res.data.items || []
  let validationMessage = ''
  if (!detailItems.length) {
    validationMessage = '确认前请至少添加一条产品明细'
  } else {
    const invalidProductIndex = detailItems.findIndex((item) => item.productId == null || !String(item.prodCode || '').trim())
    const invalidQtyIndex = detailItems.findIndex((item) => !Number.isFinite(Number(item.qty)) || Number(item.qty) <= 0)
    const invalidPriceIndex = detailItems.findIndex((item) => !Number.isFinite(Number(item.unitPrice)) || Number(item.unitPrice) < 0)
    if (invalidProductIndex >= 0) validationMessage = `第 ${invalidProductIndex + 1} 条 PI 明细缺少有效产品`
    else if (invalidQtyIndex >= 0) validationMessage = `第 ${invalidQtyIndex + 1} 条 PI 明细数量必须大于 0`
    else if (invalidPriceIndex >= 0) validationMessage = `第 ${invalidPriceIndex + 1} 条 PI 明细单价不能为空且不能为负数`
  }
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    await openDetail(row)
    await beginEdit()
    return
  }
  const totalQty = detailItems.reduce((sum, item) => sum + Number(item.qty || 0), 0)
  await ElMessageBox.confirm(
    `确认 PI「${row.piNo}」？共 ${detailItems.length} 项，产品总数量 ${totalQty}。请确认产品、数量和价格无误，确认后将进入财务应收账款。`,
    '确认 PI',
    { type: 'warning', confirmButtonText: '明细无误，确认' }
  )
  await confirmSalesPi(row.id!)
  ElMessage.success('已确认')
  loadData()
}

function draftRemainingSeconds(row: SalesPi) {
  if (row.status !== 'DRAFT') return null
  if (row.draftExpireTime) {
    return Math.max(Math.floor((new Date(row.draftExpireTime.replace(' ', 'T')).getTime() - now.value) / 1000), 0)
  }
  return row.draftRemainingSeconds ?? null
}

function draftCountdown(row: SalesPi) {
  const seconds = draftRemainingSeconds(row)
  if (seconds === null || seconds === undefined) return ''
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

async function onShip(row: SalesPi) {
  const { value } = await ElMessageBox.prompt(
    `为 PI「${row.piNo}」生成出货单。留空将按当前可用库存出货；库存不足时会生成部分出货单。`,
    '一键生成出货',
    { inputPlaceholder: '出货数量(可空)', inputPattern: /^$|^\d+(\.\d+)?$/, inputErrorMessage: '请输入数字' }
  )
  const qty = value ? Number(value) : undefined
  await shipSalesPi(row.id!, { qty })
  ElMessage.success('已生成出货单，库存和剩余待出货数量已同步')
  loadData()
}

function formatMoney(value?: number) {
  if (value === undefined || value === null) return ''
  return Number(value).toFixed(2)
}

function lineAmount(row: SalesPiItem) {
  if (row.amount !== undefined && row.amount !== null) return Number(row.amount) || 0
  return (Number(row.qty) || 0) * (Number(row.unitPrice) || 0)
}

function quoteSourceValue(row: SalesPiItem) {
  if (row.quoteSource === 'SALES_QUOTE' && row.quoteId) return `SALES_QUOTE:${row.quoteId}`
  return row.quoteSource || ''
}

function quoteSourceOptions(row: SalesPiItem) {
  const quotes = approvedQuoteOptions.value
    .filter((quote) => String(quote.productId) === String(row.productId))
    .map((quote) => ({
      label: `销售报价 ${quote.id}｜${formatMoney(quote.contractPrice ?? quote.dealUnitPrice)}`,
      value: `SALES_QUOTE:${quote.id}`
    }))
  const productQuote = findProductQuote(row.productId)
  if (!productQuote) return quotes
  return [
    ...quotes,
    { label: `标准单价｜${formatMoney(productQuote.standardUnitPrice ?? productQuote.price)}`, value: 'STANDARD_PRICE' },
    { label: `折扣单价｜${formatMoney(productQuote.discountUnitPrice)}`, value: 'DISCOUNT_PRICE' },
    { label: `底价｜${formatMoney(productQuote.floorPrice ?? productQuote.discountUnitPrice)}`, value: 'FLOOR_PRICE' }
  ]
}

function quoteSourceLabel(row: SalesPiItem) {
  const value = quoteSourceValue(row)
  return quoteSourceOptions(row).find((item) => item.value === value)?.label || '-'
}

function findProductQuote(productId?: number | string) {
  if (productId === undefined || productId === null) return undefined
  return standardPriceOptions.value.find((quote) => String(quote.productId) === String(productId))
}

function formatTotal() {
  return `$${computedTotal.value.toFixed(2)}`
}

function amountPlaceholder(row: SalesPiItem) {
  const amount = (Number(row.qty) || 0) * (Number(row.unitPrice) || 0)
  return amount ? amount.toFixed(2) : '自动计算'
}

function normalizeAmount(row: SalesPiItem) {
  if (row.amount === null) {
    row.amount = undefined
  }
}

function formatQuoteSource(row: SalesPiItem) {
  if (row.quoteSource === 'SALES_QUOTE' && row.quoteId) return `销售报价 ${row.quoteId}`
  if (row.quoteSource === 'STANDARD_PRICE') return '标准单价'
  if (row.quoteSource === 'DISCOUNT_PRICE') return '折扣单价'
  if (row.quoteSource === 'FLOOR_PRICE') return '底价'
  return '-'
}

function displayCustomerId(row: SalesPi) {
  return row.customerCode || row.customerId || ''
}

function today() {
  return new Date().toISOString().slice(0, 10)
}

function firstText(...values: Array<string | number | undefined | null>) {
  for (const value of values) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    if (text) return text
  }
  return ''
}

function firstQuantityUnit(...values: Array<string | undefined | null>) {
  const text = firstText(...values)
  return /^(USD|CNY|EUR|GBP|JPY|HKD|RMB)$/i.test(text) ? '' : text
}

onMounted(() => {
  loadOwnerOptions()
  loadData()
  countdownTimer = window.setInterval(() => { now.value = Date.now() }, 1000)
})
onUnmounted(() => { if (countdownTimer) window.clearInterval(countdownTimer) })
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            placeholder="PI单号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item v-if="ownerOptions.length > 1" label="合同担当">
          <el-select v-model="query.ownerId" clearable placeholder="全部业务员" style="width: 160px">
            <el-option v-for="owner in ownerOptions" :key="String(owner.id)" :label="owner.name" :value="owner.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'sales_pi:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="piNo" label="PI单号" min-width="150" />
        <el-table-column label="客户ID" width="120">
          <template #default="{ row }: any">{{ displayCustomerId(row) }}</template>
        </el-table-column>
        <el-table-column prop="customerName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="合计金额" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="约定定金" width="120" align="left">
          <template #default="{ row }: any">{{ formatMoney(row.depositAmount) }}</template>
        </el-table-column>
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="出货进度" width="150" align="left">
          <template #default="{ row }: any">已出 {{ row.shippedQty || 0 }} / 剩余 {{ row.remainingShipQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: any">
            <el-tag size="small" :type="statusMap[row.status]?.type || 'info'">{{ statusMap[row.status]?.text || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signDate" label="签约日期" width="110" />
        <el-table-column prop="deliveryDate" label="交货日期" width="110" />
        <el-table-column prop="paymentMethod" label="付款方式" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="390" fixed="right">
          <template #default="{ row }: any">
            <div class="operation-cell">
              <div v-if="row.status === 'DRAFT'" class="expire-tip">草稿将在 {{ draftCountdown(row) }} 后自动删除</div>
              <div class="operation-buttons">
                <el-button link type="primary" :icon="View" @click="openDetail(row)">查看详细</el-button>
                <el-button v-if="row.status === 'DRAFT'" v-permission="'sales_pi:update'" link type="success" @click="onConfirm(row)">确认</el-button>
                <el-button v-if="['CONFIRMED', 'ORDERED', 'SHIPPED'].includes(row.status || '')" v-permission="'sales_pi:update'" link type="warning" @click="onShip(row)">生成出货</el-button>
                <el-button v-permission="'sales_pi:export'" link type="primary" @click="onDoc(row)">PI单据</el-button>
                <el-button v-permission="'sales_pi:export'" link type="primary" @click="onCiDoc(row)">CI单据</el-button>
                <el-button v-if="['DRAFT', 'COMPLETED', 'CLOSED'].includes(row.status || '')" v-permission="'sales_pi:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="1180px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="PI单号">
              <el-input v-model="form.piNo" disabled placeholder="保存后自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                filterable
                clearable
                :disabled="!editable"
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
          <el-col :span="8">
            <el-form-item label="客户名称">
              <el-input v-model="form.customerName" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种">
              <CurrencySelect v-model="form.currency" :disabled="!editable" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="form.status" disabled style="width: 100%">
                <el-option label="草稿" value="DRAFT" />
                <el-option label="已确认" value="CONFIRMED" />
                <el-option label="已下单" value="ORDERED" />
                <el-option label="已出货" value="SHIPPED" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="签约日期" prop="signDate">
              <el-date-picker v-model="form.signDate" :disabled="!editable" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="交货日期">
              <el-date-picker v-model="form.deliveryDate" :disabled="!editable" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="付款方式">
              <el-input v-model="form.paymentMethod" :disabled="!editable" placeholder="如 T/T 30%定金" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="约定定金">
              <el-input-number v-model="form.depositAmount" :disabled="!editable" :min="0" :max="computedTotal" :precision="2" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="贸易条款">
              <el-input v-model="form.tradeTerm" :disabled="!editable" placeholder="如 FOB Shenzhen" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="目的港">
              <el-input v-model="form.destPort" :disabled="!editable" placeholder="如 Rotterdam" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="唛头">
              <el-input v-model="form.shippingMarks" :disabled="!editable" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="备注">
              <el-input v-model="form.remark" :disabled="!editable" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="items-header">
        <span>PI明细</span>
        <div>
          <span class="total">合计：{{ formatTotal() }}</span>
          <el-button v-if="editable" size="small" type="primary" :icon="Plus" @click="addItem">添加明细</el-button>
        </div>
      </div>
      <el-table class="pi-items-table" :data="items" border size="small" max-height="360">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column label="产品资料" min-width="260">
          <template #default="{ row }: any">
            <el-select
              v-model="row.productId"
              filterable
              clearable
              :disabled="!editable"
              placeholder="产品ID / 名称 / 型号"
              style="width: 100%"
              @change="onProductChange(row)"
            >
              <el-option
                v-for="product in productOptions"
                :key="String(product.id)"
                :label="productOptionLabel(product)"
                :value="productOptionValue(product)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }: any"><el-input v-model="row.productName" :disabled="!editable" /></template>
        </el-table-column>
        <el-table-column label="产品型号" min-width="150" show-overflow-tooltip>
          <template #default="{ row }: any"><el-input v-model="row.productModel" :disabled="!editable" /></template>
        </el-table-column>
        <el-table-column label="数量" width="110">
          <template #default="{ row }: any"><el-input-number v-model="row.qty" :disabled="!editable" :controls="false" :precision="4" style="width: 100%" /></template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }: any">
            <el-input-number
              v-model="row.unitPrice"
              :disabled="!editable"
              :controls="false"
              :precision="4"
              style="width: 100%"
              @change="onUnitPriceChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130">
          <template #default="{ row }: any">
            <el-input-number
              v-model="row.amount"
              :disabled="!editable"
              :controls="false"
              :precision="2"
              :placeholder="amountPlaceholder(row)"
              style="width: 100%"
              @change="normalizeAmount(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="报价来源" width="190" show-overflow-tooltip>
          <template #default="{ row }: any">
            <el-select
              v-if="editable"
              :model-value="quoteSourceValue(row)"
              clearable
              :disabled="!row.productId"
              placeholder="-"
              style="width: 100%"
              @change="(value: string) => onQuoteSourceChange(row, value)"
            >
              <el-option
                v-for="option in quoteSourceOptions(row)"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <span v-else>{{ quoteSourceLabel(row) || formatQuoteSource(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }: any"><el-input v-model="row.remark" :disabled="!editable" /></template>
        </el-table-column>
        <el-table-column v-if="editable" label="操作" width="60">
          <template #default="{ $index }: any">
            <el-button link type="danger" :icon="Delete" @click="removeItem($index)" />
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ editable ? '取消' : '关闭' }}</el-button>
        <el-button v-if="!editable && !isCreateMode" v-permission="'sales_pi:update'" type="primary" :icon="Edit" @click="beginEdit">编辑</el-button>
        <el-button v-if="editable" type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  margin-top: 4px;
}
.operation-cell { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; }
.operation-buttons { display: flex; align-items: center; gap: 4px; white-space: nowrap; }
.operation-buttons :deep(.el-button) { margin-left: 0; }
.expire-tip { color: #f56c6c; font-size: 12px; line-height: 18px; }

.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 8px 0;
  font-weight: 600;
}

.total {
  margin-right: 16px;
  color: #f56c6c;
}

.pi-items-table :deep(.el-input-number .el-input__inner) {
  text-align: right;
}

.pi-items-table :deep(.el-select),
.pi-items-table :deep(.el-input) {
  width: 100%;
}
</style>
