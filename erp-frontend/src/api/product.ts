import request, { type ApiResult, downloadFile } from './request'
import type { PageResult, ImportResult } from './customer'

export interface Product {
  id?: number | string
  prodCode?: string
  productName?: string
  companyModel?: string
  factoryModel?: string
  factoryPrice?: number
  currency?: string
  remark?: string
  prodNameCn?: string
  prodNameEn?: string
  spec?: string
  unit?: string
  costPrice?: number
  safetyStock?: number | null
  createTime?: string
}

const BASE = '/basedata/product'

export function pageProduct(params: {
  pageNum: number
  pageSize: number
  keyword?: string
}): Promise<ApiResult<PageResult<Product>>> {
  return request.get(`${BASE}/page`, { params })
}

export function createProduct(data: Product): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateProduct(data: Product, originalId?: number | string): Promise<ApiResult<void>> {
  if (originalId !== undefined && originalId !== null) {
    return request.put(`${BASE}/${originalId}`, data)
  }
  return request.put(BASE, data)
}

export function deleteProduct(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function importProduct(file: File): Promise<ApiResult<ImportResult>> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${BASE}/import`, form)
}

export function exportProduct(keyword?: string): Promise<void> {
  return downloadFile(`${BASE}/export`, '产品资料.xlsx', { keyword })
}

export function downloadProductTemplate(): Promise<void> {
  return downloadFile(`${BASE}/template`, '产品资料导入模板.xlsx')
}
