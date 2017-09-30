/*** In The Name of Allah ***/
package progex;

import java.io.IOException;
import progex.graphs.pdg.ProgramDependeceGraph;
import progex.java.JavaPDGBuilder;
import progex.utils.FileUtils;
import progex.utils.Logger;

/**
 * Main class for testing the Java PDG-Builder.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class TestPDG {
	
	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		System.out.println("Hello! This is PROGEX!\n");
		try {
			Logger.init("PROGEX.log");
			//Logger.redirectStandardError("PROGEX.err");
		} catch (IOException ex) {
			System.err.println("Logger init failed : " + ex);
		}
		//
		String[] javaFiles;
		if (args.length > 0) 
			// /home/mohammad/Downloads/Juliet-Java/src-2
			javaFiles = FileUtils.listSourceCodeFiles(args, ".java");
		else 
			javaFiles = new String[] { "Test.java" };
		//
		try {
			System.out.println("Analyzing " + javaFiles.length + " Java files ...\n");
			for (ProgramDependeceGraph pdg: JavaPDGBuilder.buildForAll(javaFiles)) {
				pdg.CDS.exportDOT("output/");
				pdg.DDS.exportDOT("output/", false);
				pdg.DDS.getCFG().exportDOT("output/");
				pdg.DDS.printAllNodesUseDefs(Logger.getStream());
			}
		} catch (IOException ex) {
			System.err.println(ex);
		}
	}
	
}
