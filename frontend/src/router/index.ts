import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import ProjectView from '@/views/ProjectView.vue'
import RuleView from '@/views/RuleView.vue'
import SkillView from '@/views/SkillView.vue'
import ScriptRuleView from '@/views/ScriptRuleView.vue'
import ReviewTaskView from '@/views/ReviewTaskView.vue'
import IssueView from '@/views/IssueView.vue'
import NotificationView from '@/views/NotificationView.vue'
import SystemConfigView from '@/views/SystemConfigView.vue'

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
