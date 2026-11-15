import { useEffect, useRef, useState } from 'react';
import { Button } from '../components/ui';
import { WishGallery } from '../features/content/WishGallery';
import { WishComments } from '../features/content/WishComments';
import { WishToss } from '../features/content/WishToss';
import type { Experience } from './experience-store';

export function FountainExperience({ store }: { store: Experience }) {
  const slot = store.slots.wishes;
  const [text, setText] = useState(slot.latestRevision?.content || '');
  const [phase, setPhase] = useState<'write' | 'launching' | 'landed'>(slot.id ? 'landed' : 'write');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [anonymous, setAnonymous] = useState(slot.latestRevision?.anonymous ?? true);
  const input = useRef<HTMLTextAreaElement>(null);
  const active = useRef(true);
  const posting = useRef(false);

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);
  useEffect(() => {
    if (phase !== 'launching') return;
    const timer = window.setTimeout(() => setPhase('landed'), 1800);
    return () => window.clearTimeout(timer);
  }, [phase]);

  async function launch() {
    if (posting.current || phase !== 'write') return;
    if (!text.trim()) {
      setError('종이배에 담을 소원을 적어주세요.');
      input.current?.focus();
      return;
    }
    posting.current = true;
    setBusy(true);
    setError('');
    try {
      const current = store.slots.wishes;
      await store.content.submit('wishes', !!current.id, crypto.randomUUID(), {
        content: text.trim(),
        templateId: 'PLAIN',
        style: 'ROSE',
        anonymous,
        expectedVersion: current.id ? current.version : null,
      });
      if (active.current) setPhase('launching');
    } catch {
      if (active.current) setError('가상 접수에 실패했어요. 같은 소원으로 다시 시도해 주세요.');
    } finally {
      posting.current = false;
      if (active.current) setBusy(false);
    }
  }

  return <section className="demo-fountain">
    {phase === 'write' && <form className="demo-wish-composer" onSubmit={(event) => { event.preventDefault(); void launch(); }}>
      <label htmlFor="demo-wish">종이배에 어떤 소원을 담을까요?</label>
      <textarea ref={input} id="demo-wish" maxLength={400} rows={3} value={text} disabled={busy} onChange={(event) => setText(event.target.value)} />
      <small>{text.length}/400 · 가상 소원, 실제 전송 없음</small>
      <label className="content-choice"><input type="checkbox" checked={anonymous} disabled={busy} onChange={(event) => setAnonymous(event.target.checked)} />익명으로 남기기</label>
      <Button type="submit" disabled={busy}>{busy ? '띄우는 중…' : '종이배 띄우기'}</Button>
    </form>}
    <div className="demo-throw-scene">
      <WishGallery
        items={store.feeds.wishes}
        sceneOverlay={phase === 'launching' ? <WishToss earned /> : undefined}
        renderComments={(id) => <WishComments id={id} api={store.reactions} embedded />}
      />
    </div>
    {phase === 'landed' && <p className="demo-wish-notice" role="status">소원을 담은 종이배를 호수에 띄웠어요.</p>}
    {phase === 'landed' && <Button variant="quiet" onClick={() => setPhase('write')}>같은 가상 소원 수정하기</Button>}
    {error && <p role="alert" className="form-notice is-error">{error}</p>}
  </section>;
}
