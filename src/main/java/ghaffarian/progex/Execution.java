/*** In The Name of Allah ***/
package ghaffarian.progex;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import ghaffarian.progex.graphs.ast.ASTBuilder;
import ghaffarian.progex.graphs.ast.AbstractSyntaxTree;
import ghaffarian.progex.graphs.cfg.CFGBuilder;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.graphs.cfg.ICFGBuilder;
import ghaffarian.progex.graphs.pdg.PDGBuilder;
import ghaffarian.progex.graphs.pdg.ProgramDependeceGraph;
import ghaffarian.progex.utils.FileUtils;
import ghaffarian.progex.utils.SystemUtils;
import ghaffarian.nanologger.Logger;

/**
 * A class which holds program execution options.
 * These options determine the execution behavior of the program.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class Execution {
	
	private final ArrayList<Analysis> analysisTypes;
	private final ArrayList<String> inputPaths;
    private boolean debugMode;
	private String outputDir;
	private Languages lang;
	private Formats format;
	
	public Execution() {
        debugMode = false;
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
    
    public void setDebugMode(boolean isDebug) {
        debugMode = isDebug;
    }
	
	public void setOutputFormat(Formats fmt) {
		format = fmt;
	}
	
	public boolean setOutputDirectory(String outPath) {
        if (!outPath.endsWith(File.separator))
            outPath += File.separator;
		File outDir = new File(outPath);
        outDir.mkdirs();
		if (outDir.exists()) {
			if (outDir.canWrite() && outDir.isDirectory()) {
				outputDir = outPath;
				return true;
			}
		}
		return false;
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
		if (inputPaths.isEmpty()) {
			Logger.info("No input path provided!\nAbort.");
			System.exit(0);
		}
		if (analysisTypes.isEmpty()) {
			Logger.info("No analysis type provided!\nAbort.");
			System.exit(0);
		}
		
		Logger.info(toString());
		
		// 1. Extract source files from input-paths, based on selected language
		String[] paths = inputPaths.toArray(new String[inputPaths.size()]);
		String[] filePaths = new String[0];
		if (paths.length > 0)
			filePaths = FileUtils.listFilesWithSuffix(paths, lang.suffix);
		Logger.info("\n# " + lang.name + " source files = " + filePaths.length + "\n");
		
		// Check language
		if (!lang.equals(Languages.JAVA)) {
			Logger.info("Analysis of " + lang.name + " programs is not yet implemented!");
			Logger.info("Abort.");
			System.exit(0);
		}

		// 2. For each analysis type, do the analysis and output results
		for (Analysis analysis: analysisTypes) {
			
			Logger.debug("\nMemory Status");
			Logger.debug("=============");
			Logger.debug(SystemUtils.getMemoryStats());

			switch (analysis.type) {
				//
				case "AST":
					Logger.info("\nAbstract Syntax Analysis");
					Logger.info("========================");
					Logger.info("START: " + Logger.time() + '\n');
					for (String srcFile : filePaths) {
						try {
                            AbstractSyntaxTree ast = ASTBuilder.build(lang.name, srcFile);
							ast.export(format.name, outputDir);
						} catch (IOException ex) {
							Logger.error(ex);
						}
					}
					break;
				//
				case "CFG":
					Logger.info("\nControl-Flow Analysis");
					Logger.info("=====================");
					Logger.info("START: " + Logger.time() + '\n');
					for (String srcFile : filePaths) {
						try {
							ControlFlowGraph cfg = CFGBuilder.build(lang.name, srcFile);
							cfg.export(format.name, outputDir);
						} catch (IOException ex) {
							Logger.error(ex);
						}
					}
					break;
				//
				case "ICFG":
					Logger.info("\nInterprocedural Control-Flow Analysis");
					Logger.info("=====================================");
					Logger.info("START: " + Logger.time() + '\n');
					try {
						ControlFlowGraph icfg = ICFGBuilder.buildForAll(lang.name, filePaths);
						icfg.export(format.name, outputDir);
					} catch (IOException ex) {
						Logger.error(ex);
					}
					break;
				//
				case "PDG":
					Logger.info("\nProgram-Dependence Analysis");
					Logger.info("===========================");
					Logger.info("START: " + Logger.time() + '\n');
					try {
						for (ProgramDependeceGraph pdg: PDGBuilder.buildForAll(lang.name, filePaths)) {
							pdg.CDS.export(format.name, outputDir);
							pdg.DDS.export(format.name, outputDir);
                            if (debugMode) {
                                pdg.DDS.getCFG().export(format.name, outputDir);
                                pdg.DDS.printAllNodesUseDefs(Logger.Level.DEBUG);
                            }
						}
					} catch (IOException ex) {
						Logger.error(ex);
					}
					break;
				//
				case "INFO":
					Logger.info("\nCode Information Analysis");
					Logger.info("=========================");
					Logger.info("START: " + Logger.time() + '\n');
					for (String srcFile : filePaths)
						CodeInfoAnalyzer.analyzeInfo(lang.name, srcFile);
					break;
				//
				default:
					Logger.info("\n\'" + analysis.type + "\' analysis is not yet implemented!\n");
			}
			Logger.info("\nFINISH: " + Logger.time());
		}
		//
		Logger.debug("\nMemory Status");
		Logger.debug("=============");
		Logger.debug(SystemUtils.getMemoryStats());
	}
}
