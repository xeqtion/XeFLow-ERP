import request, { type ApiResult, downloadFile } from './request'
import type { PageResult } from './customer'

export interface Shipment {
  id?: number | string
  shipmentNo: string
  siId?: number | string
  siNo?: string
  piId?: number | string
  shipDate?: string
  qty?: number
  packageCount?: number | null
  grossWeight?: number | null
  netWeight?: number | null
  volumeCbm?: number | null
  containerType?: string
  containerNo?: string
  blNo?: string
  status?: string
  remark?: string
  createTime?: string
}

const BASE = '/shipment'

export function pageShipment(params: { pageNum: number; pageSize: number; keyword?: string }): Promise<ApiResult<PageResult<Shipment>>> {
  return request.get(`${BASE}/page`, { params })
}
export function createShipment(data: Shipment): Promise<ApiResult<number | string>> {
  return request.post(BASE, data)
}
export function updateShipment(data: Shipment): Promise<ApiResult<void>> {
  const { id, shipDate, qty, packageCount, grossWeight, netWeight,
    volumeCbm, containerType, containerNo, blNo, remark } = data
  return request.put(BASE, { id, shipDate, qty, packageCount, grossWeight,
    netWeight, volumeCbm, containerType, containerNo, blNo, remark })
}
export function deleteShipment(id: number | string): Promise<ApiResult<void>> {
  return request.delete(`${BASE}/${id}`)
}
export function exportShipment(keyword?: string): Promise<void> {
  return downloadFile(`${BASE}/export`, '出货单.xlsx', { keyword })
}
export function downloadShipmentDoc(id: number | string, shipmentNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/document`, `Shipment_${shipmentNo}.xls`)
}

export function downloadPackingList(id: number | string, shipmentNo: string): Promise<void> {
  return downloadFile(`${BASE}/${id}/packing-list`, `装箱单_${shipmentNo}.docx`)
}
