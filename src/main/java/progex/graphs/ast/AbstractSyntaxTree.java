/*** In The Name of Allah ***/
package progex.graphs.ast;

import progex.graphs.Graph;

/**
 * Abstract Syntax Tree (AST).
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class AbstractSyntaxTree extends Graph<ASNode, ASEdge> {
	
	public AbstractSyntaxTree() {
		super(true);
	}
    
    public AbstractSyntaxTree(AbstractSyntaxTree ast) {
        super(ast);
    }
	
}
