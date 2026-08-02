<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Message, Key } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { register, sendRegisterCode, sendLoginCode, getRegisterOptions, type RegisterOptions } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref<'password' | 'code'>('password')

/** ---------------- 密码登录 ---------------- */
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ username: '', password: '' })
const pwdRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

/** ---------------- 验证码登录 ---------------- */
const codeFormRef = ref<FormInstance>()
const codeForm = reactive({ email: '', code: '' })
const codeRules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}
const loginCountdown = ref(0)

async function onSendLoginCode() {
  if (!codeForm.email) {
    ElMessage.warning('请先输入邮箱')
    return
  }
  try {
    const res = await sendLoginCode(codeForm.email)
    handleCodeSent(res.data?.devCode, (c) => (codeForm.code = c))
    startCountdown(loginCountdown)
  } catch {
    /* 错误已由拦截器提示 */
  }
}

/** ---------------- 注册 ---------------- */
const registerVisible = ref(false)
const regFormRef = ref<FormInstance>()
const regForm = reactive({
  username: '',
  password: '',
  email: '',
  code: '',
  realName: '',
  phone: '',
  registerCode: ''
})
// 注册选项 (后端策略驱动: 是否需要邀请码 / 邮箱验证码)
const regOptions = ref<RegisterOptions>({ enabled: true, requireRegisterCode: true, requireEmailCode: false })
async function loadRegisterOptions() {
  try {
    regOptions.value = (await getRegisterOptions()).data
  } catch {
    /* 取不到则用默认值: 邀请码必填、无需邮箱验证码 */
  }
}
onMounted(loadRegisterOptions)

