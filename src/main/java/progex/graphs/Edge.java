/*** In The Name of Allah ***/
package progex.graphs;

import java.util.Objects;

/**
 * Edge class for graphs.
 * Contains references for source vertex and target vertex.
 * This class can also contain a label of any type.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class Edge<V,E> {
    
    public final E label;
    public final V source;
    public final V target;
    
    public Edge(V src, E lbl, V trgt) {
        label = lbl;
        source = src;
        target = trgt;
    }
    
    @Override
    public String toString() {
        if (label == null)
            return String.format("%s --> %s", source.toString(), target.toString());
        else
            return String.format("%s --(%s)-> %s", source.toString(), label.toString(), target.toString());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Edge<V,E> other = (Edge<V,E>) obj;
        return  Objects.equals(this.label, other.label) && 
                Objects.equals(this.source, other.source) &&
                Objects.equals(this.target, other.target);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + Objects.hashCode(this.label);
        hash = 53 * hash + Objects.hashCode(this.source);
        hash = 53 * hash + Objects.hashCode(this.target);
        return hash;
    }
}
