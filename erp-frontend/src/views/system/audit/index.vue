<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { pageAudit, type AuditLog } from '@/api/system'

const loading = ref(false)
const list = ref<AuditLog[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)
const query = reactive<{
  pageNum: number
  pageSize: number
  username: string
  module: string
  success: number | null
  startDate?: string
  endDate?: string
}>({
  pageNum: 1,
  pageSize: 10,
  username: '',
  module: '',
  success: null
})

const detailVisible = ref(false)
const detail = ref<AuditLog | null>(null)

async function loadData() {
  loading.value = true
  query.startDate = dateRange.value?.[0]
  query.endDate = dateRange.value?.[1]
  try {
    const res = await pageAudit(query)
    list.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.pageNum = 1
  loadData()
}
function onReset() {
  query.username = ''
  query.module = ''
  query.success = null
  dateRange.value = null
  onSearch()
}

function openDetail(row: AuditLog) {
  detail.value = row
  detailVisible.value = true
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="操作人">
          <el-input v-model="query.username" placeholder="用户名" clearable style="width: 140px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="模块">
          <el-input v-model="query.module" placeholder="模块" clearable style="width: 140px" @keyup.enter="onSearch" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="username" label="操作人" width="120" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="operation" label="操作" width="120" />
        <el-table-column prop="requestMethod" label="方法" width="80" align="left" />
        <el-table-column prop="requestUri" label="请求地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="结果" width="80" align="left">
          <template #default="{ row }: any">
            <el-tag :type="row.success === 1 ? 'success' : 'danger'" size="small">
              {{ row.success === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="costMs" label="耗时(ms)" width="100" align="left" />
        <el-table-column prop="ip" label="IP" width="130" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="160" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadData"
        @size-change="onSearch"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="审计详情" width="640px">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="操作人">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ detail.operation }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="detail.success === 1 ? 'success' : 'danger'" size="small">
            {{ detail.success === 1 ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="请求方法">{{ detail.requestMethod }}</el-descriptions-item>
        <el-descriptions-item label="耗时(ms)">{{ detail.costMs }}</el-descriptions-item>
        <el-descriptions-item label="请求地址" :span="2">{{ detail.requestUri }}</el-descriptions-item>
        <el-descriptions-item label="目标方法" :span="2">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="params">{{ detail.params }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息" :span="2">
          <span class="error">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.params {
  margin: 0;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
.error {
  color: #f56c6c;
}
</style>
