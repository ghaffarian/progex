/*** In The Name of Allah ***/
package progex.graphs.cfg;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 *
 * @author Seyed Mohammad Ghaffarian
 */
public class CFPathTraversal {
	
	private CFNode start;
	private ControlFlowGraph cfg;
	
	private CFEdge edge;
	private CFNode current;
	private Deque<CFEdge> paths;
	private boolean continueNextPath;
	
	public CFPathTraversal(ControlFlowGraph cfg, CFNode startNode) {
		this.cfg = cfg;
		start = startNode;
		paths = new ArrayDeque<>();
		continueNextPath = false;
		current = null;
		edge = null;
	}
	
	private CFNode start() {
		edge = new CFEdge(CFEdge.Type.EPSILON);
		current = start;
		return current;
	}
	
	public boolean hasNext() {
		return current == null || (!paths.isEmpty()) || 
				(cfg.outDegreeOf(current) > 0 && !continueNextPath);
	}
	
	public CFNode next() {
		if (current == null)
			return start();
		//
		if (!continueNextPath)
			for (CFEdge edge: cfg.outgoingEdgesOf(current))
				paths.push(edge);
		continueNextPath = false;
		//
		if (paths.isEmpty())
			return null;
		edge = paths.pop();
		current = cfg.getEdgeTarget(edge);
		return current;
	}
	
	public CFEdge edge() {
		return edge;
	}
	
	public void continueNextPath() {
		continueNextPath = true;
	}
	
}
