/*** In The Name of Allah ***/
package progex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import progex.graphs.cfg.CFGBuilder;
import progex.graphs.cfg.ControlFlowGraph;
import progex.graphs.cfg.ICFGBuilder;
import progex.graphs.pdg.PDGBuilder;
import progex.graphs.pdg.ProgramDependeceGraph;
import progex.utils.FileUtils;
import progex.utils.Logger;
import progex.utils.SystemUtils;

/**
 * A class which holds program execution options.
 * These options determine the execution behavior of the program.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class Execution {
	
	private final ArrayList<Analysis> analysisTypes;
	private final ArrayList<String> inputPaths;
	private String outputDir;
	private Languages lang;
	private Formats format;
	
	public Execution() {
		analysisTypes = new ArrayList<>();
		inputPaths = new ArrayList<>();
		lang = Languages.JAVA;
		format = Formats.DOT;
		outputDir = System.getProperty("user.dir");
		if (!outputDir.endsWith(File.separator))
			outputDir += File.separator;
	}
	
	/**
	 * Enumeration of different execution options.
	 */
	public enum Analysis {
		// analysis types
		CFG			("CFG"),
		PDG			("PDG"),
		AST			("AST"),
		ICFG		("ICFG"),
		SRC_INFO 	("INFO");
		
		private Analysis(String str) {
			type = str;
		}
		@Override
		public String toString() {
			return type;
		}
		public final String type;
	}
	
	/**
	 * Enumeration of different supported languages.
	 */
	public enum Languages {
		C		("C", ".c"),
		JAVA	("Java", ".java"),
		PYTHON	("Python", ".py");
		
		private Languages(String str, String suffix) {
			name = str;
			this.suffix = suffix;
		}
		@Override
		public String toString() {
			return name;
		}
		public final String name;
		public final String suffix;
	}
	
	/**
	 * Enumeration of different supported output formats.
	 */
	public enum Formats {
		DOT		("DOT"),
		JSON	("JSON");
		
		private Formats(String str) {
			name = str;
		}
		@Override
		public String toString() {
			return name;
		}
		public final String name;
	}
	
	
	/*=======================================================*/
	
	
	public void addAnalysisOption(Analysis opt) {
		analysisTypes.add(opt);
	}
	
	public void addInputPath(String path) {
		inputPaths.add(path);
	}
	
	public void setLanguage(Languages lang) {
		this.lang = lang;
	}
	
	public void setOutputFormat(Formats fmt) {
		format = fmt;
	}
	
	public boolean setOutputDirectory(String outPath) {
		boolean result = false;
		File newDir = new File(outPath);
		if (newDir.exists()) {
			if (newDir.canWrite() && newDir.isDirectory()) {
				outputDir = outPath;
				result = true;
			} else {
				newDir = new File(outPath + File.separator);
				if (newDir.canWrite() && newDir.isDirectory()) {
					outputDir = outPath + File.separator;
					result = true;
				}
			}
		}
		if (!outputDir.endsWith(File.separator))
			outputDir += File.separator;
		return result;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("PROGEX execution config:");
		str.append("\n  Language = ").append(lang);
		str.append("\n  Output format = ").append(format);
		str.append("\n  Output directory = ").append(outputDir);
		str.append("\n  Analysis types = ").append(Arrays.toString(analysisTypes.toArray()));
		str.append("\n  Input paths = \n");
		for (String path: inputPaths)
			str.append("        ").append(path).append('\n');
		return str.toString();
	}
	
	/**
	 * Execute the PROGEX program with the given options.
	 */
	public void execute() {
		Logger.setEchoToStdOut(true);
        
		if (inputPaths.isEmpty()) {
			Logger.log("No input path provided!\nAbort.");
			System.exit(0);
		}
		if (analysisTypes.isEmpty()) {
			Logger.log("No analysis type provided!\nAbort.");
			System.exit(0);
		}
		
		Logger.log(toString());
		
		// 1. Extract source files from input-paths, based on selected language
		String[] paths = inputPaths.toArray(new String[inputPaths.size()]);
		String[] filePaths = new String[0];
		if (paths.length > 0)
			filePaths = FileUtils.listFilesWithSuffix(paths, lang.suffix);
		Logger.log("\n# " + lang.name + " source files = " + filePaths.length + "\n");
		
		// Check language
		if (!lang.equals(Languages.JAVA)) {
			Logger.log("Analysis of " + lang.name + " programs is not yet implemented!");
			Logger.log("Abort.");
			System.exit(0);
		}

		// 2. For each analysis type, do the analysis and output results
		for (Analysis analysis: analysisTypes) {
			
			Logger.log("\nMemory Status");
			Logger.log("=============");
			Logger.log(SystemUtils.getMemoryStats());

			switch (analysis.type) {
				//
				case "CFG":
					Logger.log("\nControl-Flow Analysis");
					Logger.log("=====================");
					Logger.log("START: " + Logger.time() + '\n');
					for (String srcFile : filePaths) {
						try {
							ControlFlowGraph cfg = CFGBuilder.build(lang.name, srcFile);
							cfg.export(format.name, outputDir);
						} catch (IOException ex) {
							Logger.log(ex, Logger.Level.RAW);
						}
					}
					break;
				//
				case "ICFG":
					Logger.log("\nInterprocedural Control-Flow Analysis");
					Logger.log("=====================================");
					Logger.log("START: " + Logger.time() + '\n');
					try {
						ControlFlowGraph icfg = ICFGBuilder.buildForAll(lang.name, filePaths);
						icfg.export(format.name, outputDir);
					} catch (IOException ex) {
						Logger.log(ex, Logger.Level.RAW);
					}
					break;
				//
				case "PDG":
					Logger.log("\nProgram-Dependence Analysis");
					Logger.log("===========================");
					Logger.log("START: " + Logger.time() + '\n');
					try {
						for (ProgramDependeceGraph pdg: PDGBuilder.buildForAll(lang.name, filePaths)) {
							pdg.CDS.export(format.name, outputDir);
							pdg.DDS.export(format.name, outputDir);
							pdg.DDS.getCFG().export(format.name, outputDir);;
							pdg.DDS.printAllNodesUseDefs(Logger.getStream());
						}
					} catch (IOException ex) {
						Logger.log(ex, Logger.Level.RAW);
					}
					break;
				//
				case "INFO":
					Logger.log("\nCode Information Analysis");
					Logger.log("=========================");
					Logger.log("START: " + Logger.time() + '\n');
					for (String srcFile : filePaths)
						CodeInfoAnalyzer.analyzeInfo(lang.name, srcFile);
					break;
				//
				default:
					Logger.log("\n\'" + analysis.type + "\' analysis is not yet implemented!\n");
			}
			Logger.log("\nFINISH: " + Logger.time());
		}
		//
		Logger.log("\nMemory Status");
		Logger.log("=============");
		Logger.log(SystemUtils.getMemoryStats());
	}
}
