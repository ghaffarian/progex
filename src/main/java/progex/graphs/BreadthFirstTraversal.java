/*** In The Name of Allah ***/
package progex.graphs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

/**
 * Breadth-First-Search (BFS) graph traversal.
 * This type of traversal will try to reach all connected vertices 
 * of the starting vertex, with respect to edge directions (if graph is directed).
 * Not all connected edges will be visited in this type of traversal.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class BreadthFirstTraversal<V,E> implements GraphTraversal<V, E> {
    
    public final Graph<V,E> GRAPH;
    public final V START_VERTEX;
    
    private V nextVertex;
    private Edge<V,E> nextEdge;
    private final Deque<V> visited;
    private final Deque<Edge<V,E>> visiting;
    
    /**
     * Construct a new breadth-first traversal 
     * on the given graph, starting from the given vertex.
     */
    public BreadthFirstTraversal(Graph graph, V start) {
        GRAPH = graph;
        nextEdge = null;
        nextVertex = null;
        START_VERTEX = start;
        visited = new ArrayDeque<>(GRAPH.allVertices.size());
        visiting = new ArrayDeque<>(GRAPH.allEdges.size());
        Edge<V,E> startEdge = new Edge<>(null, null, START_VERTEX);
        visiting.add(startEdge); // dummy start edge
    }

    @Override
    public boolean hasNext() {
        return !visiting.isEmpty();
    }

    @Override
    public V nextVertex() {
        if (visiting.isEmpty())
            throw new NoSuchElementException("No more vertices to traverse!");
        next();
        return nextVertex;
    }

    @Override
    public Edge<V, E> nextEdge() {
        if (visiting.isEmpty())
            throw new NoSuchElementException("No more vertices to traverse!");
        next();
        if (nextEdge.source == null)
            // first call to next() (ie. edge is dummy); do next() one more time
            next();
        return nextEdge;
    }

    
    private void next() {
        nextEdge = visiting.remove();
        
        if (GRAPH.IS_DIRECTED) {
            // directed graph
            nextVertex = nextEdge.target;
            if (visited.add(nextVertex)) {
                for (Edge<V,E> out: GRAPH.outEdges.get(nextVertex))
                    if (!visited.contains(out.target))
                        visiting.add(out);
            }
        } else {
            // undirected graph
            if (visited.contains(nextEdge.source))
                nextVertex = nextEdge.target;
            else
                nextVertex = nextEdge.source;
            //
            if (visited.add(nextVertex)) {
                for (Edge<V,E> out: GRAPH.outEdges.get(nextVertex))
                    if (!visited.contains(out.target))
                        visiting.add(out);
                for (Edge<V,E> in: GRAPH.inEdges.get(nextVertex))
                    if (!visited.contains(in.source))
                        visiting.add(in);
            }
        }
    }
}
