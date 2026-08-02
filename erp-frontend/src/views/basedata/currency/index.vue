<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  pageCurrency,
  createCurrency,
  updateCurrency,
  deleteCurrency,
  baseCurrency,
  convertCurrency,
  listSupportedCurrencies,
  refreshOnlineRates,
  type CurrencyConvertResult,
  type CurrencyOption,
  type CurrencyRate
} from '@/api/currency'

type RateView = 'today' | 'history'
type ConvertMode = 'base' | 'cross'

function formatLocalDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const today = formatLocalDate(new Date())
const base = ref('CNY')
const loading = ref(false)
const list = ref<CurrencyRate[]>([])
const total = ref(0)
const rateView = ref<RateView>('today')
const historyDate = ref('')
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', date: today })

const currencyOptions = ref<CurrencyOption[]>([
  { code: 'CNY', name: '人民币', base: true },
  { code: 'USD', name: '美元', base: false }
])
const foreignOptions = computed(() => currencyOptions.value.filter(item => !item.base))
const displayedRates = computed(() => {
  const order = new Map(currencyOptions.value.map((item, index) => [item.code, index]))
  return [...list.value].sort((left, right) =>
    (order.get(left.currency) ?? Number.MAX_SAFE_INTEGER)
      - (order.get(right.currency) ?? Number.MAX_SAFE_INTEGER)
  )
})

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<CurrencyRate>(emptyForm())

function emptyForm(): CurrencyRate {
  return {
    id: undefined,
    currency: '',
    currencyName: '',
    rateToBase: null,
    effectiveDate: today,
    remark: ''
  }
}

const rules: FormRules = {
  currency: [{ required: true, message: '请选择或输入外币代码', trigger: 'change' }],
  currencyName: [{ max: 64, message: '中文名称最多 64 个字符', trigger: 'blur' }],
  rateToBase: [{ required: true, message: '请输入汇率', trigger: 'blur' }]
}

const conv = reactive<{
  mode: ConvertMode
  amount: number
  from: string
  to: string
}>({
  mode: 'base',
  amount: 1000,
  from: 'USD',
  to: 'CNY'
})
const conversionResult = ref<CurrencyConvertResult>()

watch(() => conv.mode, (mode) => {
  conversionResult.value = undefined
  if (mode === 'base') {
    conv.to = base.value
    return
  }
  if (conv.from === base.value) {
    conv.from = foreignOptions.value[0]?.code || 'USD'
  }
  if (conv.to === base.value || conv.to === conv.from) {
    conv.to = foreignOptions.value.find(item => item.code !== conv.from)?.code || 'SGD'
  }
})

function currencyName(code?: string, customName?: string) {
  if (customName?.trim()) return customName.trim()
  if (!code) return ''
  return currencyOptions.value.find(item => item.code === code.toUpperCase())?.name || code.toUpperCase()
}

function onFormCurrencyChange(code: string) {
  const currentName = form.currencyName?.trim()
  const currentIsSystemName = currencyOptions.value.some(item => item.name === currentName)
  if (!currentName || currentIsSystemName) {
    form.currencyName = currencyOptions.value.find(item => item.code === code)?.name || ''
  }
}

function currencyLabel(item: CurrencyOption) {
  return `${item.code} · ${item.name}`
}

async function loadOptions() {
  const res = await listSupportedCurrencies()
  currencyOptions.value = res.data
  const baseOption = res.data.find(item => item.base)
  if (baseOption) {
    base.value = baseOption.code
    if (conv.mode === 'base') conv.to = baseOption.code
  }
  if (!res.data.some(item => item.code === conv.from)) {
    conv.from = foreignOptions.value[0]?.code || 'USD'
  }
}

