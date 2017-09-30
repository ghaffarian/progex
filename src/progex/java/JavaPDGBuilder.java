/*** In The Name of Allah ***/
package progex.java;

import java.io.File;
import java.io.IOException;
import progex.graphs.pdg.ControlDependenceGraph;
import progex.graphs.pdg.DataDependenceGraph;
import progex.graphs.pdg.ProgramDependeceGraph;
import progex.utils.Logger;
import progex.utils.SystemUtils;

/**
 * Program Dependence Graph (PDG) builder for Java programs.
 * A Java parser generated via ANTLRv4 is used for this purpose.
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaPDGBuilder {
	
	/**
	 * Builds and returns Program Dependence Graphs (PDG) for each given Java file.
	 */
	public static ProgramDependeceGraph[] buildForAll(String[] javaFilePaths) throws IOException {
		File[] javaFiles = new File[javaFilePaths.length];
		for (int i = 0; i < javaFiles.length; ++i)
			javaFiles[i] = new File(javaFilePaths[i]);
		return buildForAll(javaFiles);
	}
	
	/**
	 * Builds and returns Program Dependence Graphs (PDG) for each given Java file.
	 */
	public static ProgramDependeceGraph[] buildForAll(File[] javaFiles) throws IOException {
		
		// Build the control-dependence subgraphs
		//System.out.println("Time & Memory Stats of Control-Dependence Analysis");
		//System.out.println("==================================================");
		//System.out.println("START: " + Logger.time());
		//SystemUtils.printMemoryStats(System.out);
		ControlDependenceGraph[] ctrlSubgraphs;
		ctrlSubgraphs = new ControlDependenceGraph[javaFiles.length];
		for (int i = 0; i < javaFiles.length; ++i)
			ctrlSubgraphs[i] = JavaCDGBuilder.build(javaFiles[i]);
		//System.out.println("FINISH: " + Logger.time());
		//SystemUtils.printMemoryStats(System.out);

		// Build the data-dependence subgraphs
		//System.out.println("Time & Memory Stats of Data-Dependence Analysis");
		//System.out.println("===============================================");
		//System.out.println("START: " + Logger.time());
		//SystemUtils.printMemoryStats(System.out);
		DataDependenceGraph[] dataSubgraphs;
		dataSubgraphs = JavaDDGBuilder.buildForAll(javaFiles);
		//System.out.println("FINISH: " + Logger.time());
		//SystemUtils.printMemoryStats(System.out);

		// Join the subgraphs into PDGs
		ProgramDependeceGraph[] pdgArray = new ProgramDependeceGraph[javaFiles.length];
		for (int i = 0; i < javaFiles.length; ++i) {
			pdgArray[i] = new ProgramDependeceGraph(javaFiles[i].getName(), 
					ctrlSubgraphs[i], dataSubgraphs[i]);
		}
		
		return pdgArray;
	}

}

