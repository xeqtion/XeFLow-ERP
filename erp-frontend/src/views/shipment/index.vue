<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageShipment, updateShipment, deleteShipment,
  downloadPackingList, downloadShipmentDoc, type Shipment
} from '@/api/shipment'

const loading = ref(false)
const list = ref<Shipment[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<Shipment>(emptyForm())
const statusMap: Record<string, { text: string; type: 'info' | 'primary' | 'success' | 'warning' }> = {
  DRAFT: { text: '草稿', type: 'info' },
  PARTIAL_SHIPPED: { text: '部分出货', type: 'warning' },
  FULL_SHIPPED: { text: '全部出货', type: 'success' },
  COMPLETED: { text: '全部出货', type: 'success' }
}

function emptyForm(): Shipment {
  return { shipmentNo: '', siId: undefined, piId: undefined, shipDate: '', qty: undefined, status: 'PARTIAL_SHIPPED', remark: '' }
}
const rules: FormRules = {}

async function loadData() {
  loading.value = true
  try { const res = await pageShipment(query); list.value = res.data.records; total.value = res.data.total }
  finally { loading.value = false }
}
function onSearch() { query.pageNum = 1; loadData() }
function onReset() { query.keyword = ''; onSearch() }
function openEdit(row: Shipment) { Object.assign(form, row); dialogTitle.value = '修改出货单'; dialogVisible.value = true }
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    await updateShipment(form)
    ElMessage.success('修改成功')
    dialogVisible.value = false; loadData()
  })
}
function onPackingList(row: Shipment) { downloadPackingList(row.id!, row.shipmentNo) }
function onShipmentDoc(row: Shipment) { downloadShipmentDoc(row.id!, row.shipmentNo) }
function canDelete(row: Shipment) { return ['DRAFT', 'FULL_SHIPPED', 'COMPLETED'].includes(row.status || '') }
async function onDelete(row: Shipment) {
  await ElMessageBox.confirm(`确认删除出货单「${row.shipmentNo}」？`, '提示', { type: 'warning' })
  await deleteShipment(row.id!)
  ElMessage.success('删除成功')
  loadData()
}
onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="出货单号" clearable style="width: 200px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="onReset">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="shipmentNo" label="出货单号" width="150" />
        <el-table-column prop="siNo" label="关联出货指示" width="170"><template #default="{ row }: any">{{ row.siNo || '-' }}</template></el-table-column>
        <el-table-column prop="piId" label="关联PI" width="160" />
        <el-table-column prop="shipDate" label="出货日期" width="120" />
        <el-table-column prop="qty" label="数量" width="90" align="left" />
        <el-table-column prop="containerType" label="柜型" width="80" />
        <el-table-column prop="blNo" label="提单号" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }: any">
            <el-tag size="small" :type="statusMap[row.status]?.type || 'warning'">
              {{ statusMap[row.status]?.text || '部分出货' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'shipment:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'shipment:export'" link type="primary" @click="onShipmentDoc(row)">出货单</el-button>
            <el-button v-permission="'shipment:export'" link type="primary" @click="onPackingList(row)">装箱单</el-button>
            <el-button v-if="canDelete(row)" v-permission="'shipment:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="760px" top="6vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="105px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="出货单号"><el-input v-model="form.shipmentNo" disabled placeholder="保存时自动生成" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="关联PI"><el-input :model-value="String(form.piId || '-')" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出货指示单号"><el-input :model-value="form.siNo || '-'" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出货日期"><el-date-picker v-model="form.shipDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="数量"><el-input-number v-model="form.qty" :controls="false" :precision="4" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="出货状态"><el-input :model-value="statusMap[form.status || '']?.text || '部分出货'" disabled /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="箱数"><el-input-number v-model="form.packageCount" :min="0" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="毛重(kg)"><el-input-number v-model="form.grossWeight" :min="0" :precision="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="净重(kg)"><el-input-number v-model="form.netWeight" :min="0" :precision="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="体积(CBM)"><el-input-number v-model="form.volumeCbm" :min="0" :precision="3" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="柜型"><el-input v-model="form.containerType" placeholder="如 40HQ" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="柜号"><el-input v-model="form.containerNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="提单号"><el-input v-model="form.blNo" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitForm">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>.toolbar { margin-top: 4px; }</style>
