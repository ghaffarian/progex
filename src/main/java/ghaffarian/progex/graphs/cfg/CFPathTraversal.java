/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.cfg;

import ghaffarian.graphs.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Control-Flow Path Traversal.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class CFPathTraversal implements Iterator {
	
	private final CFNode start;
	private final ControlFlowGraph cfg;
	private final Deque<Edge<CFNode, CFEdge>> paths;
	
	private CFNode current;
	private boolean continueNextPath;
	private Edge<CFNode, CFEdge> nextEdge;
	
	public CFPathTraversal(ControlFlowGraph cfg, CFNode startNode) {
		this.cfg = cfg;
		start = startNode;
		paths = new ArrayDeque<>();
		continueNextPath = false;
		current = null;
		nextEdge = null;
	}
	
	private CFNode start() {
		nextEdge = null;  // new CFEdge(CFEdge.Type.EPSILON);
		current = start;
		return current;
	}
	
    @Override
	public boolean hasNext() {
		return current == null || (!paths.isEmpty()) || 
				(cfg.getOutDegree(current) > 0 && !continueNextPath);
	}
	
    @Override
	public CFNode next() {
		if (current == null)
			return start();
		//
		if (!continueNextPath) {
            Iterator<Edge<CFNode, CFEdge>> outEdges = cfg.outgoingEdgesIterator(current);
			while (outEdges.hasNext()) {
                Edge<CFNode, CFEdge> out = outEdges.next();
				paths.push(out);
            }
        }
		continueNextPath = false;
		//
		if (paths.isEmpty())
			return null;
		nextEdge = paths.pop();
		current = nextEdge.target;
		return current;
	}
	
	public void continueNextPath() {
		continueNextPath = true;
	}
}
