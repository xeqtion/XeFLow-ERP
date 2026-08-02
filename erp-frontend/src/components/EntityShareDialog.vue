<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Promotion, Select } from '@element-plus/icons-vue'
import { listContacts, sendMessage, type Contact } from '@/api/message'
import { getRegisteredTypes, encodeEntityRef, type EntityTypeConfig } from '@/utils/entityLink'

const emit = defineEmits<{
  (e: 'done'): void
}>()

const visible = ref(false)
const loadingEntity = ref(false)
const sending = ref(false)

/* 步骤: 1=选实体类型, 2=选实体, 3=选联系人+发送 */
const step = ref(1)

/* 实体类型 */
const entityTypes = getRegisteredTypes()
const selectedType = ref<EntityTypeConfig | null>(null)

/* 实体列表 */
const entityList = ref<any[]>([])
const entityKeyword = ref('')
const selectedEntity = ref<any | null>(null)

/* 联系人列表 */
const contacts = ref<Contact[]>([])
const selectedContact = ref<Contact | null>(null)
const contactKeyword = ref('')

/* 附言 */
const remark = ref('')

const filteredContacts = computed(() => {
  const k = contactKeyword.value.trim().toLowerCase()
  if (!k) return contacts.value
  return contacts.value.filter(
    (c) => c.name.toLowerCase().includes(k) || (c.username || '').toLowerCase().includes(k)
  )
})

async function open() {
  visible.value = true
  step.value = 1
  selectedType.value = null
  selectedEntity.value = null
  selectedContact.value = null
  entityList.value = []
  entityKeyword.value = ''
  contactKeyword.value = ''
  remark.value = ''
  // 预加载联系人
  try {
    contacts.value = (await listContacts()).data
  } catch {
    /* ignore */
  }
}

function selectType(type: EntityTypeConfig) {
  selectedType.value = type
  step.value = 2
  loadEntityList()
}

async function loadEntityList() {
  if (!selectedType.value?.loader) return
  loadingEntity.value = true
  try {
    entityList.value = await selectedType.value.loader(entityKeyword.value)
  } finally {
    loadingEntity.value = false
  }
}

function onEntitySearch() {
  loadEntityList()
}

function selectEntity(entity: any) {
  selectedEntity.value = entity
  step.value = 3
}

function backToEntityList() {
  step.value = 2
  selectedContact.value = null
}

