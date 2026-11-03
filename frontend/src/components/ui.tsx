import { useEffect, useId, useRef, useState } from 'react';
import type { ButtonHTMLAttributes, InputHTMLAttributes, KeyboardEvent, ReactNode } from 'react';
import { Icon } from './Icon';

export function Button({ variant = 'primary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'quiet' }) {
  return <button {...props} className={`button button--${variant} ${className}`} />;
}

export function Field({ label, hint, error, password = false, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string; password?: boolean }) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  return <div className={`field ${error ? 'field--error' : ''}`}>
    <label htmlFor={id}>{label}</label>
    <div className="input-wrap"><input {...props} id={id} type={password ? visible ? 'text' : 'password' : props.type ?? 'text'} aria-invalid={error ? true : undefined} aria-describedby={error || hint ? `${id}-help` : undefined} />
      {password && <button type="button" className="password-toggle" aria-label={visible ? `${label} 숨기기` : `${label} 표시`} aria-pressed={visible} onClick={() => setVisible(!visible)}><Icon name={visible ? 'eyeOff' : 'eye'} /></button>}
    </div>
    {(error || hint) && <p id={`${id}-help`} className={`field-help ${error ? 'field-help--error' : ''}`}>{error || hint}</p>}
  </div>;
}

export function Dialog({ open, title, children, onClose }: { open: boolean; title: string; children: ReactNode; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (open && !dialog?.open) dialog?.showModal();
    if (!open && dialog?.open) dialog.close();
  }, [open]);
  function trapFocus(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key !== 'Tab') return;
    const controls = [...event.currentTarget.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea, [tabindex]')]
      .filter((element) => element.tabIndex >= 0 && !element.matches(':disabled') && element.getClientRects().length > 0);
    const first = controls[0];
    const last = controls.at(-1);
    const active = document.activeElement;
    // Native dialog makes the background inert, but Chrome can still move
    // focus to the browser chrome at the end. Keep both Tab directions inside.
    if (event.shiftKey && (active === first || !controls.includes(active as HTMLElement))) {
      event.preventDefault(); last?.focus();
    } else if (!event.shiftKey && (active === last || !controls.includes(active as HTMLElement))) {
      event.preventDefault(); first?.focus();
    }
  }
  return <dialog ref={ref} className="dialog" aria-labelledby={titleId} onKeyDown={trapFocus} onCancel={(event) => { event.preventDefault(); onClose(); }} onClick={(event) => { if (event.target === event.currentTarget) onClose(); }}>
    <div className="dialog-inner"><button type="button" className="icon-button dialog-close" aria-label="닫기" onClick={onClose}><Icon name="close" /></button><h2 id={titleId}>{title}</h2>{children}</div>
  </dialog>;
}

export function EmptyState({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return <section className="empty-state"><span className="empty-symbol"><Icon name="tag" size={28} /></span><h2>{title}</h2><p>{children}</p>{action}</section>;
}
