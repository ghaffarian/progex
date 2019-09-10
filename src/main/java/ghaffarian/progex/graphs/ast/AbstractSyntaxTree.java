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
    
    public final String FILE_PATH;
    
    public final ASNode ROOT;
	
    /**
     * Construct a new empty Abstract Syntax Tree, 
     * for the given source-code file-path.
     */
	public AbstractSyntaxTree(String path) {
		super();
        FILE_PATH = path;
        ROOT = new ASNode(ASNode.Type.ROOT);
        addVertex(ROOT);
	}
    
    /**
     * Copy constructor.
     */
    public AbstractSyntaxTree(AbstractSyntaxTree ast) {
        super(ast);
        ROOT = ast.ROOT;
        FILE_PATH = ast.FILE_PATH;
    }
    
    @Override
    public void exportDOT(String outDir) throws FileNotFoundException {
        if (!outDir.endsWith(File.separator))
            outDir += File.separator;
        File outDirFile = new File(outDir);
        outDirFile.mkdirs();
        String filename = new File(FILE_PATH).getName();
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
        throw new UnsupportedOperationException("AST export to GML not implemented yet!");
    }
    
    @Override
	public void exportJSON(String outDir) throws FileNotFoundException {
        throw new UnsupportedOperationException("AST export to JSON not implemented yet!");
    }
}
