import request, { type ApiResult } from './request'
import type { PageResult } from './system'

export interface PayableRow {
  poId: number | string
  poNo: string
  supplier?: string
  currency?: string
  orderDate?: string
  totalAmount: number
  paidAmount: number
  balance: number
  settleStatus: 'UNPAID' | 'PARTIAL' | 'PAID'
  ageDays?: number
  overdue?: boolean
}

export interface PayableSummary {
  currency: string
  totalPayable: number
  totalPaid: number
  totalBalance: number
  overdueBalance: number
  poCount: number
  openCount: number
}

export interface SupplierAging {
  supplier: string
  currency: string
  d0_30: number
  d31_60: number
  d61_90: number
  d90plus: number
  totalBalance: number
}

const BASE = '/finance/payable'

export function pagePayable(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  supplier?: string
  status?: string
  overdueOnly?: boolean
  termDays?: number
}): Promise<ApiResult<PageResult<PayableRow>>> {
  return request.get(`${BASE}/page`, { params })
}

export function payableSummary(params: {
  keyword?: string
  supplier?: string
  status?: string
  overdueOnly?: boolean
  termDays?: number
}): Promise<ApiResult<PayableSummary[]>> {
  return request.get(`${BASE}/summary`, { params })
}

export function payableAging(termDays?: number): Promise<ApiResult<SupplierAging[]>> {
  return request.get(`${BASE}/aging`, { params: { termDays } })
}

export function registerPayment(params: {
  poId: number | string
  amount: number
  entryDate?: string
  remark?: string
}): Promise<ApiResult<void>> {
  return request.post(`${BASE}/register`, null, { params })
}
