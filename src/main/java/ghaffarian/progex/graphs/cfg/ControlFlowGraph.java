/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.cfg;

import ghaffarian.graphs.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ghaffarian.progex.utils.StringUtils;
import ghaffarian.nanologger.Logger;

/**
 * Control Flow Graph (CFG).
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class ControlFlowGraph extends Digraph<CFNode, CFEdge> {
	
	private String pkgName;
	public final String FILE_NAME;
	private List<CFNode> methodEntries;

	public ControlFlowGraph(String name) {
		super();
		pkgName = "";
		this.FILE_NAME = name;
		methodEntries = new ArrayList<>();
	}
	
	public void setPackage(String pkg) {
		pkgName = pkg;
	}
	
	public String getPackage() {
		return pkgName;
	}
	
	public void addMethodEntry(CFNode entry) {
		methodEntries.add(entry);
	}
	
	public CFNode[] getAllMethodEntries() {
		return methodEntries.toArray(new CFNode[methodEntries.size()]);
	}
	
	/**
	 * Export this Control Flow Graph (CFG) to specified file format.
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
	 * Export this Control Flow Graph (CFG) to JSON format.
	 * The JSON file will be saved in current working directory. 
	 */
	public void exportJSON() throws FileNotFoundException {
        exportJSON(System.getProperty("user.dir"));
    }    
	
	/**
	 * Export this Control Flow Graph (CFG) to JSON format.
	 * The JSON file will be saved inside the given directory path.
	 */
	public void exportJSON(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-CFG.json";
		try (PrintWriter json = new PrintWriter(filepath, "UTF-8")) {
			json.println("{\n  \"type\": \"CFG\",");
			json.println("  \"file\": \"" + FILE_NAME + "\",");
			json.println("\n\n  \"nodes\": [");
			Map<CFNode, String> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 1;
			for (CFNode node: allVertices) {
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
			for (Edge<CFNode, CFEdge> edge: allEdges) {
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
		Logger.info("CFG exported to: " + filepath);
	}
	
	/**
	 * Export this Control Flow Graph (CFG) to DOT format.
	 * The DOT file will be saved in current working directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
	public void exportDOT() throws FileNotFoundException {
        exportDOT(System.getProperty("user.dir"));
    }

    /**
	 * Export this Control Flow Graph (CFG) to DOT format.
	 * The DOT file will be saved inside the given directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
	public void exportDOT(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = FILE_NAME.substring(0, FILE_NAME.indexOf('.'));
		String filepath = outDir + filename + "-CFG.dot";
		try (PrintWriter dot = new PrintWriter(filepath, "UTF-8")) {
			dot.println("digraph " + filename + "_CFG {");
            dot.println("  // graph-vertices");
			Map<CFNode, String> nodeNames = new LinkedHashMap<>();
			int nodeCounter = 1;
			for (CFNode node: allVertices) {
				String name = "v" + nodeCounter++;
				nodeNames.put(node, name);
				StringBuilder label = new StringBuilder("  [label=\"");
				if (node.getLineOfCode() > 0)
					label.append(node.getLineOfCode()).append(":  ");
				label.append(StringUtils.escape(node.getCode())).append("\"];");
				dot.println("  " + name + label.toString());
			}
			dot.println("  // graph-edges");
			for (Edge<CFNode, CFEdge> edge: allEdges) {
				String src = nodeNames.get(edge.source);
				String trg = nodeNames.get(edge.target);
				if (edge.label.type.equals(CFEdge.Type.EPSILON))
					dot.println("  " + src + " -> " + trg + ";");
				else
					dot.println("  " + src + " -> " + trg + "  [label=\"" + edge.label.type + "\"];");
			}
			dot.println("  // end-of-graph\n}");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("CFG exported to: " + filepath);
	}	
}
