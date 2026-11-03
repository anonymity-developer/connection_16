#!/usr/bin/env node
import { readFileSync, readdirSync, lstatSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, relative, sep } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const lock = JSON.parse(readFileSync(resolve(root, '.agents/skills.lock.json'), 'utf8'));
const errors = [];
if (lock.schemaVersion !== 1) throw new Error('Unsupported skills lock schema');
for (const skill of lock.skills) {
  const directory = resolve(root, skill.directory);
  if (!directory.startsWith(resolve(root, '.agents/skills') + sep)) { errors.push(`${skill.name}: invalid skill directory`); continue; }
  const actual = [];
  function visit(path) {
    for (const item of readdirSync(path)) {
      const full = resolve(path, item);
      const stat = lstatSync(full);
      if (stat.isSymbolicLink()) { errors.push(`${skill.name}: unexpected symlink ${relative(directory, full)}`); continue; }
      if (stat.isDirectory()) visit(full);
      else actual.push(relative(directory, full).split(sep).join('/'));
    }
  }
  try { visit(directory); } catch (error) { errors.push(`${skill.name}: ${error.message}`); continue; }
  for (const [path, expected] of Object.entries(skill.files)) {
    const full = resolve(directory, path);
    if (!full.startsWith(directory + sep)) { errors.push(`${skill.name}: invalid locked path`); continue; }
    try {
      const actualHash = createHash('sha256').update(readFileSync(full)).digest('hex');
      if (actualHash !== expected) errors.push(`${skill.name}/${path}: source hash differs`);
    } catch { errors.push(`${skill.name}/${path}: missing file`); }
  }
  for (const path of actual) if (!(path in skill.files)) errors.push(`${skill.name}/${path}: untracked vendor file`);
  console.log(`${skill.name}: ${Object.keys(skill.files).length} pinned files @ ${skill.commit.slice(0, 12)}`);
}
if (errors.length) { console.error(errors.join('\n')); process.exitCode = 1; }
else console.log('All vendored skill files match the pinned source manifest.');
