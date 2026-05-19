const input = readInput();
const diff = String(input.diffContent || '');
const issues = [];

for (const line of addedLines(diff)) {
  const text = line.text;
  const looksLikeSql = /(select|insert|update|delete|where|from|jdbcTemplate|createQuery|@Select|@Update|@Delete|@Insert)/i.test(text);
  const hasConcat = /"\s*\+|\+\s*"/.test(text);
  if (looksLikeSql && hasConcat) {
    issues.push(issue(line, 'SQL_CONCAT', 'SQL 语句存在字符串拼接风险', '新增代码疑似将 SQL 语句与变量直接拼接，可能引入 SQL 注入或语义错误。', '请使用预编译参数、MyBatis 参数绑定或 Criteria/Wrapper 方式构造查询。'));
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
    severity: 'CRITICAL',
    summary,
    detail,
    suggestion,
    codeSnippet: line.text
  };
}

function writeIssues(items) {
  console.log(JSON.stringify({ issues: items }));
}
