import request, { type ApiResult } from './request'

export interface Profile {
  userId: number | string
  username: string
  realName?: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  roles?: string[]
  roleNames?: string[]
}

export interface ProfileUpdate {
  realName?: string
  nickname?: string
  phone?: string
  remark?: string
}

export function getProfile(): Promise<ApiResult<Profile>> {
  return request.get('/profile/me')
}

export function updateProfile(data: ProfileUpdate): Promise<ApiResult<Profile>> {
  return request.put('/profile/me', data)
}

export function uploadAvatar(file: File): Promise<ApiResult<{ avatar: string }>> {
  const form = new FormData()
  form.append('file', file)
  return request.post('/profile/avatar', form)
}

export function changeProfilePassword(oldPassword: string, newPassword: string): Promise<ApiResult<void>> {
  return request.put('/profile/password', { oldPassword, newPassword })
}

export function deactivateAccount(password: string): Promise<ApiResult<void>> {
  return request.post('/profile/deactivate', { password })
}

export interface SystemResetResult {
  usersPreserved: boolean
  currentSuperAdminRetained: boolean
  retainedUserCount: number
  clearedRowCount: number
  completedAt: string
}

export function resetSystem(data: {
  preserveUsers: boolean
  password: string
}): Promise<ApiResult<SystemResetResult>> {
  return request.post('/profile/system-reset', data)
}
