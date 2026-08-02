<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageOem, createOem, updateOem, deleteOem,
  listOemFiles, uploadOemFile, downloadOemFile, deleteOemFile,
  type OrderOem, type OrderOemFile
} from '@/api/orderOem'
import { getSalesPi, pageSalesPi, type SalesPi, type SalesPiItem } from '@/api/salesPi'

const loading = ref(false)
const list = ref<OrderOem[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<OrderOem>(emptyForm())
const piOptions = ref<SalesPi[]>([])
const piItemOptions = ref<SalesPiItem[]>([])
const fileDialogVisible = ref(false)
const fileLoading = ref(false)
const fileTarget = ref<OrderOem | null>(null)
const fileRows = ref<OrderOemFile[]>([])
const fileRemark = ref('')

function emptyForm(): OrderOem {
  return {
    oemNo: '',
    piId: undefined,
    piNo: '',
    customerId: undefined,
    customerCode: '',
    customerName: '',
    productId: undefined,
    productModel: '',
    factoryModel: '',
    requirement: '',
    status: 'DRAFT'
  }
}

const rules: FormRules = {
  piId: [{ required: true, message: '请选择关联 PI', trigger: 'change' }],
  requirement: [{ required: true, message: '请输入 OEM 要求', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try { const res = await pageOem(query); list.value = res.data.records; total.value = res.data.total }
  finally { loading.value = false }
}

async function loadPiOptions() {
  const res = await pageSalesPi({ pageNum: 1, pageSize: 1000 })
  piOptions.value = res.data.records.filter((item) => item.id !== undefined && item.id !== null)
}

function onSearch() { query.pageNum = 1; loadData() }
function onReset() { query.keyword = ''; onSearch() }

function openCreate() {
  Object.assign(form, emptyForm())
  form.id = undefined
  piItemOptions.value = []
  dialogTitle.value = '新建 OEM'
  dialogVisible.value = true
}

async function openEdit(row: OrderOem) {
  Object.assign(form, emptyForm(), row)
  piItemOptions.value = []
  if (row.piId) {
    await loadPiDetail(row.piId, false)
  }
  dialogTitle.value = '修改 OEM'
  dialogVisible.value = true
}

function piOptionLabel(pi: SalesPi) {
  return `${pi.piNo} | ${pi.customerName || pi.customerCode || pi.customerId || ''}`
}

function piItemLabel(item: SalesPiItem) {
  return `${item.productId || ''} | ${firstText(item.productModel, item.productName, item.prodCode)} | ${firstText(item.factoryModel)}`
}

async function onPiChange(piId: number | string | undefined) {
  form.piNo = ''
  form.customerId = undefined
  form.customerCode = ''
  form.customerName = ''
  form.productId = undefined
  form.productModel = ''
  form.factoryModel = ''
  piItemOptions.value = []
  if (!piId) return
  await loadPiDetail(piId, true)
}

async function loadPiDetail(piId: number | string, autoPickProduct: boolean) {
  const res = await getSalesPi(piId)
  const pi = res.data
  form.piId = pi.id
  form.piNo = pi.piNo
  form.customerId = pi.customerId
  form.customerCode = pi.customerCode || ''
  form.customerName = firstText(pi.customerName, pi.customerCode)
  piItemOptions.value = pi.items || []
  if (autoPickProduct && piItemOptions.value.length === 1) {
    applyProduct(piItemOptions.value[0])
  }
}

function onProductChange(productId: number | string | undefined) {
  const item = piItemOptions.value.find((it) => String(it.productId) === String(productId))
  if (item) applyProduct(item)
}

function applyProduct(item: SalesPiItem) {
  form.productId = item.productId
  form.productModel = firstText(item.productModel, item.productName, item.prodCode)
  form.factoryModel = firstText(item.factoryModel)
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) { await updateOem(form); ElMessage.success('修改成功') }
    else { await createOem(form); ElMessage.success('创建成功') }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row: OrderOem) {
  await ElMessageBox.confirm(`确认删除 OEM「${row.oemNo}」？`, '提示', { type: 'warning' })
  await deleteOem(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

async function openFiles(row: OrderOem) {
  fileTarget.value = row
  fileRemark.value = ''
  fileDialogVisible.value = true
  await loadFiles()
}

async function loadFiles() {
  if (!fileTarget.value?.id) return
  fileLoading.value = true
  try {
    const res = await listOemFiles(fileTarget.value.id)
    fileRows.value = res.data
  } finally {
    fileLoading.value = false
  }
}

async function customFileUpload(opt: any) {
  if (!fileTarget.value?.id) return
  const file = opt.file as File
  const max = 50 * 1024 * 1024
  if (file.size > max) {
    ElMessage.warning('单个附件不能超过 50MB')
    opt.onError?.(new Error('文件超过 50MB'))
    return
  }
  try {
    await uploadOemFile(fileTarget.value.id, file, fileRemark.value || undefined)
    ElMessage.success(`已上传 ${file.name}`)
    fileRemark.value = ''
    opt.onSuccess?.({})
    await loadFiles()
  } catch (error) {
    opt.onError?.(error)
  }
}

function onDownloadFile(row: OrderOemFile) {
  downloadOemFile(row.id, row.fileName)
}

async function onDeleteFile(row: OrderOemFile) {
  await ElMessageBox.confirm(`确认删除附件「${row.fileName}」？`, '提示', { type: 'warning' })
  await deleteOemFile(row.id)
  ElMessage.success('删除成功')
  await loadFiles()
}

function formatSize(size?: number) {
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function statusText(status?: string) {
  return status === 'CONFIRMED' ? '已确认' : '草稿'
}

function statusType(status?: string) {
  return status === 'CONFIRMED' ? 'success' : 'info'
}

function firstText(...values: Array<string | number | undefined | null>) {
  for (const value of values) {
    const text = value === undefined || value === null ? '' : String(value).trim()
    if (text) return text
  }
  return ''
}

onMounted(() => {
  loadPiOptions()
  loadData()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="OEM单号 / PI单号 / 客户 / 型号" clearable style="width: 280px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="onReset">重置</el-button></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button v-permission="'order_oem:create'" type="primary" @click="openCreate">新建</el-button>
      </div>
      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="oemNo" label="OEM单号" width="150" />
        <el-table-column prop="piNo" label="关联PI单号" width="170" show-overflow-tooltip />
        <el-table-column prop="customerCode" label="客户ID" width="130" />
        <el-table-column prop="customerName" label="客户名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="productId" label="产品ID" width="130" />
        <el-table-column prop="productModel" label="产品型号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="factoryModel" label="工厂型号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="requirement" label="OEM要求" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }: any">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'order_oem:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'order_oem:list'" link type="primary" @click="openFiles(row)">附件</el-button>
            <el-button v-permission="'order_oem:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="OEM单号"><el-input v-model="form.oemNo" disabled placeholder="保存后自动生成" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="关联PI单号" prop="piId">
              <el-select v-model="form.piId" filterable clearable style="width: 100%" @change="onPiChange">
                <el-option v-for="pi in piOptions" :key="pi.id" :label="piOptionLabel(pi)" :value="pi.id!" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="客户ID"><el-input v-model="form.customerCode" disabled /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户名称"><el-input v-model="form.customerName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="产品ID">
              <el-select v-model="form.productId" filterable clearable style="width: 100%" @change="onProductChange">
                <el-option v-for="item in piItemOptions" :key="String(item.id || item.productId)" :label="piItemLabel(item)" :value="item.productId!" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="产品型号"><el-input v-model="form.productModel" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="工厂型号"><el-input v-model="form.factoryModel" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-select v-model="form.status" style="width: 100%"><el-option label="草稿" value="DRAFT" /><el-option label="已确认" value="CONFIRMED" /></el-select></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="OEM要求" prop="requirement"><el-input v-model="form.requirement" type="textarea" :rows="4" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitForm">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="fileDialogVisible" title="OEM 附件" width="760px" top="8vh">
      <div class="file-toolbar">
        <el-input v-model="fileRemark" placeholder="附件备注(选填)" clearable style="width: 220px" />
        <el-upload :show-file-list="false" :http-request="customFileUpload">
          <el-button v-permission="'order_oem:update'" type="primary">添加附件</el-button>
        </el-upload>
        <span class="hint">单文件上限 50MB</span>
      </div>
      <el-table v-loading="fileLoading" :data="fileRows" border stripe>
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }: any">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="110" />
        <el-table-column prop="createTime" label="上传时间" width="160" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click="onDownloadFile(row)">下载</el-button>
            <el-button v-permission="'order_oem:update'" link type="danger" @click="onDeleteFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { margin-top: 4px; }
.file-toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.hint { color: #909399; font-size: 12px; }
</style>
