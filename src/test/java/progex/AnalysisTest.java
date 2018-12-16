/*** In The Name of Allah ***/
package progex;

import static org.junit.Assert.*;
import org.junit.*;
import progex.utils.FileUtils;

/**
 *
 * @author Seyed Mohammad Ghaffarian
 */
public class AnalysisTest {
    
    @Test
    public void javaCFGTest() {
        String srcDir = "src/test/resources/progex";
        String[] args = {"-cfg", "-outdir", "out", srcDir};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {srcDir}, Execution.Languages.JAVA.suffix);
        String[] outFiles = FileUtils.listFilesWithSuffix(new String[]{"out"}, "-CFG.dot");
        assertEquals(testFiles.length, outFiles.length);
    }
    
//    @Test
//    public void javaPDGTest() {
//        
//    }
//    
//    @Test
//    public void javaASTest() {
//        
//    }

}
