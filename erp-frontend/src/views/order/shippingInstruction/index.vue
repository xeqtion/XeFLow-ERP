<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageSi, getSi, createSi, updateSi, deleteSi, downloadSiDoc,
  type ShippingInstruction, type ShippingInstructionItem
} from '@/api/shippingInstruction'
import { piList, tracePi, type PiBrief } from '@/api/trace'

const loading = ref(false)
const list = ref<ShippingInstruction[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<ShippingInstruction>(emptyForm())
const piOptions = ref<PiBrief[]>([])
const piDetailLoading = ref(false)
const submitting = ref(false)
let formRequestVersion = 0
let activePiId: number | string | undefined

function emptyItem(): ShippingInstructionItem {
  return {
    productModel: '',
    factoryModel: '',
    quantity: undefined,
    cartonCount: undefined
  }
}

function emptyForm(): ShippingInstruction {
  return {
    siNo: '',
    customerCode: '',
    piId: undefined,
    shipDate: '',
    shippingAddress: '',
    deliveryAddress: '',
    contactName: '',
    phone: '',
    orderNo: '',
    orderCountry: '',
    productModel: '',
    factoryModel: '',
    quantity: undefined,
    cartonCount: undefined,
    orderRequirement: '',
    ownerName: '',
    orderDate: '',
    remark: '',
    items: [emptyItem()]
  }
}
const rules: FormRules = {}

function optionalNumber(value: number | string | undefined): number | undefined {
  return value == null || value === '' ? undefined : Number(value)
}

function normalizeItems(detail: ShippingInstruction): ShippingInstructionItem[] {
  const source = detail.items?.length
    ? detail.items
    : [{
        productModel: detail.productModel,
        factoryModel: detail.factoryModel,
        quantity: detail.quantity,
        cartonCount: detail.cartonCount
      }]
  return source.map((item, index) => ({
    ...item,
    quantity: optionalNumber(item.quantity),
    cartonCount: optionalNumber(item.cartonCount),
    sortOrder: index
  }))
}

function replaceForm(value: ShippingInstruction) {
  for (const key of Object.keys(form)) {
    delete (form as unknown as Record<string, unknown>)[key]
  }
  Object.assign(form, value)
}

function itemHasContent(item: ShippingInstructionItem): boolean {
  return Boolean(item.productModel?.trim()
    || item.factoryModel?.trim()
    || item.quantity != null && item.quantity !== ''
    || item.cartonCount != null && item.cartonCount !== '')
}

async function loadData() {
  loading.value = true
  try { const res = await pageSi(query); list.value = res.data.records; total.value = res.data.total }
  finally { loading.value = false }
}
function onSearch() { query.pageNum = 1; loadData() }
function onReset() { query.keyword = ''; onSearch() }
function openCreate() {
  formRequestVersion++
  piDetailLoading.value = false
  submitting.value = false
  activePiId = undefined
  replaceForm(emptyForm())
  dialogTitle.value = '新建出货指示'
  dialogVisible.value = true
}
async function openEdit(row: ShippingInstruction) {
  if (row.id == null) return
  const requestVersion = ++formRequestVersion
  const res = await getSi(row.id)
  if (requestVersion !== formRequestVersion) return
  const detail = res.data
  if (!detail) {
    ElMessage.error('出货指示不存在或已被删除')
    return
  }
  piDetailLoading.value = false
  submitting.value = false
  activePiId = detail.piId
  replaceForm({
    ...emptyForm(),
    ...detail,
    quantity: optionalNumber(detail.quantity),
    cartonCount: optionalNumber(detail.cartonCount),
    items: normalizeItems(detail)
  })
  dialogTitle.value = '修改出货指示'
  dialogVisible.value = true
}

function addItem() {
  if (!form.items) form.items = []
  if (form.items.length >= 200) {
    ElMessage.warning('一张出货指示最多添加 200 条产品明细')
    return
  }
  form.items.push(emptyItem())
}

function removeItem(index: number) {
  if (!form.items || form.items.length <= 1) {
    form.items = [emptyItem()]
    return
  }
  form.items.splice(index, 1)
}

async function onPiChange(piId?: number | string) {
  const requestVersion = ++formRequestVersion
  if (piId == null || piId === '') {
    piDetailLoading.value = false
    activePiId = undefined
    return
  }
  const previousPiId = activePiId
  const replacingProducts = (form.items || []).some(itemHasContent)
    && String(previousPiId ?? '') !== String(piId)
  if (replacingProducts) {
    try {
      await ElMessageBox.confirm('切换 PI 将用新 PI 的产品明细替换当前产品列表，是否继续？', '提示', {
        type: 'warning'
      })
    } catch {
      form.piId = previousPiId
      return
    }
  }
  const selectedId = String(piId)
  piDetailLoading.value = true
  try {
    const res = await tracePi(piId)
    if (requestVersion !== formRequestVersion || String(form.piId ?? '') !== selectedId) return
    const pi = res.data
    const piItems = pi.items?.map((item, index) => ({
      productModel: item.productModel || item.prodCode || '',
      factoryModel: item.factoryModel || '',
      quantity: optionalNumber(item.qty),
      cartonCount: optionalNumber(item.qty),
      sortOrder: index
    })) || []
    form.items = piItems.length ? piItems : [emptyItem()]
    form.orderNo = pi.piNo
    form.customerCode = pi.customerCode || ''
    form.ownerName = pi.ownerName || ''
    form.orderDate = pi.signDate || ''
    form.shipDate = pi.deliveryDate || ''
    form.orderRequirement = [...new Set((pi.items || [])
      .map(item => item.remark?.trim()).filter(Boolean))].join('\n')
    activePiId = piId
  } catch {
    if (requestVersion === formRequestVersion) {
      form.piId = previousPiId
      ElMessage.error('PI 产品明细加载失败，请重新选择')
    }
  } finally {
    if (requestVersion === formRequestVersion) {
      piDetailLoading.value = false
    }
  }
}

async function submitForm() {
  if (!formRef.value || submitting.value) return
  if (piDetailLoading.value) {
    ElMessage.warning('PI 产品明细加载中，请稍候')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const sourceItems = (form.items || []).filter(itemHasContent)
  const items: ShippingInstructionItem[] = []
  for (let index = 0; index < sourceItems.length; index++) {
    const item = sourceItems[index]
    const productModel = item.productModel?.trim() || ''
    const factoryModel = item.factoryModel?.trim() || ''
    const quantity = optionalNumber(item.quantity)
    const cartonCount = optionalNumber(item.cartonCount)
    if (productModel.length > 512 || factoryModel.length > 512) {
      ElMessage.warning(`产品${index + 1}的型号最多 512 个字符`)
      return
    }
    if (quantity != null && (!Number.isFinite(quantity) || quantity < 0)) {
      ElMessage.warning(`产品${index + 1}的数量不能小于 0`)
      return
    }
    if (cartonCount != null && (!Number.isFinite(cartonCount) || cartonCount < 0)) {
      ElMessage.warning(`产品${index + 1}的箱数不能小于 0`)
      return
    }
    items.push({ productModel, factoryModel, quantity, cartonCount, sortOrder: index })
  }
  if (!items.length) {
    items.push({ productModel: '', factoryModel: '', sortOrder: 0 })
  }

  const payload: ShippingInstruction = {
    id: form.id,
    siNo: form.siNo || '',
    customerCode: form.customerCode,
    piId: form.piId,
    shipDate: form.shipDate,
    shippingAddress: form.shippingAddress,
    deliveryAddress: form.deliveryAddress,
    contactName: form.contactName,
    phone: form.phone,
    orderNo: form.orderNo,
    orderCountry: form.orderCountry,
    orderRequirement: form.orderRequirement,
    ownerName: form.ownerName,
    orderDate: form.orderDate,
    remark: form.remark,
    items
  }
  submitting.value = true
  try {
    if (form.id) {
      await updateSi(payload)
      ElMessage.success('修改成功')
    } else {
      await createSi(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    await loadData()
  } finally {
    submitting.value = false
  }
}
async function onDelete(row: ShippingInstruction) {
  await ElMessageBox.confirm(`确认删除出货指示「${row.siNo}」？`, '提示', { type: 'warning' })
  await deleteSi(row.id!); ElMessage.success('删除成功'); loadData()
}
function onDoc(row: ShippingInstruction) { downloadSiDoc(row.id!, row.siNo) }
onMounted(async () => {
  const [pis] = await Promise.all([piList(), loadData()])
  piOptions.value = pis.data
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="指示单号 / PI单号 / 客户ID / 产品型号" clearable style="width: 280px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="onReset">重置</el-button></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button v-permission="'shipping_instruction:create'" type="primary" @click="openCreate">新建</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="siNo" label="出货指示单号" width="150" />
        <el-table-column prop="orderNo" label="订单编号" width="160" />
        <el-table-column prop="customerCode" label="客户ID" width="120" />
        <el-table-column prop="orderCountry" label="订单国家" width="120" />
        <el-table-column prop="productModel" label="产品型号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="quantity" label="数量" width="90" align="left" />
        <el-table-column prop="cartonCount" label="箱数" width="90" align="left" />
        <el-table-column prop="shipDate" label="出货日期" width="120" />
        <el-table-column prop="ownerName" label="负责人" width="110" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'shipping_instruction:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'shipping_instruction:export'" link type="primary" @click="onDoc(row)">指示单</el-button>
            <el-button v-permission="'shipping_instruction:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(1080px, 96vw)" top="4vh" class="si-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="112px">
        <el-row :gutter="12">
          <el-col :span="24"><div class="section-title">单据关联</div></el-col>
          <el-col :span="8"><el-form-item label="出货指示单号"><el-input v-model="form.siNo" placeholder="保存时自动生成" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="关联PI单号"><el-select v-model="form.piId" filterable clearable placeholder="选择 PI（可选）" style="width: 100%" @change="onPiChange"><el-option v-for="pi in piOptions" :key="String(pi.id)" :label="`${pi.piNo} · ${pi.customer || '-'}`" :value="pi.id" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出货日期"><el-date-picker v-model="form.shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="客户ID"><el-input v-model="form.customerCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="订单编号"><el-input v-model="form.orderNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="订单国家"><el-input v-model="form.orderCountry" /></el-form-item></el-col>
          <el-col :span="24"><div class="section-title">物流与联系人</div></el-col>
          <el-col :span="12"><el-form-item label="发货物流地址"><el-input v-model="form.shippingAddress" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="送货地址"><el-input v-model="form.deliveryAddress" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="负责人"><el-input v-model="form.ownerName" /></el-form-item></el-col>
          <el-col :span="24">
            <div class="section-heading">
              <div class="section-title">产品与数量</div>
              <el-button type="primary" plain size="small" :disabled="submitting || piDetailLoading" @click="addItem">新增产品</el-button>
            </div>
            <el-table v-loading="piDetailLoading" :data="form.items || []" border class="product-table">
              <el-table-column label="产品" width="72" align="center">
                <template #default="{ $index }: any">产品{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="产品型号" min-width="210">
                <template #default="{ row, $index }: any">
                  <el-input v-model="row.productModel" :placeholder="`产品型号${$index + 1}`" :disabled="submitting" />
                </template>
              </el-table-column>
              <el-table-column label="工厂型号" min-width="180">
                <template #default="{ row, $index }: any">
                  <el-input v-model="row.factoryModel" :placeholder="`工厂型号${$index + 1}`" :disabled="submitting" />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="130">
                <template #default="{ row }: any">
                  <el-input-number v-model="row.quantity" :controls="false" :precision="0" :min="0" :disabled="submitting" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="箱数" width="130">
                <template #default="{ row }: any">
                  <el-input-number v-model="row.cartonCount" :controls="false" :precision="0" :min="0" :disabled="submitting" style="width: 100%" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="72" align="center">
                <template #default="{ $index }: any">
                  <el-button link type="danger" :disabled="submitting || piDetailLoading" @click="removeItem($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-col>
          <el-col :span="24"><div class="section-title">订单要求</div></el-col>
          <el-col :span="8"><el-form-item label="下单日期"><el-date-picker v-model="form.orderDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="订单要求"><el-input v-model="form.orderRequirement" type="textarea" :rows="4" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="4" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="submitting" :disabled="piDetailLoading" @click="submitForm">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { margin-top: 4px; }
.section-title { margin: 4px 0 14px; padding: 8px 10px; border-left: 3px solid #409eff; background: #f5f7fa; color: #303133; font-size: 14px; font-weight: 600; }
.section-heading { display: flex; align-items: flex-start; gap: 12px; }
.section-heading .section-title { flex: 1; }
.section-heading .el-button { margin-top: 6px; }
.product-table { margin: -2px 0 16px; }
:deep(.si-dialog .el-dialog__body) { max-height: 78vh; overflow-y: auto; padding-top: 12px; }
</style>
