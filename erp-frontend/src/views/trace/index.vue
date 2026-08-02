<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  piList,
  traceOwners,
  tracePi,
  type PiBrief,
  type PiTrace,
  type PiTraceItem,
  type TimelineEvent,
  type TraceOwnerOption
} from '@/api/trace'

const pis = ref<PiBrief[]>([])
const ownerOptions = ref<TraceOwnerOption[]>([])
const selectedOwnerId = ref<number | string | null>(null)
const selectedPi = ref<number | string | null>(null)
const trace = ref<PiTrace | null>(null)
const loading = ref(false)
const showOwnerFilter = computed(() => ownerOptions.value.length > 1)

const NODE_COLOR: Record<string, string> = {
  PI: '#1f3a93',
  DEPOSIT: '#67c23a',
  PO: '#606266',
  SI: '#e6a23c',
  FULL_PAYMENT: '#67c23a',
  SHIPMENT: '#409eff',
  DELIVERY: '#409eff',
  COMPLETE: '#67c23a'
}

/** 单据状态英文 -> 中文 (未收录的原样显示, 如收付款类型本就是中文) */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CONFIRMED: '已确认',
  ORDERED: '已下单',
  SHIPPED: '已出货',
  COMPLETED: '已完成',
  CLOSED: '已完成',
  RECEIVED: '已入库',
  PENDING: '待处理'
}
function statusText(s?: string) {
  return s ? STATUS_LABEL[s] || s : ''
}

async function onSelect(id: number | string) {
  if (!id) return
  loading.value = true
  try {
    trace.value = (await tracePi(id)).data
  } finally {
    loading.value = false
  }
}

async function loadOwners() {
  ownerOptions.value = (await traceOwners()).data || []
}

async function loadPis() {
  loading.value = true
  try {
    const ownerId = selectedOwnerId.value || undefined
    pis.value = (await piList({ ownerId })).data || []
    selectedPi.value = pis.value[0]?.id || null
    trace.value = null
    if (selectedPi.value) {
      await onSelect(selectedPi.value)
    }
  } finally {
    loading.value = false
  }
}

function onOwnerChange() {
  loadPis()
}

function eventColor(e: TimelineEvent) {
  return NODE_COLOR[e.node] || '#909399'
}

function formatMoney(value?: number) {
  if (value === undefined || value === null) return ''
  return Number(value).toFixed(2)
}

function formatQty(row: PiTraceItem) {
  const qty = Number(row.qty) || 0
  const unit = row.unit || ''
  return `${qty.toFixed(4).replace(/\.?0+$/, '')}${unit ? ` ${unit}` : ''}`
}

onMounted(async () => {
  await loadOwners()
  await loadPis()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span>单据追踪 · PI 明细 → 收定金 → PO采购 → 出货指示 → 收全款 → 出货单 → 交货 → 完成订单</span>
          <div class="filters">
            <el-select
              v-if="showOwnerFilter"
              v-model="selectedOwnerId"
              placeholder="全部合同担当"
              clearable
              filterable
              style="width: 180px"
              @change="onOwnerChange"
              @clear="onOwnerChange"
            >
              <el-option v-for="u in ownerOptions" :key="u.id" :label="u.name" :value="u.id" />
            </el-select>
            <el-select
              v-model="selectedPi"
              placeholder="选择 PI"
              filterable
              style="width: 380px"
              :disabled="!pis.length"
              @change="onSelect"
            >
              <el-option
                v-for="p in pis"
                :key="p.id"
                :label="`${p.piNo}　${p.customer || ''}　${p.ownerName ? '担当：' + p.ownerName : ''}　${p.signDate || ''}`"
                :value="p.id"
              />
            </el-select>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <el-descriptions v-if="trace" :column="4" border style="margin-bottom: 20px">
          <el-descriptions-item label="PI 单号">{{ trace.piNo }}</el-descriptions-item>
          <el-descriptions-item label="合同担当">{{ trace.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户ID">{{ trace.customerCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ trace.customer || '-' }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">
            {{ formatMoney(trace.totalAmount) }} {{ trace.currency }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small">{{ statusText(trace.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="签约日期">{{ trace.signDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="交货日期">{{ trace.deliveryDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="付款方式">{{ trace.paymentMethod || '-' }}</el-descriptions-item>
          <el-descriptions-item label="贸易条款">{{ trace.tradeTerm || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="trace" class="section-title">PI 明细</div>
        <el-table
          v-if="trace"
          :data="trace.items || []"
          border
          stripe
          size="small"
          style="margin-bottom: 20px"
        >
          <el-table-column prop="productId" label="产品ID" width="110" />
          <el-table-column prop="productName" label="产品名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="productModel" label="产品型号" min-width="150" show-overflow-tooltip />
          <el-table-column label="数量" width="110">
            <template #default="{ row }: any">{{ formatQty(row) }}</template>
          </el-table-column>
          <el-table-column label="单价" width="110" align="left">
            <template #default="{ row }: any">{{ formatMoney(row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="left">
            <template #default="{ row }: any">{{ formatMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="quoteId" label="销售报价ID" width="130" />
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>

        <div v-if="trace" class="section-title">单据链路</div>
        <el-empty v-if="trace && (!trace.events || !trace.events.length)" description="暂无关联单据" />

        <el-timeline v-else-if="trace">
          <el-timeline-item
            v-for="(e, i) in trace.events"
            :key="i"
            :timestamp="e.date || '日期未填'"
            placement="top"
            :color="eventColor(e)"
            size="large"
          >
            <el-card shadow="hover">
              <div class="ev-head">
                <el-tag :color="eventColor(e)" effect="dark" size="small" style="border: none">
                  {{ e.nodeLabel }}
                </el-tag>
                <span class="ev-no">{{ e.docNo }}</span>
                <el-tag v-if="e.status" size="small" type="info" effect="plain">{{ statusText(e.status) }}</el-tag>
              </div>
              <div class="ev-summary">{{ e.summary }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>

        <el-empty v-if="!trace" description="请选择一个 PI 查看其单据链路" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.ev-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ev-no {
  font-weight: 600;
  color: #303133;
}
.section-title {
  margin: 4px 0 10px;
  font-weight: 600;
  color: #303133;
}
.ev-summary {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}
</style>
