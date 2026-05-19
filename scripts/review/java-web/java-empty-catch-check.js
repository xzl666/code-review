const input = readInput();
const diff = String(input.diffContent || '');
const issues = [];
const lines = addedLines(diff);

for (let index = 0; index < lines.length; index += 1) {
  const line = lines[index];
  if (!/catch\s*\([^)]*\)\s*\{?/.test(line.text)) {
    continue;
  }
  const body = lines.slice(index, index + 5).map((item) => item.text.trim()).join(' ');
  const emptyCatch = /catch\s*\([^)]*\)\s*\{\s*\}/.test(body) || /catch\s*\([^)]*\)\s*\{?\s*$/.test(line.text) && /^\}?$/.test((lines[index + 1] || {}).text || '');
  const onlyPrintStackTrace = /printStackTrace\s*\(\s*\)/.test(body) && !/throw\s+|log\.(warn|error)\s*\(|return\s+/.test(body);
  if (emptyCatch || onlyPrintStackTrace) {
    issues.push(issue(line, 'WEAK_EXCEPTION_HANDLING', '异常处理不充分', '新增 catch 块为空或仅调用 printStackTrace，可能导致异常被吞掉且无法被监控系统感知。', '请记录包含业务上下文的错误日志，并根据场景重新抛出、返回明确错误或执行补偿逻辑。'));
  }
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
