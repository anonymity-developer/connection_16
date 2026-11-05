/** Old API responses remain readable without inventing an interaction score. */
export function relationshipPresentation(weight?: number) {
  if (weight === undefined || !Number.isFinite(weight) || weight < 1) return { strength: 'light', width: 1.5, label: '프로필 방문 연결' };
  const bounded = Math.min(weight, 6);
  return bounded >= 5 ? { strength: 'strong', width: 4, label: '교류 활발' } : bounded >= 3 ? { strength: 'medium', width: 2.5, label: '교류 이어짐' } : { strength: 'light', width: 1.5, label: '첫 연결' };
}
