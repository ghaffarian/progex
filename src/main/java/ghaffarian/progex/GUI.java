/*** In The Name of Allah ***/
package ghaffarian.progex;

import javax.swing.JOptionPane;
import ghaffarian.nanologger.Logger;

/**
 * Graphical User Interface (GUI) of PROGEX.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class GUI {
	
	public GUI() {
		// Set Look and Feel
		try {
			javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | 
				 javax.swing.UnsupportedLookAndFeelException ex) {
			Logger.warn("WARNING -- Failed to set Look & Feel! " + ex.toString());
		}
	}
	
	public void show() {
		String msg = "Currently, no GUI is implemented for this program.\n"
				   + "Please use the command-line interface instead:\n"
				   + "    java -jar  PROGEX.jar -help";
		JOptionPane.showMessageDialog(null, msg, "PROGEX", JOptionPane.INFORMATION_MESSAGE);
	}

}
