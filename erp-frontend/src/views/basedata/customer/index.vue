<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Document, Download, Plus, Refresh, Search, Switch, Upload, View } from '@element-plus/icons-vue'
import ImportExcelDialog from '@/components/ImportExcelDialog.vue'
import CurrencySelect from '@/components/CurrencySelect.vue'
import {
  pageCustomer,
  getCustomer,
  createCustomer,
  updateCustomer,
  deleteCustomer,
  importCustomer,
  exportCustomer,
  downloadCustomerTemplate,
  downloadCustomerDoc,
  assignCustomerAssignee,
  transferCustomerToPublicPool,
  listCustomerAssignees,
  listCustomerExtras,
  saveCustomerExtras,
  type Customer,
  type CustomerAssigneeOption,
  type CustomerExtraField
} from '@/api/customer'
import { useUserStore } from '@/stores/user'

const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const list = ref<Customer[]>([])
const total = ref(0)
const query = reactive<{ pageNum: number; pageSize: number; keyword: string; poolType: 'PERSONAL' | 'PUBLIC' }>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  poolType: 'PERSONAL'
})

const userStore = useUserStore()
const canManageCustomerPool = computed(() =>
  ['sales_manager', 'super_admin', 'admin'].some((role) => userStore.roles.includes(role))
)

const detailVisible = ref(false)
const importVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Customer>(emptyForm())
const extraFields = ref<CustomerExtraField[]>([])
const assignVisible = ref(false)
const assigning = ref(false)
const assigneesLoading = ref(false)
const assignees = ref<CustomerAssigneeOption[]>([])
const assigningCustomer = ref<Customer>()
const assigneeId = ref<number | string>()
const transferVisible = ref(false)
const transferring = ref(false)
const transferringCustomer = ref<Customer>()
const transferReason = ref('')

function emptyForm(): Customer {
  return {
    custCode: '',
    contactName: '',
    contactPosition: '',
    country: '',
    companyName: '',
    address: '',
    currency: 'USD',
    poolType: 'PERSONAL',
    website: '',
    phone: '',
    email: '',
    socialContact: '',
    remark: ''
  }
}

