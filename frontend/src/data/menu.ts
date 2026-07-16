import {
  Bell,
  ChartNoAxesCombined,
  ClipboardList,
  FolderGit2,
  ListChecks,
  Settings,
  ShieldCheck
} from 'lucide-vue-next'

export const menuItems = [
  { path: '/dashboard', title: '首页看板', icon: ChartNoAxesCombined },
  { path: '/projects', title: '项目管理', icon: FolderGit2 },
  { path: '/rules', title: '检视规则', icon: ShieldCheck },
  { path: '/review-tasks', title: '检视任务', icon: ClipboardList },
  { path: '/issues', title: '问题列表', icon: ListChecks },
  { path: '/notifications', title: '通知配置', icon: Bell },
  { path: '/system-config', title: '系统配置', icon: Settings }
]
