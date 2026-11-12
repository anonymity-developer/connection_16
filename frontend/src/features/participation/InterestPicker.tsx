import type { Interest } from './api';
import './interests.css';

import { interestCategories } from './catalog';

export function InterestPicker({ catalog, selected, onChange, disabled = false, filter = false }: {
  catalog: Interest[]; selected: number[]; onChange: (ids: number[]) => void; disabled?: boolean; filter?: boolean;
}) {
  return <div className={`interest-picker${filter ? ' interest-picker--filter' : ''}`}>{interestCategories.map(category => {
    const items = catalog.filter(item => item.category === category.code);
    const count = items.filter(item => selected.includes(item.id)).length;
    const groups = [...new Set(items.map(item => item.groupName || ''))];
    const fields = <fieldset className="interest-options" disabled={disabled}>
      <legend>{category.title} <span>{filter ? `${count}개 선택` : `${count}/${category.max}개 · 최소 1개`}</span></legend>
      {groups.map(group => <div className="interest-subgroup" key={group}>{group && <p>{group}</p>}<div className="interest-choice-grid">{items.filter(item => (item.groupName || '') === group).map(item =>
        <label key={item.id}><input type="checkbox" name={category.key} checked={selected.includes(item.id)}
          disabled={disabled || !filter && !selected.includes(item.id) && count >= category.max}
          onChange={event => onChange(event.target.checked ? [...selected, item.id] : selected.filter(id => id !== item.id))} /><span>{item.name}</span></label>
      )}</div></div>)}
    </fieldset>;
    return filter ? <details className="interest-filter" key={category.code}><summary>{category.title}{count > 0 ? ` · ${count}개` : ''}</summary>{fields}</details> : <div key={category.code}>{fields}</div>;
  })}</div>;
}
