import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './system'

export interface Contact {
  userId: number
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
  id: number
  fileName: string
  contentType?: string
  size?: number
}

export interface UserMessage {
  id: number
  senderId: number
  senderName: string
  recipientId: number
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

export function getContact(userId: number): Promise<ApiResult<Contact>> {
  return request.get(`${BASE}/contacts/${userId}`)
}

export function getConversation(userId: number): Promise<ApiResult<UserMessage[]>> {
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
export function sendMessage(recipientId: number, content: string, files: File[]): Promise<ApiResult<UserMessage>> {
  const form = new FormData()
  form.append('recipientId', String(recipientId))
  if (content) form.append('content', content)
  files.forEach((f) => form.append('files', f))
  return request.post(`${BASE}/send`, form)
}

export function markMessageRead(id: number): Promise<ApiResult<void>> {
  return request.put(`${BASE}/${id}/read`)
}

export function deleteMessage(id: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function clearConversation(userId: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/conversation/${userId}`)
}

export function downloadAttachment(id: number, fileName: string): Promise<void> {
  return downloadFile(`${BASE}/attachment/${id}/download`, fileName)
}
