/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

/**
 * Class type of Control Dependence (CD) edges.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class CDEdge {

	public final Type type;
	
	public CDEdge(Type type) {
		this.type = type;
	}
    
    @Override
    public String toString() {
        return type.toString();
    }

	/**
	 * Enumeration of different types for CD edges.
	 */
	public enum Type {
		EPSILON    (""),
		TRUE       ("True"),
		FALSE      ("False"),
		THROWS     ("Throws"),
		NOT_THROWS ("Not-Throws");

		public final String label;

		private Type(String lbl) {
			label = lbl;
		}

		@Override
		public String toString() {
			return label;
		}
	}	
}
