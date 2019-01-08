/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

/**
 * Program Dependence Graph (PDG).
 * A PDG consists of two main subgraphs:
 * Control Dependence Subgraph (CDS),
 * and Data Dependence Subgraph (DDS).
 * In this class, both the CDS and DDS are accessible.
 * 
 * NOTE: the vertex-sets of the two subgraphs are not equivalent and 
 *       are different sets with different node instances.
 *       This might change in future versions.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class ProgramDependeceGraph {	
	
	/**
	 * Name of the corresponding Java source file.
	 */
	public final String FILE_NAME;
	
	/**
	 * Control Dependence Subgraph (CDS) of this PDG.
	 */
	public final ControlDependenceGraph CDS;
	
	/**
	 * Data Dependence Subgraph (DDS) of this PDG.
	 */
	public final DataDependenceGraph DDS;
	
	/**
	 * Constructs a new Program Dependence Graph (PDG) instance, 
	 * based on the given Java file-name, Control Dependence Graph, 
	 * and Data Dependence Graph.
	 */
	public ProgramDependeceGraph(String name, 
			ControlDependenceGraph cds, DataDependenceGraph dds) {
		FILE_NAME = name;
		CDS = cds;
		DDS = dds;
	}
}