async function loadData() {
  if (rateView.value === 'history' && !historyDate.value) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    query.date = rateView.value === 'today' ? today : historyDate.value
    const res = await pageCurrency(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function switchRateView(tabName: string | number) {
  const view: RateView = tabName === 'history' ? 'history' : 'today'
  rateView.value = view
  query.pageNum = 1
  query.keyword = ''
  if (view === 'today') {
    query.date = today
    loadData()
  } else {
    list.value = []
    total.value = 0
  }
}

function onSearch() {
  if (rateView.value === 'history' && !historyDate.value) {
    ElMessage.warning('请先选择需要查询的历史日期')
    return
  }
  query.pageNum = 1
  loadData()
}

function resetSearch() {
  query.keyword = ''
  if (rateView.value === 'history') historyDate.value = ''
  onSearchOrClear()
}

function onSearchOrClear() {
  if (rateView.value === 'history' && !historyDate.value) {
    list.value = []
    total.value = 0
    return
  }
  onSearch()
}

function disableNonHistoryDate(date: Date) {
  const startOfToday = new Date()
  startOfToday.setHours(0, 0, 0, 0)
  return date.getTime() >= startOfToday.getTime()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogTitle.value = '新增汇率'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(row: CurrencyRate) {
  Object.assign(form, row)
  dialogTitle.value = '修改汇率'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    form.currency = form.currency.trim().toUpperCase()
    form.currencyName = form.currencyName?.trim() || undefined
    if (form.id) {
      await updateCurrency(form)
      ElMessage.success('修改成功')
    } else {
      await createCurrency(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadData()
  })
}

async function onDelete(row: CurrencyRate) {
  await ElMessageBox.confirm(
    `确认删除 ${row.currency} 在 ${row.effectiveDate || '-'} 的汇率？`,
    '提示',
    { type: 'warning' }
  )
  await deleteCurrency(row.id!)
  ElMessage.success('删除成功')
  loadData()
}

const refreshing = ref(false)
async function onRefreshOnline() {
  refreshing.value = true
  try {
    const res = await refreshOnlineRates()
    const rates = res.data.rates || {}
    const failedCurrencies = Object.keys(res.data.failed || {})
    const failed = failedCurrencies.length ? `，未获取：${failedCurrencies.join('、')}` : ''
    ElMessage.success(`已更新 ${res.data.date} 的 ${Object.keys(rates).length} 个中行折算价${failed}`)
    rateView.value = 'today'
    query.date = today
    await loadData()
  } finally {
    refreshing.value = false
  }
}

function swapCurrencies() {
  if (conv.mode !== 'cross') return
  const currentFrom = conv.from
  conv.from = conv.to
  conv.to = currentFrom
  conversionResult.value = undefined
}

async function doConvert() {
  if (conv.amount === undefined || conv.amount === null || !conv.from || !conv.to) {
    ElMessage.warning('请输入金额并选择换算币种')
    return
  }
  if (conv.from === conv.to) {
    ElMessage.warning('源币种和目标币种不能相同')
    return
  }
  const res = await convertCurrency({
    amount: conv.amount,
    from: conv.from,
    to: conv.to,
    date: today
  })
  conversionResult.value = res.data
}

function formatNumber(value?: number, maximumFractionDigits = 4) {
  return Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 0,
    maximumFractionDigits
  })
}

onMounted(async () => {
  base.value = (await baseCurrency()).data.base
  await Promise.all([loadOptions(), loadData()])
})
</script>

<template>
  <div class="app-container currency-page">
    <section class="converter-panel">
      <div class="converter-heading">
        <div>
          <h2>货币换算</h2>
          <p>按 {{ today }} 中国银行中行折算价计算</p>
        </div>
        <el-radio-group v-model="conv.mode" size="default">
          <el-radio-button value="base">本位币换算</el-radio-button>
          <el-radio-button value="cross">外币互换</el-radio-button>
        </el-radio-group>
      </div>

      <div class="converter-grid">
        <div class="converter-field amount-field">
          <label>金额</label>
          <el-input-number v-model="conv.amount" :min="0" :precision="2" :controls="false" />
        </div>
        <div class="converter-field">
          <label>源币种</label>
          <el-select v-model="conv.from" filterable>
            <el-option
              v-for="item in foreignOptions"
              :key="item.code"
              :label="currencyLabel(item)"
              :value="item.code"
            />
          </el-select>
        </div>
        <el-tooltip content="交换币种" placement="top">
          <el-button
            class="swap-button"
            :disabled="conv.mode !== 'cross'"
            circle
            aria-label="交换币种"
            @click="swapCurrencies"
          >
            <el-icon><Switch /></el-icon>
          </el-button>
        </el-tooltip>
        <div class="converter-field">
          <label>目标币种</label>
          <el-select v-if="conv.mode === 'cross'" v-model="conv.to" filterable>
            <el-option
              v-for="item in foreignOptions"
              :key="item.code"
              :label="currencyLabel(item)"
              :value="item.code"
              :disabled="item.code === conv.from"
            />
          </el-select>
          <el-input v-else :model-value="`${base} · ${currencyName(base)}`" disabled />
        </div>
        <el-button type="primary" class="convert-button" @click="doConvert">换算</el-button>
      </div>

      <div v-if="conversionResult" class="conversion-result">
        <div class="result-primary">
          {{ formatNumber(conv.amount, 2) }} {{ conversionResult.from }}
          <span>≈</span>
          {{ formatNumber(conversionResult.convertedAmount) }} {{ conversionResult.to }}
        </div>
        <div class="result-rate">
          1 {{ conversionResult.from }} = {{ formatNumber(conversionResult.rate, 8) }} {{ conversionResult.to }}
        </div>
      </div>
    </section>

    <section class="rates-panel">
      <div class="rates-header">
        <el-tabs v-model="rateView" class="rate-tabs" @tab-change="switchRateView">
          <el-tab-pane label="今日汇率" name="today" />
          <el-tab-pane label="历史汇率" name="history" />
        </el-tabs>
        <div class="source-note">数据源：中国银行外汇牌价 · 中行折算价 · 每日 08:30 自动更新</div>
      </div>

      <div class="query-bar">
        <el-date-picker
          v-if="rateView === 'history'"
          v-model="historyDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择历史日期"
          :disabled-date="disableNonHistoryDate"
          style="width: 170px"
        />
        <el-input
          v-model="query.keyword"
          placeholder="搜索币种代码"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
        />
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
        <div class="query-spacer" />
        <el-button v-permission="'currency:create'" @click="openCreate">新增汇率</el-button>
        <el-button
          v-permission="'currency:update'"
          type="success"
          plain
          :loading="refreshing"
          @click="onRefreshOnline"
        >
          立即拉取今日汇率
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="displayedRates"
        border
        stripe
        :empty-text="rateView === 'today' ? '今日汇率尚未拉取' : '请选择日期查询历史汇率'"
      >
        <el-table-column type="index" label="#" width="56" />
        <el-table-column label="外币" width="110">
          <template #default="{ row }: any">
            <el-tooltip :content="currencyName(row.currency, row.currencyName)" placement="top">
              <span class="currency-code">{{ row.currency }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="中文名称" min-width="130">
          <template #default="{ row }: any">{{ currencyName(row.currency, row.currencyName) }}</template>
        </el-table-column>
        <el-table-column label="中行折算" min-width="220">
          <template #default="{ row }: any">
            1 {{ row.currency }} = {{ row.rateToBase }} {{ base }}
          </template>
        </el-table-column>
        <el-table-column prop="effectiveDate" label="汇率日期" width="130" />
        <el-table-column prop="remark" label="来源/备注" min-width="210" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }: any">
            <el-button v-permission="'currency:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'currency:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        class="pagination"
        @current-change="loadData"
        @size-change="onSearch"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="外币代码" prop="currency">
          <el-select
            v-model="form.currency"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入 ISO 代码"
            style="width: 100%"
            @change="onFormCurrencyChange"
          >
            <el-option
              v-for="item in foreignOptions"
              :key="item.code"
              :label="currencyLabel(item)"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="中文名称" prop="currencyName">
          <el-input v-model="form.currencyName" maxlength="64" placeholder="选填，如 美元" clearable />
        </el-form-item>
        <el-form-item label="中行折算价" prop="rateToBase">
          <el-input-number
            v-model="form.rateToBase"
            :min="0"
            :precision="6"
            :controls="false"
            style="width: 100%"
          />
          <span class="form-hint">1 外币 = 对应数值 {{ base }}</span>
        </el-form-item>
        <el-form-item label="汇率日期">
          <el-date-picker
            v-model="form.effectiveDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.currency-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.converter-panel,
.rates-panel {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.converter-panel {
  padding: 20px 22px;
}

.converter-heading,
.rates-header,
.query-bar,
.converter-grid {
  display: flex;
  align-items: center;
}

.converter-heading {
  justify-content: space-between;
  margin-bottom: 18px;
}

.converter-heading h2 {
  margin: 0 0 5px;
  font-size: 17px;
  font-weight: 600;
}

.converter-heading p,
.source-note {
  margin: 0;
  color: #909399;
  font-size: 12px;
}

.converter-grid {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(170px, 1fr) 40px minmax(170px, 1fr) 88px;
  gap: 12px;
  align-items: end;
}

.converter-field {
  min-width: 0;
}

.converter-field label {
  display: block;
  margin-bottom: 7px;
  color: #606266;
  font-size: 13px;
}

.converter-field :deep(.el-input-number),
.converter-field :deep(.el-select) {
  width: 100%;
}

.swap-button {
  margin-bottom: 1px;
}

.convert-button {
  width: 88px;
}

.conversion-result {
  display: flex;
  align-items: baseline;
  gap: 18px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.result-primary {
  color: #1f4f9c;
  font-size: 20px;
  font-weight: 600;
}

.result-primary span {
  margin: 0 8px;
  color: #909399;
  font-weight: 400;
}

.result-rate {
  color: #606266;
  font-size: 13px;
}

.rates-panel {
  padding: 0 16px 14px;
}

.rates-header {
  justify-content: space-between;
  min-height: 58px;
  border-bottom: 1px solid #ebeef5;
}

.rate-tabs {
  width: 220px;
}

.rate-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.rate-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.query-bar {
  gap: 10px;
  padding: 14px 0;
  flex-wrap: wrap;
}

.query-bar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.query-spacer {
  flex: 1;
}

.currency-code {
  color: #1f5fae;
  font-weight: 600;
  cursor: help;
}

.pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

.form-hint {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
}

@media (max-width: 900px) {
  .converter-grid {
    grid-template-columns: 1fr 1fr;
  }

  .swap-button {
    display: none;
  }

  .convert-button {
    width: 100%;
  }

  .source-note {
    display: none;
  }
}
</style>
