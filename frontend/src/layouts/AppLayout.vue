<template>
  <el-container class="app-shell">
    <el-aside class="app-sidebar" width="236px">
      <div class="sidebar-grid" />
      <div class="brand">
        <div class="brand-mark">
          <img src="/favicon.svg" alt="AI 代码检视平台" />
        </div>
        <div>
          <div class="brand-title">代码检视平台</div>
          <div class="brand-subtitle">Code Review Console</div>
        </div>
      </div>

      <el-menu :default-active="route.path" router class="side-menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <component :is="item.icon" class="menu-icon" :size="18" />
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="page-heading">
          <div class="eyebrow">AI CODE REVIEW</div>
          <h1>{{ pageTitle }}</h1>
          <p>基于 Skill、规则和任务流的自动化代码质量控制台</p>
        </div>
        <div class="header-actions">
          <div class="runtime-pill">
            <span class="pulse-dot" />
            后端已接入
          </div>
          <div class="env-pill">DEV</div>
          <el-select v-model="selectedUserId" filterable class="user-switch" @change="switchUser">
            <el-option v-for="user in users" :key="user.userId" :label="`${user.userName} ${user.employeeId}`" :value="user.userId" />
          </el-select>
          <el-avatar :size="36" class="user-avatar">{{ currentUser?.userName?.slice(0, 2) || '用户' }}</el-avatar>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { menuItems } from '@/data/menu'
import { listSystemUsers, type SystemUser } from '@/api/user'
import { CURRENT_USER_KEY, currentUserId } from '@/utils/currentUser'

const route = useRoute()
const pageTitle = computed(() => String(route.meta.title || '工作台'))
const users = ref<SystemUser[]>([])
const selectedUserId = ref(currentUserId())
const currentUser = computed(() => users.value.find(user => user.userId === selectedUserId.value))

async function loadUsers() { users.value = await listSystemUsers() }
function switchUser(userId: string) {
  localStorage.setItem(CURRENT_USER_KEY, userId)
  window.location.reload()
}
onMounted(loadUsers)
</script>
