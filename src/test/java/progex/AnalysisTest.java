/*** In The Name of Allah ***/
package progex;

import java.io.File;
import progex.utils.FileUtils;

import static org.junit.Assert.*;
import org.junit.*;

/**
 *
 * @author Seyed Mohammad Ghaffarian
 */
public class AnalysisTest {
    
    private final String SRC_DIR = "src/test/resources/progex";
    
    @BeforeClass
    public static void cleanUp() {
        File out = new File("out/");
        for (File file: out.listFiles()) {
            if (file.isFile())
                file.delete();
            else
                deleteDir(file);
        }
    }
    
    private static void deleteDir(File dir) {
        if (dir.list().length > 0) {
            for (File file: dir.listFiles()) {
                if (file.isFile())
                    file.delete();
                else
                    deleteDir(file);
            }
        }
        dir.delete();
    }
    
    @Test
    public void javaCFGTest() {
        String[] args = {"-cfg", "-outdir", "out/cfg/", SRC_DIR};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {SRC_DIR}, Execution.Languages.JAVA.suffix);
        String[] outFiles = FileUtils.listFilesWithSuffix(new String[]{"out/cfg/"}, "-CFG.dot");
        assertEquals(testFiles.length, outFiles.length);
    }
    
    @Test
    public void javaPDGTest() {
        String[] args = {"-pdg", "-outdir", "out/pdg/", SRC_DIR};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {SRC_DIR}, Execution.Languages.JAVA.suffix);
        String[] outDataFiles = FileUtils.listFilesWithSuffix(new String[]{"out/pdg/"}, "-PDG-DATA.dot");
        String[] outCtrlFiles = FileUtils.listFilesWithSuffix(new String[]{"out/pdg/"}, "-PDG-CTRL.dot");
        assertEquals(testFiles.length, outDataFiles.length);
        assertEquals(testFiles.length, outCtrlFiles.length);
    }
    
}
