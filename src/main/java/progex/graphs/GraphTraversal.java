/*** In The Name of Allah ***/
package progex.graphs;

/**
 * Graph traversal interface, similar to Java Collections Iterator.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public interface GraphTraversal<V,E> {
    
    /**
     * Check if the traversal is completed or not.
     */
    public boolean hasNext();
    
    /**
     * Returns the next vertex in this traversal.
     * Note that the first call to this method will return the starting vertex.
     */
    public V nextVertex();
    
    /**
     * Returns the next edge in this traversal.
     */
    public Edge<V,E> nextEdge();

}
