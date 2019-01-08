/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

/**
 * Class type of Data Dependence (DD) edges.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class DDEdge {
	
	public final Type type;
	public final String var;
	
	public DDEdge(Type type, String var) {
		this.type = type;
		this.var = var;
	}
	
	@Override
	public String toString() {
        return var;
		//return type.toString() + '(' + var + ')';
	}

	/**
	 * Enumeration of different types for DD edges.
	 */
	public enum Type {
		FLOW    ("Flows"),
		ANTI    ("Anti"),
		OUTPUT  ("Out");

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
