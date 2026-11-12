package com.tagme.discovery;
import java.sql.DriverManager;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
@Tag("integration") @Testcontainers
class RecommendationPolicySqlIntegrationTest {
 @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
 @Test void sqlMatchesReferenceIncludingAllCategoriesAndExposureCaps()throws Exception{
  var random=new Random(20261112);var columns=new java.util.ArrayList<String>();
  for(String category:java.util.List.of("industry","development","stack"))for(String field:java.util.List.of("common","viewer","peer"))columns.add("? AS "+category+"_"+field);
  for(String flag:java.util.List.of("same_type","comment_interaction","like_interaction","visited","different_class","exposures"))columns.add("? AS "+flag);
  try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var statement=connection.prepareStatement("SELECT "+RecommendationPolicy.scoreExpression()+" AS score FROM (SELECT "+String.join(",",columns)+") features")){
   for(int sample=0;sample<256;sample++){
    var similarities=new java.util.ArrayList<RecommendationPolicy.Similarity>();int parameter=1;
    for(int category=0;category<3;category++){int common=sample==0?0:random.nextInt(10),viewer=sample==0?0:common+random.nextInt(12),peer=sample==0?0:common+random.nextInt(12);similarities.add(new RecommendationPolicy.Similarity(common,viewer,peer));statement.setInt(parameter++,common);statement.setInt(parameter++,viewer);statement.setInt(parameter++,peer);}
    for(int flag=0;flag<5;flag++)statement.setBoolean(parameter++,(sample&(1<<flag))!=0);
    int exposures=sample%8;statement.setInt(parameter,exposures);
    var signals=new RecommendationPolicy.Signals(similarities.get(0),similarities.get(1),similarities.get(2),(sample&1)!=0,(sample&2)!=0,(sample&4)!=0,(sample&8)!=0,(sample&16)!=0,exposures);
    try(var result=statement.executeQuery()){assertThat(result.next()).isTrue();assertThat(result.getInt("score")).as("sample %s",sample).isEqualTo(RecommendationPolicy.score(signals)).isBetween(0,10000);}
   }
  }
 }
}
