import request, { type ApiResult } from './request'
import type { PageResult } from './system'

export interface ReceivableRow {
  piId: number | string
  piNo: string
  customerId: number | string
  customerCode?: string
  customerName?: string
  currency?: string
  signDate?: string
  totalAmount: number
  depositAmount: number
  depositReceivedAmount: number
  receivedAmount: number
  balance: number
  settleStatus: 'UNPAID' | 'PARTIAL' | 'PAID'
  ageDays?: number
  overdue?: boolean
}

export interface ReceivableSummary {
  currency: string
  totalReceivable: number
  totalReceived: number
  totalBalance: number
  overdueBalance: number
  piCount: number
  openCount: number
}

export interface CustomerAging {
  customerId: number | string
  customerCode?: string
  customerName?: string
  currency: string
  d0_30: number
  d31_60: number
  d61_90: number
  d90plus: number
  totalBalance: number
}

const BASE = '/finance/receivable'

export function pageReceivable(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  customerId?: number | string
  customerCode?: string
  status?: string
  overdueOnly?: boolean
  termDays?: number
}): Promise<ApiResult<PageResult<ReceivableRow>>> {
  return request.get(`${BASE}/page`, { params })
}

export function receivableSummary(params: {
  keyword?: string
  customerId?: number | string
  customerCode?: string
  status?: string
  overdueOnly?: boolean
  termDays?: number
}): Promise<ApiResult<ReceivableSummary[]>> {
  return request.get(`${BASE}/summary`, { params })
}

export function receivableAging(termDays?: number): Promise<ApiResult<CustomerAging[]>> {
  return request.get(`${BASE}/aging`, { params: { termDays } })
}

export function registerReceipt(params: {
  piId: number | string
  amount: number
  receiptType?: 'PRE_RECEIVE' | 'RECEIVABLE'
  entryDate?: string
  remark?: string
}): Promise<ApiResult<void>> {
  return request.post(`${BASE}/register`, null, { params })
}
