<template>
  <div class="page-stack">
    <section class="metrics-grid">
      <div v-for="metric in metrics" :key="metric.label" class="metric-card" :class="metric.tone">
        <div class="metric-topline">
          <span>{{ metric.label }}</span>
          <component :is="metric.icon" :size="20" />
        </div>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </div>
    </section>

    <section class="content-grid">
      <div class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2>问题趋势</h2>
            <p>最近检视结果的发现量走势</p>
          </div>
          <el-segmented v-model="range" :options="['7 天', '30 天']" />
        </div>
        <div ref="trendRef" class="chart-box" />
      </div>

      <div class="panel">
        <div class="panel-header">
          <div>
            <h2>严重度分布</h2>
            <p>按规则输出统一归类</p>
          </div>
        </div>
        <div class="severity-list">
          <div v-for="item in severity" :key="item.name" class="severity-row">
            <span class="severity-dot" :style="{ background: item.color }" />
            <span>{{ item.name }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import * as echarts from 'echarts'
import { onMounted, ref } from 'vue'
import { Bot, FolderGit2, ListChecks, Workflow } from 'lucide-vue-next'

const range = ref('7 天')
const trendRef = ref<HTMLDivElement>()

const metrics = [
  { label: '项目总数', value: '0', note: '等待项目接入', icon: FolderGit2, tone: 'tone-blue' },
  { label: '今日任务', value: '0', note: '手动与定时任务', icon: Workflow, tone: 'tone-cyan' },
  { label: '开放问题', value: '0', note: 'OPEN 状态', icon: ListChecks, tone: 'tone-red' },
  { label: 'AI 调用', value: '0', note: '今日累计', icon: Bot, tone: 'tone-green' }
]

const severity = [
  { name: '阻断', value: 0, color: '#9f1239' },
  { name: '严重', value: 0, color: '#dc2626' },
  { name: '主要', value: 0, color: '#ea580c' },
  { name: '次要', value: 0, color: '#2563eb' },
  { name: '提示', value: 0, color: '#64748b' }
]

onMounted(() => {
  if (!trendRef.value) return
  const chart = echarts.init(trendRef.value)
  chart.setOption({
    grid: { top: 28, right: 20, bottom: 34, left: 36 },
    xAxis: {
      type: 'category',
      data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
      axisLine: { lineStyle: { color: '#d7deea' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#edf2f8' } },
      axisLabel: { color: '#64748b' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: [0, 0, 0, 0, 0, 0, 0],
        symbolSize: 8,
        areaStyle: { color: 'rgba(14, 165, 233, 0.12)' },
        itemStyle: { color: '#0891b2' },
        lineStyle: { color: '#0891b2', width: 3 }
      }
    ],
    tooltip: { trigger: 'axis' }
  })
})
</script>
