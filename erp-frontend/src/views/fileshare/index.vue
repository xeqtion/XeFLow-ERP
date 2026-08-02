<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  pageFile,
  uploadFile,
  downloadSharedFile,
  deleteFile,
  type SharedFile
} from '@/api/fileshare'

const loading = ref(false)
const list = ref<SharedFile[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

const uploadVisible = ref(false)
const uploading = ref(false)
const fileList = ref<UploadFile[]>([])
const uploadForm = reactive({
  file: null as File | null,
  category: '',
  remark: '',
  expirePreset: '30',
  customDays: undefined as number | undefined
})

const expireOptions = [
  { label: '24小时', value: '1' },
  { label: '7天', value: '7' },
  { label: '15天', value: '15' },
  { label: '30天', value: '30' },
  { label: '90天', value: '90' },
  { label: '180天', value: '180' },
  { label: '自定义', value: 'custom' }
]

function fmtSize(n?: number) {
  if (!n) return '-'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(1) + ' MB'
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageFile(query)
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
  query.keyword = ''
  onSearch()
}

function openUpload() {
  uploadVisible.value = true
  resetUploadForm()
}

function resetUploadForm() {
  uploadForm.file = null
  uploadForm.category = ''
  uploadForm.remark = ''
  uploadForm.expirePreset = '30'
  uploadForm.customDays = undefined
  fileList.value = []
}

function onFileChange(file: UploadFile, files: UploadFile[]) {
  fileList.value = files.slice(-1)
  uploadForm.file = file.raw || null
}

function onFileRemove() {
  fileList.value = []
  uploadForm.file = null
}

function selectedExpireDays() {
  if (uploadForm.expirePreset !== 'custom') {
    return Number(uploadForm.expirePreset)
  }
  return Number(uploadForm.customDays)
}

async function submitUpload() {
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }
  const expireDays = selectedExpireDays()
  if (!Number.isInteger(expireDays) || expireDays < 1 || expireDays > 180) {
    ElMessage.warning('有效时长需为 1-180 天')
    return
  }
  uploading.value = true
  try {
    await uploadFile(uploadForm.file, uploadForm.category || undefined, uploadForm.remark || undefined, expireDays)
    ElMessage.success(`已上传 ${uploadForm.file.name}`)
    uploadVisible.value = false
    loadData()
  } finally {
    uploading.value = false
  }
}

function onDownload(row: SharedFile) {
  downloadSharedFile(row.id, row.fileName)
}

async function onDelete(row: SharedFile) {
  await ElMessageBox.confirm(`确认删除「${row.fileName}」？`, '提示', { type: 'warning' })
  await deleteFile(row.id)
  ElMessage.success('已删除')
  loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="搜索">
          <el-input
            v-model="query.keyword"
            placeholder="文件名 / 分类 / 格式 / 上传人"
            clearable
            style="width: 320px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'fileshare:upload'" type="primary" :icon="UploadFilled" @click="openUpload">上传文件</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="fileName" label="文件名" min-width="240" show-overflow-tooltip />
        <el-table-column label="分类" width="120">
          <template #default="{ row }: any">{{ row.category || '无' }}</template>
        </el-table-column>
        <el-table-column label="格式" width="90">
          <template #default="{ row }: any">{{ row.fileFormat || '无' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }: any">{{ fmtSize(row.size) }}</template>
        </el-table-column>
        <el-table-column prop="uploaderName" label="上传人" width="120" />
        <el-table-column prop="remark" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column prop="expireTime" label="过期时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }: any">
            <el-button link type="primary" @click="onDownload(row)">下载</el-button>
            <el-button v-permission="'fileshare:delete'" link type="danger" @click="onDelete(row)">删除</el-button>
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
        @size-change="onSearch"
      />
    </el-card>

    <el-dialog v-model="uploadVisible" title="上传文件" width="640px" top="8vh" @closed="resetUploadForm">
      <div class="upload-layout">
        <el-upload
          drag
          class="upload-drop"
          :auto-upload="false"
          :limit="1"
          :file-list="fileList"
          :on-change="onFileChange"
          :on-remove="onFileRemove"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入文件或点击选择</div>
          <template #tip>
            <div class="el-upload__tip">单文件最大 50MB</div>
          </template>
        </el-upload>

        <el-form class="upload-form" label-position="top">
          <el-form-item label="分类">
            <el-input v-model="uploadForm.category" placeholder="未填写则显示“无”" maxlength="64" />
          </el-form-item>
          <el-form-item label="有效时长">
            <div class="expire-row">
              <el-select v-model="uploadForm.expirePreset" style="width: 150px">
                <el-option v-for="item in expireOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input-number
                v-if="uploadForm.expirePreset === 'custom'"
                v-model="uploadForm.customDays"
                :min="1"
                :max="180"
                :precision="0"
                :controls="false"
                placeholder="天数"
                style="width: 110px"
              />
            </div>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="uploadForm.remark" type="textarea" :rows="4" maxlength="255" show-word-limit />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.upload-layout {
  display: grid;
  grid-template-columns: 230px 1fr;
  gap: 22px;
  align-items: start;
}

.upload-drop {
  width: 100%;
}

.upload-drop :deep(.el-upload) {
  width: 100%;
}

.upload-drop :deep(.el-upload-dragger) {
  width: 100%;
  min-height: 210px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  box-sizing: border-box;
  border-radius: 6px;
}

.upload-drop :deep(.el-icon--upload) {
  margin-left: 0;
  margin-right: 0;
}

.upload-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.expire-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>
