import { pathToFileURL } from 'node:url';
export function render(opens, closes, moderation) {
  const inputs = [opens, closes, moderation];
  if (!inputs.every(v => typeof v === 'string' && /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ$/.test(v) && Number.isFinite(Date.parse(v)) && new Date(v).toISOString().replace('.000Z','Z') === v)) throw new Error('Three exact UTC timestamps required');
  if (!(Date.parse(opens) < Date.parse(closes) && Date.parse(closes) <= Date.parse(moderation))) throw new Error('Invalid date order');
  const [a,b,c] = inputs.map(v => v.replace('T',' ').slice(0,-1));
  return `-- REVIEW ONLY. TEST FIXTURE, NOT APPROVED EVENT/POLICIES. No SQL connection is made by this generator.
-- Dedicated empty schema tagme_local_validation only. Existing accounts/settings are never overwritten.
SET SESSION time_zone='+00:00';
START TRANSACTION;
SET @tagme_dev_ok=(DATABASE()='tagme_local_validation' AND (SELECT COUNT(*) FROM users)=0 AND (SELECT COUNT(*) FROM event_settings)=0 AND (SELECT COUNT(*) FROM consent_policies)=0 AND (SELECT COUNT(*) FROM current_consent_policies)=0);
INSERT INTO event_settings(id,opens_at,submit_closes_at,moderation_closes_at)
 SELECT 1,'${a}','${b}','${c}' WHERE @tagme_dev_ok;
INSERT INTO consent_policies(policy_type,policy_version,title,body)
 SELECT 'PARTICIPATION','local-fixture-v1','LOCAL TEST ONLY','Synthetic local test fixture. NOT an approved privacy or participation policy.' WHERE @tagme_dev_ok;
INSERT INTO consent_policies(policy_type,policy_version,title,body)
 SELECT 'PROFILE_PUBLICATION','local-fixture-v1','LOCAL TEST ONLY','Synthetic local test fixture. NOT approved profile publication terms.' WHERE @tagme_dev_ok;
INSERT INTO current_consent_policies(policy_type,policy_version)
 SELECT policy_type,policy_version FROM consent_policies WHERE policy_version='local-fixture-v1' AND @tagme_dev_ok;
SELECT @tagme_dev_ok AS isolated_empty_schema_guard;
-- Default intentionally persists nothing. Do not execute in the existing UI review/test-account database.
ROLLBACK;
`;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { if (process.argv.length !== 5) throw new Error(); process.stdout.write(render(...process.argv.slice(2))); }
  catch { console.error('Provide opens/closes/moderation timestamps in YYYY-MM-DDTHH:mm:ssZ order. No SQL generated.'); process.exitCode=1; }
}
