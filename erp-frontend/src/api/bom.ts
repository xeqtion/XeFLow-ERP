import request, { type ApiResult, downloadFile } from './request'
import type { ImportResult, PageResult } from './customer'

export interface Bom {
  id?: number | string
  bomCode?: string
  productId?: number | string
  prodCode?: string
  nameCn?: string
  nameEn?: string
  version?: string
  supportModels?: string
  universal?: number | boolean
  spec?: string
  qty?: number
  supplier?: string
  supplierPhone?: string
  supplierContact?: string
  unitPrice?: number
  currency?: string
  totalPrice?: number
  remark?: string
  createTime?: string
}

const BASE = '/basedata/bom'

export function pageBom(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  productModel?: string
  universalOnly?: boolean
}): Promise<ApiResult<PageResult<Bom>>> {
  return request.get(`${BASE}/page`, { params })
}

export function getBom(id: number | string): Promise<ApiResult<Bom>> {
  return request.get(`${BASE}/${id}`)
}

export function createBom(data: Bom): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateBom(data: Bom): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function deleteBom(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function importBom(file: File): Promise<ApiResult<ImportResult>> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${BASE}/import`, form)
}

export function exportBom(keyword?: string): Promise<void> {
  return downloadFile(`${BASE}/export`, '产品配件资料.xlsx', { keyword })
}

export function downloadBomTemplate(): Promise<void> {
  return downloadFile(`${BASE}/template`, '产品配件资料导入模板.xlsx')
}
