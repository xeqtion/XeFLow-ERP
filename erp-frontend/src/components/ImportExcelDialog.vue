<script setup lang="ts">
import { computed, ref, type Component, watch } from 'vue'
import { ElMessage, type UploadFile, type UploadInstance, type UploadRawFile } from 'element-plus'
import { UploadFilled, Download, SuccessFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import type { ApiResult } from '@/api/request'
import type { ImportResult } from '@/api/customer'

const props = defineProps<{
  modelValue: boolean
  title?: string
  importApi: (file: File) => Promise<ApiResult<ImportResult>>
  downloadTemplate?: () => Promise<void>
  identifierLabel?: string
  nameLabel?: string
  errorFileName?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const visible = ref(props.modelValue)
const file = ref<UploadRawFile>()
const uploadRef = ref<UploadInstance>()
const loading = ref(false)
const result = ref<ImportResult | null>(null)
const statusFilter = ref<string>('')
const uploadCollapsed = ref(false)

watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
    if (v) {
      file.value = undefined
      result.value = null
      statusFilter.value = ''
      uploadCollapsed.value = false
    }
  }
)
watch(visible, (v) => emit('update:modelValue', v))

function onChange(uploadFile: UploadFile) {
  if (!uploadFile.raw || !validateSelectedFile(uploadFile.raw)) {
    file.value = undefined
    uploadRef.value?.clearFiles()
    return
  }
  file.value = uploadFile.raw
  result.value = null
  statusFilter.value = ''
  uploadCollapsed.value = false
}

function validateSelectedFile(raw: File): boolean {
  const name = raw.name.toLowerCase()
  if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
    ElMessage.warning('仅支持 .xlsx 或 .xls 格式的 Excel 文件')
    return false
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.warning('导入文件不能超过 20MB')
    return false
  }
  return true
}

async function submit() {
  if (!file.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!validateSelectedFile(file.value)) return
  loading.value = true
  try {
    const res = await props.importApi(file.value)
    result.value = normalizeResult(res.data)
    // 导入完成后折叠上传区
    uploadCollapsed.value = true
    if (result.value.fail === 0 && result.value.warning === 0) {
      ElMessage.success(`导入成功 ${result.value.success} 条`)
      emit('success')
    } else if (result.value.fail === 0 && result.value.warning > 0) {
      ElMessage.success(`成功 ${result.value.success} 条，警告 ${result.value.warning} 条`)
      emit('success')
    } else {
      ElMessage.warning(`成功 ${result.value.success} 条，警告 ${result.value.warning} 条，失败 ${result.value.fail} 条`)
    }
  } finally {
    loading.value = false
  }
}

/** 兼容旧版后端：如果 lacks details/fields，从 errors 构建 */
function normalizeResult(r: ImportResult): ImportResult {
  if (!r.details || r.details.length === 0) {
    const details: ImportResult['details'] = []
    for (let i = 0; i < r.success; i++) {
      details.push({ row: i + 1, status: 'SUCCESS' })
    }
    for (const e of r.errors || []) {
      details.push({ row: e.row, message: e.message, status: 'ERROR' })
    }
    r.details = details
  }
  if (r.warning === undefined || r.warning === null) r.warning = 0
  return r
}

/** 排序：错误 → 警告 → 成功，内部按行号升序 */
const sortedDetails = computed(() => {
  if (!result.value?.details) return []
  const arr = [...result.value.details]
  const rank: Record<string, number> = { ERROR: 0, WARNING: 1, SUCCESS: 2 }
  arr.sort((a, b) => {
    const ra = rank[a.status] ?? 9
    const rb = rank[b.status] ?? 9
    if (ra !== rb) return ra - rb
    return a.row - b.row
  })
  return arr
})

/** 当前筛选后的数据 */
const filteredDetails = computed(() => {
  if (!statusFilter.value) return sortedDetails.value
  return sortedDetails.value.filter((d) => d.status === statusFilter.value)
})

