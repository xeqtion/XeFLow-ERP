<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  pageNotification,
  markRead,
  markAllRead,
  markReadBatch,
  deleteNotification,
  broadcast,
  listRecipients,
  previewDigest,
  sendDigest,
  type Notification,
  type Recipient
} from '@/api/notification'

const userStore = useUserStore()
/** 通知管理(发送/删除公告)仅管理员(持 system:notification:send 权限) */
const isAdmin = computed(() => userStore.hasPermission('system:notification:send'))
const canManageAnnounce = isAdmin

const TYPE_META: Record<string, { label: string; tag: 'primary' | 'success' | 'warning' }> = {
  SYSTEM: { label: '系统', tag: 'primary' },
  QUOTE_APPROVAL: { label: '系统', tag: 'primary' },
  QUOTE_AUDIT: { label: '系统', tag: 'primary' },
  QUOTE_AP: { label: '系统', tag: 'primary' },
  REPORT: { label: '报表', tag: 'success' },
  ANNOUNCE: { label: '公告', tag: 'warning' }
}

const loading = ref(false)
const list = ref<Notification[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; onlyUnread: boolean }>({
  pageNum: 1,
  pageSize: 10,
  onlyUnread: false
})

const detailVisible = ref(false)
const detail = ref<Notification | null>(null)
const safeDetailHtml = computed(() => (detail.value?.type === 'REPORT' ? sanitizeHtml(detail.value.content || '') : ''))
const selected = ref<Notification[]>([])

async function loadData() {
  loading.value = true
  try {
    const res = await pageNotification(query)
    list.value = res.data.records
    total.value = res.data.total
    selected.value = []
  } finally {
    loading.value = false
  }
}
function switchTab() {
  query.pageNum = 1
  loadData()
}
function onSelectionChange(rows: Notification[]) {
  selected.value = rows
}
async function onMarkSelected() {
  const ids = selected.value.filter((r) => r.isRead === 0).map((r) => r.id)
  if (!ids.length) {
    ElMessage.info('请选择未读通知')
    return
  }
  await markReadBatch(ids)
  ElMessage.success(`已标记 ${ids.length} 条为已读`)
  loadData()
  window.dispatchEvent(new Event('notify-refresh'))
}

async function openDetail(row: Notification) {
  detail.value = row
  detailVisible.value = true
  if (row.isRead === 0) {
    await markRead(row.id)
    row.isRead = 1
    window.dispatchEvent(new Event('notify-refresh'))
  }
}

async function onMarkAll() {
  await markAllRead()
  ElMessage.success('已全部标记为已读')
  loadData()
  window.dispatchEvent(new Event('notify-refresh'))
}

async function onDelete(row: Notification) {
  const isAnn = row.type === 'ANNOUNCE'
  await ElMessageBox.confirm(isAnn ? '确认删除该公告？(将对所有人撤回)' : '确认删除该通知？', '提示', {
    type: 'warning'
  })
  await deleteNotification(row.id)
  ElMessage.success('已删除')
  loadData()
  window.dispatchEvent(new Event('notify-refresh'))
}

/* ---------------- 发送公告 ---------------- */
const annVisible = ref(false)
const annFormRef = ref<FormInstance>()
const annForm = reactive<{
  title: string
  content: string
  sendEmail: boolean
  mode: 'ALL' | 'SELECTED'
  recipientIds: string[]
}>({ title: '', content: '', sendEmail: false, mode: 'ALL', recipientIds: [] })
const annRules: FormRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入内容', trigger: 'blur' }]
}
const recipientOptions = ref<Recipient[]>([])
async function openAnnounce() {
  annForm.title = ''
  annForm.content = ''
  annForm.sendEmail = false
  annForm.mode = 'ALL'
  annForm.recipientIds = []
  annVisible.value = true
  annFormRef.value?.clearValidate()
  if (!recipientOptions.value.length) {
    try {
      recipientOptions.value = (await listRecipients()).data
    } catch {
      /* ignore */
    }
  }
}
async function submitAnnounce() {
  if (!annFormRef.value) return
  await annFormRef.value.validate(async (valid) => {
    if (!valid) return
    if (annForm.mode === 'SELECTED' && !annForm.recipientIds.length) {
      ElMessage.warning('请选择接收人')
      return
    }
    const res = await broadcast({
      title: annForm.title,
      content: annForm.content,
      sendEmail: annForm.sendEmail,
      recipientIds: annForm.mode === 'SELECTED' ? annForm.recipientIds : undefined
    })
    ElMessage.success(`公告已发送给 ${res.data.recipients} 人`)
    annVisible.value = false
    loadData()
    window.dispatchEvent(new Event('notify-refresh'))
  })
}