const rules: FormRules<Customer> = {
  custCode: [{ max: 64, message: '客户ID最多 64 个字符', trigger: 'blur' }],
  companyName: [{ max: 255, message: '公司名称最多 255 个字符', trigger: 'blur' }],
  email: [{ type: 'email', message: '电子邮箱格式不正确', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageCustomer({
      ...query,
      poolType: canManageCustomerPool.value ? query.poolType : 'PERSONAL'
    })
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

function onPoolChange() {
  onSearch()
}

function addExtraField() {
  extraFields.value.push({ fieldKey: '', fieldValue: '', sort: extraFields.value.length + 1 })
}

function removeExtraField(index: number) {
  extraFields.value.splice(index, 1)
}

function normalizeExtras() {
  return extraFields.value
    .filter((item) => item.fieldKey?.trim())
    .map((item, index) => ({
      fieldKey: item.fieldKey.trim(),
      fieldValue: item.fieldValue?.trim() || '',
      sort: index + 1
    }))
}

function openCreate() {
  Object.assign(form, emptyForm())
  form.id = undefined
  extraFields.value = []
  detailVisible.value = true
}

async function openDetail(row: Customer) {
  if (!row.id) return
  detailVisible.value = true
  detailLoading.value = true
  try {
    const [customerRes, extrasRes] = await Promise.all([
      getCustomer(row.id),
      listCustomerExtras(row.id)
    ])
    Object.assign(form, emptyForm(), customerRes.data)
    extraFields.value = extrasRes.data.length ? extrasRes.data : []
  } finally {
    detailLoading.value = false
  }
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (form.id) {
      await updateCustomer(form)
      await saveCustomerExtras(form.id, normalizeExtras())
      ElMessage.success('客户资料已保存')
    } else {
      const res = await createCustomer(form)
      if (res.data) {
        form.id = res.data
        const extras = normalizeExtras()
        if (extras.length) {
          await saveCustomerExtras(res.data, extras)
        }
      }
      ElMessage.success('客户已创建')
    }
    detailVisible.value = false
    await loadData()
  } finally {
    saving.value = false
  }
}

async function onDeleteCurrent() {
  if (!form.id) return
  await ElMessageBox.confirm(`确认删除客户「${form.companyName || form.custCode}」？`, '提示', {
    type: 'warning'
  })
  await deleteCustomer(form.id)
  ElMessage.success('客户已删除')
  detailVisible.value = false
  await loadData()
}

function onExport() {
  exportCustomer(query.keyword, canManageCustomerPool.value ? query.poolType : 'PERSONAL')
}

function onDownloadDoc() {
  if (form.id) {
    downloadCustomerDoc(form.id, form.custCode || '客户资料')
  }
}

async function openAssign(row: Customer) {
  if (!row.id) return
  assigningCustomer.value = row
  assigneeId.value = undefined
  assignVisible.value = true
  assigneesLoading.value = true
  try {
    const res = await listCustomerAssignees()
    assignees.value = res.data
  } finally {
    assigneesLoading.value = false
  }
}

async function submitAssign() {
  if (!assigningCustomer.value?.id || !assigneeId.value) return
  assigning.value = true
  try {
    await assignCustomerAssignee(assigningCustomer.value.id, assigneeId.value)
    ElMessage.success('客户担当已分配，客户已转入个人发展客户池')
    assignVisible.value = false
    await loadData()
  } finally {
    assigning.value = false
  }
}

function openTransfer(row: Customer) {
  if (!row.id) return
  transferringCustomer.value = row
  transferReason.value = ''
  transferVisible.value = true
}

async function submitTransfer() {
  if (!transferringCustomer.value?.id) return
  transferring.value = true
  try {
    await transferCustomerToPublicPool(transferringCustomer.value.id, transferReason.value)
    ElMessage.success('客户已转入公共发展客户池，原担当已收到系统通知')
    transferVisible.value = false
    await loadData()
  } finally {
    transferring.value = false
  }
}

function poolLabel(poolType?: Customer['poolType']) {
  return poolType === 'PUBLIC' ? '公共发展客户池' : '个人发展客户池'
}

onMounted(() => {
  if (canManageCustomerPool.value) {
    query.poolType = 'PUBLIC'
  }
  loadData()
})
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-tabs v-if="canManageCustomerPool" v-model="query.poolType" class="pool-tabs" @tab-change="onPoolChange">
        <el-tab-pane label="公共发展客户池" name="PUBLIC" />
        <el-tab-pane label="个人发展客户池" name="PERSONAL" />
      </el-tabs>

      <el-form :inline="true" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="客户ID / 公司 / 联系人 / 国家"
            clearable
            style="width: 260px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <el-button v-permission="'customer:create'" type="primary" :icon="Plus" @click="openCreate">新建</el-button>
        <el-button v-permission="'customer:import'" :icon="Upload" @click="importVisible = true">导入</el-button>
        <el-button v-permission="'customer:export'" :icon="Document" @click="downloadCustomerTemplate">模板</el-button>
        <el-button v-permission="'customer:export'" :icon="Download" @click="onExport">导出</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe style="margin-top: 12px">
        <el-table-column v-if="canManageCustomerPool" label="客户池" width="150">
          <template #default="{ row }: any">
            <el-tag :type="row.poolType === 'PUBLIC' ? 'warning' : 'success'" effect="light">
              {{ poolLabel(row.poolType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="custCode" label="客户ID" width="130" />
        <el-table-column prop="companyName" label="公司" min-width="190" show-overflow-tooltip />
        <el-table-column v-if="canManageCustomerPool" label="担当" width="128" show-overflow-tooltip>
          <template #default="{ row }: any">{{ row.ownerName || '未分配' }}</template>
        </el-table-column>
        <el-table-column prop="contactName" label="联系人" width="120" show-overflow-tooltip />
        <el-table-column prop="country" label="国家" width="110" show-overflow-tooltip />
        <el-table-column prop="phone" label="电话" width="140" show-overflow-tooltip />
        <el-table-column prop="website" label="网站" min-width="160" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }: any">
            <el-button
              v-if="canManageCustomerPool && query.poolType === 'PUBLIC'"
              v-permission="'customer:update'"
              link
              type="primary"
              @click="openAssign(row)"
            >
              分配担当
            </el-button>
            <el-button
              v-if="canManageCustomerPool && query.poolType === 'PERSONAL'"
              v-permission="'customer:update'"
              link
              type="warning"
              :icon="Switch"
              @click="openTransfer(row)"
            >
              转入公共池
            </el-button>
            <el-button v-permission="'customer:list'" link type="primary" :icon="View" @click="openDetail(row)">
              详情
            </el-button>
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

    <el-dialog
      v-model="detailVisible"
      :title="form.id ? `客户详情 - ${form.custCode || ''}` : '新建客户'"
      width="860px"
      top="6vh"
    >
      <el-skeleton v-if="detailLoading" :rows="8" animated />
      <div v-else>
        <div v-if="form.id" class="pool-summary">
          <el-tag :type="form.poolType === 'PUBLIC' ? 'warning' : 'success'" effect="light">
            {{ poolLabel(form.poolType) }}
          </el-tag>
          <span>担当：{{ form.ownerName || '未分配' }}</span>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="104px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="客户ID" prop="custCode">
                <el-input v-model="form.custCode" placeholder="留空按国家自动生成，如 CN0001" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="国家">
                <el-input v-model="form.country" placeholder="如 中国 / US" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="公司名称">
                <el-input v-model="form.companyName" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系人姓名">
                <el-input v-model="form.contactName" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="职位">
                <el-input v-model="form.contactPosition" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="币种">
                <CurrencySelect v-model="form.currency" placeholder="请选择或输入币种" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="联系电话">
                <el-input v-model="form.phone" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="电子邮箱" prop="email">
                <el-input v-model="form.email" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="网站">
                <el-input v-model="form.website" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="社媒联系">
                <el-input v-model="form.socialContact" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="地址">
                <el-input v-model="form.address" />
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="备注">
                <el-input v-model="form.remark" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="extra-header">
          <span>其他资料</span>
            <el-button link type="primary" :icon="Plus" @click="addExtraField">
            新增字段
          </el-button>
        </div>
        <div class="extra-list">
          <div v-for="(item, index) in extraFields" :key="index" class="extra-row">
            <el-input v-model="item.fieldKey" placeholder="键" />
            <el-input v-model="item.fieldValue" placeholder="值" />
            <el-button circle :icon="Delete" @click="removeExtraField(index)" />
          </div>
          <el-empty v-if="!extraFields.length" description="暂无其他资料" :image-size="72" />
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <div>
            <el-button v-if="form.id" v-permission="'customer:export'" :icon="Download" @click="onDownloadDoc">
              资料表
            </el-button>
            <el-button
              v-if="form.id"
              v-permission="'customer:delete'"
              type="danger"
              :icon="Delete"
              @click="onDeleteCurrent"
            >
              删除
            </el-button>
          </div>
          <div>
            <el-button @click="detailVisible = false">取消</el-button>
            <el-button v-if="form.id" v-permission="'customer:update'" type="primary" :loading="saving" @click="submitForm">
              保存
            </el-button>
            <el-button v-else v-permission="'customer:create'" type="primary" :loading="saving" @click="submitForm">
              保存
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <ImportExcelDialog
      v-model="importVisible"
      title="导入客户资料"
      :import-api="importCustomer"
      :download-template="downloadCustomerTemplate"
      @success="loadData"
    />

    <el-dialog v-model="assignVisible" title="分配客户担当" width="480px" append-to-body>
      <el-form label-width="104px">
        <el-form-item label="客户">
          <span>{{ assigningCustomer?.companyName || assigningCustomer?.custCode }}</span>
        </el-form-item>
        <el-form-item label="跟单业务员" required>
          <el-select v-model="assigneeId" :loading="assigneesLoading" placeholder="请选择业务员" style="width: 100%">
            <el-option v-for="item in assignees" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!assigneeId" :loading="assigning" @click="submitAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转入公共发展客户池" width="520px" append-to-body>
      <el-form label-width="88px">
        <el-form-item label="客户">
          <span>{{ transferringCustomer?.companyName || transferringCustomer?.custCode }}</span>
        </el-form-item>
        <el-form-item label="当前担当">
          <span>{{ transferringCustomer?.ownerName || '未分配' }}</span>
        </el-form-item>
        <el-form-item label="转移理由">
          <el-input
            v-model="transferReason"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="选填，填写后将随系统通知发送给原担当"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" :loading="transferring" @click="submitTransfer">确认转移</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.pool-tabs {
  margin-bottom: 4px;
}

.pool-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}

.extra-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 10px;
  font-weight: 600;
}

.extra-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.extra-row {
  display: grid;
  grid-template-columns: minmax(140px, 220px) 1fr 34px;
  gap: 8px;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

@media (max-width: 760px) {
  .extra-row {
    grid-template-columns: 1fr;
  }

  .dialog-footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
