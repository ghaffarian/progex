/*** In The Name of Allah ***/
package progex.graphs;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A generic class for labeled graphs.
 * This class supports both directed and undirected graphs.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class Graph<V,E> {
    
    public final boolean IS_DIRECTED;
    
    protected Set<V> allVertices;
    protected Set<Edge<V,E>> allEdges;
    protected Map<V, Set<Edge<V,E>>> inEdges;
    protected Map<V, Set<Edge<V,E>>> outEdges;
    
    /**
     * Construct a new empty Graph object with the given direction property.
     * 
     * @param directed specify if graph is directed (true) or undirected (false)
     */
    public Graph(boolean directed) {
        IS_DIRECTED = directed;
        allEdges = new LinkedHashSet<>(32);
        allVertices = new LinkedHashSet<>();
        inEdges = new LinkedHashMap<>();
        outEdges = new LinkedHashMap<>();
    }
    
    /**
     * Copy constructor: 
     * create a new Graph instance by copying the state of the given Graph object.
     * 
     * @param graph the Graph object to be copied
     */
    public Graph(Graph<V,E> graph) {
        IS_DIRECTED = graph.IS_DIRECTED;
        // copy all vertices and edges
        allEdges = new LinkedHashSet<>(graph.allEdges);
        allVertices = new LinkedHashSet<>(graph.allVertices);
        // copy incoming-edges map
        inEdges = new LinkedHashMap<>();
        for (V v: graph.inEdges.keySet())
            inEdges.put(v, new LinkedHashSet<>(graph.inEdges.get(v)));
        // copy outgoing-edges map
        outEdges = new LinkedHashMap<>();
        for (V v: graph.outEdges.keySet())
            outEdges.put(v, new LinkedHashSet<>(graph.outEdges.get(v)));
    }
    
    /**
     * Add the given vertex to this graph.
     * 
     * @return true if the vertex is added, or
     *         false if such vertex is already in the graph.
     */
    public boolean addVertex(V v) {
        if (allVertices.add(v)) {
            inEdges.put(v, new LinkedHashSet<>());
            outEdges.put(v, new LinkedHashSet<>());
            return true;
        }
        return false;
    }
    
    /**
     * Remove the given vertex from this graph.
     * 
     * @return true if the vertex is removed, or
     *         false if no such vertex is in the graph.
     */
    public boolean removeVertex(V v) {
        if (allVertices.remove(v)) {
            allEdges.removeAll(inEdges.remove(v));
            allEdges.removeAll(outEdges.remove(v));
            return true;
        }
        return false;
    }
    
    /**
     * Add the given edge to this graph.
     * Both vertices (source and target) of the edge must be in the graph
     * otherwise, an exception is thrown indicating this issue.
     * 
     * @return true if the edge is added, or
     *         false if the edge is already in the graph.
     */
    public boolean addEdge(Edge<V,E> e) {
        if (!allVertices.contains(e.source))
            throw new IllegalArgumentException("No such source-vertex in this graph!");
        if (!allVertices.contains(e.target))
            throw new IllegalArgumentException("No such target-vertex in this graph!");
        if (allEdges.add(e)) {
            inEdges.get(e.target).add(e);
            outEdges.get(e.source).add(e);
            return true;
        }
        return false;
    }
    
    /**
     * Remove the given edge from this graph.
     * 
     * @return true if the vertex is removed, or
     *         false if no such vertex is in the graph.
     */
    public boolean removeEdge(Edge<V,E> e) {
        if (allEdges.remove(e)) {
            inEdges.get(e.target).remove(e);
            outEdges.get(e.source).remove(e);
            return true;
        }
        return false;
    }
    
    /**
     * Remove all edges in this graph between the given source vertex and target vertex.
     * 
     * @return the set of edges removed from this graph as a result of this operation.
     */
    public Set<Edge<V,E>> removeEdges(V src, V trgt) {
        if (!allVertices.contains(src))
            throw new IllegalArgumentException("No such source-vertex in this graph!");
        if (!allVertices.contains(trgt))
            throw new IllegalArgumentException("No such target-vertex in this graph!");
        Set<Edge<V,E>> iterSet;
        Set<Edge<V,E>> removed = new LinkedHashSet<>();
        if (inEdges.get(trgt).size() > outEdges.get(src).size()) {
            iterSet = outEdges.get(src);
            Iterator<Edge<V,E>> it = iterSet.iterator();
            while (it.hasNext()) {
                Edge<V,E> next = it.next();
                if (next.target.equals(trgt)) {
                    it.remove();
                    allEdges.remove(next);
                    inEdges.get(trgt).remove(next);
                    removed.add(next);
                }
            }
        } else {
            iterSet = inEdges.get(trgt);
            Iterator<Edge<V,E>> it = iterSet.iterator();
            while (it.hasNext()) {
                Edge<V,E> next = it.next();
                if (next.source.equals(src)) {
                    it.remove();
                    allEdges.remove(next);
                    outEdges.get(src).remove(next);
                    removed.add(next);
                }
            }
        }
        return removed;
    }
    
    /**
     * Adds all vertices and edges of the given graph to this graph.
     * 
     * @return true if this graph was modified; otherwise false.
     */
    public boolean addGraph(Graph<V,E> graph) {
        boolean modified = false;
        for (V vrtx: graph.allVertices)
            modified |= addVertex(vrtx);
        for (Edge<V,E> edge: graph.allEdges)
            modified |= addEdge(edge);
        return modified;
    }
    
    /**
     * Return the number of vertices in this graph.
     */
    public int vertexCount() {
        return allVertices.size();
    }
    
    /**
     * Return the number of edges in this graph.
     */
    public int edgeCount() {
        return allEdges.size();
    }
    
    /**
     * Return an enumeration over all edges of the graph.
     */
    public Enumeration<Edge<V,E>> enumerateAllEdges() {
        return Collections.enumeration(allEdges);
    }
    
    /**
     * Return an enumeration over all vertices of the graph.
     */
    public Enumeration<V> enumerateAllVertices() {
        return Collections.enumeration(allVertices);
    }
    
    /**
     * Return a copy of the set of all edges in this graph.
     * This method has the overhead of creating of copy of the current set of edges.
     * Hence the returned collection is safe to use and modify (it is not linked to this graph).
     */
    public Set<Edge<V,E>> copyEdgeSet() {
        return new LinkedHashSet<>(allEdges);
    }
    
    /**
     * Return a copy of the set of all vertices in this graph.
     * This method has the overhead of creating of copy of the current set of vertices.
     * Hence the returned collection is safe to use and modify (it is not linked to this graph).
     */
    public Set<V> copyVertexSet() {
        return new LinkedHashSet<>(allVertices);
    }
    
    /**
     * Return an enumeration over the set of incoming edges to the given vertex.
     */
    public Enumeration<Edge<V,E>> enumerateIncomingEdges(V v) {
        return Collections.enumeration(inEdges.get(v));
    }
    
    /**
     * Return an enumeration over the set of outgoing edges from the given vertex.
     */
    public Enumeration<Edge<V,E>> enumerateOutgoingEdges(V v) {
        return Collections.enumeration(outEdges.get(v));
    }
    
    /**
     * Return a copy of the set of incoming edges to the given vertex.
     * This method has the overhead of creating of copy of the current set of incoming edges.
     * Hence the returned collection is safe to use and modify (it is not linked to this graph).
     */
    public Set<Edge<V,E>> copyIncomingEdges(V v) {
        if (!allVertices.contains(v))
            throw new IllegalArgumentException("No such vertex in this graph!");
        return new LinkedHashSet<>(inEdges.get(v));
    }
    
    /**
     * Return a copy of the set of outgoing edges from the given vertex.
     * This method has the overhead of creating of copy of the current set of outgoing edges.
     * Hence the returned collection is safe to use and modify (it is not linked to this graph).
     */
    public Set<Edge<V,E>> copyOutgoingEdges(V v) {
        if (!allVertices.contains(v))
            throw new IllegalArgumentException("No such vertex in this graph!");
        return new LinkedHashSet<>(outEdges.get(v));
    }
    
    /**
     * Return the count of incoming edges to the given vertex.
     */
    public int getInDegree(V v) {
        if (!allVertices.contains(v))
            throw new IllegalArgumentException("No such vertex in this graph!");
        return inEdges.get(v).size();
    }
    
    /**
     * Return the count of outgoing edges from the given vertex.
     */
    public int getOutDegree(V v) {
        if (!allVertices.contains(v))
            throw new IllegalArgumentException("No such vertex in this graph!");
        return outEdges.get(v).size();
    }
    
    /**
     * Return the set of edges with a label same as the given value.
     */
    public Set<Edge<V,E>> getEdgesWithLabel(E label) {
        Set<Edge<V,E>> edges = new LinkedHashSet<>();
        for (Edge e: allEdges) {
            if (label.equals(e.label))
                edges.add(e);
        }
        return edges;
    }
    
    /**
     * Check if this graph contains the given edge.
     */
    public boolean containsEdge(Edge<V,E> e) {
        if (IS_DIRECTED)
            return allEdges.contains(e);
        else
            return allEdges.contains(e.reverse());
    }
    
    /**
     * Check if this graph contains an edge between the given vertices.
     */
    public boolean containsEdge(V src, V trg) {
        for (Edge<V,E> edge: outEdges.get(src)) {
            if (edge.target.equals(trg))
                return true;
        }
        if (!IS_DIRECTED) {
            for (Edge<V,E> edge: outEdges.get(trg))
                if (edge.target.equals(src))
                    return true;
        }
        return false;
    }
    
    /**
     * Check if this graph contains the given vertex.
     */
    public boolean containsVertex(V v) {
        return allVertices.contains(v);
    }
    
    /**
     * Check if this graph is a subgraph of the given base graph.
     */
    public boolean isSubgraphOf(Graph<V,E> base) {
        if (IS_DIRECTED != base.IS_DIRECTED)
            return false;
        if (this.allVertices.size() > base.allVertices.size() || this.allEdges.size() > base.allEdges.size())
            return false;
        return base.allVertices.containsAll(this.allVertices) && base.allEdges.containsAll(this.allEdges);
    }
    
    /**
     * Check if this graph is a proper subgraph of the given base graph.
     * A proper subgraph is a subgraph which is not equal to the base graph.
     * A proper subgraph lacks at least on vertex or edge compared to the base.
     */
    public boolean isProperSubgraphOf(Graph<V,E> base) {
        if (this.allVertices.size() == base.allVertices.size() || this.allEdges.size() == base.allEdges.size())
            return false;
        return isSubgraphOf(base);
    }
    
    /**
     * Check whether this graph is connected or not.
     * Connectivity is determined by a breadth-first-traversal starting from a random vertex.
     */
    public boolean isConnected() {
        Set<V> visited = new HashSet<>();
        Deque<V> visiting = new ArrayDeque<>();
        visiting.add(allVertices.iterator().next());
        while (!visiting.isEmpty()) {
            V next = visiting.remove();
            visited.add(next);
            for (Edge<V,E> out: outEdges.get(next)) {
                if (!visited.contains(out.target))
                    visiting.add(out.target);
            }
            for (Edge<V,E> in: inEdges.get(next)) {
                if (!visited.contains(in.source))
                    visiting.add(in.source);
            }
        }
        return visited.size() == allVertices.size();
        // return visited.containsAll(allVertices);
    }
    
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (V vrtx: allVertices) {
            str.append(vrtx).append(":\n");
            for (Edge<V,E> edge: outEdges.get(vrtx)) {
                if (edge.label == null)
                    str.append("  --> ").append(edge.target).append("\n");
                else
                    str.append("  --(").append(edge.label).append(")--> ").append(edge.target).append("\n");
            }
        }
        return str.toString();
    }
    
    public String toSingleLineString() {
        StringBuilder str = new StringBuilder("{ ");
        for (V vrtx: allVertices) {
            str.append(vrtx).append(": [ ");
            if (!outEdges.get(vrtx).isEmpty()) {
                for (Edge<V,E> edge: outEdges.get(vrtx)) {
                    if (edge.label == null)
                        str.append("(->").append(edge.target).append(") ");
                    else
                        str.append("(").append(edge.label).append("->").append(edge.target).append(") ");
                }
            }
            str.append("]; ");
        }
        str.append("}");
        return str.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Graph<V,E> other = (Graph<V,E>) obj;
        return  this.IS_DIRECTED == other.IS_DIRECTED &&
                this.vertexCount() == other.vertexCount() && 
                this.edgeCount() == other.edgeCount() && 
                this.allVertices.containsAll(other.allVertices) &&
                this.allEdges.containsAll(other.allEdges);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 71 * hash + (this.IS_DIRECTED ? 1 : 0);
        for (V v: allVertices)
            hash = 71 * hash + Objects.hashCode(v);
        for (Edge<V,E> e: allEdges)
            hash = 71 * hash + Objects.hashCode(e);
        return hash;
    }

}
