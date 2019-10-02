/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.pdg;

import ghaffarian.graphs.Edge;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;
import ghaffarian.progex.utils.StringUtils;
import ghaffarian.nanologger.Logger;
import ghaffarian.progex.graphs.AbstractProgramGraph;
import java.io.IOException;
import java.util.Map.Entry;

/**
 * Control Dependence Graph.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class ControlDependenceGraph extends AbstractProgramGraph<PDNode, CDEdge> {
	
	public final String fileName;
	
	public ControlDependenceGraph(String fileName) {
		super();
		this.fileName = fileName;
        properties.put("label", "CDG of " + fileName);
        properties.put("type", "Control Dependence Graph (CDG)");
	}
	
    @Override
	public void exportDOT(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
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

    @Override
    public void exportGML(String outDir) throws IOException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
		String filepath = outDir + filename + "-PDG-CTRL.gml";
		try (PrintWriter gml = new PrintWriter(filepath, "UTF-8")) {
			gml.println("graph [");
			gml.println("  directed 1");
			for (Entry<String, String> property: properties.entrySet()) {
                switch (property.getKey()) {
                    case "directed":
                        continue;
                    default:
                        gml.println("  " + property.getKey() + " \"" + property.getValue() + "\"");
                }
            }
            gml.println("  file \"" + this.fileName + "\"\n");
            //
			Map<PDNode, Integer> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 0;
			for (PDNode node: allVertices) {
				gml.println("  node [");
				gml.println("    id " + nodeCounter);
				gml.println("    line " + node.getLineOfCode());
				gml.println("    label \"" + StringUtils.escape(node.getCode()) + "\"");
				gml.println("  ]");
				nodeIDs.put(node, nodeCounter);
				++nodeCounter;
			}
            gml.println();
            //
			int edgeCounter = 0;
			for (Edge<PDNode, CDEdge> edge: allEdges) {
				gml.println("  edge [");
				gml.println("    id " + edgeCounter);
				gml.println("    source " + nodeIDs.get(edge.source));
				gml.println("    target " + nodeIDs.get(edge.target));
				gml.println("    label \"" + edge.label.type + "\"");
				gml.println("  ]");
				++edgeCounter;
			}
			gml.println("]");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("CDS of PDG exported to: " + filepath);
    }
	
    @Override
	public void exportJSON(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
		String filepath = outDir + filename + "-PDG-CTRL.json";
		try (PrintWriter json = new PrintWriter(filepath, "UTF-8")) {
			json.println("{\n  \"directed\": true,");
			for (Entry<String, String> property: properties.entrySet()) {
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
			Map<PDNode, Integer> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 0;
			for (PDNode node: allVertices) {
				json.println("    {");
				json.println("      \"id\": " + nodeCounter + ",");
				json.println("      \"line\": " + node.getLineOfCode() + ",");
				json.println("      \"label\": \"" + StringUtils.escape(node.getCode()) + "\"");
				nodeIDs.put(node, nodeCounter);
				++nodeCounter;
                if (nodeCounter == allVertices.size())
                    json.println("    }");
                else
                    json.println("    },");
			}
            //
			json.println("  ],\n\n  \"edges\": [");
			int edgeCounter = 0;
			for (Edge<PDNode, CDEdge> edge: allEdges) {
				json.println("    {");
				json.println("      \"id\": " + edgeCounter + ",");
				json.println("      \"source\": " + nodeIDs.get(edge.source) + ",");
				json.println("      \"target\": " + nodeIDs.get(edge.target) + ",");
				json.println("      \"label\": \"" + edge.label.type + "\"");
				++edgeCounter;
                if (edgeCounter == allEdges.size())
                    json.println("    }");
                else
                    json.println("    },");
			}
			json.println("  ]\n}");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("CDS of PDG exported to: " + filepath);
	}
}
