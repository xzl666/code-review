const input = readInput();
const diff = String(input.diffContent || '');
const issues = [];

for (const line of addedLines(diff)) {
  if (!/@Transactional\b/.test(line.text)) {
    continue;
  }
  if (/rollbackFor\s*=|rollbackForClassName\s*=/.test(line.text)) {
    continue;
  }
  issues.push(issue(line, 'TRANSACTION_ROLLBACK_FOR', '事务注解缺少 rollbackFor 配置', '新增 `@Transactional` 未显式声明 rollbackFor。默认只对 RuntimeException 回滚，受检异常可能不会触发回滚。', '建议统一使用 `@Transactional(rollbackFor = Exception.class)`，或明确说明只需要运行时异常回滚。'));
}

writeIssues(issues);

function readInput() {
  const fs = require('fs');
  const text = fs.readFileSync(0, 'utf8');
  return text.trim() ? JSON.parse(text) : {};
}

function addedLines(diffText) {
  const result = [];
  let currentFile = '';
  let newLine = 0;
  for (const raw of diffText.split(/\r?\n/)) {
    const fileMatch = raw.match(/^\+\+\+\s+b\/(.+)$/);
    if (fileMatch) currentFile = fileMatch[1];
    const hunkMatch = raw.match(/^@@\s+-\d+(?:,\d+)?\s+\+(\d+)(?:,\d+)?\s+@@/);
    if (hunkMatch) {
      newLine = Number(hunkMatch[1]);
      continue;
    }
    if (raw.startsWith('+') && !raw.startsWith('+++')) {
      result.push({ filePath: currentFile, startLine: newLine, text: raw.slice(1) });
      newLine += 1;
    } else if (!raw.startsWith('-')) {
      newLine += 1;
    }
  }
  return result;
}

function issue(line, type, summary, detail, suggestion) {
  return {
    issueType: type,
    filePath: line.filePath || 'UNKNOWN',
    startLine: line.startLine,
    endLine: line.startLine,
    severity: 'MINOR',
    summary,
    detail,
    suggestion,
    codeSnippet: line.text
  };
}

function writeIssues(items) {
  console.log(JSON.stringify({ issues: items }));
}
