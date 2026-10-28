import type { EntityId } from '@/utils/identifier'
import request, { type ApiResult, downloadFile } from './request'

export interface ReportRow {
  period: string
  amount: number
  currency: string
}
export interface ProfitRow {
  period: string
  sales: number
  purchase: number
  profit: number
  currency: string
}
export interface ReportQuery {
  dimension?: string
  startDate?: string
  endDate?: string
}
export interface SalesmanRow {
  ownerId?: EntityId
  salesman: string
  orderCount: number
  totalAmount: number
  revenueShare: number
  currency: string
}
export interface SalesmanTrendRow {
  period: string
  ownerId?: number | string
  salesman: string
  orderCount: number
  totalAmount: number
  currency: string
}
export interface SalesPerformanceDetail {
  piId: number | string
  piNo: string
  ownerId?: number | string
  salesman: string
  customerCode?: string
  customerName?: string
  signDate?: string
  status?: string
  currency: string
  originalAmount: number
  amountCny: number
}

const BASE = '/report'

export function salesBySalesman(params: ReportQuery): Promise<ApiResult<SalesmanRow[]>> {
  return request.get(`${BASE}/by-salesman`, { params })
}
export function salesTrendBySalesman(params: ReportQuery): Promise<ApiResult<SalesmanTrendRow[]>> {
  return request.get(`${BASE}/by-salesman/trend`, { params })
}
export function salesPerformanceDetails(
  ownerId: number | string,
  params: ReportQuery
): Promise<ApiResult<SalesPerformanceDetail[]>> {
  return request.get(`${BASE}/by-salesman/details`, { params: { ...params, ownerId } })
}

export function purchaseReport(params: ReportQuery): Promise<ApiResult<ReportRow[]>> {
  return request.get(`${BASE}/purchase`, { params })
}
export function salesReport(params: ReportQuery): Promise<ApiResult<ReportRow[]>> {
  return request.get(`${BASE}/sales`, { params })
}
export function profitReport(params: ReportQuery): Promise<ApiResult<ProfitRow[]>> {
  return request.get(`${BASE}/profit`, { params })
}
export function exportPurchase(params: ReportQuery): Promise<void> {
  return downloadFile(`${BASE}/purchase/export`, '采购报表.xlsx', params as Record<string, unknown>)
}
export function exportSales(params: ReportQuery): Promise<void> {
  return downloadFile(`${BASE}/sales/export`, '销售报表.xlsx', params as Record<string, unknown>)
}
export function exportProfit(params: ReportQuery): Promise<void> {
  return downloadFile(`${BASE}/profit/export`, '财务利润报表.xlsx', params as Record<string, unknown>)
}
export function downloadStatement(customerCode: string, month: string): Promise<void> {
  return downloadFile(`${BASE}/statement`, `对账单_${customerCode}_${month}.docx`, { customerCode, month })
}
export function downloadPerformance(params: ReportQuery): Promise<void> {
  return downloadFile(`${BASE}/performance`, '外贸业绩报告.docx', params as Record<string, unknown>)
}
