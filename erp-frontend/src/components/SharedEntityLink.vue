<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  tryParseEntityRef,
  resolveEntityRef,
  checkEntityPermission,
  navigateToEntity,
  type EntityRefResolved
} from '@/utils/entityLink'

const props = defineProps<{
  /** 消息内容文本, 可能是纯文本或包含实体引用 JSON */
  text: string
}>()

const userStore = useUserStore()

const resolved = computed<EntityRefResolved | null>(() => {
  const ref = tryParseEntityRef(props.text)
  if (!ref) return null
  return resolveEntityRef(ref)
})

const hasAccess = computed(() => {
  if (!resolved.value) return false
  return checkEntityPermission(resolved.value, (perm) => userStore.hasPermission(perm))
})

const remarkText = computed(() => {
  return resolved.value?.extra?.remark as string | undefined
})

const detailLines = computed(() => {
  if (!resolved.value || !resolved.value.config.detailFields || !resolved.value.extra) return []
  return resolved.value.config.detailFields
    .filter((f) => f.key !== 'remark' && resolved.value!.extra![f.key] != null && resolved.value!.extra![f.key] !== '')
    .map((f) => ({
      label: f.label,
      value: String(resolved.value!.extra![f.key])
    }))
})

function handleClick() {
  if (!resolved.value) return
  navigateToEntity(resolved.value)
}
</script>

<template>
  <div v-if="resolved" class="entity-card" :class="{ clickable: hasAccess }" @click="hasAccess ? handleClick() : undefined">
    <div class="entity-card__head">
      <el-tag size="small" type="primary" effect="plain" class="entity-tag">
        {{ resolved.config.label }}
      </el-tag>
      <span class="entity-name">{{ resolved.name }}</span>
    </div>
    <div v-if="remarkText" class="entity-card__remark">{{ remarkText }}</div>
    <div v-if="detailLines.length" class="entity-card__body">
      <div v-for="d in detailLines" :key="d.label" class="entity-detail">
        <span class="detail-label">{{ d.label }}</span>
        <span class="detail-value">{{ d.value }}</span>
      </div>
    </div>
    <div v-if="hasAccess" class="entity-card__action">
      <el-icon><Link /></el-icon>
      <span>点击查看详情</span>
    </div>
    <div v-else class="entity-card__action no-perm">
      <el-icon><WarningFilled /></el-icon>
      <span>暂无访问权限</span>
    </div>
  </div>
  <span v-else class="plain-text">{{ text }}</span>
</template>

<style scoped>
.entity-card {
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  padding: 10px 12px;
  margin: 4px 0;
  cursor: default;
  transition: box-shadow 0.2s;
}
.entity-card.clickable {
  cursor: pointer;
}
.entity-card.clickable:hover {
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
  border-color: #409eff;
}
.entity-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.entity-tag {
  flex-shrink: 0;
}
.entity-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.entity-card__remark {
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  white-space: pre-wrap;
  word-break: break-word;
  padding: 4px 0;
  border-bottom: 1px dashed #d9ecff;
}
.entity-card__body {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-bottom: 6px;
}
.entity-detail {
  font-size: 12px;
  color: #606266;
  display: flex;
  gap: 4px;
}
.detail-label {
  color: #909399;
}
.detail-value {
  color: #303133;
  font-weight: 500;
}
.entity-card__action {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #409eff;
  margin-top: 2px;
}
.entity-card__action.no-perm {
  color: #e6a23c;
}
.plain-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  color: #303133;
}
</style>
