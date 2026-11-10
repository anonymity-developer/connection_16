// Structural preflight parity with Questionnaire.validate; no publication or score/type assignment.
export function validateQuestionnaire(q) {
  try {
    const require = value => { if (!value) throw new Error('invalid'); };
    const text = (v, max) => typeof v === 'string' && v.trim().length > 0 && v.length <= max;
    const id = v => typeof v === 'string' && /^[A-Za-z0-9_-]{1,32}$/.test(v);
    require(q && text(q.title,100) && Array.isArray(q.axes) && q.axes.length>=1 && q.axes.length<=4 && Array.isArray(q.questions) && q.questions.length>=1 && q.questions.length<=20 && Array.isArray(q.types));
    const axes=new Set();let combinations=new Set(['']);
    for(const a of q.axes){require(a && id(a.id) && !axes.has(a.id) && /^[A-Z]$/.test(a.negative) && /^[A-Z]$/.test(a.positive) && a.negative!==a.positive && ['NEGATIVE','POSITIVE'].includes(a.tie));axes.add(a.id);combinations=new Set([...combinations].flatMap(c=>[c+a.negative,c+a.positive]));}
    const questions=new Set();
    for(const question of q.questions){require(question && id(question.id) && !questions.has(question.id) && text(question.text,200) && Array.isArray(question.options) && question.options.length>=2 && question.options.length<=6);questions.add(question.id);const options=new Set();for(const o of question.options){require(o && id(o.id) && !options.has(o.id) && text(o.label,150) && o.scores && !Array.isArray(o.scores) && Object.keys(o.scores).length===axes.size && Object.keys(o.scores).every(k=>axes.has(k)) && Object.values(o.scores).every(v=>Number.isInteger(v)&&v>=-5&&v<=5));options.add(o.id);}}
    const types=new Set();for(const t of q.types){require(t && combinations.has(t.code) && !types.has(t.code) && text(t.title,60) && text(t.description,500));types.add(t.code);}require(types.size===combinations.size);
    return {valid:true,questionCount:q.questions.length,axisCount:q.axes.length,typeCount:q.types.length};
  } catch { return {valid:false,questionCount:0,axisCount:0,typeCount:0}; }
}
