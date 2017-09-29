/*** In The Name of Allah ***/
package progex.graphs.ast;

import org.jgrapht.graph.SimpleDirectedGraph;

/**
 * Control Flow Graph (CFG).
 * This is based on the DirectedPseudograph implementation from JGraphT lib.
 * @see http://jgrapht.org
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class AbstractSyntaxTree extends SimpleDirectedGraph<ASNode, ASEdge> {
	
	public AbstractSyntaxTree() {
		super(ASEdge.class);
	}
	
	
}
