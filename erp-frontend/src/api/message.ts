import type { EntityId } from '@/utils/identifier'
import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './system'

export interface Contact {
  userId: EntityId
  name: string
  username: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  roles?: string
  unread: number
}

export interface MessageAttachment {
  id: EntityId
  fileName: string
  contentType?: string
  size?: number
}

export interface UserMessage {
  id: EntityId
  senderId: EntityId
  senderName: string
  recipientId: EntityId
  recipientName: string
  content?: string
  isRead: number
  createTime?: string
  mine: boolean
  attachments?: MessageAttachment[]
}

const BASE = '/message'

export function listContacts(): Promise<ApiResult<Contact[]>> {
  return request.get(`${BASE}/contacts`)
}

export function getContact(userId: EntityId): Promise<ApiResult<Contact>> {
  return request.get(`${BASE}/contacts/${userId}`)
}

export function getConversation(userId: EntityId): Promise<ApiResult<UserMessage[]>> {
  return request.get(`${BASE}/conversation/${userId}`)
}

export function inbox(params: { pageNum: number; pageSize: number }): Promise<ApiResult<PageResult<UserMessage>>> {
  return request.get(`${BASE}/inbox`, { params })
}

export function sentBox(params: { pageNum: number; pageSize: number }): Promise<ApiResult<PageResult<UserMessage>>> {
  return request.get(`${BASE}/sent`, { params })
}

export function messageUnreadCount(): Promise<ApiResult<{ count: number }>> {
  return request.get(`${BASE}/unread-count`)
}

/** 发送消息(可带附件) */
export function sendMessage(recipientId: EntityId, content: string, files: File[]): Promise<ApiResult<UserMessage>> {
  const form = new FormData()
  form.append('recipientId', String(recipientId))
  if (content) form.append('content', content)
  files.forEach((f) => form.append('files', f))
  return request.post(`${BASE}/send`, form)
}

export function markMessageRead(id: EntityId): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${id}/read`)
}

export function deleteMessage(id: EntityId): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function clearConversation(userId: EntityId): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/conversation/${userId}`)
}

export function downloadAttachment(id: EntityId, fileName: string): Promise<void> {
  return downloadFile(`${BASE}/attachment/${id}/download`, fileName)
}
