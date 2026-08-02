<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  pageStock,
  lowStockCount,
  pageMovements,
  recordMovement,
  updateSafetyStock,
  type StockRow,
  type StockMovement
} from '@/api/inventory'

const loading = ref(false)
const list = ref<StockRow[]>([])
const total = ref(0)
const lowCount = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', lowOnly: false })

const safetyVisible = ref(false)
const safetyTarget = ref<StockRow | null>(null)
const safetyValue = ref<number>(0)

function openSafetyStock(row: StockRow) {
  if (row.productId == null) {
    ElMessage.warning('该库存记录尚未关联产品资料，请先建立产品资料后再设置安全库存')
    return
  }
  safetyTarget.value = row
  safetyValue.value = Number(row.safetyStock || 0)
  safetyVisible.value = true
}

async function submitSafetyStock() {
  if (safetyTarget.value?.productId == null) return
  if (!Number.isFinite(safetyValue.value) || safetyValue.value < 0) {
    ElMessage.warning('安全库存不能小于 0')
    return
  }
  await updateSafetyStock(safetyTarget.value.productId, safetyValue.value)
  ElMessage.success('安全库存设置成功')
  safetyVisible.value = false
  await loadData()
}

const bizTypeMap: Record<string, string> = {
  PURCHASE_IN: '采购入库', SALE_OUT: '销售出库', BOM_OUT: '生产领料', ADJUST: '盘点调整', MANUAL: '手工'
}

async function loadData() {
  loading.value = true
  try {
    const [p, l] = await Promise.all([pageStock(query), lowStockCount()])
    list.value = p.data.records
    total.value = p.data.total
    lowCount.value = l.data.low
  } finally {
    loading.value = false
  }
}
function onSearch() { query.pageNum = 1; loadData() }

/* 出入库登记 */
const mvVisible = ref(false)
const mvForm = reactive<{ prodCode: string; direction: 'IN' | 'OUT'; qty?: number; bizType: string; relatedDocNo: string; moveDate: string; remark: string }>({
  prodCode: '', direction: 'IN', qty: undefined, bizType: 'MANUAL', relatedDocNo: '', moveDate: '', remark: ''
})
function openMove(row?: StockRow) {
  Object.assign(mvForm, { prodCode: row?.prodCode || '', direction: 'IN', qty: undefined, bizType: 'MANUAL', relatedDocNo: '', moveDate: '', remark: '' })
  mvVisible.value = true
}
async function submitMove() {
  if (!mvForm.prodCode || !mvForm.qty || mvForm.qty <= 0) { ElMessage.warning('请填写产品代码与数量'); return }
  await recordMovement({
    prodCode: mvForm.prodCode, direction: mvForm.direction, qty: mvForm.qty,
    bizType: mvForm.bizType, relatedDocNo: mvForm.relatedDocNo || undefined,
    moveDate: mvForm.moveDate || undefined, remark: mvForm.remark || undefined
  })
  ElMessage.success('登记成功')
  mvVisible.value = false
  loadData()
}

