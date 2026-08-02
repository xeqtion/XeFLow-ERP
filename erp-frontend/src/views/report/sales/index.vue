<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import ReportChart from '@/components/ReportChart.vue'
import { salesReport, exportSales, type ReportRow } from '@/api/report'

const loading = ref(false)
const rows = ref<ReportRow[]>([])
const dateRange = ref<[string, string] | null>(null)
const query = reactive({ dimension: 'MONTH', startDate: '', endDate: '' })

const fmt = (value?: number) => Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

async function loadData() {
  loading.value = true
  query.startDate = dateRange.value?.[0] || ''
  query.endDate = dateRange.value?.[1] || ''
  try {
    rows.value = (await salesReport(query)).data
  } finally {
    loading.value = false
  }
}
function onExport() { exportSales(query) }

const analysis = computed(() => {
  const enriched = rows.value.map((row, index) => {
    const previous = index > 0 ? Number(rows.value[index - 1].amount || 0) : 0
    const amount = Number(row.amount || 0)
    return { ...row, growth: previous ? ((amount - previous) / previous) * 100 : null }
  })
  const total = enriched.reduce((sum, row) => sum + Number(row.amount || 0), 0)
  const peak = enriched.reduce((best, row) => !best || Number(row.amount) > Number(best.amount) ? row : best, enriched[0])
  return {
    rows: enriched,
    total,
    average: enriched.length ? total / enriched.length : 0,
    peak,
    latestGrowth: enriched.length ? enriched[enriched.length - 1].growth : null
  }
})

const option = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 20, top: 30, bottom: 40 },
  xAxis: { type: 'category', data: rows.value.map((r) => r.period) },
  legend: { data: ['销售额(CNY)', '环比增长'] },
  yAxis: [
    { type: 'value', name: 'CNY' },
    { type: 'value', name: '%', axisLabel: { formatter: '{value}%' } }
  ],
  series: [
    { name: '销售额(CNY)', type: 'bar', data: rows.value.map((r) => r.amount), itemStyle: { color: '#2f6fed' }, barMaxWidth: 34 },
    { name: '环比增长', type: 'line', yAxisIndex: 1, smooth: true, data: analysis.value.rows.map((r) => r.growth), itemStyle: { color: '#19a974' } }
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
          <el-button v-permission="'report:sales'" @click="onExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="20" class="metrics">
        <el-col :xs="12" :sm="6"><el-statistic title="销售总额(CNY)" :value="analysis.total" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="周期均值(CNY)" :value="analysis.average" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="峰值金额(CNY)" :value="Number(analysis.peak?.amount || 0)" :precision="2" /></el-col>
        <el-col :xs="12" :sm="6"><el-statistic title="最新环比(%)" :value="analysis.latestGrowth ?? 0" :precision="1" /></el-col>
      </el-row>

      <div v-loading="loading">
        <ReportChart :option="option" />
        <el-table :data="analysis.rows" border stripe size="small" style="margin-top: 12px">
          <el-table-column prop="period" label="周期" />
          <el-table-column label="销售额(CNY)" align="left"><template #default="{ row }: any">{{ fmt(row.amount) }}</template></el-table-column>
          <el-table-column label="环比" align="left"><template #default="{ row }: any">
            <span :class="row.growth >= 0 ? 'positive' : 'negative'">{{ row.growth == null ? '-' : `${row.growth.toFixed(1)}%` }}</span>
          </template></el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.metrics { margin: 8px 0 18px; padding-bottom: 16px; border-bottom: 1px solid #ebeef5; }
.positive { color: #19a974; }
.negative { color: #e5484d; }
</style>
