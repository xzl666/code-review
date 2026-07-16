const ISSUE_TYPE_TEXT: Record<string, string> = {
  BUG: '缺陷',
  COMPILATION: '编译问题',
  COMPILE: '编译问题',
  BUILD: '构建问题',
  SECURITY: '安全问题',
  PERFORMANCE: '性能问题',
  RELIABILITY: '可靠性问题',
  MAINTAINABILITY: '可维护性问题',
  STYLE: '代码规范',
  NAMING: '命名规范',
  NULL_POINTER: '空指针风险',
  EXCEPTION: '异常处理',
  TRANSACTION: '事务问题',
  CONCURRENCY: '并发问题',
  RESOURCE_LEAK: '资源泄漏',
  SQL: 'SQL 问题',
  API: '接口问题',
  CONFIG: '配置问题',
  TEST: '测试问题',
  DOCUMENTATION: '文档问题',
  OTHER: '其他问题',
  CUSTOM: '自定义'
}

export function issueTypeText(issueType?: string) {
  if (!issueType) {
    return '-'
  }
  const normalized = issueType.trim().toUpperCase()
  return ISSUE_TYPE_TEXT[normalized] || issueType
}
