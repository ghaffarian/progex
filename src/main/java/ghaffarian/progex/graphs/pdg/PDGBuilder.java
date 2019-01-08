/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

import java.io.IOException;
import ghaffarian.progex.java.JavaPDGBuilder;

/**
 * Program Dependence Graph (PDG) Builder.
 * This class invokes the appropriate builder based on the given language parameter.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class PDGBuilder {
	
	public static ProgramDependeceGraph[] buildForAll(String lang, String[] srcFilePaths) throws IOException {
		switch (lang) {
			case "C":
				return null;
			//
			case "Java":
				return JavaPDGBuilder.buildForAll(srcFilePaths);
			//
			case "Python":
				return null;
			//
			default:
				return null;
		}
	}
	
}
