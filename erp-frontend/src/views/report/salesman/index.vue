<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import ReportChart from '@/components/ReportChart.vue'
import {
  salesBySalesman,
  salesTrendBySalesman,
  salesPerformanceDetails,
  type SalesmanRow,
  type SalesmanTrendRow,
  type SalesPerformanceDetail
} from '@/api/report'

const loading = ref(false)
const rows = ref<SalesmanRow[]>([])
const trendRows = ref<SalesmanTrendRow[]>([])
const range = ref<[string, string] | null>(null)
const trendDimension = ref<'QUARTER' | 'YEAR'>('QUARTER')

const detailsVisible = ref(false)
const detailsLoading = ref(false)
const details = ref<SalesPerformanceDetail[]>([])
const selectedSalesman = ref('')

const statusMap: Record<string, string> = {
  CONFIRMED: '已确认', ORDERED: '已下单', SHIPPED: '已出货', COMPLETED: '已完成', CLOSED: '已完成'
}

const fmt = (value?: number) => Number(value || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const metrics = computed(() => {
  const revenue = rows.value.reduce((sum, row) => sum + Number(row.totalAmount || 0), 0)
  const orders = rows.value.reduce((sum, row) => sum + Number(row.orderCount || 0), 0)
  return {
    revenue,
    orders,
    salesmen: rows.value.length,
    average: rows.value.length ? revenue / rows.value.length : 0
  }
})

const rankingOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis', valueFormatter: (value: any) => `${fmt(Number(value))} CNY` },
  grid: { left: 90, right: 24, top: 20, bottom: 34 },
  xAxis: { type: 'value' },
  yAxis: { type: 'category', data: [...rows.value].reverse().map(row => row.salesman) },
  series: [{
    name: '销售额',
    type: 'bar',
    data: [...rows.value].reverse().map(row => row.totalAmount),
    itemStyle: { color: '#2f6fed' },
    barMaxWidth: 28
  }]
}))

const shareOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}<br/>{c} CNY · {d}%' },
  legend: { type: 'scroll', orient: 'vertical', right: 8, top: 'middle' },
  series: [{
    name: '营收占比',
    type: 'pie',
    radius: ['46%', '70%'],
    center: ['38%', '50%'],
    avoidLabelOverlap: true,
    label: { formatter: '{b}\n{d}%' },
    data: rows.value.map(row => ({ name: row.salesman, value: Number(row.totalAmount || 0) }))
  }]
}))

const trendOption = computed<EChartsOption>(() => {
  const periods = [...new Set(trendRows.value.map(row => row.period))].sort()
  const salesmen = [...new Set(trendRows.value.map(row => row.salesman))]
  return {
    tooltip: { trigger: 'axis', valueFormatter: (value: any) => `${fmt(Number(value))} CNY` },
    legend: { type: 'scroll', data: salesmen },
    grid: { left: 70, right: 24, top: 52, bottom: 42 },
    xAxis: { type: 'category', data: periods },
    yAxis: { type: 'value', name: 'CNY' },
    series: salesmen.map(salesman => ({
      name: salesman,
      type: 'line',
      smooth: true,
      symbolSize: 8,
      data: periods.map(period => trendRows.value.find(
        row => row.period === period && row.salesman === salesman
      )?.totalAmount || 0)
    }))
  }
})

function queryParams(dimension?: string) {
  return {
    dimension,
    startDate: range.value?.[0],
    endDate: range.value?.[1]
  }
}

async function loadData() {
  loading.value = true
  try {
    const [summaryRes, trendRes] = await Promise.all([
      salesBySalesman(queryParams()),
      salesTrendBySalesman(queryParams(trendDimension.value))
    ])
    rows.value = summaryRes.data
    trendRows.value = trendRes.data
  } finally {
    loading.value = false
  }
}

async function loadTrend() {
  trendRows.value = (await salesTrendBySalesman(queryParams(trendDimension.value))).data
}

async function openDetails(row: SalesmanRow) {
  if (row.ownerId == null) return
  selectedSalesman.value = row.salesman
  detailsVisible.value = true
  detailsLoading.value = true
  try {
    details.value = (await salesPerformanceDetails(row.ownerId, queryParams())).data
  } finally {
    detailsLoading.value = false
  }
}

