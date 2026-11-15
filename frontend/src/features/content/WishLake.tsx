export function WishLakeArt() {
  return <svg className="wish-lake-art" viewBox="0 0 720 520" role="img" aria-label="종이배가 떠다니는 잔잔한 소원 호수" preserveAspectRatio="xMidYMid slice">
    <path className="wish-lake-sky" d="M0 0h720v520H0z" />
    <path className="wish-lake-hill wish-lake-hill--back" d="M0 188c86-70 155-76 235-23 76-83 168-90 264-9 73-50 147-52 221-11v150H0Z" />
    <path className="wish-lake-hill" d="M0 244c92-42 174-36 251 17 107-81 230-78 333 2 49-31 94-42 136-31v105H0Z" />
    <ellipse className="wish-lake-water" cx="360" cy="354" rx="348" ry="174" />
    <g className="wish-lake-ripples"><path d="M62 326c92-42 192-43 297-6 104 37 203 36 299-5" /><path d="M28 382c115-45 229-43 342 5 108 45 217 44 326-3" /><path d="M92 438c81-25 168-21 262 11 91 31 182 29 273-7" /></g>
    <g className="wish-lake-shore-plants" aria-hidden="true"><path d="M39 364c3-61 14-109 34-143M49 302c-28-15-40-39-34-71 31 13 44 36 34 71Zm15-35c3-35 19-57 49-67 4 33-12 56-49 67Z" /><path d="M681 365c-2-62-15-110-36-145M669 302c28-16 39-40 32-72-31 14-43 38-32 72Zm-15-36c-4-35-20-57-50-66-3 33 14 56 50 66Z" /></g>
    <g className="wish-lake-sparkles" aria-hidden="true"><path d="m116 118 5 14 14 5-14 5-5 14-5-14-14-5 14-5Z" /><path d="m614 91 4 11 11 4-11 4-4 11-4-11-11-4 11-4Z" /><circle cx="168" cy="84" r="4" /><circle cx="581" cy="153" r="3" /></g>
  </svg>;
}

/** Static artwork for an explicitly unlocked development preview; live records use ContentMission. */
export function WishLake() {
  return <figure className="wish-lake-intro"><div className="wish-lake-scene"><WishLakeArt /><p className="wish-lake-invitation">우리의 바람이 머무는 소원 호수</p></div><figcaption>종이배를 눌러 동료의 소원을 만나보세요.</figcaption></figure>;
}
