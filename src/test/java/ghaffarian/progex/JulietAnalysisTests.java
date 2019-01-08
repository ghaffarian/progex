/*** In The Name of Allah ***/
package ghaffarian.progex;

import java.io.File;
import ghaffarian.progex.utils.FileUtils;

import static org.junit.Assert.*;
import org.junit.*;

/**
 * Tests for different types of analyses using NIST's Juliet dataset.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JulietAnalysisTests {

    private final String JAVA_SRC_DIR = "src/test/resources/java/juliet-v1.3/";
    private static final String OUTPUT_DIR = "out/java/juliet/";
    
    @BeforeClass
    public static void cleanUp() {
        File out = new File(OUTPUT_DIR);
        if (out.exists()) {
            for (File file : out.listFiles()) {
                if (file.isFile())
                    file.delete();
                else
                    deleteDir(file);
            }
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
    public void javaASTreeTest() {
        String outDir = OUTPUT_DIR + "AST/";
        String[] args = {"-debug", "-ast", "-outdir", outDir, JAVA_SRC_DIR};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {JAVA_SRC_DIR}, Execution.Languages.JAVA.suffix);
        String[] outFiles = FileUtils.listFilesWithSuffix(new String[] {outDir}, "-AST.dot");
        assertEquals(testFiles.length, outFiles.length);
    }
    
    @Test
    public void javaCFGTest() {
        String outDir = OUTPUT_DIR + "CFG/";
        String[] args = {"-cfg", "-outdir", outDir, JAVA_SRC_DIR};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {JAVA_SRC_DIR}, Execution.Languages.JAVA.suffix);
        String[] outFiles = FileUtils.listFilesWithSuffix(new String[] {outDir}, "-CFG.dot");
        assertEquals(testFiles.length, outFiles.length);
    }
    
    @Test
    public void javaPDGTest() {
        String outDir = OUTPUT_DIR + "PDG/";
        String[] args = {"-pdg", "-outdir", outDir, JAVA_SRC_DIR};
        Main.main(args);
        //
        String[] testFiles = FileUtils.listFilesWithSuffix(new String[] {JAVA_SRC_DIR}, Execution.Languages.JAVA.suffix);
        String[] outDataFiles = FileUtils.listFilesWithSuffix(new String[] {outDir}, "-PDG-DATA.dot");
        String[] outCtrlFiles = FileUtils.listFilesWithSuffix(new String[] {outDir}, "-PDG-CTRL.dot");
        assertEquals(testFiles.length, outDataFiles.length);
        assertEquals(testFiles.length, outCtrlFiles.length);
    }
    
}