/** 各状态计数 */
const countByStatus = computed(() => {
  if (!result.value?.details) return { SUCCESS: 0, WARNING: 0, ERROR: 0 }
  const c = { SUCCESS: 0, WARNING: 0, ERROR: 0 }
  for (const d of result.value.details) {
    if (c[d.status] !== undefined) c[d.status]++
  }
  return c
})

// 图标映射
const statusIcons: Record<string, Component> = {
  SUCCESS: SuccessFilled,
  WARNING: WarningFilled,
  ERROR: CircleCloseFilled
}

/** 导出错误 CSV */
function exportErrors() {
  if (!result.value) return
  const rows = result.value.details.filter((d) => d.status === 'ERROR')
  if (rows.length === 0) {
    ElMessage.info('没有错误记录')
    return
  }
  const identifierLabel = props.identifierLabel || '工厂型号'
  const nameLabel = props.nameLabel || '产品名称'
  const header = `行号,状态,${identifierLabel},${nameLabel},信息\n`
  const body = rows.map((d) => {
    const fm = d.factoryModel ? d.factoryModel.replace(/,/g, '，') : ''
    const pn = d.productName ? d.productName.replace(/,/g, '，') : ''
    const msg = d.message ? d.message.replace(/,/g, '，') : ''
    return `${d.row},${d.status},${fm},${pn},${msg}`
  }).join('\n')
  const bom = '\uFEFF'
  const blob = new Blob([bom + header + body], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = props.errorFileName || '导入错误清单.csv'
  a.click()
  URL.revokeObjectURL(url)
}

/** 行背景类 */
function rowClass({ row }: { row: ImportResult['details'][0] }) {
  if (row.status === 'ERROR') return 'row-error'
  if (row.status === 'WARNING') return 'row-warning'
  return ''
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCESS') return 'success'
  if (status === 'WARNING') return 'warning'
  if (status === 'ERROR') return 'danger'
  return 'info'
}

function statusLabel(status: string): string {
  if (status === 'SUCCESS') return '成功'
  if (status === 'WARNING') return '调整'
  if (status === 'ERROR') return '失败'
  return status
}
</script>

<template>
  <el-dialog v-model="visible" :title="title || 'Excel 导入'" width="780px" top="5vh" class="import-dialog">
    <!-- ====== 顶部：统计卡片 ====== -->
    <div v-if="result" class="stat-cards">
      <div class="stat-item stat-total">
        <span class="stat-num">{{ result.total }}</span>
        <span class="stat-label">总计</span>
      </div>
      <div class="stat-item stat-success">
        <el-icon :size="20"><SuccessFilled /></el-icon>
        <span class="stat-num">{{ result.success }}</span>
        <span class="stat-label">成功</span>
      </div>
      <div class="stat-item stat-warning">
        <el-icon :size="20"><WarningFilled /></el-icon>
        <span class="stat-num">{{ result.warning }}</span>
        <span class="stat-label">调整</span>
      </div>
      <div class="stat-item stat-error">
        <el-icon :size="20"><CircleCloseFilled /></el-icon>
        <span class="stat-num">{{ result.fail }}</span>
        <span class="stat-label">失败</span>
      </div>
    </div>

    <!-- ====== 上传区域（可折叠） ====== -->
    <div v-if="!uploadCollapsed" class="upload-section">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="onChange"
        :show-file-list="true"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将 Excel 拖到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            仅支持 .xlsx/.xls，单个文件不超过 20MB，最多导入 5000 行。
            <el-link v-if="downloadTemplate" type="primary" @click="downloadTemplate">下载导入模板</el-link>
          </div>
        </template>
      </el-upload>
    </div>
    <!-- 折叠后的缩略条 -->
    <div v-else class="upload-collapsed">
      <el-icon><UploadFilled /></el-icon>
      <span class="upload-filename">{{ file?.name }}</span>
      <el-button link size="small" @click="uploadCollapsed = false">更换文件</el-button>
    </div>

    <!-- ====== 筛选栏 + 导出按钮 ====== -->
    <div v-if="result && result.details && result.details.length" class="filter-bar">
      <div class="filter-tabs">
        <el-button
          :type="statusFilter === '' ? 'primary' : 'default'"
          size="small"
          @click="statusFilter = ''"
        >
          全部 <span class="filter-count">{{ result.details.length }}</span>
        </el-button>
        <el-button
          :type="statusFilter === 'SUCCESS' ? 'success' : 'default'"
          size="small"
          @click="statusFilter = 'SUCCESS'"
        >
          成功 <span class="filter-count">{{ countByStatus.SUCCESS }}</span>
        </el-button>
        <el-button
          :type="statusFilter === 'WARNING' ? 'warning' : 'default'"
          size="small"
          @click="statusFilter = 'WARNING'"
        >
          调整 <span class="filter-count">{{ countByStatus.WARNING }}</span>
        </el-button>
        <el-button
          :type="statusFilter === 'ERROR' ? 'danger' : 'default'"
          size="small"
          @click="statusFilter = 'ERROR'"
        >
          失败 <span class="filter-count">{{ countByStatus.ERROR }}</span>
        </el-button>
      </div>
      <el-button size="small" :icon="Download" @click="exportErrors">导出错误清单</el-button>
    </div>

    <!-- ====== 核心：业务字段结果表格 ====== -->
    <el-table
      v-if="result && filteredDetails.length"
      :data="filteredDetails"
      size="small"
      max-height="320"
      style="margin-top: 8px"
      :row-class-name="rowClass"
    >
      <el-table-column label="行号" width="65" prop="row" />
      <el-table-column :label="identifierLabel || '工厂型号'" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.factoryModel" style="font-family: monospace; font-weight: 500">{{ row.factoryModel }}</span>
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column :label="nameLabel || '产品名称'" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.productName">{{ row.productName }}</span>
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small" effect="dark" round>
            <el-icon style="margin-right: 3px; vertical-align: -2px">
              <component :is="statusIcons[row.status]" />
            </el-icon>
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="系统信息" min-width="180">
        <template #default="{ row }">
          <span v-if="row.message" class="cell-message" :title="row.message">{{ row.message }}</span>
          <span v-else style="color: #c0c4cc">-</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 无数据提示 -->
    <el-empty v-else-if="result && result.details && result.details.length === 0" description="无导入数据" />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="loading" @click="submit">开始导入</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
/* 统计卡片 */
.stat-cards {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border-radius: 8px;
  min-width: 90px;
  flex: 1;
}
.stat-total {
  background: #f5f7fa;
  color: #606266;
}
.stat-success {
  background: #f0f9eb;
  color: #67c23a;
}
.stat-warning {
  background: #fdf6ec;
  color: #e6a23c;
}
.stat-error {
  background: #fef0f0;
  color: #f56c6c;
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}
.stat-label {
  font-size: 13px;
  opacity: 0.8;
}

/* 折叠后的上传缩略条 */
.upload-collapsed {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  margin-bottom: 12px;
  background: #f5f7fa;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
}
.upload-filename {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 上传区上边距 */
.upload-section {
  margin-bottom: 8px;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}
.filter-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.filter-count {
  display: inline-block;
  min-width: 18px;
  padding: 0 4px;
  margin-left: 4px;
  border-radius: 8px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  background: rgba(0, 0, 0, 0.08);
}

/* 行背景染色（浅色，不刺眼） */
:deep(.el-table .row-error) {
  --el-table-tr-bg-color: #fff1f0;
}
:deep(.el-table .row-warning) {
  --el-table-tr-bg-color: #fffbe6;
}
:deep(.el-table .row-error:hover > td) {
  background-color: #ffd8d5 !important;
}
:deep(.el-table .row-warning:hover > td) {
  background-color: #fff7cc !important;
}

/* 错误信息太长时缩略 + tooltip */
.cell-message {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #606266;
}
</style>
