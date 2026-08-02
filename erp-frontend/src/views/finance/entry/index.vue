<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Document, Download, Upload } from '@element-plus/icons-vue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import CurrencySelect from '@/components/CurrencySelect.vue'
import {
  financeTypes, pageFinance, financeSummary, createFinance, updateFinance,
  deleteFinance, importFinance, exportFinance, downloadFinanceTemplate,
  type FinanceEntry, type FinanceType, type FinanceSummary
} from '@/api/finance'

const loading = ref(false)
const list = ref<FinanceEntry[]>([])
const total = ref(0)
const types = ref<FinanceType[]>([])
const summary = ref<FinanceSummary[]>([])
const dateRange = ref<[string, string] | null>(null)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', type: '', customerCode: '', startDate: '', endDate: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<FinanceEntry>(emptyForm())
const importVisible = ref(false)

function emptyForm(): FinanceEntry {
  return { type: '', direction: '', customerCode: '', amount: undefined, currency: 'USD', relatedDocType: '', relatedDocNo: '', entryDate: '', remark: '' }
}
const rules: FormRules = {
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
}

const typeLabel = computed(() => Object.fromEntries(types.value.map((t) => [t.code, t.label])))

function syncDate() {
  query.startDate = dateRange.value?.[0] || ''
  query.endDate = dateRange.value?.[1] || ''
}

async function loadData() {
  loading.value = true
  syncDate()
  try {
    const [pageRes, sumRes] = await Promise.all([
      pageFinance(query),
      financeSummary({ type: query.type, customerCode: query.customerCode, startDate: query.startDate, endDate: query.endDate })
    ])
    list.value = pageRes.data.records
    total.value = pageRes.data.total
    summary.value = sumRes.data
  } finally {
    loading.value = false
  }
}
function onSearch() { query.pageNum = 1; loadData() }
function onReset() { query.keyword = ''; query.type = ''; query.customerCode = ''; dateRange.value = null; onSearch() }

function onTypeChange(code: string) {
  const t = types.value.find((x) => x.code === code)
  if (t) form.direction = t.direction
}
function openCreate() { Object.assign(form, emptyForm()); form.id = undefined; dialogTitle.value = '新建收付款'; dialogVisible.value = true }
function openEdit(row: FinanceEntry) { Object.assign(form, row); dialogTitle.value = '修改收付款'; dialogVisible.value = true }
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.id) { await updateFinance(form); ElMessage.success('修改成功') }
    else { await createFinance(form); ElMessage.success('创建成功') }
    dialogVisible.value = false; loadData()
  })
}
async function onDelete(row: FinanceEntry) {
  await ElMessageBox.confirm('确认删除该收付款记录？', '提示', { type: 'warning' })
  await deleteFinance(row.id!); ElMessage.success('删除成功'); loadData()
}
function onExport() { exportFinance({ keyword: query.keyword, type: query.type, customerCode: query.customerCode, startDate: query.startDate, endDate: query.endDate }) }

onMounted(async () => {
  types.value = (await financeTypes()).data
  loadData()
})
</script>

<template>
  <div class="app-container">
    <!-- 汇总卡片 -->
    <div class="summary-scope">财务合计仅支持统计 CNY 和 USD，其他币种仍保留在流水明细中。</div>
    <el-row :gutter="12" class="summary-row">
      <el-col v-for="s in summary" :key="`${s.direction}-${s.currency}`" :xs="12" :sm="8" :lg="4">
        <el-card shadow="hover" class="summary-card" :body-style="{ padding: '12px' }">
          <div class="s-label">
            {{ s.direction === 'in' ? '收入' : '支出' }}
            <el-tag size="small" :type="s.direction === 'in' ? 'success' : 'warning'">{{ s.direction === 'in' ? '收' : '付' }}</el-tag>
          </div>
          <div class="s-amount">{{ Number(s.totalAmount).toFixed(2) }} <span class="s-base">{{ s.currency || 'CNY' }}</span></div>
          <div class="s-count">{{ s.count }} 笔</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="类型">
          <el-select v-model="query.type" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="t in types" :key="t.code" :label="t.label" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户ID">
          <el-input v-model="query.customerCode" style="width: 150px" placeholder="如 AU9104" clearable />
        </el-form-item>
        <el-form-item label="入账日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="起" end-placeholder="止" style="width: 240px" />
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" placeholder="流水号/单据号" clearable style="width: 160px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'finance:create'" type="primary" @click="openCreate">新建</el-button>
        <el-button v-permission="'finance:import'" :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button v-permission="'finance:export'" :icon="Document" @click="downloadFinanceTemplate">模板</el-button>
        <el-button v-permission="'finance:export'" :icon="Download" @click="onExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="entryNo" label="流水号" width="130" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }: any">{{ typeLabel[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column label="方向" width="70">
          <template #default="{ row }: any">
            <el-tag size="small" :type="row.direction === 'in' ? 'success' : 'warning'">{{ row.direction === 'in' ? '收' : '付' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerCode" label="客户ID" width="150" />
        <el-table-column prop="amount" label="金额" width="120" align="left" />
        <el-table-column prop="currency" label="币种" width="70" />
        <el-table-column prop="relatedDocNo" label="关联单据" width="130" show-overflow-tooltip />
        <el-table-column prop="entryDate" label="入账日期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'finance:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'finance:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadData" @size-change="onSearch" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="类型" prop="type">
            <el-select v-model="form.type" style="width: 100%" @change="onTypeChange">
              <el-option v-for="t in types" :key="t.code" :label="t.label" :value="t.code" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="方向">
            <el-select v-model="form.direction" style="width: 100%"><el-option label="收 (进账)" value="in" /><el-option label="付 (出账)" value="out" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item label="客户ID"><el-input v-model="form.customerCode" placeholder="如 AU9104" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="金额" prop="amount"><el-input-number v-model="form.amount" :precision="4" :controls="false" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="币种"><CurrencySelect v-model="form.currency" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="流水号"><el-input v-model="form.entryNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关联单据类型"><el-input v-model="form.relatedDocType" placeholder="如 PI / PO" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="关联单据号"><el-input v-model="form.relatedDocNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="入账日期"><el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" @click="submitForm">确定</el-button></template>
    </el-dialog>

    <ImportExcelDialog v-model="importVisible" title="导入收付款" :import-api="importFinance" :download-template="downloadFinanceTemplate" @success="loadData" />
  </div>
</template>

<style scoped>
.summary-row { margin-bottom: 12px; }
.summary-scope { color: #909399; font-size: 12px; margin-bottom: 8px; }
.summary-card { margin-bottom: 12px; }
.s-label { font-size: 13px; color: #606266; display: flex; justify-content: space-between; align-items: center; }
.s-amount { font-size: 20px; font-weight: 600; margin: 4px 0; }
.s-count { font-size: 12px; color: #909399; }
.s-base { font-size: 12px; color: #909399; font-weight: 400; }
.toolbar { margin-top: 4px; }
</style>
