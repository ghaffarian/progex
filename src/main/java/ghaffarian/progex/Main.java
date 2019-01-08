/*** In The Name of Allah ***/
package ghaffarian.progex;

import ghaffarian.nanologger.Logger;

/**
 * The executions starting point.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class Main {
	
	public static String VERSION = "3.0.0";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
		try {
			Logger.init("progex.log");
            Logger.setEchoToStdOut(true);
            Logger.setTimeTagEnabled(false);
		} catch (java.io.IOException ex) {
			System.err.println("[ERR] LOGGER INIT FAILED : " + ex);
		}
        //
        Logger.printf(Logger.Level.INFO, "\nPROGEX (Program Graph Extractor)  [ v%s ]", VERSION);
        Logger.info("Visit project website @ https://github.com/ghaffarian/progex\n");
		//
		if (args.length == 0) {
			CLI.printHelp(null);
			// if there are no command-line arguments, show the GUI;
			new GUI().show();
		} else {
			// otherwise, parse the arguments and execute accordingly.
			new CLI().parse(args).execute();
		}
    }

}
