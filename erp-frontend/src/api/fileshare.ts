import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './system'

export interface SharedFile {
  id: number
  fileName: string
  contentType?: string
  fileFormat?: string
  size?: number
  category?: string
  uploaderName?: string
  remark?: string
  expireTime?: string
  createTime?: string
}

const BASE = '/fileshare'

export function pageFile(params: {
  pageNum: number
  pageSize: number
  keyword?: string
}): Promise<ApiResult<PageResult<SharedFile>>> {
  return request.get(`${BASE}/page`, { params })
}

export function uploadFile(file: File, category?: string, remark?: string, expireDays?: number): Promise<ApiResult<SharedFile>> {
  const form = new FormData()
  form.append('file', file)
  if (category) form.append('category', category)
  if (remark) form.append('remark', remark)
  if (expireDays) form.append('expireDays', String(expireDays))
  return request.post(`${BASE}/upload`, form)
}

export function downloadSharedFile(id: number, fileName: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/download`, fileName)
}

export function deleteFile(id: number): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
