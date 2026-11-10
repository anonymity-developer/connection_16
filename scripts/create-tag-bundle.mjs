#!/usr/bin/env node
import { randomBytes, createHash } from 'node:crypto';
import { mkdir, realpath, writeFile, rm } from 'node:fs/promises';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const codes = ['SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'];
export function createBundle(origin) {
  const url = new URL(origin);
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || url.pathname !== '/' || url.port && url.port !== '443') throw new Error('Use an HTTPS origin without credentials, path, query or fragment.');
  const tags = codes.map(missionCode => {
    const token = randomBytes(32).toString('base64url');
    return { missionCode, url: `${url.origin}/t/${token}`, hash: createHash('sha256').update(token, 'ascii').digest('hex') };
  });
  const sql = ['-- New tags only. Does not open the event or disable existing tags.', 'START TRANSACTION;', ...tags.map(tag => `INSERT INTO tags(mission_id,token_hash) SELECT id,'${tag.hash}' FROM missions WHERE code='${tag.missionCode}';`), 'COMMIT;', ''].join('\n');
  return { sql, tags };
}
export async function writeBundle(origin, output) {
  const root = await realpath(fileURLToPath(new URL('../', import.meta.url)));
  const parent = await realpath(dirname(resolve(output)));
  const target = resolve(parent, resolve(output).split(sep).at(-1));
  const rel = relative(root, target);
  if (!rel || rel !== '..' && !rel.startsWith(`..${sep}`)) throw new Error('Choose a new private directory outside the Git repository.');
  const bundle = createBundle(origin);
  await mkdir(target, { mode: 0o700 }); // existing directories are never overwritten
  try {
    await writeFile(resolve(target, 'tags.sql'), bundle.sql, { mode: 0o600, flag: 'wx' });
    await writeFile(resolve(target, 'private-tag-urls.json'), JSON.stringify(bundle.tags.map(({ missionCode, url }) => ({ missionCode, url })), null, 2) + '\n', { mode: 0o600, flag: 'wx' });
  } catch (error) { await rm(target, { recursive: true }); throw error; }
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  try {
    if (args.length !== 4 || args[0] !== '--origin' || args[2] !== '--out') throw new Error('Usage: node scripts/create-tag-bundle.mjs --origin https://your-domain --out /private/new-directory');
    await writeBundle(args[1], args[3]);
    console.log('Created four private tag URLs and hash-only SQL. No database changes were made.');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
