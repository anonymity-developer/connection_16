import { useId } from 'react';
/** Original TAG ME letter artwork on an architectural fountain; no new mascot. */
export function FountainArtwork() {
  const id=useId();
  return <div className="fountain-art" aria-hidden="true">
    <svg viewBox="0 0 600 600" preserveAspectRatio="none" focusable="false">
      <defs><linearGradient id={`${id}-stone`} x2="0" y2="1"><stop stopColor="#fffdf6"/><stop offset="1" stopColor="#dce4ed"/></linearGradient><linearGradient id={`${id}-water`} x2="0" y2="1"><stop stopColor="#d7f6fc"/><stop offset="1" stopColor="#8dcedd"/></linearGradient></defs>
      <path fill="#f0f7fa" d="M85 350V212C85-30 515-30 515 212V350Z"/>
      <path fill="none" stroke="#d9e8ef" strokeWidth="3" d="M113 329V214C113 18 487 18 487 214V329M135 324V215C135 48 465 48 465 215V324"/>
      <path fill={`url(#${id}-stone)`} stroke="#c7d6df" d="M201 288H399L419 317H181ZM235 243H365L383 287H217ZM225 319H375V367H225Z"/>
      <ellipse fill="#b8dce6" cx="300" cy="243" rx="87" ry="14"/><path fill="none" stroke="#fff" strokeWidth="5" d="M212 244Q300 272 388 244"/>
      <g className="fountain-streams" fill="none" strokeLinecap="round"><path d="M235 261Q207 311 194 379M365 261Q394 311 406 379" stroke="#a0d8e7" strokeWidth="11"/><path d="M235 261Q207 311 194 379M365 261Q394 311 406 379" stroke="#e7fbff" strokeWidth="3"/></g>
      <path fill="#cbdbe5" d="M51 444Q300 328 549 444V480Q300 630 51 480Z"/>
      <ellipse fill={`url(#${id}-stone)`} stroke="#bdcedb" strokeWidth="2" cx="300" cy="442" rx="260" ry="113"/>
      <ellipse fill={`url(#${id}-water)`} cx="300" cy="432" rx="235" ry="94"/>
      <path fill="none" stroke="#f4feff" strokeWidth="3" d="M100 427Q300 523 500 427M132 400Q300 346 468 400M157 470Q300 514 443 470"/>
      <path fill="#d8e4ec" d="M78 506Q300 618 522 506L548 543Q300 657 52 543Z"/>
      <g fill="#a4cfbd"><path d="M58 397Q14 357 32 328Q65 344 58 397ZM542 397Q586 357 568 328Q535 344 542 397Z"/><path d="M65 406Q29 387 13 402Q35 429 65 406ZM535 406Q571 387 587 402Q565 429 535 406Z"/></g>
    </svg>
    <img className="fountain-letter" src="/images/letters/make-a-wish.webp" alt="" draggable={false}/>
  </div>;
}
