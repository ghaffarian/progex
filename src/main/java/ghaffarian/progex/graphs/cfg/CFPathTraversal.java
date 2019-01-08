/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.cfg;

import ghaffarian.graphs.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;

/**
 *
 * @author Seyed Mohammad Ghaffarian
 */
public class CFPathTraversal {
	
	private CFNode start;
	private ControlFlowGraph cfg;
	
	private CFNode current;
	private boolean continueNextPath;
	private Edge<CFNode, CFEdge> nextEdge;
	private Deque<Edge<CFNode, CFEdge>> paths;
	
	public CFPathTraversal(ControlFlowGraph cfg, CFNode startNode) {
		this.cfg = cfg;
		start = startNode;
		paths = new ArrayDeque<>();
		continueNextPath = false;
		current = null;
		nextEdge = null;
	}
	
	private CFNode start() {
		nextEdge = null;//new CFEdge(CFEdge.Type.EPSILON);
		current = start;
		return current;
	}
	
	public boolean hasNext() {
		return current == null || (!paths.isEmpty()) || 
				(cfg.getOutDegree(current) > 0 && !continueNextPath);
	}
	
	public CFNode next() {
		if (current == null)
			return start();
		//
		if (!continueNextPath) {
            Enumeration<Edge<CFNode, CFEdge>> outEdges = cfg.enumerateOutgoingEdges(current);
			while (outEdges.hasMoreElements()) {
                Edge<CFNode, CFEdge> out = outEdges.nextElement();
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
