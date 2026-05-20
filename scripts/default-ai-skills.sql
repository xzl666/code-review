USE code_review;

INSERT INTO cr_ai_skill (
  skill_name,
  skill_code,
  function_name,
  function_description,
  parameters_schema,
  version,
  status
)
SELECT
  '默认结构化代码问题上报',
  'DEFAULT_REVIEW_ISSUE_REPORTER',
  'submit_review_issues',
  '将 AI 代码检视发现的问题按平台字段结构化上报。没有明确、可执行的问题时返回空 issues 数组。',
  '{
  "type": "object",
  "properties": {
    "issues": {
      "type": "array",
      "description": "代码检视发现的问题列表。只包含本次 diff 引入或暴露的明确问题。",
      "items": {
        "type": "object",
        "properties": {
          "severity": {
            "type": "string",
            "enum": ["BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"],
            "description": "问题严重级别。BLOCKER 表示会导致系统不可用、数据损坏或严重安全事故；CRITICAL 表示高风险安全、事务、权限、资金等问题；MAJOR 表示明显缺陷或重要可维护性问题；MINOR 表示轻微缺陷；INFO 表示提示。"
          },
          "issueType": {
            "type": "string",
            "description": "问题类型，例如 SECURITY、PERFORMANCE、RELIABILITY、MAINTAINABILITY、STYLE。"
          },
          "filePath": {
            "type": "string",
            "description": "问题所在文件路径，优先使用平台传入的 File 路径。"
          },
          "startLine": {
            "type": "integer",
            "description": "问题起始行号，优先使用新文件行号；无法定位时可省略。"
          },
          "endLine": {
            "type": "integer",
            "description": "问题结束行号；单行问题与 startLine 相同；无法定位时可省略。"
          },
          "summary": {
            "type": "string",
            "description": "中文问题摘要，建议 30 字以内。"
          },
          "detail": {
            "type": "string",
            "description": "中文问题详情，说明风险、触发条件和影响。"
          },
          "suggestion": {
            "type": "string",
            "description": "中文修改建议，给出可执行修复方式。"
          },
          "codeSnippet": {
            "type": "string",
            "description": "相关代码片段，可摘录最小必要上下文。"
          }
        },
        "required": ["severity", "issueType", "filePath", "summary", "detail", "suggestion"]
      }
    }
  },
  "required": ["issues"]
}',
  '1.0.0',
  1
WHERE NOT EXISTS (
  SELECT 1 FROM cr_ai_skill WHERE skill_code = 'DEFAULT_REVIEW_ISSUE_REPORTER' AND deleted = 0
);

SET @default_ai_skill_id := (
  SELECT id
  FROM cr_ai_skill
  WHERE skill_code = 'DEFAULT_REVIEW_ISSUE_REPORTER' AND deleted = 0
  ORDER BY id DESC
  LIMIT 1
);

INSERT INTO cr_review_rule (
  rule_name,
  rule_code,
  rule_kind,
  rule_type,
  severity,
  project_type,
  prompt_template,
  skill_id,
  status,
  sort_order
)
SELECT
  '默认 AI 安全检视',
  'DEFAULT_AI_SECURITY_REVIEW',
  'AI',
  'SECURITY',
  'CRITICAL',
  'ALL',
  '你正在为代码检视平台执行安全专项检视。请只基于本次 Git diff 判断，不要臆测 diff 外的代码。

项目名称：${projectName}
项目类型：${projectType}
检视分支：${branch}
检视范围：最近 ${reviewDays} 天提交

检视重点：
1. 身份认证、权限校验、越权访问、敏感接口暴露。
2. SQL/NoSQL/命令/模板/路径注入，反序列化风险，SSRF，XSS，开放重定向。
3. 密钥、Token、密码、手机号、身份证、银行卡等敏感信息泄露或明文日志。
4. 文件上传下载、外部 URL 访问、加解密、签名验签、回调验签缺陷。

输出要求：
- 必须调用 submit_review_issues，并返回 {"issues":[...]}。
- 所有 summary、detail、suggestion 必须使用中文。
- 只报告有明确风险和证据的问题；没有问题时返回空数组。
- filePath 使用 diff 对应文件路径；能定位行号时填写新文件 startLine/endLine。
- severity 只能使用 BLOCKER、CRITICAL、MAJOR、MINOR、INFO。

Git diff:
${diffContent}',
  @default_ai_skill_id,
  1,
  10
WHERE @default_ai_skill_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_AI_SECURITY_REVIEW' AND deleted = 0
  );

INSERT INTO cr_review_rule (
  rule_name,
  rule_code,
  rule_kind,
  rule_type,
  severity,
  project_type,
  prompt_template,
  skill_id,
  status,
  sort_order
)
SELECT
  '默认 AI 后端可靠性检视',
  'DEFAULT_AI_BACKEND_RELIABILITY_REVIEW',
  'AI',
  'CUSTOM',
  'MAJOR',
  'BACKEND',
  '你正在为代码检视平台执行后端可靠性专项检视。请只基于本次 Git diff 判断，不要臆测 diff 外的代码。

