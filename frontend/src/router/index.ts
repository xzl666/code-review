import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

const DashboardView = () => import('@/views/DashboardView.vue')
const ProjectView = () => import('@/views/ProjectView.vue')
const RuleView = () => import('@/views/RuleView.vue')
const SkillView = () => import('@/views/SkillView.vue')
const ScriptRuleView = () => import('@/views/ScriptRuleView.vue')
const ReviewTaskView = () => import('@/views/ReviewTaskView.vue')
const IssueView = () => import('@/views/IssueView.vue')
const NotificationView = () => import('@/views/NotificationView.vue')
const SystemConfigView = () => import('@/views/SystemConfigView.vue')

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '首页看板' } },
      { path: 'projects', name: 'projects', component: ProjectView, meta: { title: '项目管理' } },
      { path: 'rules', name: 'rules', component: RuleView, meta: { title: '检视规则' } },
      { path: 'skills', name: 'skills', component: SkillView, meta: { title: 'AI Skill' } },
      { path: 'scripts', name: 'scripts', component: ScriptRuleView, meta: { title: '脚本规则' } },
      { path: 'review-tasks', name: 'review-tasks', component: ReviewTaskView, meta: { title: '检视任务' } },
      { path: 'issues', name: 'issues', component: IssueView, meta: { title: '问题列表' } },
      { path: 'notifications', name: 'notifications', component: NotificationView, meta: { title: '通知配置' } },
      { path: 'system-config', name: 'system-config', component: SystemConfigView, meta: { title: '系统配置' } }
    ]
  }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
