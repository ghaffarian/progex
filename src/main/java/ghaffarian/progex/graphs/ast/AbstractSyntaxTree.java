/*** In The Name of Allah ***/
package ghaffarian.progex.graphs.ast;

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
 * Abstract Syntax Tree (AST).
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class AbstractSyntaxTree extends AbstractProgramGraph<ASNode, ASEdge> {
    
    public final String filePath;
    public final String fileName;
    public final ASNode root;
	
    /**
     * Construct a new empty Abstract Syntax Tree, 
     * for the given source-code file-path.
     */
	public AbstractSyntaxTree(String path) {
		super();
        this.filePath = path;
        this.fileName = new File(path).getName();
        this.root = new ASNode(ASNode.Type.ROOT);
        properties.put("label", "AST of " + fileName);
        properties.put("type", "Abstract Syntax Tree (AST)");
        addVertex(root);
	}
    
    /**
     * Copy constructor.
     */
    public AbstractSyntaxTree(AbstractSyntaxTree ast) {
        super(ast);
        this.root = ast.root;
        this.fileName = ast.fileName;
        this.filePath = ast.filePath;
    }
    
    @Override
    public void exportDOT(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = fileName.substring(0, fileName.lastIndexOf('.'));
        String filepath = outDir + filename + "-AST.dot";
        try (PrintWriter dot = new PrintWriter(filepath, "UTF-8")) {
            dot.println("digraph " + filename + "_AST {");
            dot.println("  // graph-vertices");
            Map<ASNode, String> nodeNames = new LinkedHashMap<>();
            int nodeCounter = 1;
            for (ASNode node : allVertices) {
                String name = "n" + nodeCounter++;
                nodeNames.put(node, name);
                StringBuilder label = new StringBuilder("  [label=\"");
                label.append(StringUtils.escape(node.toString())).append("\"];");
                dot.println("  " + name + label.toString());
            }
			dot.println("  // graph-edges");
            for (Edge<ASNode, ASEdge> edge : allEdges) {
                String src = nodeNames.get(edge.source);
                String trg = nodeNames.get(edge.target);
                dot.println("  " + src + " -> " + trg + ";");
            }
			dot.println("  // end-of-graph\n}");
        } catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
        }
		Logger.info("AST exported to: " + filepath);
    }

    @Override
    public void exportGML(String outDir) throws IOException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.lastIndexOf('.'));
		String filepath = outDir + filename + "-AST.gml";
		try (PrintWriter gml = new PrintWriter(filepath, "UTF-8")) {
			gml.println("graph [");
			gml.println("  directed 1");
			for (Map.Entry<String, String> property: properties.entrySet()) {
                switch (property.getKey()) {
                    case "directed":
                        continue;
                    default:
                        gml.println("  " + property.getKey() + " \"" + property.getValue() + "\"");
                }
            }
            gml.println("  file \"" + this.fileName + "\"\n");
            //
			Map<ASNode, Integer> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 0;
			for (ASNode node: allVertices) {
				gml.println("  node [");
				gml.println("    id " + nodeCounter);
				gml.println("    line " + node.getLineOfCode());
				gml.println("    type \"" + node.getType() + "\"");
                String code = node.getCode();
                code = StringUtils.isEmpty(code) ? node.getType().label : StringUtils.escape(code);
				gml.println("    label \"" + code + "\"");
                String normalized = node.getNormalizedCode();
                normalized = StringUtils.isEmpty(normalized) ? code : StringUtils.escape(normalized);
				gml.println("    normalized \"" + normalized + "\"");
				gml.println("  ]");
				nodeIDs.put(node, nodeCounter);
				++nodeCounter;
			}
            gml.println();
            //
			int edgeCounter = 0;
			for (Edge<ASNode, ASEdge> edge: allEdges) {
				gml.println("  edge [");
				gml.println("    id " + edgeCounter);
				gml.println("    source " + nodeIDs.get(edge.source));
				gml.println("    target " + nodeIDs.get(edge.target));
				gml.println("    label \"" + edge.label + "\"");
				gml.println("  ]");
				++edgeCounter;
			}
			gml.println("]");
		} catch (UnsupportedEncodingException ex) {
			Logger.error(ex);
		}
		Logger.info("AST exported to: " + filepath);
    }
	
    @Override
	public void exportJSON(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
		String filename = fileName.substring(0, fileName.indexOf('.'));
		String filepath = outDir + filename + "-AST.json";
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
			json.println("  \"nodes\": [");
            //
			Map<ASNode, Integer> nodeIDs = new LinkedHashMap<>();
			int nodeCounter = 0;
			for (ASNode node: allVertices) {
				json.println("    {");
				json.println("      \"id\": " + nodeCounter + ",");
				json.println("      \"line\": " + node.getLineOfCode() + ",");
				json.println("      \"type\": \"" + node.getType() + "\",");
                String code = node.getCode();
                code = StringUtils.isEmpty(code) ? node.getType().label : StringUtils.escape(code);
				json.println("      \"label\": \"" + code + "\",");
                String normalized = node.getNormalizedCode();
                normalized = StringUtils.isEmpty(normalized) ? code : StringUtils.escape(normalized);
				json.println("      \"normalized\": \"" + normalized + "\"");
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
			for (Edge<ASNode, ASEdge> edge: allEdges) {
				json.println("    {");
				json.println("      \"id\": " + edgeCounter + ",");
				json.println("      \"source\": " + nodeIDs.get(edge.source) + ",");
				json.println("      \"target\": " + nodeIDs.get(edge.target) + ",");
				json.println("      \"label\": \"\"");  // TODO: should be 'edge.label'; 
                // Java-AST-Builder uses Digraph::addEdge(V, V) which is addEdge(new Edge(V, null, V))!
                // Using a null edge label can have its use-cases, but in this case we need something like
                // Digraph::addDefaultEdge(V, V) which is addEdge(V, new E(), V) using a default constructor.
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
		Logger.info("AST exported to: " + filepath);
    }
}
