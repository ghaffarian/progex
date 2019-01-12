/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

import ghaffarian.graphs.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;
import ghaffarian.progex.utils.StringUtils;
import ghaffarian.nanologger.Logger;

/**
 * Control Dependence Graph.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class ControlDependenceGraph extends Digraph<PDNode, CDEdge> {
	
	public final String FILE_NAME;
	
	public ControlDependenceGraph(String javaFileName) {
		super();
		FILE_NAME = javaFileName;
	}
	
	/**
	 * Export this Control Dependence Subgraph (CDG) of PDG to specified file format.
	 */
	public void export(String format, String outDir) throws FileNotFoundException {
		switch (format.toLowerCase()) {
			case "dot":
				exportDOT(outDir);
				break;
			case "json":
				exportJSON(outDir);
				break;
		}
	}
	
	/**
	 * Export this Control Dependence Subgraph (CDG) of PDG to JSON format.
	 * The JSON file will be saved in current working directory. 
	 */
	public void exportJSON() throws FileNotFoundException {
        exportJSON(System.getProperty("user.dir"));
    }    
	
	/**
	 * Export this Control Dependence Subgraph (CDG) of PDG to JSON format.
	 * The JSON file will be saved inside the given directory.
	 */
	public void exportJSON(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-PDG-CTRL.json";
		try (PrintWriter json = new PrintWriter(filepath, "UTF-8")) {
			json.println("{\n  \"type\": \"PDG-CTRL\",");
			json.println("  \"file\": \"" + FILE_NAME + "\",");
			json.println("\n\n  \"nodes\": [");
			Map<PDNode, String> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 1;
			for (PDNode node: allVertices) {
				json.println("    {");
				String id = "n" + nodeCounter++;
				nodeIDs.put(node, id);
				json.println("      \"id\": \"" + id + "\",");
				json.println("      \"line\": \"" + node.getLineOfCode() + "\",");
				json.println("      \"code\": \"" + node.getCode().replace("\"", "\\\"") + "\"");
				json.println("    },");
			}
			json.println("  ],\n\n\n  \"edges\": [");
			int edgeCounter = 1;
			for (Edge<PDNode, CDEdge> edge: allEdges) {
				json.println("    {");
				String id = "e" + edgeCounter++;
				json.println("      \"id\": \"" + id + "\",");
				String src = nodeIDs.get(edge.source);
				json.println("      \"source\": \"" + src + "\",");
				String trgt = nodeIDs.get(edge.target);
				json.println("      \"target\": \"" + trgt + "\",");
				json.println("      \"label\": \"" + edge.label.type + "\"");
				json.println("    },");
			}
			json.println("  ]\n}");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("CDS of PDG exported to: " + filepath);
	}
	
	/**
	 * Export this Control Dependence Subgraph (CDG) of PDG to DOT format.
	 * The DOT file will be saved in current working directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
	public void exportDOT() throws FileNotFoundException {
        exportDOT(System.getProperty("user.dir"));
    }
    
	/**
	 * Export this Control Dependence Subgraph (CDG) of PDG to DOT format.
	 * The DOT file will be saved inside the given directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
	public void exportDOT(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-PDG-CTRL.dot";
		try (PrintWriter dot = new PrintWriter(filepath, "UTF-8")) {
			dot.println("digraph " + filename + "_PDG_CTRL {");
            dot.println("  // graph-vertices");
			Map<PDNode, String> nodeNames = new LinkedHashMap<>();
			int nodeCounter = 1;
			for (PDNode node: allVertices) {
				String name = "v" + nodeCounter++;
				nodeNames.put(node, name);
				StringBuilder label = new StringBuilder("  [label=\"");
				if (node.getLineOfCode() > 0)
					label.append(node.getLineOfCode()).append(":  ");
				label.append(StringUtils.escape(node.getCode())).append("\"];");
				dot.println("  " + name + label.toString());
			}
			dot.println("  // graph-edges");
			for (Edge<PDNode, CDEdge> edge: allEdges) {
				String src = nodeNames.get(edge.source);
				String trg = nodeNames.get(edge.target);
				if (edge.label.type.equals(CDEdge.Type.EPSILON))
					dot.println("  " + src + " -> " + trg + ";");
				else
					dot.println("  " + src + " -> " + trg + "  [label=\"" + edge.label.type + "\"];");
			}
			dot.println("  // end-of-graph\n}");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("CDS of PDG exported to: " + filepath);
	}
}
