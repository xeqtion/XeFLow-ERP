import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './customer'

export interface PurchaseOrderItem {
  id?: number
  poId?: number
  productId?: number
  prodCode?: string
  productName?: string
  qty?: number
  receivedQty?: number
  unitPrice?: number
  amount?: number
}

export interface PurchaseOrder {
  id?: number | string
  poNo: string
  piId?: number | string
  piNo?: string
  purchaserId?: number | string
  purchaserName?: string
  supplier?: string
  purchaseSummary?: string
  currency?: string
  totalAmount?: number
  status?: string
  orderDate?: string
  remark?: string
  createTime?: string
  items?: PurchaseOrderItem[]
}

export interface PoReceiptItem {
  itemId: number | string
  receivedQty: number
}

const BASE = '/order/po'

export function pagePo(params: { pageNum: number; pageSize: number; keyword?: string; purchaserId?: number | string }): Promise<ApiResult<PageResult<PurchaseOrder>>> {
  return request.get(`${BASE}/page`, { params })
}
export function getPo(id: number | string): Promise<ApiResult<PurchaseOrder>> {
  return request.get(`${BASE}/${id}`)
}
export function createPo(data: PurchaseOrder): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}
export function updatePo(data: PurchaseOrder): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}
export function deletePo(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
export function downloadPoDoc(id: number | string, poNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/document`, `PO_${poNo}.xlsx`)
}
export function confirmPo(id: number | string): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/confirm`)
}
export interface PurchaserOption { id: number | string; name: string }
export function listPurchasers(): Promise<ApiResult<PurchaserOption[]>> {
  return request.get(`${BASE}/purchasers`)
}
export function receivePo(id: number | string, items: PoReceiptItem[]): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/receive`, { items })
}