/* ---------------- 发送周报/月报 ---------------- */
const digestVisible = ref(false)
const digestForm = reactive<{ period: 'WEEKLY' | 'MONTHLY'; range: [string, string] | null }>({
  period: 'WEEKLY',
  range: null
})
const previewHtml = ref('')
const safePreviewHtml = computed(() => sanitizeHtml(previewHtml.value))
const previewing = ref(false)
function openDigest() {
  digestForm.period = 'WEEKLY'
  digestForm.range = null
  previewHtml.value = ''
  digestVisible.value = true
}
async function onPreview() {
  previewing.value = true
  try {
    const res = await previewDigest({
      period: digestForm.period,
      startDate: digestForm.range?.[0],
      endDate: digestForm.range?.[1]
    })
    previewHtml.value = res.data.html
  } finally {
    previewing.value = false
  }
}
async function onSendDigest() {
  const res = await sendDigest({
    period: digestForm.period,
    startDate: digestForm.range?.[0],
    endDate: digestForm.range?.[1]
  })
  ElMessage.success(`摘要已发送给 ${res.data.recipients} 人 (${res.data.startDate} ~ ${res.data.endDate})`)
  digestVisible.value = false
  loadData()
  window.dispatchEvent(new Event('notify-refresh'))
}

const ALLOWED_PREVIEW_TAGS = new Set([
  'B',
  'BR',
  'DIV',
  'EM',
  'H1',
  'H2',
  'H3',
  'H4',
  'I',
  'LI',
  'OL',
  'P',
  'SMALL',
  'SPAN',
  'STRONG',
  'TABLE',
  'TBODY',
  'TD',
  'TH',
  'THEAD',
  'TR',
  'UL'
])
const ALLOWED_PREVIEW_ATTRS = new Set(['colspan', 'rowspan'])

function sanitizeHtml(html: string) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  scrubElement(doc.body)
  return doc.body.innerHTML
}

function scrubElement(parent: Element) {
  Array.from(parent.children).forEach((child) => {
    if (!ALLOWED_PREVIEW_TAGS.has(child.tagName)) {
      child.replaceWith(document.createTextNode(child.textContent || ''))
      return
    }
    Array.from(child.attributes).forEach((attr) => {
      if (!ALLOWED_PREVIEW_ATTRS.has(attr.name.toLowerCase())) {
        child.removeAttribute(attr.name)
      }
    })
    scrubElement(child)
  })
}

onMounted(loadData)
defineExpose({ reload: loadData })
</script>

