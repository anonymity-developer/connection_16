import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createBundle, writeBundle } from './create-tag-bundle.mjs';

test('tag bundles contain four NFC-only unique canonical 32-byte tokens and only their hashes in SQL', () => {
  const bundle = createBundle('https://tagme.example.com');
  assert.equal(new Set(bundle.tags.map(tag => tag.url)).size, 4);
  assert.deepEqual(bundle.tags.map(t => t.missionCode), ['SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING']);
  for (const tag of bundle.tags) {
    const token = new URL(tag.url).pathname.slice(3);
    assert.match(token, /^[A-Za-z0-9_-]{43}$/); assert.equal(Buffer.from(token, 'base64url').length, 32);
    assert.equal(Buffer.from(token, 'base64url').toString('base64url'), token);
    assert.equal(tag.hash, createHash('sha256').update(token).digest('hex'));
    assert.equal(bundle.sql.includes(token), false); assert.equal(bundle.sql.includes(tag.hash), true);
  }
  for (const bad of ['http://example.com', 'https://example.com/path', 'https://user:pass@example.com', 'https://example.com/?token=x']) assert.throws(() => createBundle(bad));
});
test('private bundles cannot overwrite a directory or be written into the repository', async () => {
  const root = await mkdtemp(join(tmpdir(), 'tagme-bundle-test-'));
  try {
    const out = join(root, 'private'); await writeBundle('https://tagme.example.com', out);
    const sql = await readFile(join(out, 'tags.sql'), 'utf8'); assert.equal(sql.includes('/t/'), false);
    await assert.rejects(writeBundle('https://tagme.example.com', out));
    await assert.rejects(writeBundle('https://tagme.example.com', join(process.cwd(), 'should-not-exist')));
  } finally { await rm(root, { recursive: true }); }
});
