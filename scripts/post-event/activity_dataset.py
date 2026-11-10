"""Offline fixture contract and HMAC pseudonymization; deliberately no DB/network connector.
Real individual export is NOT enabled. Do not feed participant data before policy approval.
"""
import csv
import hashlib
import hmac
from datetime import datetime, timezone
from pathlib import Path

COLUMNS = ('participant_id', 'event_type', 'mission_code', 'event_time', 'status', 'source_type')
EVENTS = {'ACCOUNT_CREATED', 'PROFILE_FIRST_PUBLISHED', 'NFC_FIRST_UNLOCK', 'SYNC_SUBMITTED', 'WISH_SUBMITTED', 'CHEER_SUBMITTED', 'COMMENT_SUBMITTED', 'MODERATION_DECISION', 'TAP_SCHEDULED', 'TAP_SUBMITTED', 'LETTER_GRANTED', 'ALL_COMPLETED', 'HEART_CURRENT', 'PROFILE_FIRST_VISIT'}
MISSIONS = {'', 'SHOW_ME', 'SYNC_ME', 'MAKE_A_WISH', 'FAST_TAP', 'SAY_SOMETHING'}
SOURCES = {
    'ACCOUNT_CREATED':'users.created_at', 'PROFILE_FIRST_PUBLISHED':'profiles.published_at',
    'NFC_FIRST_UNLOCK':'mission_progress.unlocked_at', 'SYNC_SUBMITTED':'sync_results.submitted_at',
    'WISH_SUBMITTED':'content_revisions.submitted_at', 'CHEER_SUBMITTED':'content_revisions.submitted_at', 'COMMENT_SUBMITTED':'content_revisions.submitted_at',
    'MODERATION_DECISION':'moderation_reviews.created_at', 'TAP_SCHEDULED':'tap_attempts.starts_at', 'TAP_SUBMITTED':'tap_attempts.submitted_at',
    'LETTER_GRANTED':'mission_progress.completed_at', 'ALL_COMPLETED':'event_completions.completed_at', 'HEART_CURRENT':'cheer_likes.created_at', 'PROFILE_FIRST_VISIT':'profile_views.first_viewed_at',
}
STATUSES = {'ACTIVE','PUBLIC','PRIVATE','STORED','RECORDED','PRESENT','UNLOCKED','IN_PROGRESS','COMPLETED','COUNTDOWN','CANCELLED','INTERRUPTED','VALID','REVIEW_REQUIRED','EXPIRED','QUEUED','PROCESSING','APPROVED','REJECTED','SUPERSEDED'}
STAGING = {'internal_user_id', 'event_type', 'mission_code', 'event_time', 'status', 'source_type', 'record_key'}

def utc_time(text):
    value = datetime.fromisoformat(text.replace('Z', '+00:00'))
    if value.tzinfo is None or value.utcoffset().total_seconds() != 0:
        raise ValueError('UTC offset required')
    return value

def cell(value):
    text = str(value or '')
    if text.lstrip().startswith(('=', '+', '-', '@')) or text.startswith(('\t', '\r', '\n')):
        text = "'" + text
    return text

def transform(rows, key, period):
    """Pure design helper, tested only with synthetic rows. Key stays in memory; never saved."""
    if len(key) < 32 or not period or len(period) > 100:
        raise ValueError('A 32-byte private key and explicit analysis period are required')
    output, seen = [], {}
    for row in rows:
        if set(row) != STAGING or row['event_type'] not in EVENTS or (row['mission_code'] or '') not in MISSIONS:
            raise ValueError('Unexpected staging fields or event definition')
        if row['source_type'] != SOURCES[row['event_type']] or row['status'] not in STATUSES or int(row['internal_user_id']) < 1:
            raise ValueError('Unknown source/status or invalid participant')
        utc_time(row['event_time'])
        if row['event_type'] == 'NFC_FIRST_UNLOCK' and row['mission_code'] == 'SHOW_ME':
            raise ValueError('SHOW ME is not NFC')
        identity = (int(row['internal_user_id']), row['record_key'])
        if identity in seen:
            if row != seen[identity]:
                raise ValueError('Conflicting rows for one event key')
            continue
        seen[identity] = row
        participant = 'p_' + hmac.new(key, (period + '\0' + str(identity[0])).encode(), hashlib.sha256).hexdigest()
        output.append(dict(zip(COLUMNS, [participant, row['event_type'], row['mission_code'] or '', row['event_time'], row['status'], row['source_type']])))
    return sorted(output, key=lambda r: (utc_time(r['event_time']), r['participant_id'], r['event_type'], r['source_type']))

def write_fixture(path):
    """Only the built-in synthetic dataset is accepted by the command line."""
    rows = [
        dict(internal_user_id=101,event_type='ACCOUNT_CREATED',mission_code='',event_time='2026-01-01T00:00:00.000001Z',status='ACTIVE',source_type='users.created_at',record_key='user:101'),
        dict(internal_user_id=101,event_type='PROFILE_FIRST_PUBLISHED',mission_code='SHOW_ME',event_time='2026-01-01T00:01:00Z',status='PUBLIC',source_type='profiles.published_at',record_key='profile:101'),
        dict(internal_user_id=101,event_type='LETTER_GRANTED',mission_code='SHOW_ME',event_time='2026-01-01T00:02:00Z',status='COMPLETED',source_type='mission_progress.completed_at',record_key='grant:101:1'),
        dict(internal_user_id=202,event_type='NFC_FIRST_UNLOCK',mission_code='MAKE_A_WISH',event_time='2026-01-01T00:03:00Z',status='IN_PROGRESS',source_type='mission_progress.unlocked_at',record_key='unlock:202:3'),
        dict(internal_user_id=202,event_type='WISH_SUBMITTED',mission_code='MAKE_A_WISH',event_time='2026-01-01T00:04:00Z',status='REVIEW_REQUIRED',source_type='content_revisions.submitted_at',record_key='revision:7'),
    ]
    result = transform(rows, b'fixture-only-nonsecret-key-32bytes!', 'SYNTHETIC-EXAMPLE')
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', newline='', encoding='utf-8-sig') as f:
        writer = csv.DictWriter(f, fieldnames=COLUMNS); writer.writeheader()
        writer.writerows({k: cell(v) for k, v in row.items()} for row in result)
    return len(result)

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description='Generate SYNTHETIC fixture only. No individual export or DB access.')
    parser.add_argument('--fixture-output', required=True)
    args = parser.parse_args()
    print(f'SYNTHETIC ONLY: {write_fixture(args.fixture_output)} rows')