<template>
  <div>
    <div class="bar">
      <el-radio-group v-model="query.onlyUnread" @change="switchTab">
        <el-radio-button :value="false">全部</el-radio-button>
        <el-radio-button :value="true">未读</el-radio-button>
      </el-radio-group>
      <div class="bar-right">
        <el-button v-if="isAdmin" type="primary" @click="openAnnounce">发送公告</el-button>
        <el-button v-if="isAdmin" type="success" plain @click="openDigest">发送周报/月报</el-button>
        <el-button :disabled="!selected.length" @click="onMarkSelected">选择已读</el-button>
        <el-button @click="onMarkAll">全部已读</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="list" border style="margin-top: 12px" row-key="id"
      :row-class-name="({ row }) => (row.isRead === 1 ? 'row-read' : '')"
      @selection-change="onSelectionChange">
      <el-table-column type="selection" width="44" :reserve-selection="false" />
      <el-table-column label="类型" width="80" align="left">
        <template #default="{ row }: any">
          <el-tag :type="TYPE_META[row.type]?.tag || 'info'" size="small" :effect="row.isRead === 1 ? 'plain' : 'dark'">
            {{ TYPE_META[row.type]?.label || row.type }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip>
        <template #default="{ row }: any">
          <span :class="row.isRead === 0 ? 'unread' : 'read'">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }: any">
          <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          <el-button v-if="isAdmin" link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      style="margin-top: 12px; justify-content: flex-end"
      @current-change="loadData"
      @size-change="switchTab"
    />

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title" width="720px">
      <div v-if="detail?.type === 'REPORT'" class="detail-body report-body" v-html="safeDetailHtml"></div>
      <div v-else-if="detail" class="detail-body">{{ detail.content }}</div>
    </el-dialog>

    <!-- 发送公告 -->
    <el-dialog v-model="annVisible" title="发送公告" width="520px">
      <el-form ref="annFormRef" :model="annForm" :rules="annRules" label-width="70px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="annForm.title" placeholder="公告标题" />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="annForm.content" type="textarea" :rows="5" placeholder="公告内容" />
        </el-form-item>
        <el-form-item label="推送范围">
          <el-radio-group v-model="annForm.mode">
            <el-radio-button value="ALL">全部用户</el-radio-button>
            <el-radio-button value="SELECTED">指定用户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="annForm.mode === 'SELECTED'" label="接收人">
          <el-select
            v-model="annForm.recipientIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择接收人"
            style="width: 100%"
          >
            <el-option v-for="r in recipientOptions" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发送邮件">
          <el-switch v-model="annForm.sendEmail" />
          <span class="hint">同时发邮件给有邮箱的用户（需后端开启 SMTP 才真正发信）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="annVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAnnounce">发送</el-button>
      </template>
    </el-dialog>

    <!-- 发送周报/月报 -->
    <el-dialog v-model="digestVisible" title="发送经营摘要" width="760px">
      <el-form :inline="true">
        <el-form-item label="周期">
          <el-select v-model="digestForm.period" style="width: 120px">
            <el-option label="周报" value="WEEKLY" />
            <el-option label="月报" value="MONTHLY" />
          </el-select>
        </el-form-item>
        <el-form-item label="自定义区间">
          <el-date-picker
            v-model="digestForm.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始(默认按周期)"
            end-placeholder="结束"
            style="width: 280px"
          />
        </el-form-item>
        <el-form-item>
          <el-button :loading="previewing" @click="onPreview">预览</el-button>
        </el-form-item>
      </el-form>
      <div v-if="previewHtml" class="preview" v-html="safePreviewHtml"></div>
      <div v-else class="preview-empty">点击「预览」查看摘要内容</div>
      <template #footer>
        <el-button @click="digestVisible = false">取消</el-button>
        <el-button type="primary" @click="onSendDigest">确认发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bar-right {
  display: flex;
  gap: 8px;
}
.unread {
  font-weight: 600;
  color: #303133;
}
.read {
  color: #a8abb2;
}
:deep(.row-read) {
  background: #fafafa;
}
:deep(.row-read) td {
  color: #a8abb2;
}
.detail-body {
  max-height: 60vh;
  overflow: auto;
  white-space: pre-wrap;
}
.report-body {
  white-space: normal;
}
.report-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0 14px;
}
.report-body :deep(th),
.report-body :deep(td) {
  border: 1px solid #ebeef5;
  padding: 8px;
  text-align: left;
}
.report-body :deep(th) {
  background: #f5f7fa;
}
.hint {
  margin-left: 10px;
  color: #909399;
  font-size: 12px;
}
.preview {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px;
  max-height: 50vh;
  overflow: auto;
}
.preview-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
}
</style>
