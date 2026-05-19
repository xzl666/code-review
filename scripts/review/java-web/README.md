# Java Web 后端常用检视脚本

这些脚本用于补充 AI 检视之外的确定性检查，适合绑定到“脚本规则”。

统一输入：从 stdin 读取 JSON，至少包含 `diffContent` 字段。

统一输出：

```json
{"issues":[]}
```

脚本清单：

1. `java-sensitive-log-check.js`：检查新增日志中是否输出 password、token、secret 等敏感字段。
2. `java-sql-concat-check.js`：检查新增 SQL/JdbcTemplate/MyBatis 注解中是否存在字符串拼接风险。
3. `java-controller-validation-check.js`：检查 Controller 新增 `@RequestBody` 入参是否缺少 `@Valid`/`@Validated`。
4. `java-empty-catch-check.js`：检查新增空 `catch` 或仅 `printStackTrace` 的异常处理。
5. `java-transaction-rollback-check.js`：检查新增 `@Transactional` 是否缺少 `rollbackFor` 配置。

这些脚本只做轻量启发式匹配，定位到的是“需要人工确认”的风险点。
