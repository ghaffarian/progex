/*** In The Name of Allah ***/
package progex.graphs.pdg;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jgrapht.graph.DirectedPseudograph;
import progex.graphs.cfg.CFEdge;
import progex.graphs.cfg.CFNode;
import progex.graphs.cfg.ControlFlowGraph;
import progex.utils.StringUtils;

/**
 * Data Dependence Graph.
 * This is based on the DirectedPseudograph implementation from JGraphT lib.
 * @see http://jgrapht.org
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class DataDependenceGraph extends DirectedPseudograph<PDNode, DDEdge> {
	
	public final String FILE_NAME;
	private ControlFlowGraph cfg;
	
	public DataDependenceGraph(String javaFileName) {
		super(DDEdge.class);
		FILE_NAME = javaFileName;
		cfg = null;
	}
	
	public void attachCFG(ControlFlowGraph cfg) {
		this.cfg = cfg;
	}
	
	public ControlFlowGraph getCFG() {
		return cfg;
	}
	
	public void printAllNodesUseDefs(PrintStream out) {
		for (PDNode node: vertexSet()) {
			out.println(node);
			out.println("  + USEs: " + Arrays.toString(node.getAllUSEs()));
			out.println("  + DEFs: " + Arrays.toString(node.getAllDEFs()) + "\n");
		}
	}

	/**
	 * Export the Data Dependence Subgraph (DDG) of PDG to specified file format.
	 */
	public void export(String format, String outDir) throws FileNotFoundException {
		switch (format.toLowerCase()) {
			case "dot":
				exportDOT(outDir, true);
				break;
			case "json":
				exportJSON(outDir);
				break;
		}
	}
	
	/**
	 * Export the Data Dependence Subgraph (DDG) of PDG to JSON file format.
	 * The JSON file will be saved inside the given directory.
	 */
	public void exportJSON(String outDir) throws FileNotFoundException {
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-PDG-DATA.json";
		try (PrintWriter json = new PrintWriter(filepath, "UTF-8")) {
			json.println("{\n  \"type\": \"PDG-DATA\",");
			json.println("  \"file\": \"" + FILE_NAME + "\",");
			json.println("\n\n  \"nodes\": [");
			int nodeCounter = 1;
			Map<CFNode, String> ctrlNodes = new HashMap<>();
			Map<PDNode, String> dataNodes = new HashMap<>();
			for (CFNode node: cfg.vertexSet()) {
				String id = "n" + nodeCounter++;
				ctrlNodes.put(node, id);
				json.println("    {");
				json.println("      \"id\": \"" + id + "\",");
				json.println("      \"line\": \"" + node.getLineOfCode() + "\",");
				PDNode pdNode = (PDNode) node.getProperty("pdnode");
				if (pdNode != null) {
					dataNodes.put(pdNode, id);
					json.println("      \"defs\": " + StringUtils.toJsonArray(pdNode.getAllDEFs()) + ",");
					json.println("      \"uses\": " + StringUtils.toJsonArray(pdNode.getAllUSEs()) + ",");
				}
				json.println("      \"code\": \"" + node.getCode().replace("\"", "\\\"") + "\"");
				json.println("    },");
			}
			json.println("  ],\n\n\n  \"edges\": [");
			int edgeCounter = 1;
			for (CFEdge ctrlEdge: cfg.edgeSet()) {
				json.println("    {");
				String id = "e" + edgeCounter++;
				json.println("      \"id\": \"" + id + "\",");
				String src = ctrlNodes.get(cfg.getEdgeSource(ctrlEdge));
				json.println("      \"source\": \"" + src + "\",");
				String trgt = ctrlNodes.get(cfg.getEdgeTarget(ctrlEdge));
				json.println("      \"target\": \"" + trgt + "\",");
				json.println("      \"type\": \"Control\",");
				json.println("      \"label\": \"" + ctrlEdge.type.label + "\"");
				json.println("    },");
			}
			for (DDEdge dataEdge: edgeSet()) {
				json.println("    {");
				String id = "e" + edgeCounter++;
				json.println("      \"id\": \"" + id + "\",");
				String src = dataNodes.get(getEdgeSource(dataEdge));
				json.println("      \"source\": \"" + src + "\",");
				String trgt = dataNodes.get(getEdgeTarget(dataEdge));
				json.println("      \"target\": \"" + trgt + "\",");
				json.println("      \"type\": \"" + dataEdge.type + "\",");
				json.println("      \"label\": \"" + dataEdge.var + "\"");
				json.println("    },");
			}
			json.println("  ]\n}");
		} catch (UnsupportedEncodingException ex) {
			System.err.println(ex);
		}
		System.out.println("DDS of PDG exported to: " + filepath);
	}
	
	/**
	 * Export the Data Dependence Subgraph (DDG) of PDG to DOT file format.
	 * The DOT file will be saved inside the given directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
	public void exportDOT(String outDir, boolean ctrlEdgeLabels) throws FileNotFoundException {
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-PDG-DATA.dot";
		try (PrintWriter dot = new PrintWriter(filepath, "UTF-8")) {
			dot.println("digraph " + filename + " {\n");
			Map<CFNode, String> ctrlNodes = new HashMap<>();
			Map<PDNode, String> dataNodes = new HashMap<>();
			int nodeCounter = 1;
			for (CFNode node: cfg.vertexSet()) {
				String name = "n" + nodeCounter++;
				ctrlNodes.put(node, name);
				PDNode pdNode = (PDNode) node.getProperty("pdnode");
				if (pdNode != null)
					dataNodes.put(pdNode, name);
				StringBuilder label = new StringBuilder("   [label=\"");
				if (node.getLineOfCode() > 0)
					label.append(node.getLineOfCode()).append(":  ");
				label.append(StringUtils.escape(node.getCode())).append("\"];");
				dot.println("   " + name + label.toString());
			}
			dot.println();
			for (CFEdge ctrlEdge: cfg.edgeSet()) {
				String src = ctrlNodes.get(cfg.getEdgeSource(ctrlEdge));
				String trg = ctrlNodes.get(cfg.getEdgeTarget(ctrlEdge));
				if (ctrlEdgeLabels)
					dot.println("   " + src + " -> " + trg + "   [arrowhead=empty, color=gray, style=dashed, label=\"" + ctrlEdge.type + "\"];");
				else
					dot.println("   " + src + " -> " + trg + "   [arrowhead=empty, color=gray, style=dashed];");
			}
			for (DDEdge dataEdge: edgeSet()) {
				String src = dataNodes.get(getEdgeSource(dataEdge));
				String trg = dataNodes.get(getEdgeTarget(dataEdge));
				dot.println("   " + src + " -> " + trg + "   [style=bold, label=\" (" + dataEdge.var + ")\"];");
			}
			dot.println("\n}");
		} catch (UnsupportedEncodingException ex) {
			System.err.println(ex);
		}
		System.out.println("DDS of PDG exported to: " + filepath);
	}
}
