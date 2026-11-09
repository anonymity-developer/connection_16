/** Small static decoration only. Disable via --winter-display: none or html[data-winter='off']. */
export function WinterDecor() {
  return <svg className="winter-decor" viewBox="0 0 120 70" aria-hidden="true" focusable="false">
    <g fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M24 8v30M11 15l26 16M11 31l26-16M18 12l6 4 6-4M18 34l6-4 6 4" />
      <path d="M91 28v26M80 34l22 14M80 48l22-14" />
    </g><path className="winter-star" d="m61 9 3 8 8 3-8 3-3 8-3-8-8-3 8-3z" />
    <circle cx="49" cy="49" r="2" fill="currentColor" /><circle cx="110" cy="16" r="2" fill="currentColor" />
  </svg>;
}
