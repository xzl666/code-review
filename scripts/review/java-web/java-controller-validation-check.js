const input = readInput();
const diff = String(input.diffContent || '');
const issues = [];
const lines = addedLines(diff);

for (let index = 0; index < lines.length; index += 1) {
  const line = lines[index];
  if (!/@RequestBody\b/.test(line.text)) {
    continue;
  }
  const windowText = lines.slice(Math.max(0, index - 3), index + 2).map((item) => item.text).join('\n');
  const isController = /Controller\.java$/.test(line.filePath) || /@RequestMapping|@PostMapping|@PutMapping|@PatchMapping/.test(windowText);
  const hasValidation = /@Valid\b|@Validated\b/.test(windowText);
  if (isController && !hasValidation) {
    issues.push(issue(line, 'MISSING_REQUEST_VALIDATION', 'Controller 请求体缺少参数校验', '新增 `@RequestBody` 入参没有同时声明 `@Valid` 或 `@Validated`，可能绕过 DTO 字段约束。', '请在请求体参数前增加 `@Valid` 或 `@Validated`，并确保 DTO 字段上声明必要的校验注解。'));
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
