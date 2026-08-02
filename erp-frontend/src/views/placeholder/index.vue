<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta?.title as string) || '模块')
const permKey = computed(() => (route.meta?.permKey as string) || '')
</script>

<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-empty :description="`「${title}」模块开发中`">
        <template #image>
          <el-icon :size="64" color="#c0c4cc"><Tools /></el-icon>
        </template>
        <div class="meta">
          <p>该页面由后端菜单动态注册，对应路由：<code>{{ route.path }}</code></p>
          <p v-if="permKey">权限标识：<el-tag size="small">{{ permKey }}</el-tag></p>
          <p class="hint">按《开发方案》P1–P5 在 <code>src/views</code> 下实现对应业务页面即可。</p>
        </div>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.meta {
  text-align: center;
  color: #606266;
  line-height: 2;
}
.meta code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #c7254e;
}
.hint {
  color: #909399;
  font-size: 13px;
}
</style>
