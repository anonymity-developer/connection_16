import com.tagme.operations.*;
import java.time.Instant;
import java.util.*;
/** Dependency-free contract against the actual export implementation, no DB/server. */
class ExportSmoke {
    public static void main(String[] args){
        for(String input:List.of("=SUM(1)"," \t+CMD\nnext","-1+1","@SUM(1)","\r=1","\n=1"))if(!PostEventExport.cell(input).startsWith("\"'"))throw new AssertionError("CSV formula guard failed");
        if(!PostEventExport.cell("a,\"b\"\nc").equals("\"a,\"\"b\"\"\nc\""))throw new AssertionError("CSV quoting failed");
        var d=new PostEventModels.Data(new AwardModels.Window(Instant.parse("2026-01-01T00:00:00Z"),null,null,null),"NOT_CONFIGURED","SYNTHETIC",List.of(new PostEventModels.Metric("registered",0,"명","fixture")),List.of(),List.of(),List.of(),false,List.of(),List.of(),0,List.of("미확정"),List.of("집계 불가"),false);
        var r=new PostEventModels.Report("fixture","0".repeat(64),d);String csv=PostEventExport.csv(r),md=PostEventExport.markdown(r);
        if(!csv.startsWith("\uFEFF")||!csv.contains("SUMMARY")||!md.contains("확정 불가")||!md.contains("집계할 참여 기록 없음"))throw new AssertionError("Empty export failed");
        System.out.println("PASS: actual Java CSV formula/quoting/BOM and empty Markdown export; no Spring or DB executed");
    }
}
