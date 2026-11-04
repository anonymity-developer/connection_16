package com.tagme.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Strict, bounded scoring definition. Parsed before publication; never contains participant data. */
public record Questionnaire(String title,List<Axis> axes,List<Question> questions,List<Type> types) {
    public record Axis(String id,String negative,String positive,String tie) { }
    public record Option(String id,String label,Map<String,Integer> scores) { }
    public record Question(String id,String text,List<Option> options) { }
    public record Type(String code,String title,String description) { }
    public record Answer(String questionId,String optionId) { }
    public record Score(Map<String,Integer> axes,Type type,List<Answer> canonicalAnswers) { }
    public static Questionnaire parse(String json,ObjectMapper mapper) {
        try {
            var value=mapper.readValue(json,Questionnaire.class);value.validate();return value;
        } catch (Exception invalid) { throw new IllegalArgumentException("Invalid questionnaire definition",invalid); }
    }
    private static void require(boolean condition) { if(!condition)throw new IllegalArgumentException("Invalid questionnaire definition"); }
    private static boolean text(String value,int max) { return value!=null&&!value.isBlank()&&value.length()<=max; }
    private static boolean id(String value) { return value!=null&&value.matches("[A-Za-z0-9_-]{1,32}"); }
    public void validate() {
        require(text(title,100)&&axes!=null&&axes.size()>=1&&axes.size()<=4&&questions!=null&&questions.size()>=1&&questions.size()<=20&&types!=null);
        Set<String> axisIds=new HashSet<>();Set<String> combinations=new HashSet<>();combinations.add("");
        for(var axis:axes){
            require(axis!=null&&id(axis.id())&&axisIds.add(axis.id())&&axis.negative()!=null&&axis.negative().matches("[A-Z]")&&axis.positive()!=null&&axis.positive().matches("[A-Z]")&&!axis.negative().equals(axis.positive())&&Set.of("NEGATIVE","POSITIVE").contains(axis.tie()));
            Set<String> next=new HashSet<>();for(String code:combinations){next.add(code+axis.negative());next.add(code+axis.positive());}combinations=next;
        }
        Set<String> questionIds=new HashSet<>();
        for(var question:questions){
            require(question!=null&&id(question.id())&&questionIds.add(question.id())&&text(question.text(),200)&&question.options()!=null&&question.options().size()>=2&&question.options().size()<=6);
            Set<String> optionIds=new HashSet<>();
            for(var option:question.options())require(option!=null&&id(option.id())&&optionIds.add(option.id())&&text(option.label(),150)&&option.scores()!=null&&option.scores().keySet().equals(axisIds)&&option.scores().values().stream().allMatch(n -> n!=null&&n>=-5&&n<=5));
        }
        Set<String> typeCodes=new HashSet<>();
        for(var type:types)require(type!=null&&type.code()!=null&&combinations.contains(type.code())&&typeCodes.add(type.code())&&text(type.title(),60)&&text(type.description(),500));
        require(typeCodes.equals(combinations));
    }
    public Score score(List<Answer> answers) {
        require(answers!=null&&answers.size()==questions.size());Map<String,String> selected=new HashMap<>();
        for(var answer:answers)require(answer!=null&&answer.questionId()!=null&&answer.optionId()!=null&&selected.put(answer.questionId(),answer.optionId())==null);
        Map<String,Integer> totals=new LinkedHashMap<>();for(var axis:axes)totals.put(axis.id(),0);List<Answer> canonical=new ArrayList<>();
        for(var question:questions){
            var option=question.options().stream().filter(o -> o.id().equals(selected.get(question.id()))).findFirst().orElseThrow(IllegalArgumentException::new);
            canonical.add(new Answer(question.id(),option.id()));option.scores().forEach((key,value) -> totals.compute(key,(k,total) -> total+value));
        }
        StringBuilder code=new StringBuilder();for(var axis:axes){int score=totals.get(axis.id());code.append(score>0||score==0&&axis.tie().equals("POSITIVE")?axis.positive():axis.negative());}
        return new Score(totals,types.stream().filter(t -> t.code().contentEquals(code)).findFirst().orElseThrow(),canonical);
    }
}
