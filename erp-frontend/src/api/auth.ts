import request, { type ApiResult } from './request'

export interface LoginParams {
  loginType?: 'password' | 'code'
  username?: string
  password?: string
  email?: string
  code?: string
}

export interface RegisterParams {
  username: string
  password: string
  email: string
  code?: string
  realName?: string
  phone?: string
  registerCode?: string
}

export interface RegisterOptions {
  enabled: boolean
  requireRegisterCode: boolean
  requireEmailCode: boolean
  allowedDomains?: string[]
}

export interface SendCodeResult {
  mailSent: boolean
  devCode?: string | null
}

export interface LoginResult {
  token: string
  tokenType: string
}

export interface UserInfo {
  userId: number | string
  username: string
  realName: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  roles: string[]
  permissions: string[]
}

export interface MenuNode {
  id: number
  parentId: number
  name: string
  permKey?: string
  path?: string
  component?: string
  icon?: string
  sort?: number
  children?: MenuNode[]
}

export function login(data: LoginParams): Promise<ApiResult<LoginResult>> {
  return request.post<LoginResult>('/auth/login', data)
}

export function register(data: RegisterParams): Promise<ApiResult<void>> {
  return request.post<void>('/auth/register', data)
}

export function getRegisterOptions(): Promise<ApiResult<RegisterOptions>> {
  return request.get<RegisterOptions>('/auth/register/options')
}

export function sendRegisterCode(email: string): Promise<ApiResult<SendCodeResult>> {
  return request.post<SendCodeResult>('/auth/register/code', { email })
}

export function sendLoginCode(email: string): Promise<ApiResult<SendCodeResult>> {
  return request.post<SendCodeResult>('/auth/login/code', { email })
}

export function logout(): Promise<ApiResult<void>> {
  return request.post<void>('/auth/logout')
}

export function getUserInfo(): Promise<ApiResult<UserInfo>> {
  return request.get<UserInfo>('/auth/info')
}

export function getMenus(): Promise<ApiResult<MenuNode[]>> {
  return request.get<MenuNode[]>('/auth/menus')
}
