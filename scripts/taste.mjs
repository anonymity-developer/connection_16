#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const [command, ...args] = process.argv.slice(2);
if (!['draw', 'inspect', 'lint', 'scenes', 'assets'].includes(command)) {
  console.error('Usage: node scripts/taste.mjs <draw|inspect|lint|scenes|assets> [...upstream arguments]');
  process.exit(2);
}
if (Number(process.versions.node.split('.')[0]) < 22) {
  console.error('Taste requires Node 22+; this project uses Node 24.');
  process.exit(2);
}
const script = resolve(root, '.agents/skills/design-taste-frontend/scripts', `${command}.mjs`);
const result = spawnSync(process.execPath, [script, ...args], { cwd: root, stdio: 'inherit', env: process.env });
if (result.error) console.error(result.error.message);
if (result.signal) console.error(`Taste terminated by ${result.signal}`);
process.exit(result.status ?? 1);
