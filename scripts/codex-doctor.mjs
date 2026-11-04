#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../', import.meta.url));
const run = (command, args = []) => spawnSync(command, args, { cwd: root, encoding: 'utf8', timeout: 6000, windowsHide: true });
const checks = [];
function check(name, available, detail, required = false) { checks.push({ name, available, required, detail }); }
check('Node 24', Number(process.versions.node.split('.')[0]) === 24, process.version, true);
const npm = run(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['--version']);
check('npm', npm.status === 0, npm.status === 0 ? npm.stdout.trim() : 'Install Node 24 including npm.', true);
const java = run('java', ['-version']);
const javaVersion = (java.stderr || java.stdout || '').split('\n')[0];
check('Java 21', java.status === 0 && /version "21\b/.test(javaVersion), javaVersion || 'Install/select JDK 21 for backend work.');
const javac = run('javac', ['-version']);
const javacVersion = (javac.stdout || javac.stderr || '').trim();
check('JDK 21 compiler', javac.status === 0 && /javac 21\b/.test(javacVersion), javacVersion || 'Install JDK 21 (a JRE alone cannot compile the backend).');
const docker = run('docker', ['info', '--format', '{{.ServerVersion}}']);
check('Docker daemon', docker.status === 0, docker.status === 0 ? docker.stdout.trim() : 'Docker unavailable; Testcontainers/full-stack checks cannot pass here.');
const chromeCandidates = [process.env.CHROME_PATH, '/usr/bin/chromium', '/usr/bin/google-chrome', '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', process.env.PROGRAMFILES && resolve(process.env.PROGRAMFILES, 'Google/Chrome/Application/chrome.exe')].filter(Boolean);
const chrome = chromeCandidates.find((path) => existsSync(path));
check('Chrome executable', Boolean(chrome), chrome || 'Install Chrome/Chromium or set CHROME_PATH. Presence alone does not prove launch is allowed.');
const gh = run('gh', ['auth', 'status']);
check('GitHub CLI authentication', gh.status === 0, gh.status === 0 ? 'Authenticated; no credentials printed.' : 'gh is not authenticated/available; use the connected GitHub app when supplied.');
const skills = run(process.execPath, ['scripts/verify-skills.mjs']);
check('Pinned project skills', skills.status === 0, skills.status === 0 ? 'Source integrity passed.' : (skills.stderr || skills.error?.message || 'Check missing skill files.'), true);
const config = JSON.parse(readFileSync(resolve(root, 'frontend/package.json'), 'utf8'));
check('Frontend dependencies', existsSync(resolve(root, 'frontend/node_modules')), 'Run npm ci in frontend; lockfile is committed.', false);
console.log(JSON.stringify({ repository: 'connection_16', frontendEngine: config.engines.node, checks, notes: ['Browser launch, Playwright CLI package and external network access require their own actual smoke checks.', 'A connected GitHub app is independent of gh auth.', 'Run without --strict for an inventory; --strict fails missing required Node/npm/source checks.'] }, null, 2));
if (process.argv.includes('--strict') && checks.some((item) => item.required && !item.available)) process.exitCode = 1;
