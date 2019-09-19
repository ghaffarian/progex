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
        String filename = new File(filePath).getName();
        filename = filename.substring(0, filename.lastIndexOf('.'));
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
			gml.println("  multigraph 1");
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
				gml.println("    label \"" + (StringUtils.isEmpty(code) ? node.getType() : StringUtils.escape(code)) + "\"");
				gml.println("    normalized \"" + StringUtils.escape(node.getNormalizedCode())+ "\"");
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
        throw new UnsupportedOperationException("AST export to JSON not implemented yet!");
    }
}
