<script setup lang="ts">
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts'

use([CanvasRenderer, BarChart, LineChart, PieChart, GridComponent, TooltipComponent, LegendComponent])

defineProps<{ option: EChartsOption }>()
</script>

<template>
  <v-chart class="chart" :option="option" autoresize />
</template>

<style scoped>
.chart {
  height: 340px;
  width: 100%;
}
</style>
