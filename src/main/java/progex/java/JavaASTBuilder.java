/*** In The Name of Allah ***/
package progex.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import progex.graphs.ast.AbstractSyntaxTree;
import progex.java.parser.JavaBaseVisitor;
import progex.java.parser.JavaLexer;
import progex.java.parser.JavaParser;
import progex.utils.Logger;

/**
 * Abstract Syntax Tree (AST) builder for Java programs.
 * A Java parser generated via ANTLRv4 is used for this purpose.
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaASTBuilder {
	
	/*
	public static AbstractSyntaxTree build(File javaFile) throws IOException {
		if (!javaFile.getName().endsWith(".java"))
			throw new IOException("Not a Java File!");
		InputStream inFile = new FileInputStream(javaFile);
		ANTLRInputStream input = new ANTLRInputStream(inFile);
		JavaLexer lexer = new JavaLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(tokens);
		ParseTree tree = parser.compilationUnit();
		Map<ParserRuleContext, Object> contextualASTs = new HashMap<>();
		return build(tree, contextualASTs);
	}
	*/
	
	public static AbstractSyntaxTree build(ParseTree tree, Map<ParserRuleContext, Object> contextualASTs) {
		AbstractSyntaxTree ast = new AbstractSyntaxTree();
		AbstractSyntaxVisitor visitor = new AbstractSyntaxVisitor(ast);
		visitor.visit(tree);
		return null;
	}
	
	/**
	 * Visitor class which constructs the AST for a given ParseTree.
	 */
	private static class AbstractSyntaxVisitor extends JavaBaseVisitor<String> {
		
		public AbstractSyntaxVisitor(AbstractSyntaxTree ast) {
			
		}
		
	}
}
