import { createApp } from 'vue'
import { createPinia } from 'pinia'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { setupDirectives } from './directives/permission'
// 程序式调用的组件(ElMessage/ElMessageBox/ElNotification/ElLoading)样式不会被
// unplugin 按需引入, 需手动引入其样式, 否则弹窗/提示会丢失 CSS(错位到左上角)。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import './styles/index.css'

// 初始化内置实体类型注册（消息中心分享系统数据）
import { initDefaultEntityTypes } from './utils/entityLink'
initDefaultEntityTypes()

const app = createApp(App)

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
setupDirectives(app)
app.mount('#app')