项目名称：${projectName}
项目类型：${projectType}
检视分支：${branch}
检视范围：最近 ${reviewDays} 天提交

检视重点：
1. 事务边界、异常吞没、回滚失效、并发更新、幂等性缺失。
2. 空指针、边界条件、类型转换、日期时间、分页、排序、重复提交问题。
3. HTTP 接口参数校验、错误码、兼容性、超时、重试、资源释放。
4. 数据库写入一致性、批量操作、乐观锁、分布式锁、缓存一致性。

输出要求：
- 必须调用 submit_review_issues，并返回 {"issues":[...]}。
- 所有 summary、detail、suggestion 必须使用中文。
- 只报告本次变更中可复现、可解释、可修复的问题；不要输出泛泛建议。
- filePath 使用 diff 对应文件路径；能定位行号时填写新文件 startLine/endLine。
- severity 只能使用 BLOCKER、CRITICAL、MAJOR、MINOR、INFO。

Git diff:
${diffContent}',
  @default_ai_skill_id,
  1,
  20
WHERE @default_ai_skill_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_AI_BACKEND_RELIABILITY_REVIEW' AND deleted = 0
  );

INSERT INTO cr_review_rule (
  rule_name,
  rule_code,
  rule_kind,
  rule_type,
  severity,
  project_type,
  prompt_template,
  skill_id,
  status,
  sort_order
)
SELECT
  '默认 AI 性能与资源检视',
  'DEFAULT_AI_PERFORMANCE_REVIEW',
  'AI',
  'PERFORMANCE',
  'MAJOR',
  'ALL',
  '你正在为代码检视平台执行性能与资源专项检视。请只基于本次 Git diff 判断，不要臆测 diff 外的代码。

项目名称：${projectName}
项目类型：${projectType}
检视分支：${branch}
检视范围：最近 ${reviewDays} 天提交

检视重点：
1. 循环内数据库/网络/文件 IO、N+1 查询、无界分页或一次性加载大数据。
2. 连接、流、线程池、定时任务、锁、缓存、临时文件等资源泄漏或滥用。
3. 不必要的同步阻塞、重复计算、低效集合操作、错误缓存策略。
4. 前端场景关注重复渲染、大体积资源、无节制请求、内存泄漏。

输出要求：
- 必须调用 submit_review_issues，并返回 {"issues":[...]}。
- 所有 summary、detail、suggestion 必须使用中文。
- 只报告有明确性能风险的问题；不要输出微优化或风格偏好。
- filePath 使用 diff 对应文件路径；能定位行号时填写新文件 startLine/endLine。
- severity 只能使用 BLOCKER、CRITICAL、MAJOR、MINOR、INFO。

Git diff:
${diffContent}',
  @default_ai_skill_id,
  1,
  30
WHERE @default_ai_skill_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_AI_PERFORMANCE_REVIEW' AND deleted = 0
  );

INSERT INTO cr_review_rule (
  rule_name,
  rule_code,
  rule_kind,
  rule_type,
  severity,
  project_type,
  prompt_template,
  skill_id,
  status,
  sort_order
)
SELECT
  '默认 AI 前端质量检视',
  'DEFAULT_AI_FRONTEND_QUALITY_REVIEW',
  'AI',
  'CUSTOM',
  'MAJOR',
  'FRONTEND',
  '你正在为代码检视平台执行前端质量专项检视。请只基于本次 Git diff 判断，不要臆测 diff 外的代码。

项目名称：${projectName}
项目类型：${projectType}
检视分支：${branch}
检视范围：最近 ${reviewDays} 天提交

检视重点：
1. 状态管理、异步请求、竞态条件、重复提交、错误处理缺失。
2. 表单校验、权限控制、路由守卫、接口返回兼容性、空态和异常态。
3. XSS、危险 HTML 注入、敏感信息暴露、前端鉴权绕过风险。
4. Vue/React 生命周期、事件监听、定时器、订阅清理、组件性能问题。

输出要求：
- 必须调用 submit_review_issues，并返回 {"issues":[...]}。
- 所有 summary、detail、suggestion 必须使用中文。
- 只报告会影响功能、安全、可靠性或明显可维护性的问题；不要输出审美偏好。
- filePath 使用 diff 对应文件路径；能定位行号时填写新文件 startLine/endLine。
- severity 只能使用 BLOCKER、CRITICAL、MAJOR、MINOR、INFO。

Git diff:
${diffContent}',
  @default_ai_skill_id,
  1,
  40
WHERE @default_ai_skill_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM cr_review_rule WHERE rule_code = 'DEFAULT_AI_FRONTEND_QUALITY_REVIEW' AND deleted = 0
  );
