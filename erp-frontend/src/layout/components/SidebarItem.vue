<script setup lang="ts">
import type { MenuNode } from '@/api/auth'

defineProps<{ item: MenuNode }>()
</script>

<template>
  <!-- 有子菜单 -->
  <el-sub-menu v-if="item.children && item.children.length" :index="String(item.id)">
    <template #title>
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <span>{{ item.name }}</span>
    </template>
    <sidebar-item v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>

  <!-- 叶子菜单 -->
  <el-menu-item v-else-if="item.path" :index="item.path">
    <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
    <template #title>{{ item.name }}</template>
  </el-menu-item>
</template>
