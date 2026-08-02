import request, { type ApiResult } from './request'
import type { PageResult } from './system'

export interface StockRow {
  productId?: number | string
  prodCode: string
  prodName?: string
  unit?: string
  currentStock: number
  safetyStock?: number
  lowStock?: boolean
}

export interface StockMovement {
  id: number
  prodCode: string
  direction: 'IN' | 'OUT'
  qty: number
  bizType?: string
  relatedDocNo?: string
  moveDate?: string
  remark?: string
}

const BASE = '/inventory'

export function pageStock(params: { pageNum: number; pageSize: number; keyword?: string; lowOnly?: boolean }): Promise<ApiResult<PageResult<StockRow>>> {
  return request.get(`${BASE}/stock`, { params })
}

export function lowStockCount(): Promise<ApiResult<{ low: number }>> {
  return request.get(`${BASE}/low-count`)
}

export function updateSafetyStock(productId: number | string, safetyStock: number): Promise<ApiResult<void>> {
  return request.put(`${BASE}/stock/${productId}/safety-stock`, null, { params: { safetyStock } })
}

export function pageMovements(prodCode: string, params: { pageNum: number; pageSize: number }): Promise<ApiResult<PageResult<StockMovement>>> {
  return request.get(`${BASE}/movements`, { params: { ...params, prodCode } })
}

export function recordMovement(params: {
  prodCode: string
  direction: 'IN' | 'OUT'
  qty: number
  bizType?: string
  relatedDocNo?: string
  moveDate?: string
  remark?: string
}): Promise<ApiResult<number>> {
  return request.post(`${BASE}/movement`, null, { params })
}
