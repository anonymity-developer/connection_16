import test from 'node:test';
import assert from 'node:assert/strict';
import { validateAuth } from '../src/features/auth/validation.ts';

const values = { username: 'ui_test', password: 'example-only-password', confirmation: 'example-only-password', code: '012345' };

test('ordinary login uses the Mattermost username and service password without an OTP', () => {
  assert.deepEqual(validateAuth('login', 'account', { ...values, code: '' }), {});
  const errors = validateAuth('login', 'account', { ...values, username: ' ', password: '' });
  assert.ok(errors.username);
  assert.ok(errors.password);
  assert.equal(errors.code, undefined);
});

test('signup and reset request a code before requiring a new password', () => {
  for (const mode of ['signup', 'reset']) {
    assert.deepEqual(validateAuth(mode, 'account', { ...values, password: '', confirmation: '', code: '' }), {});
    assert.ok(validateAuth(mode, 'account', { ...values, username: '' }).username);
  }
});

test('all verification flows accept six ASCII digits and preserve a leading zero', () => {
  for (const mode of ['signup', 'reset', 'change']) {
    for (const code of ['012345', '000000', '123456']) assert.deepEqual(validateAuth(mode, 'verify', { ...values, code }), {});
    for (const code of ['', '12345', '1234567', '12345a', '１２３４５６', '123 45']) assert.ok(validateAuth(mode, 'verify', { ...values, code }).code);
  }
  assert.equal(values.code, '012345');
});

test('password change checks the code and confirmation without asking for another account', () => {
  assert.deepEqual(validateAuth('change', 'verify', { ...values, username: '' }), {});
  assert.ok(validateAuth('change', 'verify', { ...values, confirmation: 'different' }).confirmation);
  assert.ok(validateAuth('change', 'verify', { ...values, code: '' }).code);
  assert.ok(validateAuth('change', 'verify', { ...values, password: '' }).password);
});

test('new passwords use 15–64 Unicode code points without trimming', () => {
  for (const password of ['a'.repeat(14), '가'.repeat(65)]) assert.ok(validateAuth('signup', 'verify', { ...values, password, confirmation: password }).password);
  for (const password of ['가'.repeat(64), '😀'.repeat(15), ' a long password ']) assert.deepEqual(validateAuth('signup', 'verify', { ...values, password, confirmation: password }), {});
});
