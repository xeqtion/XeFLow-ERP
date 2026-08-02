<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Calendar, Check, Delete, Edit, Location, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  calendarTasks,
  createTask,
  deleteTask,
  pendingTasks,
  taskStats,
  toggleTask,
  updateTask,
  type PersonalTask,
  type TaskStats
} from '@/api/task'
import { messageUnreadCount } from '@/api/message'

const userStore = useUserStore()
const router = useRouter()

const PRIORITY_META: Record<string, { label: string; tag: 'info' | 'warning' | 'danger'; color: string }> = {
  LOW: { label: '低', tag: 'info', color: '#909399' },
  NORMAL: { label: '中', tag: 'warning', color: '#e6a23c' },
  HIGH: { label: '高', tag: 'danger', color: '#f56c6c' }
}

const CATEGORY_LABELS: Record<string, string> = {
  MANUAL: '手动',
  PI_DELIVERY: 'PI交货',
  SHIPPING_INSTRUCTION: '出货指示',
  SHIPMENT: '出货单'
}

function fmtDate(d: Date) {
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const todayStr = fmtDate(new Date())
const stats = ref<TaskStats>({ pending: 0, today: 0, overdue: 0 })
const unreadMsg = ref(0)
const calendarDate = ref(new Date())
const selectedDay = ref(todayStr)
const dayDrawerVisible = ref(false)
const monthTasks = ref<PersonalTask[]>([])
const pending = ref<PersonalTask[]>([])

const displayName = computed(() => userStore.nickname || userStore.realName || userStore.username)
const tasksByDate = computed(() => {
  const map: Record<string, PersonalTask[]> = {}
  for (const t of monthTasks.value) {
    if (!t.taskDate) continue
    ;(map[t.taskDate] ||= []).push(t)
  }
  return map
})
const selectedDayTasks = computed(() => sortTasks(tasksByDate.value[selectedDay.value] || []))

async function loadStats() {
  try {
    stats.value = (await taskStats()).data
  } catch {
    /* ignore */
  }
  try {
    unreadMsg.value = (await messageUnreadCount()).data.count
  } catch {
    /* ignore */
  }
}

async function loadMonth() {
  const d = calendarDate.value
  const month = `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}`
  monthTasks.value = (await calendarTasks(month)).data
}

async function loadPending() {
  pending.value = (await pendingTasks()).data
}

async function reloadAll() {
  await Promise.all([loadMonth(), loadPending(), loadStats()])
}

watch(
  () => `${calendarDate.value.getFullYear()}-${calendarDate.value.getMonth()}`,
  () => loadMonth()
)

function isOverdue(t: PersonalTask) {
  return t.status === 'PENDING' && !!t.taskDate && t.taskDate < todayStr
}

function categoryLabel(t: PersonalTask) {
  return CATEGORY_LABELS[t.category || 'MANUAL'] || t.category || '事项'
}

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive<PersonalTask>({
  title: '',
  content: '',
  taskDate: todayStr,
  allDay: 1,
  startTime: '',
  endTime: '',
  location: '',
  priority: 'NORMAL',
  category: 'MANUAL'
})
const timeRange = ref<[string, string] | null>(null)
const rules: FormRules = { title: [{ required: true, message: '请输入标题', trigger: 'blur' }] }

function sortTasks(list: PersonalTask[]) {
  return [...list].sort((a, b) => {
    const aAll = a.allDay ?? 1
    const bAll = b.allDay ?? 1
    if (aAll !== bAll) return bAll - aAll
    const at = a.startTime || '99:99'
    const bt = b.startTime || '99:99'
    if (at !== bt) return at.localeCompare(bt)
    return String(a.title || '').localeCompare(String(b.title || ''), 'zh-CN')
  })
}

function scheduleLabel(t: PersonalTask) {
  if ((t.allDay ?? 1) === 1 || !t.startTime) return '全天'
  const start = t.startTime.slice(0, 5)
  const end = t.endTime ? t.endTime.slice(0, 5) : ''
  return end ? `${start}-${end}` : start
}

function openCreate(date?: string) {
  Object.assign(form, {
    id: undefined,
    title: '',
    content: '',
    taskDate: date || selectedDay.value || todayStr,
    allDay: 1,
    startTime: '',
    endTime: '',
    location: '',
    priority: 'NORMAL',
    category: 'MANUAL'
  })
  timeRange.value = null
  dialogTitle.value = '新建日程'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function openEdit(t: PersonalTask) {
  Object.assign(form, {
    id: t.id,
    title: t.title,
    content: t.content,
    taskDate: t.taskDate,
    allDay: t.allDay ?? 1,
    startTime: t.startTime || '',
    endTime: t.endTime || '',
    location: t.location || '',
    priority: t.priority || 'NORMAL',
    category: t.category || 'MANUAL'
  })
  timeRange.value = t.startTime ? [t.startTime.slice(0, 5), (t.endTime || t.startTime).slice(0, 5)] : null
  dialogTitle.value = '修改日程'
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (form.allDay === 1) {
      form.startTime = ''
      form.endTime = ''
    } else {
      form.startTime = timeRange.value?.[0] || ''
      form.endTime = timeRange.value?.[1] || ''
      if (!form.startTime || !form.endTime) {
        ElMessage.warning('请选择开始和结束时间')
        return
      }
      if (form.endTime <= form.startTime) {
        ElMessage.warning('结束时间必须晚于开始时间')
        return
      }
    }
    if (form.id) {
      await updateTask(form)
      ElMessage.success('已更新')
    } else {
      await createTask(form)
      ElMessage.success('已添加')
    }
    dialogVisible.value = false
    reloadAll()
  })
}

