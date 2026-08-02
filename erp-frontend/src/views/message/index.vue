<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadUserFile } from 'element-plus'
import { Delete, Download, InfoFilled, Paperclip, Promotion, Search, Share } from '@element-plus/icons-vue'
import NotificationPanel from '@/components/NotificationPanel.vue'
import SharedEntityLink from '@/components/SharedEntityLink.vue'
import EntityShareDialog from '@/components/EntityShareDialog.vue'
import { unreadCount as notifUnreadCount } from '@/api/notification'
import {
  clearConversation,
  deleteMessage,
  downloadAttachment,
  getContact,
  getConversation,
  listContacts,
  sendMessage,
  type Contact,
  type UserMessage
} from '@/api/message'
import { containsEntityRef } from '@/utils/entityLink'

const route = useRoute()
const activeTab = ref<'chat' | 'notice'>(route.query.tab === 'notice' ? 'notice' : 'chat')
watch(
  () => route.query.tab,
  (tab) => {
    activeTab.value = tab === 'notice' ? 'notice' : 'chat'
  }
)

const contacts = ref<Contact[]>([])
const keyword = ref('')
const activeId = ref<number | null>(null)
const messages = ref<UserMessage[]>([])
const loadingMsg = ref(false)
const sending = ref(false)
const draft = ref('')
const fileList = ref<UploadUserFile[]>([])
const noticeUnread = ref(0)
const threadRef = ref<HTMLElement>()
const shareDialogRef = ref<InstanceType<typeof EntityShareDialog>>()
const detailVisible = ref(false)
const contactDetail = ref<Contact | null>(null)

const chatUnread = computed(() => contacts.value.reduce((sum, c) => sum + (c.unread || 0), 0))
const activeContact = computed(() => contacts.value.find((c) => c.userId === activeId.value) || null)
const filteredContacts = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return contacts.value
  return contacts.value.filter((c) =>
    [c.name, c.username, c.realName, c.nickname, c.email, c.phone]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(k))
  )
})

function initials(name?: string) {
  return (name || 'U').charAt(0).toUpperCase()
}

