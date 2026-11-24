#!/usr/bin/env node
// Checks the documentation for the mistakes that were found by reading it (ADR 0060, ADR 0061):
//   1. every relative link in a Markdown file points at a file or folder that exists;
//   2. every ADR file has a row in decisions/INDEX.md, and the row's status agrees with the file's own status line;
//   3. every ADR the index lists exists.
// Links inside code fences and inline code are ignored, as are web links and links to a heading in the same file.
// Run from anywhere: node scripts/check-docs.mjs. Exit code 1 lists what is wrong.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const docs = join(root, "kibootia-docs");
const problems = [];

function markdownFiles(dir) {
  const files = [];
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      if (name === "node_modules" || name === ".git") continue;
      files.push(...markdownFiles(path));
    } else if (name.endsWith(".md")) {
      files.push(path);
    }
  }
  return files;
}

// Removes fenced blocks and inline code so that example links are not checked.
function withoutCode(text) {
  return text.replace(/^(```|~~~)[\s\S]*?^\1[^\n]*$/gm, "").replace(/`[^`\n]*`/g, "");
}

function isChecked(target) {
  return !(/^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith("#") || target.startsWith("//"));
}

const files = [...markdownFiles(docs), join(root, "README.md"), join(root, "SECURITY.md")].filter(existsSync);
const linkPattern = /\[[^\]\n]*\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;

for (const file of files) {
  const text = withoutCode(readFileSync(file, "utf8"));
  for (const match of text.matchAll(linkPattern)) {
    const target = match[1].replace(/^<|>$/g, "");
    if (!isChecked(target)) continue;
    const path = decodeURIComponent(target.split("#")[0].split("?")[0]);
    if (path === "") continue;
    if (!existsSync(resolve(dirname(file), path))) {
      problems.push(`${relative(root, file).split(sep).join("/")}: broken link ${target}`);
    }
  }
}

// ADR index.
const decisions = join(docs, "decisions");
const indexText = readFileSync(join(decisions, "INDEX.md"), "utf8");
const rows = new Map();
for (const line of indexText.split(/\r?\n/)) {
  const m = line.match(/^\| (\d{4}) \|.*\| ([^|]+?) \| \d{4}-\d{2}-\d{2} \| \[\d{4}\]\(([^)]+)\) \|$/);
  if (m) rows.set(m[1], { status: m[2].trim(), file: m[3] });
}
const adrFiles = readdirSync(decisions).filter((f) => /^\d{4}-.*\.md$/.test(f));
for (const f of adrFiles) {
  const number = f.slice(0, 4);
  const row = rows.get(number);
  if (!row) {
    problems.push(`decisions/${f}: no row in INDEX.md`);
    continue;
  }
  if (row.file !== f) problems.push(`decisions/INDEX.md: row ${number} links ${row.file}, the file is ${f}`);
  const own = readFileSync(join(decisions, f), "utf8").match(/^- \*\*Status:\*\* (\w+)/m);
  const word = row.status.split(/[\s(]/)[0];
  if (!own) problems.push(`decisions/${f}: no Status line`);
  else if (own[1] !== word) problems.push(`decisions/${f}: status is "${own[1]}" but INDEX.md says "${row.status}"`);
}
for (const number of rows.keys()) {
  if (!adrFiles.some((f) => f.startsWith(number))) problems.push(`decisions/INDEX.md: row ${number} has no file`);
}

if (problems.length > 0) {
  console.error(`${problems.length} documentation problem(s):`);
  for (const p of problems) console.error(`  ${p}`);
  process.exit(1);
}
console.log(`Documentation checked: ${files.length} files, ${adrFiles.length} decision records. No problems.`);
