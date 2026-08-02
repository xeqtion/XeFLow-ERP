<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete, Share, Promotion, Select, Search } from '@element-plus/icons-vue'
import { encodeEntityRef, getEntityTypeConfig } from '@/utils/entityLink'
import { listContacts, sendMessage, type Contact } from '@/api/message'
import { piList, type PiBrief } from '@/api/trace'
import { pageProduct, type Product } from '@/api/product'
import CurrencySelect from '@/components/CurrencySelect.vue'
import {
  pagePo, getPo, createPo, updatePo, deletePo, downloadPoDoc, confirmPo, receivePo, listPurchasers,
  type PurchaseOrder, type PurchaseOrderItem
} from '@/api/purchaseOrder'

const loading = ref(false)
const list = ref<PurchaseOrder[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; purchaserId?: number | string }>({ pageNum: 1, pageSize: 10, keyword: '', purchaserId: undefined })
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<PurchaseOrder>(emptyForm())
const items = ref<PurchaseOrderItem[]>([])
const piOptions = ref<PiBrief[]>([])
const productOptions = ref<Product[]>([])
const purchaserOptions = ref<Array<{ id: number | string; name: string }>>([])
const receiptDialogVisible = ref(false)
const receiptTarget = ref<PurchaseOrder | null>(null)
const receiptItems = ref<Array<PurchaseOrderItem & { currentQty: number }>>([])

function emptyForm(): PurchaseOrder {
  return { poNo: '', piNo: '', purchaserId: undefined, supplier: '', currency: 'CNY', status: 'DRAFT', orderDate: '', remark: '' }
}
const rules: FormRules = {}
const computedTotal = computed(() => items.value.reduce((s, it) => s + (Number(it.qty) || 0) * (Number(it.unitPrice) || 0), 0))

async function loadData() {
  loading.value = true
  try { const res = await pagePo(query); list.value = res.data.records; total.value = res.data.total }
  finally { loading.value = false }
}
function onSearch() { query.pageNum = 1; loadData() }
function onReset() { query.keyword = ''; query.purchaserId = undefined; onSearch() }
function openCreate() { Object.assign(form, emptyForm()); form.id = undefined; items.value = []; dialogTitle.value = '新建 PO'; dialogVisible.value = true }
function onPiChange(piId: number | string | undefined) {
  const pi = piOptions.value.find((item) => String(item.id) === String(piId))
  form.piNo = pi?.piNo || ''
}
async function openEdit(row: PurchaseOrder) {
  const res = await getPo(row.id!)
  Object.assign(form, res.data); items.value = res.data.items || []
  dialogTitle.value = '修改 PO'; dialogVisible.value = true
}
function addItem() { items.value.push({ prodCode: '', qty: undefined, unitPrice: undefined }) }
function onProductChange(row: PurchaseOrderItem) {
  const product = productOptions.value.find((item) => String(item.id) === String(row.productId))
  if (!product) return
  row.productName = product.productName || product.prodNameCn || product.prodNameEn || ''
  if (!row.prodCode) row.prodCode = product.companyModel || product.prodCode || ''
}
function onProductCodeChange(row: PurchaseOrderItem, value?: string) {
  const product = productOptions.value.find((item) => item.companyModel === value || item.prodCode === value)
  if (!product) return
  row.productId = product.id as number
  row.productName = product.productName || product.prodNameCn || product.prodNameEn || ''
}
function removeItem(i: number) { items.value.splice(i, 1) }
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const payload: PurchaseOrder = { ...form, items: items.value }
    if (form.id) { await updatePo(payload); ElMessage.success('修改成功') }
    else { await createPo(payload); ElMessage.success('创建成功') }
    dialogVisible.value = false; loadData()
  })
}
async function onDelete(row: PurchaseOrder) {
  const settledHint = ['COMPLETED', 'RECEIVED'].includes(row.status || '')
    ? '已完成 PO 仅在应付账款全部结清后才能删除。'
    : ''
  await ElMessageBox.confirm(`确认删除 PO「${row.poNo}」？${settledHint}`, '提示', { type: 'warning' })
  await deletePo(row.id!); ElMessage.success('删除成功'); loadData()
}
function onDoc(row: PurchaseOrder) { downloadPoDoc(row.id!, row.poNo) }