const regRules = computed<FormRules>(() => ({
  username: [{ required: true, message: '请输入用户名(3-32位)', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码(6-32位)', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ],
  code: regOptions.value.requireEmailCode
    ? [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }]
    : [],
  registerCode: regOptions.value.requireRegisterCode
    ? [{ required: true, message: '请输入邀请码', trigger: 'blur' }]
    : []
}))
const regCountdown = ref(0)
const regLoading = ref(false)

async function onSendRegisterCode() {
  if (!regForm.email) {
    ElMessage.warning('请先输入公司邮箱')
    return
  }
  try {
    const res = await sendRegisterCode(regForm.email)
    handleCodeSent(res.data?.devCode, (c) => (regForm.code = c))
    startCountdown(regCountdown)
  } catch {
    /* 错误已由拦截器提示 */
  }
}

async function onRegister() {
  if (!regFormRef.value) return
  await regFormRef.value.validate(async (valid) => {
    if (!valid) return
    regLoading.value = true
    try {
      const res = await register({ ...regForm })
      ElMessage.success(res.message || '注册成功')
      registerVisible.value = false
      // 注册成功后自动回填到密码登录
      pwdForm.username = regForm.username
      pwdForm.password = ''
      activeTab.value = 'password'
    } catch {
      /* 错误已由拦截器提示 */
    } finally {
      regLoading.value = false
    }
  })
}

/** ---------------- 公共 ---------------- */
function handleCodeSent(devCode: string | null | undefined, fill: (code: string) => void) {
  if (devCode) {
    // 后端未配置 SMTP, 降级为开发模式: 直接回填验证码并提示
    fill(devCode)
    ElMessage.success(`开发模式: 验证码 ${devCode} 已自动填入`)
  } else {
    ElMessage.success('验证码已发送至邮箱, 请查收')
  }
}

function startCountdown(target: { value: number }) {
  target.value = 60
  const timer = setInterval(() => {
    target.value -= 1
    if (target.value <= 0) clearInterval(timer)
  }, 1000)
}

async function doLogin(params: Parameters<typeof userStore.login>[0]) {
  loading.value = true
  try {
    await userStore.login(params)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    await router.replace(redirect)
  } catch {
    /* 错误已由拦截器提示 */
  } finally {
    loading.value = false
  }
}

async function onPasswordLogin() {
  if (!pwdFormRef.value) return
  await pwdFormRef.value.validate(async (valid) => {
    if (!valid) return
    await doLogin({ loginType: 'password', username: pwdForm.username, password: pwdForm.password })
  })
}

async function onCodeLogin() {
  if (!codeFormRef.value) return
  await codeFormRef.value.validate(async (valid) => {
    if (!valid) return
    await doLogin({ loginType: 'code', email: codeForm.email, code: codeForm.code })
  })
}
</script>

<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="always">
      <div class="login-title">
        <img src="/brand/xeflow-logo.svg" alt="XeFlow" class="brand-logo" draggable="false" />
        <p>AI-powered trade management</p>
      </div>

      <el-tabs v-model="activeTab" stretch>
        <!-- 密码登录 -->
        <el-tab-pane label="密码登录" name="password">
          <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" size="large" @keyup.enter="onPasswordLogin">
            <el-form-item prop="username">
              <el-input v-model="pwdForm.username" placeholder="用户名" :prefix-icon="User" clearable />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="pwdForm.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="onPasswordLogin">登 录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 邮箱验证码登录 -->
        <el-tab-pane label="邮箱验证码登录" name="code">
          <el-form ref="codeFormRef" :model="codeForm" :rules="codeRules" size="large" @keyup.enter="onCodeLogin">
            <el-form-item prop="email">
              <el-input v-model="codeForm.email" placeholder="注册邮箱" :prefix-icon="Message" clearable />
            </el-form-item>
            <el-form-item prop="code">
              <div class="code-row">
                <el-input v-model="codeForm.code" placeholder="验证码" :prefix-icon="Key" clearable />
                <el-button :disabled="loginCountdown > 0" @click="onSendLoginCode">
                  {{ loginCountdown > 0 ? `${loginCountdown}s` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" class="login-btn" :loading="loading" @click="onCodeLogin">登 录</el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>

      <div class="login-footer">
        <span class="login-tip">公司员工请使用公司邮箱注册</span>
        <el-link type="primary" :underline="false" @click="registerVisible = true">注册公司账号</el-link>
      </div>
    </el-card>

    <!-- 注册弹窗 -->
    <el-dialog v-model="registerVisible" title="注册公司账号" width="440px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="注册公司账号"
        description="请填写邀请码完成注册（向管理员获取）。新账号默认仅有只读权限，需由管理员分配后才可使用更多功能。"
        style="margin-bottom: 16px"
      />
      <el-form ref="regFormRef" :model="regForm" :rules="regRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="regForm.username" placeholder="3-32位登录名" clearable />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="regForm.password" type="password" placeholder="6-32位" show-password />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="regForm.email" placeholder="name@example.com" clearable />
        </el-form-item>
        <el-form-item v-if="regOptions.requireEmailCode" label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="regForm.code" placeholder="邮箱验证码" clearable />
            <el-button :disabled="regCountdown > 0" @click="onSendRegisterCode">
              {{ regCountdown > 0 ? `${regCountdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="regForm.realName" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="regForm.phone" placeholder="选填" clearable />
        </el-form-item>
        <el-form-item v-if="regOptions.requireRegisterCode" label="邀请码" prop="registerCode">
          <el-input v-model="regForm.registerCode" placeholder="请输入管理员提供的邀请码" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="regLoading" @click="onRegister">注 册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.login-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1f3a93 0%, #2c82c9 100%);
}

.login-card {
  width: 420px;
  border-radius: 10px;
}

.login-title {
  text-align: center;
  margin: -18px 0 12px;
}

.brand-logo {
  display: block;
  width: 250px;
  height: 144px;
  margin: 0 auto -44px;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.login-title p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.login-btn {
  width: 100%;
}

.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-row .el-button {
  flex-shrink: 0;
  min-width: 104px;
}

.login-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.login-tip {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
