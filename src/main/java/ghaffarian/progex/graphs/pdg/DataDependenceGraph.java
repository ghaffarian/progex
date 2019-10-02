/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

import ghaffarian.graphs.Edge;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import ghaffarian.progex.graphs.cfg.CFEdge;
import ghaffarian.progex.graphs.cfg.CFNode;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.utils.StringUtils;
import ghaffarian.nanologger.Logger;
import ghaffarian.progex.graphs.AbstractProgramGraph;
import java.io.IOException;
import java.util.Iterator;

/**
 * Data Dependence Graph.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class DataDependenceGraph extends AbstractProgramGraph<PDNode, DDEdge> {
	
	public final String fileName;
	private ControlFlowGraph cfg;
	
	public DataDependenceGraph(String fileName) {
		super();
		cfg = null;
		this.fileName = fileName;
        properties.put("label", "DDG of " + fileName);
        properties.put("type", "Data Dependence Graph (DDG)");
	}
	
	public void attachCFG(ControlFlowGraph cfg) {
		this.cfg = cfg;
	}
	
	public ControlFlowGraph getCFG() {
		return cfg;
	}
	
	public void printAllNodesUseDefs(Logger.Level level) {
		for (PDNode node: allVertices) {
			Logger.log(node, level);
			Logger.log("  + USEs: " + Arrays.toString(node.getAllUSEs()), level);
			Logger.log("  + DEFs: " + Arrays.toString(node.getAllDEFs()) + "\n", level);
		}
	}

    @Override
    public void exportDOT(String outDir) throws IOException {
        exportDOT(outDir, true);
    }

    /**
	 * Export this Data Dependence Subgraph (DDG) of PDG to DOT file format.
     * The 2nd parameter determines whether the attached CFG (if any) should also be exported.
	 * The DOT file will be saved inside the given directory.
	 * The DOT format is mainly aimed for visualization purposes.
	 */
    public void exportDOT(String outDir, boolean ctrlEdgeLabels) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
		String filepath = outDir + filename + "-PDG-DATA.dot";
		try (PrintWriter dot = new PrintWriter(filepath, "UTF-8")) {
			dot.println("digraph " + filename + "_PDG_DATA {");
            dot.println("  // graph-vertices");
			Map<CFNode, String> ctrlNodes = new HashMap<>();
			Map<PDNode, String> dataNodes = new HashMap<>();
			int nodeCounter = 1;
            Iterator<CFNode> cfNodes = cfg.allVerticesIterator();
			while (cfNodes.hasNext()) {
                CFNode node = cfNodes.next();
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
            Iterator<Edge<CFNode, CFEdge>> cfEdges = cfg.allEdgesIterator();
			while (cfEdges.hasNext()) {
                Edge<CFNode, CFEdge> ctrlEdge = cfEdges.next();
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
			Logger.error(ex);
		}
		Logger.info("DDS of PDG exported to: " + filepath);
	}
	
    @Override
    public void exportGML(String outDir) throws IOException {
        throw new UnsupportedOperationException("DDG export to GML not implemented yet!");
    }

    @Override
	public void exportJSON(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
		String filepath = outDir + filename + "-PDG-DATA.json";
		try (PrintWriter json = new PrintWriter(filepath, "UTF-8")) {
			json.println("{\n  \"directed\": true,");
			json.println("  \"multigraph\": true,");
			for (Map.Entry<String, String> property: properties.entrySet()) {
                switch (property.getKey()) {
                    case "directed":
                        continue;
                    default:
                        json.println("  \"" + property.getKey() + "\": \"" + property.getValue() + "\",");
                }
            }
			json.println("  \"file\": \"" + fileName + "\",\n");
            //
			json.println("  \"nodes\": [");
			Map<CFNode, Integer> ctrlNodes = new HashMap<>();
			Map<PDNode, Integer> dataNodes = new HashMap<>();
            Iterator<CFNode> cfNodes = cfg.allVerticesIterator();
			int nodeCounter = 0;
			while (cfNodes.hasNext()) {
                CFNode node = cfNodes.next();
				json.println("    {");
				json.println("      \"id\": " + nodeCounter + ",");
				json.println("      \"line\": " + node.getLineOfCode() + ",");
				PDNode pdNode = (PDNode) node.getProperty("pdnode");
				if (pdNode != null) {
                    json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\",");
					dataNodes.put(pdNode, nodeCounter);
					json.println("      \"defs\": " + StringUtils.toJsonArray(pdNode.getAllDEFs()) + ",");
					json.println("      \"uses\": " + StringUtils.toJsonArray(pdNode.getAllUSEs()));
				} else
                    json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\"");
				ctrlNodes.put(node, nodeCounter);
				++nodeCounter;
                if (nodeCounter == cfg.vertexCount())
                    json.println("    }");
                else
                    json.println("    },");
			}
            //
			json.println("  ],\n\n  \"edges\": [");
			int edgeCounter = 0;
            Iterator<Edge<CFNode, CFEdge>> cfEdges = cfg.allEdgesIterator();
			while (cfEdges.hasNext()) {
                Edge<CFNode, CFEdge> ctrlEdge = cfEdges.next();
				json.println("    {");
				json.println("      \"id\": " + edgeCounter + ",");
				json.println("      \"source\": " + ctrlNodes.get(ctrlEdge.source) + ",");
				json.println("      \"target\": " + ctrlNodes.get(ctrlEdge.target) + ",");
				json.println("      \"type\": \"Control\",");
				json.println("      \"label\": \"" + ctrlEdge.label.type + "\"");
                json.println("    },");
				++edgeCounter;
			}
			for (Edge<PDNode, DDEdge> dataEdge: allEdges) {
				json.println("    {");
				json.println("      \"id\": " + edgeCounter + ",");
				json.println("      \"source\": " + dataNodes.get(dataEdge.source) + ",");
				json.println("      \"target\": " + dataNodes.get(dataEdge.target) + ",");
				json.println("      \"type\": \"" + dataEdge.label.type + "\",");
				json.println("      \"label\": \"" + dataEdge.label.var + "\"");
				++edgeCounter;
                if (edgeCounter == cfg.edgeCount() + allEdges.size())
                    json.println("    }");
                else
                    json.println("    },");
			}
			json.println("  ]\n}");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("DDS of PDG exported to: " + filepath);
	}
}
