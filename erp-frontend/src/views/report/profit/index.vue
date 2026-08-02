<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import ReportChart from '@/components/ReportChart.vue'
import {
  profitReport, exportProfit, downloadStatement, downloadPerformance, type ProfitRow
} from '@/api/report'

const loading = ref(false)
const rows = ref<ProfitRow[]>([])
const dateRange = ref<[string, string] | null>(null)
const query = reactive({ dimension: 'MONTH', startDate: '', endDate: '' })

// 月对账单
const stmtCustomerCode = ref<string>('')
const stmtMonth = ref<string>('')

function syncDate() {
  query.startDate = dateRange.value?.[0] || ''
  query.endDate = dateRange.value?.[1] || ''
}
async function loadData() {
  loading.value = true
  syncDate()
  try {
    rows.value = (await profitReport(query)).data
  } finally {
    loading.value = false
  }
}
function onExport() { exportProfit(query) }
function onPerformance() { syncDate(); downloadPerformance(query) }
function onStatement() {
  if (!stmtCustomerCode.value || !stmtMonth.value) {
    ElMessage.warning('请填写客户ID与月份')
    return
  }
  downloadStatement(stmtCustomerCode.value, stmtMonth.value)
}

const totals = computed(() => ({
  sales: rows.value.reduce((s, r) => s + Number(r.sales || 0), 0),
  purchase: rows.value.reduce((s, r) => s + Number(r.purchase || 0), 0),
  profit: rows.value.reduce((s, r) => s + Number(r.profit || 0), 0),
  margin: rows.value.reduce((s, r) => s + Number(r.sales || 0), 0)
    ? rows.value.reduce((s, r) => s + Number(r.profit || 0), 0)
      / rows.value.reduce((s, r) => s + Number(r.sales || 0), 0) * 100
    : 0
}))

const analysisRows = computed(() => rows.value.map(row => ({
  ...row,
  margin: Number(row.sales || 0) ? Number(row.profit || 0) / Number(row.sales) * 100 : 0
})))

const option = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['销售额(CNY)', '采购额(CNY)', '利润(CNY)', '利润率'] },
  grid: { left: 24, right: 32, top: 44, bottom: 24, containLabel: true },
  xAxis: { type: 'category', data: rows.value.map((r) => r.period) },
  yAxis: [
    { type: 'value', name: 'CNY' },
    { type: 'value', name: '%', nameGap: 12, axisLabel: { formatter: '{value}%', margin: 12 } }
  ],
  series: [
    { name: '销售额(CNY)', type: 'bar', data: rows.value.map((r) => r.sales), itemStyle: { color: '#67c23a' } },
    { name: '采购额(CNY)', type: 'bar', data: rows.value.map((r) => r.purchase), itemStyle: { color: '#409eff' } },
    { name: '利润(CNY)', type: 'line', data: rows.value.map((r) => r.profit), itemStyle: { color: '#f56c6c' } },
    { name: '利润率', type: 'line', yAxisIndex: 1, smooth: true, data: analysisRows.value.map((r) => r.margin), itemStyle: { color: '#7c5cfc' } }
  ]
}))

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="维度">
          <el-select v-model="query.dimension" style="width: 120px" @change="loadData">
            <el-option label="日" value="DAY" /><el-option label="周" value="WEEK" />
            <el-option label="月" value="MONTH" /><el-option label="季度" value="QUARTER" /><el-option label="年" value="YEAR" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期范围">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="起" end-placeholder="止" style="width: 240px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button v-permission="'report:profit'" @click="onExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="12" class="totals">
        <el-col :xs="12" :sm="6"><el-statistic title="销售总额(CNY)" :value="totals.sales" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="采购总额(CNY)" :value="totals.purchase" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="利润总额(CNY)" :value="totals.profit" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="综合利润率(%)" :value="totals.margin" :precision="1" /></el-col>
      </el-row>

      <div v-loading="loading">
        <ReportChart :option="option" />
        <el-table :data="analysisRows" border stripe size="small" style="margin-top: 12px">
          <el-table-column prop="period" label="周期" />
          <el-table-column prop="sales" label="销售额(CNY)" align="left" />
          <el-table-column prop="purchase" label="采购额(CNY)" align="left" />
          <el-table-column prop="profit" label="利润(CNY)" align="left" />
          <el-table-column label="利润率" align="left"><template #default="{ row }: any">
            <span :class="row.margin >= 0 ? 'positive' : 'negative'">{{ row.margin.toFixed(1) }}%</span>
          </template></el-table-column>
        </el-table>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <template #header>单据生成</template>
      <el-row :gutter="20">
        <el-col :span="12">
          <div class="doc-block">
            <div class="doc-title">外贸业绩报告</div>
            <div class="doc-desc">按当前日期范围生成（含按月明细）。</div>
            <el-button v-permission="'report:profit'" type="primary" @click="onPerformance">生成业绩报告</el-button>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="doc-block">
            <div class="doc-title">销售月对账单</div>
            <el-space>
              <el-input v-model="stmtCustomerCode" placeholder="如 AU9104" style="width: 130px" />
              <el-date-picker v-model="stmtMonth" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 150px" />
              <el-button v-permission="'report:profit'" type="primary" @click="onStatement">生成对账单</el-button>
            </el-space>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<style scoped>
.totals { margin: 8px 0 4px; }
.positive { color: #19a974; }
.negative { color: #e5484d; }
.doc-block { padding: 8px 0; }
.doc-title { font-weight: 600; margin-bottom: 4px; }
.doc-desc { color: #909399; font-size: 12px; margin-bottom: 8px; }
</style>
