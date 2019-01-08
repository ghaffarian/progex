/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.ast;

import java.io.IOException;
import ghaffarian.progex.java.JavaASTBuilder;

/**
 * Abstract Syntax Tree (AST) Builder.
 * This class invokes the appropriate builder based on the given language parameter.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class ASTBuilder {
    
	/**
	 * Build and return the CFG of the given source code file with specified language.
	 */
	public static AbstractSyntaxTree build(String lang, String srcFilePath) throws IOException {
		switch (lang) {
			case "C":
				return null;
			//
			case "Java":
				return JavaASTBuilder.build(srcFilePath);
			//
			case "Python":
				return null;
			//
			default:
				return null;
		}
	}

}
