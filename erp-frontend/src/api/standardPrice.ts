import request, { type ApiResult, downloadFile } from './request'
import type { PageResult, ImportResult } from './customer'

export interface StandardPrice {
  id?: number | string
  productId?: number | string
  prodCode?: string
  productName?: string
  companyModel?: string
  customerId?: number | string
  price?: number
  standardUnitPrice?: number
  discountUnitPrice?: number
  floorPrice?: number
  currency?: string
  effectiveDate?: string
  remark?: string
  createTime?: string
}

const BASE = '/sales/standard-price'

export function pageStandardPrice(params: {
  pageNum: number
  pageSize: number
  keyword?: string
}): Promise<ApiResult<PageResult<StandardPrice>>> {
  return request.get(`${BASE}/page`, { params })
}

export function createStandardPrice(data: StandardPrice): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateStandardPrice(data: StandardPrice): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function deleteStandardPrice(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function importStandardPrice(file: File): Promise<ApiResult<ImportResult>> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`${BASE}/import`, form)
}

export function exportStandardPrice(keyword?: string): Promise<void> {
  return downloadFile(`${BASE}/export`, '产品报价.xlsx', { keyword })
}

export function downloadStandardPriceTemplate(): Promise<void> {
  return downloadFile(`${BASE}/template`, '产品报价导入模板.xlsx')
}
