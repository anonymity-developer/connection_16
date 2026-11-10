/** Decorative event tags, never a mission status or an official SSAFY mark. */
export function ConnectionStickers({ className = '' }: { className?: string }) {
  return <svg className={`connection-stickers ${className}`} viewBox="0 0 240 160" aria-hidden="true" focusable="false">
    <path className="sticker-thread" d="M47 70C20 9 116 9 109 58S196 149 210 76" />
    <g transform="rotate(-10 79 85)"><path className="sticker-back" d="M37 34h79v99H37z" /><path className="sticker-tag sticker-tag--blue" d="M32 37h84v85l-17 17H32z" /><circle className="sticker-hole" cx="48" cy="53" r="5" /><text x="74" y="94" textAnchor="middle">TAG</text><path className="sticker-smile" d="M62 108q12 13 24 0" /></g>
    <g transform="rotate(10 163 85)"><path className="sticker-tag sticker-tag--mint" d="M123 45h80v85l-15 15h-65z" /><circle className="sticker-hole" cx="138" cy="60" r="5" /><text x="163" y="105" textAnchor="middle">ME</text><path className="sticker-heart" d="M156 119c-10-10-17 2 7 14 24-12 17-24 7-14l-7 5z" /></g>
    <path className="sticker-spark" d="m215 20 3 9 9 3-9 3-3 9-3-9-9-3 9-3zM22 117l3 7 7 3-7 3-3 7-3-7-7-3 7-3z" />
    <circle className="sticker-dot" cx="130" cy="20" r="4" /><circle className="sticker-dot" cx="224" cy="126" r="4" />
  </svg>;
}
