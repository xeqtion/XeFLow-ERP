import request, { type ApiResult } from './request'

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface SysUser {
  id?: number
  username: string
  password?: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  status?: number
  remark?: string
  createTime?: string
  roleIds?: number[]
  roleNames?: string[]
}

export interface Role {
  id?: number
  roleName: string
  roleKey: string
  status?: number
  remark?: string
  createTime?: string
}

export interface InvitationCode {
  id: string
  inviteCode: string
  expireTime: string | null
  maxUses: number
  usedCount: number
  remainingUses: number
  status: 'VALID' | 'EXPIRED' | 'EXHAUSTED'
  createTime?: string
}

export interface Permission {
  id?: number
  parentId: number
  permName: string
  permKey?: string
  type: string
  path?: string
  component?: string
  icon?: string
  sort?: number
  status?: number
  children?: Permission[]
}

export interface AuditLog {
  id: number
  username?: string
  module?: string
  operation?: string
  method?: string
  requestUri?: string
  requestMethod?: string
  params?: string
  success?: number
  errorMsg?: string
  ip?: string
  costMs?: number
  createTime?: string
}

const USER = '/system/user'

export function pageUser(params: {
  pageNum: number
  pageSize: number
  keyword?: string
  status?: number | null
}): Promise<ApiResult<PageResult<SysUser>>> {
  return request.get(`${USER}/page`, { params })
}

export function getUser(id: number): Promise<ApiResult<SysUser>> {
  return request.get(`${USER}/${id}`)
}

export function assignableRoles(): Promise<ApiResult<Role[]>> {
  return request.get(`${USER}/roles`)
}

export function createUser(data: SysUser): Promise<ApiResult<void>> {
  return request.post(USER, data)
}

export function updateUser(data: SysUser): Promise<ApiResult<void>> {
  return request.put(USER, data)
}

export function deleteUser(id: number): Promise<ApiResult<void>> {
  return request.delete(`${USER}/${id}`)
}

export function changeUserStatus(id: number, status: number): Promise<ApiResult<void>> {
  return request.put(`${USER}/${id}/status`, undefined, { params: { status } })
}

export function resetPassword(userId: number, newPassword: string): Promise<ApiResult<void>> {
  return request.put(`${USER}/reset-password`, { userId, newPassword })
}

export function pageInvitationCodes(params: {
  pageNum: number
  pageSize: number
}): Promise<ApiResult<PageResult<InvitationCode>>> {
  return request.get(`${USER}/invitations/page`, { params })
}

export function createInvitationCode(data: {
  expireTime: string | null
  maxUses: number
}): Promise<ApiResult<InvitationCode>> {
  return request.post(`${USER}/invitations`, data)
}

export function deleteInvitationCode(id: string): Promise<ApiResult<void>> {
  return request.delete(`${USER}/invitations/${id}`)
}

export function changePassword(oldPassword: string, newPassword: string): Promise<ApiResult<void>> {
  return request.put(`${USER}/change-password`, { oldPassword, newPassword })
}

export function listRoles(): Promise<ApiResult<Role[]>> {
  return request.get('/system/role/list')
}

export function pageRole(params: {
  pageNum: number
  pageSize: number
  keyword?: string
}): Promise<ApiResult<PageResult<Role>>> {
  return request.get('/system/role/page', { params })
}

export function rolePermissionIds(roleId: number): Promise<ApiResult<number[]>> {
  return request.get(`/system/role/${roleId}/permissions`)
}

export function createRole(data: Role): Promise<ApiResult<void>> {
  return request.post('/system/role', data)
}

export function updateRole(data: Role): Promise<ApiResult<void>> {
  return request.put('/system/role', data)
}

export function deleteRole(id: number): Promise<ApiResult<void>> {
  return request.delete(`/system/role/${id}`)
}

export function assignRolePermissions(roleId: number, permissionIds: number[]): Promise<ApiResult<void>> {
  return request.put(`/system/role/${roleId}/permissions`, { permissionIds })
}

export function listPermissions(): Promise<ApiResult<Permission[]>> {
  return request.get('/system/permission/list')
}

export function createPermission(data: Permission): Promise<ApiResult<void>> {
  return request.post('/system/permission', data)
}

export function updatePermission(data: Permission): Promise<ApiResult<void>> {
  return request.put('/system/permission', data)
}

export function deletePermission(id: number): Promise<ApiResult<void>> {
  return request.delete(`/system/permission/${id}`)
}

export function pageAudit(params: {
  pageNum: number
  pageSize: number
  username?: string
  module?: string
  success?: number | null
  startDate?: string
  endDate?: string
}): Promise<ApiResult<PageResult<AuditLog>>> {
  return request.get('/system/audit/page', { params })
}
