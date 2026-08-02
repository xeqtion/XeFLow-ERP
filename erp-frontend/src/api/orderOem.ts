import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './customer'

export interface OrderOem {
  id?: number | string
  oemNo: string
  piId?: number | string
  piNo?: string
  customerId?: number | string
  customerCode?: string
  customerName?: string
  productId?: number | string
  productModel?: string
  factoryModel?: string
  requirement?: string
  status?: string
  createTime?: string
}

export interface OrderOemFile {
  id: number | string
  oemId: number | string
  fileName: string
  storedName?: string
  contentType?: string
  size?: number
  uploaderName?: string
  remark?: string
  createTime?: string
}

const BASE = '/order/oem'

export function pageOem(params: { pageNum: number; pageSize: number; keyword?: string }): Promise<ApiResult<PageResult<OrderOem>>> {
  return request.get(`${BASE}/page`, { params })
}
export function createOem(data: OrderOem): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}
export function updateOem(data: OrderOem): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}
export function deleteOem(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function listOemFiles(oemId: number | string): Promise<ApiResult<OrderOemFile[]>> {
  return request.get(`${BASE}/${oemId}/files`)
}

export function uploadOemFile(oemId: number | string, file: File, remark?: string): Promise<ApiResult<OrderOemFile>> {
  const form = new FormData()
  form.append('file', file)
  if (remark) form.append('remark', remark)
  return request.post(`${BASE}/${oemId}/files`, form)
}

export function downloadOemFile(fileId: number | string, fileName: string): Promise<void> {
  return downloadFile(`${BASE}/files/${fileId}/download`, fileName)
}

export function deleteOemFile(fileId: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/files/${fileId}`)
}
