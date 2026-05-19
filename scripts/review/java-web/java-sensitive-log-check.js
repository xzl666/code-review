const input = readInput();
const diff = String(input.diffContent || '');
const issues = [];

for (const line of addedLines(diff)) {
  if (!/log\.(trace|debug|info|warn|error)\s*\(/.test(line.text)) {
    continue;
  }
  if (!/(password|passwd|pwd|token|secret|apiKey|accessKey|privateKey|authorization|cookie)/i.test(line.text)) {
    continue;
  }
  issues.push(issue(line, 'SENSITIVE_LOG', '日志中可能输出敏感信息', '新增日志语句包含密码、Token、密钥或认证信息字段，可能导致敏感数据进入日志系统。', '请移除敏感字段，或在输出前进行脱敏处理。'));
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
    severity: 'MAJOR',
    summary,
    detail,
    suggestion,
    codeSnippet: line.text
  };
}

function writeIssues(items) {
  console.log(JSON.stringify({ issues: items }));
}
