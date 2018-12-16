/*** In The Name of Allah ***/
package progex.graphs.pdg;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import progex.graphs.Edge;
import progex.graphs.Graph;
import progex.graphs.cfg.CFEdge;
import progex.graphs.cfg.CFNode;
import progex.graphs.cfg.ControlFlowGraph;
import progex.utils.StringUtils;

/**
 * Data Dependence Graph.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class DataDependenceGraph extends Graph<PDNode, DDEdge> {
	
	public final String FILE_NAME;
	private ControlFlowGraph cfg;
	
	public DataDependenceGraph(String javaFileName) {
		super(true);
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
		for (PDNode node: allVertices) {
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
            Enumeration<CFNode> cfNodes = cfg.enumerateAllVertices();
			while (cfNodes.hasMoreElements()) {
                CFNode node = cfNodes.nextElement();
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
            Enumeration<Edge<CFNode, CFEdge>> cfEdges = cfg.enumerateAllEdges();
			while (cfEdges.hasMoreElements()) {
                Edge<CFNode, CFEdge> ctrlEdge = cfEdges.nextElement();
				json.println("    {");
				String id = "e" + edgeCounter++;
				json.println("      \"id\": \"" + id + "\",");
				String src = ctrlNodes.get(ctrlEdge.source);
				json.println("      \"source\": \"" + src + "\",");
				String trgt = ctrlNodes.get(ctrlEdge.target);
				json.println("      \"target\": \"" + trgt + "\",");
				json.println("      \"type\": \"Control\",");
				json.println("      \"label\": \"" + ctrlEdge.label.type + "\"");
				json.println("    },");
			}
			for (Edge<PDNode, DDEdge> dataEdge: allEdges) {
				json.println("    {");
				String id = "e" + edgeCounter++;
				json.println("      \"id\": \"" + id + "\",");
				String src = dataNodes.get(dataEdge.source);
				json.println("      \"source\": \"" + src + "\",");
				String trgt = dataNodes.get(dataEdge.target);
				json.println("      \"target\": \"" + trgt + "\",");
				json.println("      \"type\": \"" + dataEdge.label.type + "\",");
				json.println("      \"label\": \"" + dataEdge.label.var + "\"");
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
			dot.println("digraph " + filename + "_PDG_DATA {");
            dot.println("  // graph-vertices");
			Map<CFNode, String> ctrlNodes = new HashMap<>();
			Map<PDNode, String> dataNodes = new HashMap<>();
			int nodeCounter = 1;
            Enumeration<CFNode> cfNodes = cfg.enumerateAllVertices();
			while (cfNodes.hasMoreElements()) {
                CFNode node = cfNodes.nextElement();
				String name = "v" + nodeCounter++;
				ctrlNodes.put(node, name);
				PDNode pdNode = (PDNode) node.getProperty("pdnode");
				if (pdNode != null)
					dataNodes.put(pdNode, name);
				StringBuilder label = new StringBuilder("  [label=\"");
				if (node.getLineOfCode() > 0)
					label.append(node.getLineOfCode()).append(":  ");
				label.append(StringUtils.escape(node.getCode())).append("\"];");
				dot.println("  " + name + label.toString());
			}
			dot.println("  // graph-edges");
            Enumeration<Edge<CFNode, CFEdge>> cfEdges = cfg.enumerateAllEdges();
			while (cfEdges.hasMoreElements()) {
                Edge<CFNode, CFEdge> ctrlEdge = cfEdges.nextElement();
				String src = ctrlNodes.get(ctrlEdge.source);
				String trg = ctrlNodes.get(ctrlEdge.target);
				if (ctrlEdgeLabels)
					dot.println("  " + src + " -> " + trg + 
                                "  [arrowhead=empty, color=gray, style=dashed, label=\"" + ctrlEdge.label.type + "\"];");
				else
					dot.println("  " + src + " -> " + trg + "  [arrowhead=empty, color=gray, style=dashed];");
			}
			for (Edge<PDNode, DDEdge> dataEdge: allEdges) {
				String src = dataNodes.get(dataEdge.source);
				String trg = dataNodes.get(dataEdge.target);
				dot.println("   " + src + " -> " + trg + "   [style=bold, label=\" (" + dataEdge.label.var + ")\"];");
			}
			dot.println("  // end-of-graph\n}");
		} catch (UnsupportedEncodingException ex) {
			System.err.println(ex);
		}
		System.out.println("DDS of PDG exported to: " + filepath);
	}
}
