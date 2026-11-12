import type { Interest } from './api';

export const interestCategories = [
  { code: 'INDUSTRY', title: '관심 산업', key: 'industryIds', max: 3 },
  { code: 'DEVELOPMENT', title: '관심 개발 분야', key: 'developmentIds', max: 3 },
] as const;

export function interestsComplete(ids: number[], catalog: Interest[]) {
  return interestCategories.every(category => {
    const count = catalog.filter(item => item.category === category.code && ids.includes(item.id)).length;
    return count >= 1 && count <= category.max;
  });
}
