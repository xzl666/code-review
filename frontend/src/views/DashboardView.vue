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
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { ECharts } from 'echarts/core'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Bot, FolderGit2, ListChecks, Workflow } from 'lucide-vue-next'
import { getDashboardOverview, getIssueTrend, getSeverityDistribution, type NameValue } from '@/api/dashboard'

echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const range = ref('7 天')
const trendRef = ref<HTMLDivElement>()
const overview = ref({
  projectCount: 0,
  enabledProjectCount: 0,
  todayTaskCount: 0,
  todayIssueCount: 0,
  openIssueCount: 0,
  todayAiCallCount: 0,
  blockerCount: 0,
  criticalCount: 0
})
const trend = ref<NameValue[]>([])
const severityRows = ref<NameValue[]>([])
let chart: ECharts | undefined

const metrics = computed(() => [
  {
    label: '项目总数',
    value: String(overview.value.projectCount),
    note: `已启用 ${overview.value.enabledProjectCount} 个`,
    icon: FolderGit2,
    tone: 'tone-blue'
  },
  {
    label: '今日任务',
    value: String(overview.value.todayTaskCount),
    note: `今日发现 ${overview.value.todayIssueCount} 个问题`,
    icon: Workflow,
    tone: 'tone-cyan'
  },
  {
    label: '开放问题',
    value: String(overview.value.openIssueCount),
    note: `阻断 ${overview.value.blockerCount} / 严重 ${overview.value.criticalCount}`,
    icon: ListChecks,
    tone: 'tone-red'
  },
  {
    label: 'AI 调用',
    value: String(overview.value.todayAiCallCount),
    note: '今日累计',
    icon: Bot,
    tone: 'tone-green'
  }
])

const severityColors: Record<string, string> = {
  阻断: '#9f1239',
  严重: '#dc2626',
  主要: '#ea580c',
  次要: '#2563eb',
  提示: '#64748b'
}

const severity = computed(() =>
  severityRows.value.map((item) => ({
    ...item,
    color: severityColors[item.name] || '#64748b'
  }))
)

async function loadDashboard() {
  const [overviewData, trendData, severityData] = await Promise.all([
    getDashboardOverview(),
    getIssueTrend(currentRangeDays()),
    getSeverityDistribution()
  ])
  overview.value = overviewData
  trend.value = trendData
  severityRows.value = severityData
  await nextTick()
  renderTrend()
}

async function loadTrend() {
  trend.value = await getIssueTrend(currentRangeDays())
  await nextTick()
  renderTrend()
}

function currentRangeDays() {
  return range.value.startsWith('30') ? 30 : 7
}

function renderTrend() {
  if (!trendRef.value) return
  chart = chart || echarts.init(trendRef.value)
  chart.setOption({
    grid: { top: 28, right: 20, bottom: 34, left: 36 },
    xAxis: {
      type: 'category',
      data: trend.value.map((item) => item.name.slice(5)),
      axisLine: { lineStyle: { color: '#d7deea' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf2f8' } },
      axisLabel: { color: '#64748b' }
    },
    series: [
      {
        type: 'line',
        smooth: true,
        data: trend.value.map((item) => item.value),
        symbolSize: 8,
        areaStyle: { color: 'rgba(14, 165, 233, 0.12)' },
        itemStyle: { color: '#0891b2' },
        lineStyle: { color: '#0891b2', width: 3 }
      }
    ],
    tooltip: { trigger: 'axis' }
  })
}

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  chart?.dispose()
})

watch(range, () => {
  loadTrend()
})

function resizeChart() {
  chart?.resize()
}
</script>
