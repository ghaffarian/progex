/*** In The Name of Allah ***/
package progex;

import java.io.IOException;
import progex.graphs.cfg.ControlFlowGraph;
import progex.java.JavaCFGBuilder;
import progex.utils.FileUtils;
import progex.utils.StringUtils;

/**
 * The Main class for testing various classes of PROGEX.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class TestCFG {
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		System.out.println("Hello! This is PROGEX!\n");
		String[] files;
		if (args.length > 0) {
			String suffix = ".java";
			files = FileUtils.listSourceCodeFiles(args, suffix);
		} else {
			files = new String[] { "Test.java" };
		}
		//
		try {
			for (String javaFile: files) {
				ControlFlowGraph cfg = JavaCFGBuilder.build(javaFile);
				cfg.exportDOT("output/");
			}
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}
	
}
