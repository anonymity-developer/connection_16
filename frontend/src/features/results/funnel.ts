import { postTelemetry } from '../auth/api';

export function trackSyncStep(version: string, step: number) {
  if (import.meta.env.DEV || navigator.webdriver) return;
  try {
    void postTelemetry('/sync/funnel-events', {
      eventKey: crypto.randomUUID(), mission: 'SYNC_ME',
      eventType: step === 0 ? 'SCREEN_ENTER' : 'QUESTION_VIEW',
      questionnaireVersion: version, step, occurredAt: new Date().toISOString(),
    });
  } catch { /* Unsupported browser telemetry never affects the survey. */ }
}
