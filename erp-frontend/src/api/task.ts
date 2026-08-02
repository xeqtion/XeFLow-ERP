import request, { type ApiResult } from './request'

export interface PersonalTask {
  id?: number
  ownerId?: number
  title: string
  content?: string
  taskDate?: string
  allDay?: number
  startTime?: string
  endTime?: string
  location?: string
  priority?: 'LOW' | 'NORMAL' | 'HIGH'
  category?: string
  sourceType?: string
  sourceId?: number | string
  sourceNo?: string
  systemGenerated?: number
  status?: 'PENDING' | 'DONE'
  doneTime?: string
  createTime?: string
}

export interface TaskStats {
  pending: number
  today: number
  overdue: number
}

const BASE = '/task'

/** 按月查询(日历) month=YYYY-MM */
export function calendarTasks(month?: string): Promise<ApiResult<PersonalTask[]>> {
  return request.get(`${BASE}/calendar`, { params: { month } })
}

/** 我的待办(未完成) */
export function pendingTasks(): Promise<ApiResult<PersonalTask[]>> {
  return request.get(`${BASE}/pending`)
}

export function taskStats(): Promise<ApiResult<TaskStats>> {
  return request.get(`${BASE}/stats`)
}

export function createTask(data: PersonalTask): Promise<ApiResult<number>> {
  return request.post(BASE, data)
}

export function updateTask(data: PersonalTask): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function toggleTask(id: number): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${id}/toggle`)
}

export function deleteTask(id: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
