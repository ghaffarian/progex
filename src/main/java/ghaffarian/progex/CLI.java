/*** In The Name of Allah ***/
package ghaffarian.progex;

import java.io.File;
import java.io.IOException;
import ghaffarian.nanologger.Logger;

/**
 * Command Line Interface (CLI) for PROGEX.
 * CLI argument parsing is performed in this class.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class CLI {
	
    private boolean debugMode;
	private final Execution exec;
	
	public CLI() {
		exec = new Execution();
	}
	
	/**
	 * Parse command line arguments.
	 */
	public Execution parse(String[] args) {
		for (int i = 0; i < args.length; ++i) {
			// options can start with either '-' or '--'
			if (args[i].startsWith("-")) {
				if (args[i].length() > 3) {
					String opt = args[i].substring(1).toLowerCase();
					if (args[i].startsWith("--"))
						opt = args[i].substring(2).toLowerCase();
					// Now process the value of opt
					switch (opt) {
						case "cfg":
							exec.addAnalysisOption(Execution.Analysis.CFG);
							break;
						//
						case "pdg":
							exec.addAnalysisOption(Execution.Analysis.PDG);
							break;
						//
						case "ast":
							exec.addAnalysisOption(Execution.Analysis.AST);
							break;
						//
						case "icfg":
							exec.addAnalysisOption(Execution.Analysis.ICFG);
							break;
						//
						case "info":
							exec.addAnalysisOption(Execution.Analysis.SRC_INFO);
							break;
						//
						case "help":
							printHelp(null);
							break;
						//
						case "outdir":
							if (i < args.length - 1) {
								++i;
								if (!exec.setOutputDirectory(args[i])) {
									printHelp("Output directory is not valid!");
									System.exit(1);
								}
							} else {
								printHelp("Output directory not specified!");
								System.exit(1);
							}
							break;
						//
						case "format":
							if (i < args.length - 1) {
								++i;
								switch (args[i].toLowerCase()) {
									case "dot":
										exec.setOutputFormat(Execution.Formats.DOT);
										break;
									case "json":
										exec.setOutputFormat(Execution.Formats.JSON);
										break;
									default:
										printHelp("Unknown output format: " + args[i]);
										System.exit(1);
								}
							} else {
								printHelp("Format not specified!");
								System.exit(1);
							}
							break;
						//
						case "lang":
							if (i < args.length - 1) {
								++i;
								switch (args[i].toLowerCase()) {
									case "java":
										exec.setLanguage(Execution.Languages.JAVA);
										break;
									default:
										printHelp("Unsupported language: " + args[i]);
										System.exit(1);
								}
							} else {
								printHelp("Language not specified!");
								System.exit(1);
							}
							break;
						//
						case "debug":
                            exec.setDebugMode(true);
                            try {
                                Logger.setActiveLevel(Logger.Level.DEBUG);
                                Logger.redirectStandardError("progex.err");
                            } catch (IOException ex) {
                                Logger.error(ex);
                            }
							break;
						//
						case "timetags":
                            Logger.setTimeTagEnabled(true);
							break;
						//
						default:
							printHelp("Unknown Option: " + args[i]);
							System.exit(1);
					}
				} else {
					printHelp("Invalid Option: " + args[i]);
					System.exit(1);
				}
			} else {
				// any argument that does not start with a '-' is considered an input file path
				File input = new File(args[i]);
				if (input.exists())
					exec.addInputPath(args[i]);
				else
					Logger.warn("WARNING -- Ignoring non-existant input path: " + args[i]);
			}
		}
		return exec;
	}
	
	/**
	 * Prints the usage guide for the program.
	 * If an error message is given, the message is also printed to the output.
	 */
	public static void printHelp(String errMsg) {
		if (errMsg != null && !errMsg.isEmpty())
			Logger.error("ERROR -- " + errMsg + '\n');
		
		String[] help = {
			"USAGE:\n\n   java -jar PROGEX.jar [-OPTIONS...] /path/to/program/src\n",
			"OPTIONS:\n",
			"   -help      Print this help message",
			"   -outdir    Specify path of output directory",
			"   -format    Specify output format; either 'JSON' or 'DOT'",
			"   -lang      Specify language of program source codes\n",
			"   -ast       Perform AST (Abstract Syntax Tree) analysis",
			"   -cfg       Perfomt CFG (Control Flow Graph) analysis",
			"   -icfg      Perform ICFG (Interprocedural CFG) analysis",
			"   -info      Analyze and extract detailed information about program source code",
			"   -pdg       Perform PDG (Program Dependence Graph) analysis\n",
			"   -debug     Enable more detailed logs (only for debugging)",
			"   -timetags  Enable time-tags and labels for logs (only for debugging)\n",
			"DEFAULTS:\n",
			"   - If not specified, the default output directory is the current working directory.",
			"   - If not specified, the default output format is DOT.",
			"   - If not specified, the default language is Java.",
			"   - There is no default value for analysis type.",
			"   - There is no default value for input directory path.\n",
			"EXAMPLES:\n",
			"   java -jar PROGEX.jar -cfg -lang java -format dot  /home/user/project/src\n",
			"      This example will extract the CFG of all Java source files in the given path and ",
			"      will export all extracted graphs as DOT files in the current working directory.\n",
			"   java -jar PROGEX.jar -outdir D:\\outputs -format json -pdg  C:\\Project\\src\n",
			"      This example will extract the PDGs of all Java source files in the given path and ",
			"      will export all extracted graphs as JSON files in the given output directory.\n",
			"NOTES:\n",
			"   - The important pre-assumption for analyzing any source code is that the ",
			"     program is valid according to the grammar of that language. Analyzing ",
			"     invalid programs has undefined results; most probably the program will ",
			"     crash!\n",
			"   - Analyzing large programs requires high volumes of system memory, so ",
			"     it is necessary to increase the maximum available memory to PROGEX.\n",
			"     In the example below, the -Xmx option of the JVM is used to provide PROGEX ",
			"     with 5 giga-bytes of system memory; which is required for the PDG analysis ",
			"     of very large programs (i.e. about one million LoC). Needless to say, this ",
			"     is possible on a computer with at least 8 giga-bytes of RAM:\n",
			"        java -Xmx5G -jar PROGEX.jar -pdg ...\n",
		};
		
		for (String line: help)
			Logger.info(line);
	}

}
