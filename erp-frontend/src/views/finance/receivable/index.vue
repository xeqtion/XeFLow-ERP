<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageReceivable,
  receivableSummary,
  receivableAging,
  registerReceipt,
  type ReceivableRow,
  type ReceivableSummary,
  type CustomerAging
} from '@/api/receivable'

const loading = ref(false)
const list = ref<ReceivableRow[]>([])
const total = ref(0)
const summary = ref<ReceivableSummary[]>([])
const aging = ref<CustomerAging[]>([])
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; customerCode: string; status: string; overdueOnly: boolean; termDays: number }>({
  pageNum: 1, pageSize: 10, keyword: '', customerCode: '', status: '', overdueOnly: false, termDays: 30
})

const fmt = (n?: number) => (n == null ? '0.00' : Number(n).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
const statusMap: Record<string, { text: string; type: 'info' | 'warning' | 'success' }> = {
  UNPAID: { text: '未收', type: 'info' },
  PARTIAL: { text: '部分收', type: 'warning' },
  PAID: { text: '已结清', type: 'success' }
}
const documentCounts = computed(() => ({
  total: summary.value.reduce((sum, item) => sum + item.piCount, 0),
  open: summary.value.reduce((sum, item) => sum + item.openCount, 0)
}))

async function loadAll() {
  loading.value = true
  try {
    const [p, s] = await Promise.all([pageReceivable(query), receivableSummary(query)])
    list.value = p.data.records
    total.value = p.data.total
    summary.value = s.data
  } finally {
    loading.value = false
  }
}
async function loadAging() {
  aging.value = (await receivableAging(query.termDays)).data
}
function onSearch() {
  query.pageNum = 1
  loadAll()
  loadAging()
}
function onReset() {
  Object.assign(query, { pageNum: 1, keyword: '', customerCode: '', status: '', overdueOnly: false, termDays: 30 })
  loadAll(); loadAging()
}

/* 登记收款 */
const rcVisible = ref(false)
const rcForm = reactive<{ piId?: number | string; piNo: string; balance: number; currency: string; amount?: number; receiptType: 'PRE_RECEIVE' | 'RECEIVABLE'; depositAmount: number; depositReceivedAmount: number; entryDate: string; remark: string }>({
  piId: undefined, piNo: '', balance: 0, currency: '', amount: undefined, receiptType: 'RECEIVABLE', depositAmount: 0, depositReceivedAmount: 0, entryDate: '', remark: ''
})
function openReceipt(row: ReceivableRow) {
  Object.assign(rcForm, {
    piId: row.piId, piNo: row.piNo, balance: row.balance, currency: row.currency || '',
    amount: row.balance > 0 ? row.balance : undefined, receiptType: 'RECEIVABLE',
    depositAmount: row.depositAmount || 0, depositReceivedAmount: row.depositReceivedAmount || 0,
    entryDate: '', remark: ''
  })
  rcVisible.value = true
}
watch(() => rcForm.receiptType, (type) => {
  rcForm.amount = type === 'PRE_RECEIVE'
    ? Math.max(0, Math.min(rcForm.balance, rcForm.depositAmount - rcForm.depositReceivedAmount))
    : rcForm.balance
})
async function submitReceipt() {
  if (!rcForm.piId || !rcForm.amount || rcForm.amount <= 0) {
    ElMessage.warning('请输入收款金额')
    return
  }
  await registerReceipt({ piId: rcForm.piId, amount: rcForm.amount, receiptType: rcForm.receiptType, entryDate: rcForm.entryDate || undefined, remark: rcForm.remark || undefined })
  ElMessage.success('收款已登记')
  rcVisible.value = false
  loadAll(); loadAging()
}

onMounted(() => { loadAll(); loadAging() })
</script>

<template>
  <div class="app-container">
    <!-- 汇总卡片 -->
    <div class="summary-scope">财务合计仅支持统计 CNY 和 USD，其他币种仍保留在明细与账龄中。</div>
    <el-row :gutter="12" class="summary-row" v-if="summary.length">
      <el-col :xs="12" :sm="6"><el-card shadow="hover" :body-style="{ padding: '12px' }">
        <div class="s-label">应收合计</div>
        <div v-for="item in summary" :key="item.currency" class="s-amount">{{ fmt(item.totalReceivable) }} <small>{{ item.currency }}</small></div>
      </el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" :body-style="{ padding: '12px' }">
        <div class="s-label">已收合计</div>
        <div v-for="item in summary" :key="item.currency" class="s-amount green">{{ fmt(item.totalReceived) }} <small>{{ item.currency }}</small></div>
      </el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" :body-style="{ padding: '12px' }">
        <div class="s-label">未收余额</div>
        <div v-for="item in summary" :key="item.currency" class="s-amount orange">{{ fmt(item.totalBalance) }} <small>{{ item.currency }}</small></div>
      </el-card></el-col>
      <el-col :xs="12" :sm="6"><el-card shadow="hover" :body-style="{ padding: '12px' }">
        <div class="s-label">逾期未收</div>
        <div v-for="item in summary" :key="item.currency" class="s-amount red">{{ fmt(item.overdueBalance) }} <small>{{ item.currency }}</small></div>
        <div class="s-count">{{ documentCounts.open }}/{{ documentCounts.total }} 张未结清</div>
      </el-card></el-col>
    </el-row>

    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="PI号"><el-input v-model="query.keyword" placeholder="PI 单号" clearable style="width: 150px" @keyup.enter="onSearch" /></el-form-item>
        <el-form-item label="客户ID"><el-input v-model="query.customerCode" clearable style="width: 140px" placeholder="如 AU9104" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="未收" value="UNPAID" /><el-option label="部分收" value="PARTIAL" /><el-option label="已结清" value="PAID" />
          </el-select>
        </el-form-item>
        <el-form-item label="账期(天)"><el-input-number v-model="query.termDays" :min="0" :controls="false" style="width: 90px" /></el-form-item>
        <el-form-item><el-checkbox v-model="query.overdueOnly">仅逾期</el-checkbox></el-form-item>
        <el-form-item><el-button type="primary" @click="onSearch">查询</el-button><el-button @click="onReset">重置</el-button></el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="piNo" label="PI单号" width="130" />
        <el-table-column prop="customerCode" label="客户ID" width="130" />
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="currency" label="币种" width="70" />
        <el-table-column prop="signDate" label="签约日" width="110" />
        <el-table-column label="应收" width="120" align="left"><template #default="{ row }: any">{{ fmt(row.totalAmount) }}</template></el-table-column>
        <el-table-column label="约定定金" width="120" align="left"><template #default="{ row }: any">{{ fmt(row.depositAmount) }}</template></el-table-column>
        <el-table-column label="已收定金" width="120" align="left"><template #default="{ row }: any"><span class="green">{{ fmt(row.depositReceivedAmount) }}</span></template></el-table-column>
        <el-table-column label="已收" width="120" align="left"><template #default="{ row }: any"><span class="green">{{ fmt(row.receivedAmount) }}</span></template></el-table-column>
        <el-table-column label="未收" width="120" align="left"><template #default="{ row }: any"><span class="orange">{{ fmt(row.balance) }}</span></template></el-table-column>
        <el-table-column label="账龄" width="90" align="left"><template #default="{ row }: any">
          <span :class="{ red: row.overdue }">{{ row.ageDays ?? '-' }}天</span>
        </template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }: any">
          <el-tag size="small" :type="statusMap[row.settleStatus]?.type">{{ statusMap[row.settleStatus]?.text }}</el-tag>
          <el-tag v-if="row.overdue" size="small" type="danger" style="margin-left:4px">逾期</el-tag>
        </template></el-table-column>
        <el-table-column label="操作" width="100" fixed="right"><template #default="{ row }: any">
          <el-button v-permission="'finance:create'" link type="primary" :disabled="row.balance <= 0" @click="openReceipt(row)">登记收款</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end" @current-change="loadAll" @size-change="onSearch" />
    </el-card>

    <!-- 客户账龄 -->
    <el-card shadow="never" style="margin-top: 12px">
      <template #header><span>客户账龄分析（按原币，账期 {{ query.termDays }} 天）</span></template>
      <el-table :data="aging" border stripe size="small">
        <el-table-column prop="customerCode" label="客户ID" width="130" />
        <el-table-column prop="customerName" label="客户" min-width="160" show-overflow-tooltip />
        <el-table-column prop="currency" label="币种" width="80" />
        <el-table-column label="0-30天" align="left"><template #default="{ row }: any">{{ fmt(row.d0_30) }}</template></el-table-column>
        <el-table-column label="31-60天" align="left"><template #default="{ row }: any">{{ fmt(row.d31_60) }}</template></el-table-column>
        <el-table-column label="61-90天" align="left"><template #default="{ row }: any">{{ fmt(row.d61_90) }}</template></el-table-column>
        <el-table-column label="90天以上" align="left"><template #default="{ row }: any"><span class="red">{{ fmt(row.d90plus) }}</span></template></el-table-column>
        <el-table-column label="未收合计" align="left"><template #default="{ row }: any"><b class="orange">{{ fmt(row.totalBalance) }}</b></template></el-table-column>
      </el-table>
    </el-card>

    <!-- 登记收款 -->
    <el-dialog v-model="rcVisible" title="登记收款核销" width="460px">
      <el-form label-width="90px">
        <el-form-item label="PI单号"><el-input :model-value="rcForm.piNo" disabled /></el-form-item>
        <el-form-item label="收款类型">
          <el-radio-group v-model="rcForm.receiptType">
            <el-radio-button value="PRE_RECEIVE">定金收款</el-radio-button>
            <el-radio-button value="RECEIVABLE">普通收款</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="定金情况"><span>{{ fmt(rcForm.depositReceivedAmount) }} / {{ fmt(rcForm.depositAmount) }} {{ rcForm.currency }}</span></el-form-item>
        <el-form-item label="未收余额"><span class="orange">{{ fmt(rcForm.balance) }} {{ rcForm.currency }}</span></el-form-item>
        <el-form-item label="收款金额"><el-input-number v-model="rcForm.amount" :min="0" :precision="2" :controls="false" style="width: 100%" /></el-form-item>
        <el-form-item label="收款日期"><el-date-picker v-model="rcForm.entryDate" type="date" value-format="YYYY-MM-DD" placeholder="默认今天" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="rcForm.remark" type="textarea" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="rcVisible = false">取消</el-button><el-button type="primary" @click="submitReceipt">确定</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-row { margin-bottom: 12px; }
.summary-scope { color: #909399; font-size: 12px; margin-bottom: 8px; }
.s-label { color: #909399; font-size: 12px; }
.s-amount { font-size: 20px; font-weight: 700; margin-top: 4px; }
.s-amount small { font-size: 12px; font-weight: 500; color: #909399; }
.s-count { color: #909399; font-size: 12px; margin-top: 2px; }
.green { color: #67c23a; }
.orange { color: #e6a23c; }
.red { color: #f56c6c; }
</style>