function fmtSize(n?: number) {
  if (!n) return ''
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(1)} MB`
}

function dateOnly(value?: string) {
  return value ? value.slice(0, 10) : ''
}

function timeOnly(value?: string) {
  return value ? value.slice(11, 16) : ''
}

function showDateDivider(index: number) {
  const current = dateOnly(messages.value[index]?.createTime)
  const prev = index > 0 ? dateOnly(messages.value[index - 1]?.createTime) : ''
  return !!current && current !== prev
}

async function loadContacts() {
  contacts.value = (await listContacts()).data
}

async function loadNoticeUnread() {
  try {
    noticeUnread.value = (await notifUnreadCount()).data.count
  } catch {
    /* ignore */
  }
}

async function selectContact(c: Contact) {
  activeId.value = c.userId
  await loadConversation()
  await loadContacts()
  window.dispatchEvent(new Event('message-refresh'))
}

async function loadConversation() {
  if (activeId.value == null) return
  loadingMsg.value = true
  try {
    messages.value = (await getConversation(activeId.value)).data
    await scrollToBottom()
  } finally {
    loadingMsg.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (threadRef.value) threadRef.value.scrollTop = threadRef.value.scrollHeight
}

function onFileChange() {
  if (fileList.value.length > 9) {
    ElMessage.warning('一次最多选择9个附件')
    fileList.value = fileList.value.slice(-9)
  }
}

function removeDraftFile(idx: number) {
  fileList.value.splice(idx, 1)
}

async function onSend() {
  if (activeId.value == null) return
  const content = draft.value.trim()
  const files = fileList.value.map((f) => f.raw).filter((f): f is NonNullable<typeof f> => !!f)
  if (!content && !files.length) {
    ElMessage.warning('请输入内容或选择附件')
    return
  }
  sending.value = true
  try {
    await sendMessage(activeId.value, content, files)
    draft.value = ''
    fileList.value = []
    await loadConversation()
    await loadContacts()
    window.dispatchEvent(new Event('message-refresh'))
  } finally {
    sending.value = false
  }
}

function openShareDialog() {
  shareDialogRef.value?.open()
}

function onShareDone() {
  if (activeId.value != null) loadConversation()
}

function onDownload(attId: number, fileName: string) {
  downloadAttachment(attId, fileName)
}

async function onDeleteMsg(m: UserMessage) {
  await deleteMessage(m.id)
  messages.value = messages.value.filter((x) => x.id !== m.id)
}

async function onClearConversation() {
  if (activeId.value == null || !activeContact.value) return
  await ElMessageBox.confirm(
    `确认清空与 ${activeContact.value.name} 的聊天记录吗？该操作只会清空你这一侧的会话视图。`,
    '清空聊天记录',
    {
      type: 'warning',
      confirmButtonText: '确认清空',
      cancelButtonText: '取消'
    }
  )
  await clearConversation(activeId.value)
  messages.value = []
  await loadContacts()
  window.dispatchEvent(new Event('message-refresh'))
  ElMessage.success('聊天记录已清空')
}

async function openContactDetail(c?: Contact | null) {
  const target = c || activeContact.value
  if (!target) return
  contactDetail.value = (await getContact(target.userId)).data
  detailVisible.value = true
}

let timer: number | undefined
const refreshNotice = () => loadNoticeUnread()
onMounted(async () => {
  await Promise.all([loadContacts(), loadNoticeUnread()])
  const target = contacts.value.find((c) => c.unread > 0) || contacts.value[0]
  if (target) await selectContact(target)
  timer = window.setInterval(async () => {
    await Promise.all([loadContacts(), loadNoticeUnread()])
    if (activeId.value != null && activeTab.value === 'chat') await loadConversation()
  }, 20000)
  window.addEventListener('notify-refresh', refreshNotice)
})
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer)
  window.removeEventListener('notify-refresh', refreshNotice)
})
</script>

<template>
  <div class="app-container message-page">
    <el-tabs v-model="activeTab" class="msg-tabs">
      <el-tab-pane name="chat">
        <template #label>
          <el-badge :value="chatUnread" :hidden="chatUnread === 0" :max="99" class="tab-badge">内部沟通</el-badge>
        </template>
        <div class="msg-shell">
          <aside class="contacts">
            <div class="contacts-head">
              <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜索联系人/邮箱/电话" clearable />
            </div>
            <el-scrollbar class="contacts-list">
              <div
                v-for="c in filteredContacts"
                :key="c.userId"
                class="contact"
                :class="{ active: c.userId === activeId }"
                @click="selectContact(c)"
              >
                <el-badge :value="c.unread" :hidden="c.unread === 0" :max="99" class="avatar-badge">
                  <el-avatar :size="38" :src="c.avatar || undefined" class="c-avatar">{{ initials(c.name) }}</el-avatar>
                </el-badge>
                <div class="c-meta">
                  <div class="c-name">{{ c.name }}</div>
                  <div class="c-sub">{{ c.roles || c.username }}</div>
                </div>
                <el-button class="contact-info" :icon="InfoFilled" circle text @click.stop="openContactDetail(c)" />
              </div>
              <div v-if="!filteredContacts.length" class="contacts-empty">暂无联系人</div>
            </el-scrollbar>
          </aside>

          <section class="chat">
            <template v-if="activeContact">
              <header class="chat-head">
                <div class="chat-person">
                  <el-avatar :size="36" :src="activeContact.avatar || undefined" class="c-avatar">
                    {{ initials(activeContact.name) }}
                  </el-avatar>
                  <div>
                    <div class="chat-title">{{ activeContact.name }}</div>
                    <div class="chat-sub">{{ activeContact.roles || activeContact.username }}</div>
                  </div>
                </div>
                <div class="chat-actions">
                  <span class="retention">消息保留14天</span>
                  <el-button :icon="Delete" text type="danger" @click="onClearConversation">清空聊天记录</el-button>
                </div>
              </header>

              <div ref="threadRef" v-loading="loadingMsg" class="chat-thread">
                <div v-if="!messages.length" class="thread-empty">还没有消息，发送第一条吧 👋</div>
                <template v-for="(m, index) in messages" :key="m.id">
                  <div v-if="showDateDivider(index)" class="date-divider">{{ dateOnly(m.createTime) }}</div>
                  <div class="row" :class="{ mine: m.mine }">
                    <el-avatar :size="32" class="m-avatar">{{ initials(m.senderName) }}</el-avatar>
                    <div class="bubble-wrap">
                      <div class="sender-line">
                        <span>{{ m.mine ? '我' : m.senderName }}</span>
                        <span>{{ timeOnly(m.createTime) }}</span>
                      </div>
                      <div class="bubble">
                        <SharedEntityLink v-if="m.content && containsEntityRef(m.content)" :text="m.content" />
                        <div v-else-if="m.content" class="bubble-text">{{ m.content }}</div>
                        <div v-if="m.attachments?.length" class="atts">
                          <div v-for="a in m.attachments" :key="a.id" class="att" @click="onDownload(a.id, a.fileName)">
                            <el-icon><Paperclip /></el-icon>
                            <span class="att-name">{{ a.fileName }}</span>
                            <span class="att-size">{{ fmtSize(a.size) }}</span>
                            <el-icon class="att-dl"><Download /></el-icon>
                          </div>
                        </div>
                      </div>
                      <button class="msg-del" title="从我的视图删除" @click="onDeleteMsg(m)">
                        <el-icon><Delete /></el-icon>
                      </button>
                    </div>
                  </div>
                </template>
              </div>

              <footer class="composer">
                <div v-if="fileList.length" class="composer-files">
                  <el-tag
                    v-for="(f, idx) in fileList"
                    :key="idx"
                    type="info"
                    class="draft-file"
                    closable
                    @close="removeDraftFile(idx)"
                  >
                    <el-icon><Paperclip /></el-icon>{{ f.name }}
                  </el-tag>
                </div>
                <el-input
                  v-model="draft"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  maxlength="2000"
                  show-word-limit
                  placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                  @keydown.enter.exact.prevent="onSend"
                />
                <div class="composer-bar">
                  <div class="composer-left">
                    <el-upload
                      v-model:file-list="fileList"
                      :auto-upload="false"
                      :on-change="onFileChange"
                      :show-file-list="false"
                      multiple
                    >
                      <el-button :icon="Paperclip" text>附件{{ fileList.length ? ` (${fileList.length})` : '' }}</el-button>
                    </el-upload>
                    <el-button :icon="Share" text @click="openShareDialog">分享系统数据</el-button>
                  </div>
                  <el-button type="primary" :icon="Promotion" :loading="sending" @click="onSend">发送</el-button>
                </div>
              </footer>
            </template>
            <div v-else class="chat-placeholder">
              <el-empty description="选择左侧联系人开始沟通" />
            </div>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane name="notice">
        <template #label>
          <el-badge :value="noticeUnread" :hidden="noticeUnread === 0" :max="99" class="tab-badge">通知</el-badge>
        </template>
        <div class="notice-wrap">
          <NotificationPanel />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="联系人资料" size="380px" append-to-body>
      <div v-if="contactDetail" class="detail">
        <el-avatar :size="72" :src="contactDetail.avatar || undefined" class="detail-avatar">
          {{ initials(contactDetail.name) }}
        </el-avatar>
        <div class="detail-name">{{ contactDetail.name }}</div>
        <div class="detail-role">{{ contactDetail.roles || '成员' }}</div>
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="姓名">{{ contactDetail.realName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ contactDetail.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ contactDetail.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ contactDetail.phone || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <EntityShareDialog ref="shareDialogRef" @done="onShareDone" />
  </div>
</template>

<style scoped>
.message-page {
  padding: 16px;
}
.msg-tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}
.tab-badge :deep(.el-badge__content) {
  top: 6px;
}
.msg-shell {
  display: flex;
  height: calc(100vh - 178px);
  min-height: 620px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  overflow: hidden;
}
.contacts {
  width: 286px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #fbfcfe;
}
.contacts-head {
  padding: 12px;
  border-bottom: 1px solid #f0f2f5;
}
.contacts-list {
  flex: 1;
}
.contact {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 12px;
  cursor: pointer;
  position: relative;
}
.contact:hover {
  background: #f4f7fb;
}
.contact.active {
  background: #eaf3ff;
}
.c-avatar,
.m-avatar,
.detail-avatar {
  background: linear-gradient(135deg, #1f3a93, #2c82c9);
  color: #fff;
  font-weight: 600;
}
.avatar-badge {
  flex: 0 0 auto;
  line-height: 0;
}
.avatar-badge :deep(.el-badge__content) {
  top: 2px;
  right: 4px;
}
.c-meta {
  min-width: 0;
  flex: 1;
}
.c-name {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.c-sub {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.contact-info {
  opacity: 0;
  transition: opacity 0.15s;
}
.contact:hover .contact-info,
.contact.active .contact-info {
  opacity: 1;
}
.contacts-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 36px 0;
}
.chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.chat-head {
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chat-person,
.chat-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chat-title {
  font-size: 15px;
  font-weight: 700;
  color: #303133;
}
.chat-sub,
.retention {
  font-size: 12px;
  color: #909399;
}
.chat-thread {
  flex: 1;
  overflow-y: auto;
  padding: 18px 20px;
  background: #f5f7fa;
}
.thread-empty {
  text-align: center;
  color: #c0c4cc;
  margin-top: 56px;
}
.date-divider {
  width: fit-content;
  margin: 8px auto 16px;
  padding: 3px 10px;
  border-radius: 12px;
  background: #e4e7ed;
  color: #909399;
  font-size: 12px;
}
.row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;
}
.row.mine {
  flex-direction: row-reverse;
}
.bubble-wrap {
  max-width: 68%;
  position: relative;
}
.sender-line {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  color: #909399;
  font-size: 12px;
}
.row.mine .sender-line {
  justify-content: flex-end;
}
.bubble {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 9px 12px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.row.mine .bubble {
  background: #e9f7df;
  border-color: #d9efca;
}
.bubble-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
}
.atts {
  margin-top: 6px;
}
.att {
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 6px 8px;
  margin-top: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #409eff;
}
.att:hover {
  background: #ecf5ff;
}
.att-name {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.att-size {
  color: #909399;
  font-size: 12px;
}
.att-dl {
  margin-left: auto;
}
.msg-del {
  border: 0;
  background: transparent;
  color: #c0c4cc;
  cursor: pointer;
  opacity: 0;
  margin-top: 4px;
}
.bubble-wrap:hover .msg-del {
  opacity: 1;
}
.msg-del:hover {
  color: #f56c6c;
}
.composer {
  border-top: 1px solid #ebeef5;
  padding: 10px 16px;
  background: #fff;
}
.composer-files {
  margin-bottom: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.draft-file {
  max-width: 220px;
}
.draft-file :deep(.el-icon) {
  vertical-align: middle;
  margin-right: 2px;
}
.composer-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.composer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.notice-wrap {
  min-height: calc(100vh - 178px);
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
}
.detail {
  text-align: center;
}
.detail-name {
  margin-top: 12px;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.detail-role {
  margin-top: 4px;
  color: #909399;
}
.detail-desc {
  margin-top: 20px;
  text-align: left;
}
</style>