/* 流水抽屉 */
const mvListVisible = ref(false)
const mvCode = ref('')
const mvList = ref<StockMovement[]>([])
const mvTotal = ref(0)
const mvQuery = reactive({ pageNum: 1, pageSize: 20 })
async function openMovements(row: StockRow) {
  mvCode.value = row.prodCode
  mvQuery.pageNum = 1
  await loadMovements()
  mvListVisible.value = true
}
async function loadMovements() {
  const res = await pageMovements(mvCode.value, mvQuery)
  mvList.value = res.data.records
  mvTotal.value = res.data.total
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-alert v-if="lowCount > 0" type="warning" :closable="false" show-icon style="margin-bottom: 12px"
      :title="`有 ${lowCount} 个产品低于安全库存，请及时补货`" />

    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="产品代码/名称" clearable style="width: 200px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item><el-checkbox v-model="query.lowOnly">仅看预警</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button></el-form-item>
      </el-form>
      <div class="toolbar">
        <el-button v-permission="'inventory:manage'" type="primary" @click="openMove()">出入库登记</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column prop="prodCode" label="产品代码" width="140" />
        <el-table-column prop="prodName" label="产品名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="当前结存" width="120" align="left">
          <template #default="{ row }: any"><b :class="{ red: row.lowStock }">{{ Number(row.currentStock).toLocaleString() }}</b></template>
        </el-table-column>
        <el-table-column label="安全库存" width="110" align="left">
          <template #default="{ row }: any">{{ row.safetyStock != null ? Number(row.safetyStock).toLocaleString() : '-' }}</template>
        </el-table-column>
        <el-table-column label="预警" width="80">
          <template #default="{ row }: any"><el-tag v-if="row.lowStock" size="small" type="danger">低库存</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'inventory:manage'" link type="primary" @click="openMove(row)">出入库</el-button>
            <el-button link type="primary" @click="openMovements(row)">流水</el-button>
            <el-button v-permission="'inventory:manage'" link type="warning" @click="openSafetyStock(row)">安全库存</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="mvVisible" title="出入库登记" width="460px">
      <el-form label-width="90px">
        <el-form-item label="产品代码"><el-input v-model="mvForm.prodCode" /></el-form-item>
        <el-form-item label="方向"><el-radio-group v-model="mvForm.direction"><el-radio value="IN">入库</el-radio><el-radio value="OUT">出库</el-radio></el-radio-group></el-form-item>
        <el-form-item label="数量"><el-input-number v-model="mvForm.qty" :min="0" :precision="4" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="mvForm.bizType" style="width: 100%">
            <el-option label="手工" value="MANUAL" /><el-option label="采购入库" value="PURCHASE_IN" /><el-option label="销售出库" value="SALE_OUT" /><el-option label="生产领料" value="BOM_OUT" /><el-option label="盘点调整" value="ADJUST" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联单据"><el-input v-model="mvForm.relatedDocNo" placeholder="如 PO/出货单号" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="mvForm.moveDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="mvForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="mvVisible = false">取消</el-button><el-button type="primary" @click="submitMove">确定</el-button></template>
    </el-dialog>

    <el-dialog v-model="safetyVisible" title="设置安全库存" width="420px">
      <el-form label-width="100px">
        <el-form-item label="产品代码"><el-input :model-value="safetyTarget?.prodCode || ''" disabled /></el-form-item>
        <el-form-item label="产品名称"><el-input :model-value="safetyTarget?.prodName || ''" disabled /></el-form-item>
        <el-form-item label="当前库存"><el-input :model-value="String(safetyTarget?.currentStock ?? 0)" disabled /></el-form-item>
        <el-form-item label="安全库存">
          <el-input-number v-model="safetyValue" :min="0" :precision="4" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>
      <div style="color: #909399; font-size: 13px;">当前库存低于安全库存时触发预警；设置为 0 可关闭预警。</div>
      <template #footer><el-button @click="safetyVisible = false">取消</el-button><el-button type="primary" @click="submitSafetyStock">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="mvListVisible" :title="`${mvCode} 出入库流水`" width="720px">
      <el-table :data="mvList" border stripe size="small" max-height="50vh">
        <el-table-column prop="moveDate" label="日期" width="110" />
        <el-table-column label="方向" width="70"><template #default="{ row }: any"><el-tag size="small" :type="row.direction === 'IN' ? 'success' : 'warning'">{{ row.direction === 'IN' ? '入库' : '出库' }}</el-tag></template></el-table-column>
        <el-table-column prop="qty" label="数量" width="100" align="left" />
        <el-table-column label="业务类型" width="100"><template #default="{ row }: any">{{ bizTypeMap[row.bizType] || row.bizType }}</template></el-table-column>
        <el-table-column prop="relatedDocNo" label="关联单据" width="130" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="mvQuery.pageNum" v-model:page-size="mvQuery.pageSize" :total="mvTotal"
        layout="total, prev, pager, next" style="margin-top: 12px; justify-content: flex-end" @current-change="loadMovements" />
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar { margin-top: 4px; }
.red { color: #f56c6c; }
</style>