async function onToggle(t: PersonalTask) {
  await toggleTask(t.id as number)
  reloadAll()
}

async function onDelete(t: PersonalTask) {
  await ElMessageBox.confirm(`确认删除日程「${t.title}」？`, '提示', { type: 'warning' })
  await deleteTask(t.id as number)
  ElMessage.success('已删除')
  reloadAll()
}

function onCellClick(day: string) {
  selectedDay.value = day
  dayDrawerVisible.value = true
}

function openCreateForSelectedDay() {
  openCreate(selectedDay.value)
}

onMounted(reloadAll)
</script>

<template>
  <div class="app-container dashboard-page">
    <section class="welcome">
      <div>
        <h2>欢迎回来，{{ displayName }}</h2>
        <p>
          今天是 {{ todayStr }}
          <el-tag v-for="r in userStore.roles" :key="r" size="small" effect="plain">{{ r }}</el-tag>
        </p>
      </div>
      <div class="stat-cards">
        <button class="stat" @click="openCreate()">
          <span class="stat-num">{{ stats.today }}</span>
          <span class="stat-label">今日待办</span>
        </button>
        <button class="stat danger">
          <span class="stat-num">{{ stats.overdue }}</span>
          <span class="stat-label">已逾期</span>
        </button>
        <button class="stat">
          <span class="stat-num">{{ stats.pending }}</span>
          <span class="stat-label">待办总数</span>
        </button>
        <button class="stat primary" @click="router.push('/message')">
          <span class="stat-num">{{ unreadMsg }}</span>
          <span class="stat-label">未读消息</span>
        </button>
      </div>
    </section>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="15">
        <section class="panel">
          <header class="panel-head">
            <div class="panel-title"><el-icon><Calendar /></el-icon> 计划日历</div>
            <span class="head-hint">点击日期查看当天日程，出货日期、PI交货日期会自动进入日历</span>
          </header>
          <el-calendar v-model="calendarDate">
            <template #date-cell="{ data }">
              <div class="cal-cell" :class="{ 'is-other-month': data.type !== 'current-month' }" @click="onCellClick(data.day)">
                <div class="cal-day" :class="{ 'is-today': data.day === todayStr }">{{ data.day.split('-')[2] }}</div>
                <div class="cal-tasks">
                  <div
                    v-for="t in (tasksByDate[data.day] || []).slice(0, 3)"
                    :key="t.id"
                    class="cal-task"
                    :class="{ done: t.status === 'DONE', auto: t.systemGenerated === 1 }"
                    :title="t.title"
                    @click.stop="openEdit(t)"
                  >
                    <span class="cal-dot" :style="{ background: PRIORITY_META[t.priority || 'NORMAL'].color }"></span>
                    <span class="cal-time">{{ scheduleLabel(t) }}</span>
                    <span class="cal-title">{{ t.title }}</span>
                  </div>
                  <div v-if="(tasksByDate[data.day] || []).length > 3" class="cal-more">
                    +{{ (tasksByDate[data.day] || []).length - 3 }}
                  </div>
                </div>
              </div>
            </template>
          </el-calendar>
        </section>
      </el-col>

      <el-col :xs="24" :lg="9">
        <section class="panel todo-panel">
          <header class="panel-head">
            <div class="panel-title">我的待处理事项</div>
            <el-button type="primary" size="small" :icon="Plus" @click="openCreate()">新建</el-button>
          </header>
          <el-scrollbar height="560px">
            <div v-if="!pending.length" class="todo-empty">暂无待办，保持高效。</div>
            <el-timeline v-else>
              <el-timeline-item
                v-for="t in pending"
                :key="t.id"
                :timestamp="`${t.taskDate || ''} ${scheduleLabel(t)}`"
                placement="top"
                :color="isOverdue(t) ? '#f56c6c' : PRIORITY_META[t.priority || 'NORMAL'].color"
              >
                <article class="todo-item" :class="{ auto: t.systemGenerated === 1 }">
                  <div class="todo-main">
                    <span class="todo-title">{{ t.title }}</span>
                    <el-tag :type="PRIORITY_META[t.priority || 'NORMAL'].tag" size="small" effect="plain">
                      {{ PRIORITY_META[t.priority || 'NORMAL'].label }}
                    </el-tag>
                    <el-tag size="small" effect="plain">{{ categoryLabel(t) }}</el-tag>
                    <el-tag v-if="t.systemGenerated === 1" type="success" size="small" effect="plain">系统</el-tag>
                    <el-tag v-if="isOverdue(t)" type="danger" size="small">逾期</el-tag>
                  </div>
                  <div v-if="t.location" class="todo-location">
                    <el-icon><Location /></el-icon>
                    {{ t.location }}
                  </div>
                  <div v-if="t.content" class="todo-content">{{ t.content }}</div>
                  <div class="todo-actions">
                    <el-button :icon="Check" link type="success" @click="onToggle(t)">完成</el-button>
                    <el-button :icon="Edit" link type="primary" @click="openEdit(t)">编辑</el-button>
                    <el-button :icon="Delete" link type="danger" @click="onDelete(t)">删除</el-button>
                  </div>
                </article>
              </el-timeline-item>
            </el-timeline>
          </el-scrollbar>
        </section>
      </el-col>
    </el-row>

    <el-drawer v-model="dayDrawerVisible" :title="`${selectedDay} 日程`" size="420px" append-to-body>
      <template #default>
        <div class="day-agenda">
          <div class="day-agenda-head">
            <div>
              <div class="day-agenda-date">{{ selectedDay }}</div>
              <div class="day-agenda-count">{{ selectedDayTasks.length }} 项安排</div>
            </div>
            <el-button type="primary" :icon="Plus" @click="openCreateForSelectedDay">添加日程</el-button>
          </div>
          <div v-if="!selectedDayTasks.length" class="day-empty">当天暂无日程，添加第一项安排吧。</div>
          <div v-else class="agenda-list">
            <article
              v-for="t in selectedDayTasks"
              :key="t.id"
              class="agenda-item"
              :class="{ done: t.status === 'DONE', auto: t.systemGenerated === 1 }"
            >
              <div class="agenda-time">
                <span>{{ scheduleLabel(t) }}</span>
              </div>
              <div class="agenda-card">
                <div class="agenda-main">
                  <span class="agenda-dot" :style="{ background: PRIORITY_META[t.priority || 'NORMAL'].color }"></span>
                  <span class="agenda-title">{{ t.title }}</span>
                  <el-tag v-if="t.systemGenerated === 1" size="small" type="success" effect="plain">系统</el-tag>
                  <el-tag v-if="t.status === 'DONE'" size="small" type="info" effect="plain">已完成</el-tag>
                </div>
                <div v-if="t.location" class="agenda-location">
                  <el-icon><Location /></el-icon>
                  {{ t.location }}
                </div>
                <div v-if="t.content" class="agenda-content">{{ t.content }}</div>
                <div class="agenda-actions">
                  <el-button :icon="Check" link type="success" @click="onToggle(t)">
                    {{ t.status === 'DONE' ? '恢复' : '完成' }}
                  </el-button>
                  <el-button :icon="Edit" link type="primary" @click="openEdit(t)">编辑</el-button>
                  <el-button :icon="Delete" link type="danger" @click="onDelete(t)">删除</el-button>
                </div>
              </div>
            </article>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="要处理的事项" maxlength="100" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.taskDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="全天">
          <el-switch v-model="form.allDay" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="form.allDay === 0" label="时间">
          <el-time-picker
            v-model="timeRange"
            is-range
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="HH:mm"
            value-format="HH:mm"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="地点">
          <el-input v-model="form.location" placeholder="地点，选填" maxlength="100" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio-button value="LOW">低</el-radio-button>
            <el-radio-button value="NORMAL">中</el-radio-button>
            <el-radio-button value="HIGH">高</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="详情">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="补充说明，选填" maxlength="500" />
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
.dashboard-page {
  padding: 16px;
}
.welcome,
.panel {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}
.welcome {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding: 18px 20px;
}
.welcome h2 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #303133;
}
.welcome p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: #606266;
}
.stat-cards {
  display: flex;
  gap: 12px;
}
.stat {
  min-width: 88px;
  border: 0;
  text-align: center;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f5f7fa;
  cursor: pointer;
}
.stat.danger {
  background: #fef0f0;
}
.stat.primary {
  background: #ecf5ff;
}
.stat-num,
.stat-label {
  display: block;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}