/* -------- 分享到消息 -------- */
const shareDialogVisible = ref(false)
const shareContacts = ref<Contact[]>([])
const shareContactKeyword = ref('')
const shareSelectedContact = ref<Contact | null>(null)
const shareSending = ref(false)
const shareTarget = ref<PurchaseOrder | null>(null)
const shareRemark = ref('')
const shareFilteredContacts = computed(() => {
  const k = shareContactKeyword.value.trim().toLowerCase()
  if (!k) return shareContacts.value
  return shareContacts.value.filter(
    (c) => c.name.toLowerCase().includes(k) || (c.username || '').toLowerCase().includes(k)
  )
})
async function openShare(row: PurchaseOrder) {
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
  if (shareTarget.value.id == null) {
    ElMessage.warning('该PO订单 ID 无效，无法分享')
    return
  }
  shareSending.value = true
  try {
    const config = getEntityTypeConfig('purchase_order')
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
      ElMessage.warning('该PO订单 ID 无效，无法分享')
      return
    }
    const entityJson = encodeEntityRef({ type: 'purchase_order', id: entity.id, name: config.getDisplayName(entity.id, entity), extra })
    await sendMessage(shareSelectedContact.value.userId, entityJson, [])
    ElMessage.success('PO订单已分享')
    shareDialogVisible.value = false
  } catch { /* interceptor handled */ }
  finally { shareSending.value = false }
}
async function openReceiveLegacy(row: PurchaseOrder) {
  await ElMessageBox.confirm(`确认 PO「${row.poNo}」到货入库？将按明细生成入库流水。`, '到货入库', { type: 'info' })
  await receivePo(row.id!, [])
  ElMessage.success('已入库')
  loadData()
}
async function onConfirm(row: PurchaseOrder) {
  const res = await getPo(row.id!)
  const detailItems = res.data.items || []
  let validationMessage = ''
  if (!detailItems.length) {
    validationMessage = '确认前请至少添加一条采购明细'
  } else {
    const invalidCodeIndex = detailItems.findIndex((item) => !String(item.prodCode || '').trim())
    const invalidQtyIndex = detailItems.findIndex((item) => !Number.isFinite(Number(item.qty)) || Number(item.qty) <= 0)
    if (invalidCodeIndex >= 0) validationMessage = `第 ${invalidCodeIndex + 1} 条采购明细缺少产品代码`
    else if (invalidQtyIndex >= 0) validationMessage = `第 ${invalidQtyIndex + 1} 条采购明细的采购数量必须大于 0`
  }
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    Object.assign(form, res.data)
    items.value = detailItems
    dialogTitle.value = '修改 PO（确认前请补全采购明细）'
    dialogVisible.value = true
    return
  }
  const totalQty = detailItems.reduce((sum, item) => sum + Number(item.qty || 0), 0)
  await ElMessageBox.confirm(
    `确认 PO「${row.poNo}」？共 ${detailItems.length} 项，采购总数量 ${totalQty}。请确认产品和数量无误，确认后将进入财务应付账款。`,
    '确认 PO',
    { type: 'warning', confirmButtonText: '明细无误，确认' }
  )
  await confirmPo(row.id!)
  ElMessage.success('已确认')
  loadData()
}
async function onReceive(row: PurchaseOrder) {
  const res = await getPo(row.id!)
  receiptTarget.value = res.data
  receiptItems.value = (res.data.items || []).map((item) => ({
    ...item,
    currentQty: Math.max(0, Number(item.qty || 0) - Number(item.receivedQty || 0))
  }))
  receiptDialogVisible.value = true
}
async function submitReceipt() {
  if (!receiptTarget.value?.id) return
  const lines = receiptItems.value
    .filter((item) => Number(item.currentQty) > 0)
    .map((item) => ({ itemId: item.id!, receivedQty: Number(item.currentQty) }))
  if (!lines.length) {
    ElMessage.warning('请至少填写一项本次到货数量')
    return
  }
  await receivePo(receiptTarget.value.id, lines)
  ElMessage.success('到货入库已登记')
  receiptDialogVisible.value = false
  loadData()
}
onMounted(async () => {
  const [pis, products, purchasers] = await Promise.all([piList(), pageProduct({ pageNum: 1, pageSize: 1000 }), listPurchasers(), loadData()])
  piOptions.value = pis.data
  productOptions.value = products.data.records
  purchaserOptions.value = purchasers.data
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="PO单号 / PI单号 / 供应商" clearable style="width: 260px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="onReset">重置</el-button></el-form-item>
        <el-form-item label="采购人"><el-select v-model="query.purchaserId" clearable filterable placeholder="全部采购人" style="width: 160px"><el-option v-for="buyer in purchaserOptions" :key="String(buyer.id)" :label="buyer.name" :value="buyer.id" /></el-select></el-form-item>
      </el-form>
      <div class="toolbar"><el-button v-permission="'purchase_order:create'" type="primary" @click="openCreate">新建</el-button></div>
      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="poNo" label="PO单号" width="150" />
        <el-table-column prop="piNo" label="关联PI单号" width="170" show-overflow-tooltip />
        <el-table-column prop="supplier" label="供应商" min-width="160" show-overflow-tooltip />
        <el-table-column prop="purchaserName" label="采购人" width="120" />
        <el-table-column prop="purchaseSummary" label="采购明细" min-width="220" show-overflow-tooltip />
        <el-table-column prop="totalAmount" label="合计金额" width="130" align="left" />
        <el-table-column prop="currency" label="币种" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }: any">
            <el-tag size="small" :type="['COMPLETED', 'RECEIVED'].includes(row.status) ? 'success' : (row.status === 'IN_PROGRESS' ? 'warning' : (row.status === 'CONFIRMED' ? 'primary' : 'info'))">
              {{ ['COMPLETED', 'RECEIVED'].includes(row.status) ? '已完成' : (row.status === 'IN_PROGRESS' ? '进行中' : (row.status === 'CONFIRMED' ? '已确认' : '草稿')) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orderDate" label="下单日期" width="110" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }: any">
            <div class="po-actions">
              <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase_order:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" v-permission="'purchase_order:update'" link type="success" @click="onConfirm(row)">确认</el-button>
              <el-button v-if="['CONFIRMED', 'IN_PROGRESS'].includes(row.status)" v-permission="'purchase_order:update'" link type="success" @click="onReceive(row)">到货入库</el-button>
              <el-button link type="info" :icon="Share" @click="openShare(row)">分享</el-button>
              <el-button v-permission="'purchase_order:export'" link type="primary" @click="onDoc(row)">PO单据</el-button>
              <el-button v-if="['DRAFT', 'COMPLETED', 'RECEIVED'].includes(row.status || '')" v-permission="'purchase_order:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="820px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="PO单号"><el-input v-model="form.poNo" placeholder="保存时自动生成" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="关联PI单号"><el-select v-model="form.piId" filterable clearable placeholder="选择 PI（可选）" style="width: 100%" @change="onPiChange"><el-option v-for="pi in piOptions" :key="String(pi.id)" :label="`${pi.piNo} · ${pi.customer || '-'}`" :value="pi.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="采购人"><el-select v-model="form.purchaserId" filterable clearable placeholder="未选时默认为当前用户" style="width: 100%"><el-option v-for="buyer in purchaserOptions" :key="String(buyer.id)" :label="buyer.name" :value="buyer.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="供应商"><el-input v-model="form.supplier" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="币种"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-input :model-value="form.status === 'DRAFT' ? '草稿（保存后请确认）' : form.status" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="下单日期"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <div class="items-header">
        <span>采购明细</span>
        <div><span class="total">合计：{{ computedTotal.toFixed(2) }}</span><el-button size="small" type="primary" :icon="Plus" @click="addItem">添加明细</el-button></div>
      </div>
      <el-table :data="items" border size="small" max-height="320">
        <el-table-column type="index" label="#" width="45" />
        <el-table-column label="产品代码（必填）" min-width="180"><template #default="{ row }: any"><el-select v-model="row.prodCode" filterable allow-create default-first-option clearable style="width: 100%" @change="onProductCodeChange(row, $event)"><el-option v-for="product in productOptions" :key="String(product.id)" :label="`${product.companyModel || product.prodCode || '-'} · ${product.productName || product.prodNameCn || product.prodNameEn || ''}`" :value="product.companyModel || product.prodCode || ''" /></el-select></template></el-table-column>
        <el-table-column label="产品名称" min-width="220"><template #default="{ row }: any"><el-select v-model="row.productId" filterable clearable placeholder="选择产品资料（可选）" style="width: 100%" @change="onProductChange(row)"><el-option v-for="product in productOptions" :key="String(product.id)" :label="`${product.productName || product.prodNameCn || product.prodNameEn || '-'} · ${product.companyModel || product.prodCode || ''}`" :value="product.id ?? 0" /></el-select></template></el-table-column>
        <el-table-column label="数量（必填）" width="130"><template #default="{ row }: any"><el-input-number v-model="row.qty" :min="0.0001" :controls="false" :precision="4" style="width: 100%" /></template></el-table-column>
        <el-table-column label="单价" width="120"><template #default="{ row }: any"><el-input-number v-model="row.unitPrice" :controls="false" :precision="4" style="width: 100%" /></template></el-table-column>
        <el-table-column label="金额(自动)" width="120" align="left"><template #default="{ row }: any">{{ ((Number(row.qty) || 0) * (Number(row.unitPrice) || 0)).toFixed(2) }}</template></el-table-column>
        <el-table-column label="操作" width="60"><template #default="{ $index }: any"><el-button link type="danger" :icon="Delete" @click="removeItem($index)" /></template></el-table-column>
      </el-table>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitForm">确定</el-button></template>
    </el-dialog>

    <!-- 分享到消息 -->
    <el-dialog v-model="shareDialogVisible" title="分享PO订单到消息" width="420px" append-to-body>
      <p style="margin-bottom: 12px; color: #606266; font-size: 13px;">
        选择联系人，将该PO订单信息发送给对方
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
    <el-dialog v-model="receiptDialogVisible" title="到货入库" width="680px" append-to-body>
      <div class="receipt-hint">PO：{{ receiptTarget?.poNo }}。填写本次实际到货数量，系统会自动累计已入库数量。</div>
      <el-table :data="receiptItems" border size="small">
        <el-table-column prop="prodCode" label="产品代码" min-width="160" />
        <el-table-column label="订购数量" width="110" align="left"><template #default="{ row }: any">{{ row.qty || 0 }}</template></el-table-column>
        <el-table-column label="已入库" width="110" align="left"><template #default="{ row }: any">{{ row.receivedQty || 0 }}</template></el-table-column>
        <el-table-column label="剩余待到货" width="120" align="left"><template #default="{ row }: any">{{ Math.max(0, Number(row.qty || 0) - Number(row.receivedQty || 0)) }}</template></el-table-column>
        <el-table-column label="本次到货" width="135">
          <template #default="{ row }: any"><el-input-number v-model="row.currentQty" :min="0" :max="Math.max(0, Number(row.qty || 0) - Number(row.receivedQty || 0))" :precision="4" :controls="false" style="width: 100%" /></template>
        </el-table-column>
      </el-table>
      <template #footer><el-button @click="receiptDialogVisible = false">取消</el-button><el-button type="primary" @click="submitReceipt">确认入库</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { margin-top: 4px; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; font-weight: 600; }
.total { margin-right: 16px; color: #f56c6c; }
.po-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.po-actions :deep(.el-button) {
  margin-left: 0;
  padding-left: 0;
  padding-right: 0;
}
.receipt-hint { margin-bottom: 12px; color: #606266; }

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
