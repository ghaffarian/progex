/*** In The Name of Allah ***/
package progex;

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
		System.out.printf("PROGEX (Program Graph Extractor)  [ ver. %s ]\n", VERSION);
		System.out.println("Visit project website @ https://github.com/ghaffarian/progex\n");
		//
		try {
			progex.utils.Logger.init("PROGEX.log");
			//Logger.redirectStandardError("PROGEX.err");
		} catch (java.io.IOException ex) {
			System.err.println("Logger init failed : " + ex);
		}
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
