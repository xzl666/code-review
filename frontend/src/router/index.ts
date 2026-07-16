import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'
import { CURRENT_USER_KEY } from '@/utils/currentUser'

const DashboardView = () => import('@/views/DashboardView.vue')
const ProjectView = () => import('@/views/ProjectView.vue')
const RuleView = () => import('@/views/RuleView.vue')
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
      { path: 'review-tasks', name: 'review-tasks', component: ReviewTaskView, meta: { title: '检视任务' } },
      { path: 'issues', name: 'issues', component: IssueView, meta: { title: '问题列表' } },
      { path: 'notifications', name: 'notifications', component: NotificationView, meta: { title: '通知配置' } },
      { path: 'system-config', name: 'system-config', component: SystemConfigView, meta: { title: '系统配置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const userId = typeof to.query.userId === 'string' ? to.query.userId : ''
  if (/^[A-Fa-f0-9]{32,64}$/.test(userId)) localStorage.setItem(CURRENT_USER_KEY, userId)
})

export default router
