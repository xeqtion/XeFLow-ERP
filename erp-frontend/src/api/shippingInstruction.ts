import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './customer'

export interface ShippingInstructionItem {
  id?: number | string
  siId?: number | string
  productModel?: string
  factoryModel?: string
  quantity?: number | string
  cartonCount?: number | string
  sortOrder?: number
}

export interface ShippingInstruction {
  id?: number | string
  siNo: string
  customerId?: number | string
  customerCode?: string
  piId?: number | string
  shipDate?: string
  shippingAddress?: string
  deliveryAddress?: string
  contactName?: string
  phone?: string
  orderNo?: string
  orderCountry?: string
  productModel?: string
  factoryModel?: string
  quantity?: number | string
  cartonCount?: number | string
  orderRequirement?: string
  ownerName?: string
  orderDate?: string
  port?: string
  status?: string
  remark?: string
  createTime?: string
  items?: ShippingInstructionItem[]
}

const BASE = '/order/shipping-instruction'

export function pageSi(params: { pageNum: number; pageSize: number; keyword?: string }): Promise<ApiResult<PageResult<ShippingInstruction>>> {
  return request.get(`${BASE}/page`, { params })
}
export function getSi(id: number | string): Promise<ApiResult<ShippingInstruction | null>> {
  return request.get(`${BASE}/${id}`)
}
export function createSi(data: ShippingInstruction): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}
export function updateSi(data: ShippingInstruction): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}
export function deleteSi(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
export function downloadSiDoc(id: number | string, siNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/document`, `SI_${siNo}.xlsx`)
}
