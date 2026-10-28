import type { EntityId } from '@/utils/identifier'
import request, { type ApiResult } from './request'
import type { PageResult } from './system'

export interface Notification {
  id: EntityId
  recipientId: EntityId
  title: string
  content?: string
  type: string
  isRead: number
  readTime?: string
  createTime?: string
}

export function pageNotification(params: {
  pageNum: number
  pageSize: number
  onlyUnread?: boolean
  type?: string
}): Promise<ApiResult<PageResult<Notification>>> {
  return request.get('/notification/page', { params })
}

export function unreadCount(): Promise<ApiResult<{ count: number }>> {
  return request.get('/notification/unread-count')
}

export function markRead(id: EntityId): Promise<ApiResult<void>> {
  return request.put(`/notification/${id}/read`)
}

export function markAllRead(): Promise<ApiResult<void>> {
  return request.put('/notification/read-all')
}

/** 选择已读 (批量) — ids 为通知 id 字符串数组 */
export function markReadBatch(ids: (number | string)[]): Promise<ApiResult<void>> {
  return request.put('/notification/read-batch', ids)
}

export function deleteNotification(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`/notification/${id}`)
}

export interface Recipient {
  id: string
  name: string
}
/** 公告可选接收人 (管理员) */
export function listRecipients(): Promise<ApiResult<Recipient[]>> {
  return request.get('/notification/recipients')
}

export function broadcast(data: {
  title: string
  content: string
  sendEmail: boolean
  recipientIds?: (number | string)[]
}): Promise<ApiResult<{ recipients: number }>> {
  return request.post('/notification/broadcast', data)
}

export function previewDigest(params: {
  period: 'WEEKLY' | 'MONTHLY'
  startDate?: string
  endDate?: string
}): Promise<ApiResult<{ title: string; html: string }>> {
  return request.get('/system/digest/preview', { params })
}

export function sendDigest(params: {
  period: 'WEEKLY' | 'MONTHLY'
  startDate?: string
  endDate?: string
}): Promise<ApiResult<{ recipients: number; startDate: string; endDate: string }>> {
  return request.post('/system/digest/send', undefined, { params })
}
