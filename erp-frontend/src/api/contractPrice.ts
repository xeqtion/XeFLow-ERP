import request, { type ApiResult } from './request'
import type { PageResult } from './customer'

export interface ContractPrice {
  id?: number | string
  customerId?: number | string
  customerCode?: string
  customerName?: string
  productId?: number | string
  productName?: string
  productModel?: string
  prodCode?: string
  standardPrice?: number
  contractPrice?: number
  dealUnitPrice?: number
  deviation?: number
  unitPriceDeviation?: number
  currency?: string
  auditStatus?: 'PASS' | 'PENDING' | 'REJECTED'
  auditorId?: number | string
  auditTime?: string
  rejectedExpireTime?: string
  rejectedRemainingSeconds?: number | null
  remark?: string
  createBy?: number | string
  createTime?: string
}

const BASE = '/sales/contract-price'

export function pageContractPrice(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  auditStatus?: string
  customerId?: number | string
  customerCode?: string
}): Promise<ApiResult<PageResult<ContractPrice>>> {
  return request.get(`${BASE}/page`, { params })
}

export function createContractPrice(data: ContractPrice): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}

export function updateContractPrice(data: ContractPrice): Promise<ApiResult<void>> {
  return request.put(BASE, data)
}

export function deleteContractPrice(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}

export function approveContractPrice(id: number | string): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/approve`)
}

export function rejectContractPrice(id: number | string, reason: string): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/reject`, { reason })
}

export function requestReviewContractPrice(id: number | string): Promise<ApiResult<void>> {
  return request.post(`${BASE}/${id}/review`)
}
