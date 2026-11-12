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

    @Test void databaseOrderingScoreMatchesTheReferenceEvaluator() throws Exception {
        // Reproducible mixed counts, all flag combinations, empty unions and fractional scores.
        var random=new Random(20261112);
        try(var connection=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
            var statement=connection.prepareStatement("SELECT "+RecommendationPolicy.scoreExpression()+" AS score FROM "
                +"(SELECT ? AS common_count,? AS viewer_count,? AS peer_count,? AS same_type,"
                +"? AS comment_interaction,? AS like_interaction,? AS visited,? AS different_class) features")) {
            for(int sample=0;sample<256;sample++) {
                int common=sample==0?0:random.nextInt(12), viewer=common+random.nextInt(15), peer=common+random.nextInt(15);
                if(sample==0)viewer=peer=0;
                var signals=new RecommendationPolicy.Signals(common,viewer,peer,(sample&1)!=0,(sample&2)!=0,
                    (sample&4)!=0,(sample&8)!=0,(sample&16)!=0);
                statement.setInt(1,common);statement.setInt(2,viewer);statement.setInt(3,peer);
                statement.setBoolean(4,signals.sameType());statement.setBoolean(5,signals.comment());
                statement.setBoolean(6,signals.like());statement.setBoolean(7,signals.visited());statement.setBoolean(8,signals.differentClass());
                try(var result=statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt("score")).as("sample %s: %s",sample,signals)
                        .isEqualTo(RecommendationPolicy.score(signals)).isBetween(0,10000);
                }
            }
        }
    }
}
