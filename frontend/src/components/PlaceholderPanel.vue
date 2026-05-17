<template>
  <section class="panel placeholder-panel">
    <div>
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <el-table :data="rows" stripe>
      <el-table-column prop="name" label="功能项" min-width="180" />
      <el-table-column prop="status" label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="row.status === '已接入' ? 'success' : 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="note" label="说明" min-width="260" />
    </el-table>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description: string
  rows: Array<{ name: string; status: string; note: string }>
}>()
</script>
