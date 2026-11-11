import { FountainArtwork } from './FountainArtwork';

export function WishLakeArt() { return <FountainArtwork />; }

/** Static artwork for an explicitly unlocked development preview; live records use ContentMission. */
export function WishLake() {
  return <figure className="wish-lake-intro"><div className="wish-lake-scene"><WishLakeArt /><p className="wish-lake-invitation">우리의 바람이 모이는 분수</p></div><figcaption>분수에 모인 소원은 익명으로 보여요.</figcaption></figure>;
}
