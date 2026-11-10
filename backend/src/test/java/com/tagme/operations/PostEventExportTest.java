package com.tagme.operations;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PostEventExportTest {
    @Test void csvNeutralizesFormulaAndPreservesQuotedCommasNewlines(){for(String text:new String[]{"=SUM(1)"," \t+CMD\nsecond","-1+1","@SUM(1)","\r=1","\n=1"})assertThat(PostEventExport.cell(text)).startsWith("\"'");assertThat(PostEventExport.cell("a,\"b\"\nc")).isEqualTo("\"a,\"\"b\"\"\nc\"");assertThat(PostEventExport.cell(null)).isEqualTo("\"\"");}
}