function summaryMethod() {
  return ['合计', '', String(metrics.value.orders), fmt(metrics.value.revenue), '100%', '']
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <div>
            <div class="title">业务员业绩分析</div>
            <div class="subtitle">按有效 PI 担当归属统计，金额统一折算为 CNY</div>
          </div>
          <div class="filters">
            <el-date-picker
              v-model="range"
              type="daterange"
              value-format="YYYY-MM-DD"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 250px"
            />
            <el-button type="primary" @click="loadData">查询</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="24" class="metrics">
        <el-col :xs="12" :sm="6"><el-statistic title="销售营收(CNY)" :value="metrics.revenue" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="有效 PI" :value="metrics.orders" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="产生业绩人数" :value="metrics.salesmen" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="人均业绩(CNY)" :value="metrics.average" :precision="2" /></el-col>
      </el-row>

      <div v-loading="loading" class="report-grid">
        <section class="chart-panel">
          <h3>业务员销售额排名</h3>
          <ReportChart :option="rankingOption" />
        </section>
        <section class="chart-panel">
          <h3>营收贡献占比</h3>
          <ReportChart :option="shareOption" />
        </section>
      </div>

      <section v-loading="loading" class="trend-panel">
        <div class="section-head">
          <h3>{{ trendDimension === 'QUARTER' ? '季度业绩趋势' : '年度业绩趋势' }}</h3>
          <el-radio-group v-model="trendDimension" size="small" @change="loadTrend">
            <el-radio-button value="QUARTER">季度</el-radio-button>
            <el-radio-button value="YEAR">年度</el-radio-button>
          </el-radio-group>
        </div>
        <ReportChart :option="trendOption" />
      </section>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        show-summary
        :summary-method="summaryMethod"
        class="performance-table"
        @row-click="openDetails"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="salesman" label="业务员" min-width="140" />
        <el-table-column prop="orderCount" label="有效 PI" width="110" align="left" />
        <el-table-column label="销售额(CNY)" min-width="170" align="left">
          <template #default="{ row }: any">{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="营收占比" min-width="190" align="left">
          <template #default="{ row }: any">
            <el-progress :percentage="Number(row.revenueShare || 0)" :stroke-width="12" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="left">
          <template #default="{ row }: any">
            <el-button link type="primary" :disabled="row.ownerId == null" @click.stop="openDetails(row)">查看来源</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="detailsVisible" :title="`${selectedSalesman} · 业绩来源`" size="760px">
      <div class="drawer-summary">
        共 {{ details.length }} 张有效 PI，合计 {{ fmt(details.reduce((sum, row) => sum + Number(row.amountCny || 0), 0)) }} CNY
      </div>
      <el-table v-loading="detailsLoading" :data="details" border stripe size="small">
        <el-table-column prop="piNo" label="PI 单号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="customerCode" label="客户ID" width="100" />
        <el-table-column prop="customerName" label="客户" min-width="150" show-overflow-tooltip />
        <el-table-column prop="signDate" label="签约日期" width="105" />
        <el-table-column label="原币金额" width="125" align="left">
          <template #default="{ row }: any">{{ fmt(row.originalAmount) }} {{ row.currency }}</template>
        </el-table-column>
        <el-table-column label="折算金额(CNY)" width="135" align="left">
          <template #default="{ row }: any">{{ fmt(row.amountCny) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }: any"><el-tag size="small">{{ statusMap[row.status] || row.status }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<style scoped>
.head, .filters, .section-head { display: flex; align-items: center; }
.head, .section-head { justify-content: space-between; gap: 16px; }
.filters { gap: 8px; }
.title { font-size: 16px; font-weight: 600; color: #303133; }
.subtitle { margin-top: 3px; color: #909399; font-size: 12px; }
.metrics { padding: 8px 0 20px; border-bottom: 1px solid #ebeef5; }
.report-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; padding-top: 18px; }
.chart-panel, .trend-panel { min-width: 0; }
.chart-panel h3, .trend-panel h3 { margin: 0; font-size: 14px; font-weight: 600; color: #303133; }
.trend-panel { padding-top: 20px; margin-top: 8px; border-top: 1px solid #ebeef5; }
.performance-table { margin-top: 18px; }
.performance-table :deep(.el-table__row) { cursor: pointer; }
.drawer-summary { margin-bottom: 12px; color: #606266; }
@media (max-width: 900px) {
  .head { align-items: flex-start; flex-direction: column; }
  .report-grid { grid-template-columns: 1fr; }
}
</style>