.stat.danger .stat-num {
  color: #f56c6c;
}
.stat.primary .stat-num {
  color: #409eff;
}
.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.main-row {
  margin-top: 16px;
}
.panel-head {
  height: 52px;
  padding: 0 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: #303133;
}
.head-hint {
  font-size: 12px;
  color: #909399;
}
.cal-cell {
  height: 100%;
  min-height: 70px;
  cursor: pointer;
}
.cal-cell.is-other-month {
  opacity: 0.42;
}
.cal-cell.is-other-month .cal-day {
  color: #c0c4cc;
}
.cal-cell.is-other-month .cal-task,
.cal-cell.is-other-month .cal-time,
.cal-cell.is-other-month .cal-more {
  color: #c0c4cc;
}
.cal-day {
  font-size: 13px;
  color: #606266;
}
.cal-day.is-today {
  display: inline-block;
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
}
.cal-tasks {
  margin-top: 3px;
}
.cal-task {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cal-task:hover {
  color: #409eff;
}
.cal-task.auto {
  color: #337ecc;
}
.cal-task.done {
  color: #c0c4cc;
  text-decoration: line-through;
}
.cal-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.cal-time {
  flex: 0 0 auto;
  color: #909399;
}
.cal-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cal-more {
  font-size: 11px;
  color: #909399;
}
.todo-panel {
  min-height: 612px;
}
.todo-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 64px 0;
}
.todo-item {
  background: #f9fafc;
  border: 1px solid #eef0f3;
  border-radius: 8px;
  padding: 9px 10px;
}
.todo-item.auto {
  background: #f6fbff;
  border-color: #d9ecff;
}
.todo-main {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.todo-title {
  font-weight: 700;
  color: #303133;
}
.todo-content {
  margin-top: 6px;
  white-space: pre-wrap;
  line-height: 1.5;
  font-size: 12px;
  color: #606266;
}
.todo-location {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
.todo-actions {
  margin-top: 4px;
}
.day-agenda {
  min-height: 100%;
}
.day-agenda-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
}
.day-agenda-date {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.day-agenda-count {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.day-empty {
  padding: 64px 0;
  text-align: center;
  color: #c0c4cc;
}
.agenda-list {
  padding: 14px 0 24px;
}
.agenda-item {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}
.agenda-time {
  padding-top: 10px;
  font-size: 12px;
  color: #606266;
  text-align: right;
  white-space: nowrap;
}
.agenda-card {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
}
.agenda-item.auto .agenda-card {
  background: #f6fbff;
  border-color: #d9ecff;
}
.agenda-item.done .agenda-card {
  opacity: 0.72;
}
.agenda-main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.agenda-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.agenda-title {
  min-width: 0;
  font-weight: 700;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agenda-location,
.agenda-content {
  margin-top: 7px;
  font-size: 12px;
  color: #606266;
}
.agenda-location {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #909399;
}
.agenda-content {
  white-space: pre-wrap;
  line-height: 1.6;
}
.agenda-actions {
  margin-top: 6px;
}
:deep(.el-calendar__body) {
  padding: 8px;
}
:deep(.el-calendar-table .el-calendar-day) {
  height: 78px;
  padding: 4px;
}
</style>