async function onSend() {
  if (!selectedType.value || !selectedEntity.value) {
    ElMessage.warning('请选择要分享的数据')
    return
  }
  if (!selectedContact.value) {
    ElMessage.warning('请选择联系人')
    return
  }
  if (selectedEntity.value.id == null) {
    ElMessage.warning('所选数据的 ID 无效')
    return
  }

  sending.value = true
  try {
    const config = selectedType.value
    const entity = selectedEntity.value

    const extra: Record<string, unknown> = {}
    // 收集详情字段
    if (config.detailFields) {
      for (const f of config.detailFields) {
        if (entity[f.key] != null) {
          extra[f.key] = entity[f.key]
        }
      }
    }
    // 附言嵌入 extra，不与实体 JSON 拼接
    const remarkText = remark.value.trim()
    if (remarkText) {
      extra.remark = remarkText
    }

    const entityJson = encodeEntityRef({
      type: config.type,
      id: entity.id,
      name: config.getDisplayName(entity.id, entity),
      extra
    })

    await sendMessage(selectedContact.value.userId, entityJson, [])
    ElMessage.success('已分享')
    visible.value = false
    emit('done')
  } catch {
    /* interceptor already handled */
  } finally {
    sending.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="分享系统数据" width="480px" :close-on-click-modal="false" append-to-body>
    <!-- 步骤1: 选择实体类型 -->
    <div v-if="step === 1">
      <p class="step-title">选择要分享的数据类型</p>
      <div class="type-grid">
        <div
          v-for="t in entityTypes"
          :key="t.type"
          class="type-card"
          @click="selectType(t)"
        >
          <div class="type-card__label">{{ t.label }}</div>
          <el-tag size="small" type="info" effect="plain">{{ t.type }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 步骤2: 选择具体实体 -->
    <div v-if="step === 2">
      <div class="step-header">
        <el-button text @click="step = 1">&lt; 返回</el-button>
        <span class="step-title">选择{{ selectedType?.label }}</span>
      </div>
      <el-input
        v-model="entityKeyword"
        placeholder="搜索..."
        clearable
        :prefix-icon="Search"
        style="margin: 12px 0"
        @keyup.enter="onEntitySearch"
      />
      <el-scrollbar max-height="320px" v-loading="loadingEntity">
        <div v-if="!entityList.length && !loadingEntity" class="empty">暂无数据</div>
        <div
          v-for="item in entityList"
          :key="item.id"
          class="entity-row"
          @click="selectEntity(item)"
        >
          <div class="entity-row__name">{{ selectedType?.getDisplayName(item.id, item) }}</div>
          <div class="entity-row__sub">
            <span>{{ item.remark || item.supplier || '' }}</span>
          </div>
        </div>
      </el-scrollbar>
    </div>

    <!-- 步骤3: 选择联系人 + 发送 -->
    <div v-if="step === 3">
      <div class="step-header">
        <el-button text @click="backToEntityList">&lt; 返回</el-button>
        <span class="step-title">发送给</span>
      </div>
      <el-input
        v-model="contactKeyword"
        placeholder="搜索联系人..."
        clearable
        :prefix-icon="Search"
        style="margin: 12px 0"
      />
      <el-scrollbar max-height="200px">
        <div
          v-for="c in filteredContacts"
          :key="c.userId"
          class="contact-row"
          :class="{ active: selectedContact?.userId === c.userId }"
          @click="selectedContact = c"
        >
          <el-avatar :size="28" class="c-avatar">{{ (c.name || 'U').charAt(0) }}</el-avatar>
          <div class="contact-row__meta">
            <div class="contact-row__name">{{ c.name }}</div>
            <div class="contact-row__sub">{{ c.username }}</div>
          </div>
          <el-icon v-if="selectedContact?.userId === c.userId" class="check-icon"><Select /></el-icon>
        </div>
        <div v-if="!filteredContacts.length" class="empty">无联系人</div>
      </el-scrollbar>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="2"
        placeholder="附言（可选）"
        style="margin-top: 12px"
      />
      <div class="preview" v-if="selectedEntity">
        <span class="preview-label">即将分享:</span>
        <span class="preview-value">{{ selectedType?.getDisplayName(selectedEntity.id, selectedEntity) }}</span>
      </div>
      <div class="send-bar">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :icon="Promotion"
          :disabled="!selectedContact"
          :loading="sending"
          @click="onSend"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<style scoped>
.step-title {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.type-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.type-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}
.type-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.12);
}
.type-card__label {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.entity-row {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.entity-row:hover {
  background: #f5f7fa;
}
.entity-row__name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}
.entity-row__sub {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.contact-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.contact-row:hover {
  background: #f5f7fa;
}
.contact-row.active {
  background: #ecf5ff;
}
.c-avatar {
  background: linear-gradient(135deg, #1f3a93, #2c82c9);
  color: #fff;
  flex-shrink: 0;
}
.contact-row__meta {
  flex: 1;
  min-width: 0;
}
.contact-row__name {
  font-size: 14px;
  color: #303133;
}
.contact-row__sub {
  font-size: 12px;
  color: #909399;
}
.check-icon {
  color: #409eff;
  font-size: 16px;
}
.preview {
  margin-top: 12px;
  padding: 8px 10px;
  background: #f0f9ff;
  border-radius: 6px;
  font-size: 13px;
}
.preview-label {
  color: #909399;
  margin-right: 6px;
}
.preview-value {
  color: #303133;
  font-weight: 500;
}
.send-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
}
</style>
