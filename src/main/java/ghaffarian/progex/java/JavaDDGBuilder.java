/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import ghaffarian.graphs.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import ghaffarian.progex.graphs.cfg.CFNode;
import ghaffarian.progex.graphs.cfg.CFPathTraversal;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.graphs.pdg.DDEdge;
import ghaffarian.progex.graphs.pdg.DataDependenceGraph;
import ghaffarian.progex.graphs.pdg.PDNode;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;
import ghaffarian.nanologger.Logger;

/**
 * Data Dependence Graph (DDG) builder for Java programs.
 * The DDG is actually a subgraph of the Program Dependence Graph (PDG).
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaDDGBuilder {
	
	// Just for debugging
	private static String currentFile;
	
	// NOTE: This doesn't handle duplicate class names;
	//       yet assuming no duplicate class names is fair enough.
	//       To handle that, we should use 'Map<String, List<JavaClass>>'
	private static Map<String, JavaClass> allClassInfos;

    private static Map<String, List<MethodDefInfo>> methodDEFs;
	
	public static DataDependenceGraph[] buildForAll(File[] files) throws IOException {
		// Parse all Java source files
		Logger.info("Parsing all source files ... ");
		ParseTree[] parseTrees = new ParseTree[files.length];
		for (int i = 0; i < files.length; ++i) {
			InputStream inFile = new FileInputStream(files[i]);
			ANTLRInputStream input = new ANTLRInputStream(inFile);
			JavaLexer lexer = new JavaLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			JavaParser parser = new JavaParser(tokens);
			parseTrees[i] = parser.compilationUnit();
		}
		Logger.info("Done.");
		
		// Extract the information of all given Java classes
		Logger.info("\nExtracting class-infos ... ");
		allClassInfos = new HashMap<>();
		List<JavaClass[]> filesClasses = new ArrayList<>();
		for (int i = 0; i < files.length; ++i) {
			List<JavaClass> classesList = JavaClassExtractor.extractInfo(files[i].getPath(), parseTrees[i]);
			filesClasses.add(classesList.toArray(new JavaClass[classesList.size()]));
			for (JavaClass cls: classesList) 
				allClassInfos.put(cls.NAME, cls);
		}
		Logger.info("Done.");
		
		// Initialize method DEF information
		Logger.info("\nInitializing method-DEF infos ... ");
		methodDEFs = new HashMap<>();
		for (JavaClass[] classArray: filesClasses) {
			for (JavaClass cls : classArray) {
				for (JavaMethod mtd : cls.getAllMethods()) {
					List<MethodDefInfo> list = methodDEFs.get(mtd.NAME);
					if (list == null) {
						list = new ArrayList<>();
						list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
						methodDEFs.put(mtd.NAME, list);
					} else {
						list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
						// no need to do 'methodDEFs.put(...)' again
					}
				}
			}
		}
		Logger.info("Done.");
		
		// Analyze method DEF information for imported libraries
		analyzeImportsDEF(filesClasses);
		
		// Iteratively, extract USE-DEF info for all program statements ...
		DataDependenceGraph[] ddgs = new DataDependenceGraph[files.length];
		for (int i = 0; i < ddgs.length; ++i)
			ddgs[i] = new DataDependenceGraph(files[i].getName());
		//
		Map<ParserRuleContext, Object>[] pdNodes = new Map[parseTrees.length];
		for (int i = 0; i < parseTrees.length; ++i)
			pdNodes[i] = new IdentityHashMap<>();
		//
		Logger.info("\nIterative DEF-USE analysis ... ");
		boolean changed;
		int iteration = 0;
		do {
			++iteration;
			changed = false;
			for (int i = 0; i < files.length; ++i) {
				currentFile = files[i].getName();
				DefUseVisitor defUse = new DefUseVisitor(iteration, filesClasses.get(i), ddgs[i], pdNodes[i]);
				defUse.visit(parseTrees[i]);
				changed |= defUse.changed;
			}
			Logger.debug("Iteration #" + iteration + ": " + (changed ? "CHANGED" : "NO-CHANGE"));
			Logger.debug("\n========================================\n");
		} while (changed);
		Logger.info("Done.");
		
		// Build control-flow graphs for all Java files including the extracted DEF-USE info ...
		Logger.info("\nExtracting CFGs ... ");
		ControlFlowGraph[] cfgs = new ControlFlowGraph[files.length];
		for (int i = 0; i < files.length; ++i) 
			cfgs[i] = JavaCFGBuilder.build(files[i].getName(), parseTrees[i], "pdnode", pdNodes[i]);
		Logger.info("Done.");
		
		// Finally, traverse all control-flow paths and draw data-flow dependency edges ...
		Logger.info("\nAdding data-flow edges ... ");
		for (int i = 0; i < files.length; ++i) {
			addDataFlowEdges(cfgs[i], ddgs[i]);
			ddgs[i].attachCFG(cfgs[i]);
		}
		Logger.info("Done.\n");
		
		return ddgs;
	}
	
	/**
	 * Analyze method DEF information for imported libraries. 
	 */
	private static void analyzeImportsDEF(List<JavaClass[]> filesClasses) throws IOException {
		// Extract the import strings
		Logger.info("\nExtracting & Parsing imports ... ");
		Set<String> rawImports = new LinkedHashSet<>();
		rawImports.add("java.lang.*");
		for (JavaClass[] classes: filesClasses) 
			for (JavaClass cls: classes) 
				for (String qualifiedName: cls.IMPORTS)
					rawImports.add(qualifiedName);
		// NOTE: imports can contain specific or whole package imports;
		//       so, we need to extract specific ZIP-entries for all imports.
		// For each import find the ZIP-entries, 
		// and extract the ParseTree and JavaClass[] infos
		ZipFile zip = new ZipFile("res/jdk7-src.zip");
		Set<String> imports = new LinkedHashSet<>();
		List<ParseTree> importsParseTrees = new ArrayList<>();
		List<JavaClass[]> importsClassInfos = new ArrayList<>();
		for (String qualifiedName: rawImports) {
			if (qualifiedName.endsWith(".*")) {
				for (ZipEntry ent: getPackageEntries(zip, qualifiedName)) {
					if (imports.add(ent.getName())) {
						ANTLRInputStream input = new ANTLRInputStream(zip.getInputStream(ent));
						JavaLexer lexer = new JavaLexer(input);
						CommonTokenStream tokens = new CommonTokenStream(lexer);
						JavaParser parser = new JavaParser(tokens);
						ParseTree tree = parser.compilationUnit();
						//
						importsParseTrees.add(tree);
						List<JavaClass> list = JavaClassExtractor.extractInfo("src.zip/" + ent.getName(), tree);
						importsClassInfos.add(list.toArray(new JavaClass[list.size()]));
						for (JavaClass cls: list) 
							allClassInfos.put(cls.NAME, cls);
					}
				}
			} else {
				String path = qualifiedName.replace('.', '/') + ".java";
				if (imports.add(path)) {
					ZipEntry entry = zip.getEntry(path);
					if (entry == null) {
						imports.remove(path);
						continue;
					}
					//
					ANTLRInputStream input = new ANTLRInputStream(zip.getInputStream(entry));
					JavaLexer lexer = new JavaLexer(input);
					CommonTokenStream tokens = new CommonTokenStream(lexer);
					JavaParser parser = new JavaParser(tokens);
					ParseTree tree = parser.compilationUnit();
					//
					importsParseTrees.add(tree);
					List<JavaClass> list = JavaClassExtractor.extractInfo("src.zip/" + path, tree);
					importsClassInfos.add(list.toArray(new JavaClass[list.size()]));
				}
			}
		}
		Logger.info("Done.");
		// 
		for (JavaClass[] classArray: importsClassInfos) {
			for (JavaClass cls : classArray) {
				for (JavaMethod mtd : cls.getAllMethods()) {
					List<MethodDefInfo> list = methodDEFs.get(mtd.NAME);
					if (list == null) {
						list = new ArrayList<>();
						list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
						methodDEFs.put(mtd.NAME, list);
					} else {
						list.add(new MethodDefInfo(mtd.RET_TYPE, mtd.NAME, cls.PACKAGE, cls.NAME, mtd.ARG_TYPES));
						// no need to do 'methodDEFs.put(...)' again
					}
				}
			}
		}
		//
		Logger.info("\nAnalyzing imports DEF-USE ... ");
		Map<ParserRuleContext, Object> dummyMap = new HashMap<>();
		DataDependenceGraph dummyDDG = new DataDependenceGraph("Dummy.java");
		boolean changed;
		int iteration = 0;
		do {
			++iteration;
			changed = false;
			int i = 0;
			for (String imprt: imports) {
				currentFile = "src.zip/" + imprt;
				DefUseVisitor defUse = new DefUseVisitor(iteration, importsClassInfos.get(i), dummyDDG, dummyMap);
				defUse.visit(importsParseTrees.get(i));
				changed |= defUse.changed;
				++i;
			}
		} while (changed);
		Logger.info("Done.");
		//
		dummyDDG = null;
		dummyMap.clear();
	}
	
	/**
	 * Returns an array of ZipEntry for a given wildcard package import.
	 */
	private static ZipEntry[] getPackageEntries(ZipFile zip, String qualifiedName) {
		// qualifiedName ends with ".*"
		String pkg = qualifiedName.replace('.', '/').substring(0, qualifiedName.length() - 1);
		int slashCount = countSlashes(pkg);
		ArrayList<ZipEntry> entries = new ArrayList<>();
		Enumeration<? extends ZipEntry> zipEntries = zip.entries();
		while (zipEntries.hasMoreElements()) {
			ZipEntry entry = zipEntries.nextElement();
			if (entry.getName().startsWith(pkg) 
					&& !entry.isDirectory()
					&& slashCount == countSlashes(entry.getName())) {
				entries.add(entry);
			}
		}
		return entries.toArray(new ZipEntry[entries.size()]);
	}
	
	/**
	 * Returns the number of forward-slash ('/') characters in a given string.
	 */
	private static int countSlashes(String str) {
		int slashCount = 0;
		for (char chr: str.toCharArray())
			if (chr == '/')
				++slashCount;
		return slashCount;
	}
	
	/**
	 * Traverses each CFG and uses the extracted DEF-USE info 
	 * to add Flow-dependence edges to the corresponding DDG.
	 */
	private static void addDataFlowEdges(ControlFlowGraph cfg, DataDependenceGraph ddg) {
		Set<CFNode> visitedDefs = new LinkedHashSet<>();
		for (CFNode entry: cfg.getAllMethodEntries()) {
			visitedDefs.clear();
			CFPathTraversal defTraversal = new CFPathTraversal(cfg, entry);
			while (defTraversal.hasNext()) {
				CFNode defCFNode = defTraversal.next();
				if (!visitedDefs.add(defCFNode)) {
					defTraversal.continueNextPath();
					continue;
				}
				PDNode defNode = (PDNode) defCFNode.getProperty("pdnode");
				if (defNode == null) {
					//Logger.debug("No PDNode: " + defCFNode);
					continue;
				}
				if (defNode.getAllDEFs().length == 0) 
					continue;
				// first add any self-flows of this node
				for (String flow: defNode.getAllSelfFlows()) {
					ddg.addEdge(new Edge<>(defNode, new DDEdge(DDEdge.Type.FLOW, flow), defNode));
				}
				// now traverse the CFG for any USEs till a DEF
				Set<CFNode> visitedUses = new LinkedHashSet<>();
				for (String def: defNode.getAllDEFs()) {
					CFPathTraversal useTraversal = new CFPathTraversal(cfg, defCFNode);
					visitedUses.clear();
					CFNode useCFNode = useTraversal.next(); // skip start node
					visitedUses.add(useCFNode);
					while (useTraversal.hasNext()) {
						useCFNode = useTraversal.next();
						PDNode useNode = (PDNode) useCFNode.getProperty("pdnode");
						if (useNode == null) {
							//Logger.debug("No PDNode: " + useCFNode);
							continue;
						}
						if (useNode.hasDEF(def))
							useTraversal.continueNextPath(); // no need to continue this path
						if (!visitedUses.add(useCFNode))
							useTraversal.continueNextPath(); // no need to continue this path
						else 
							if (useNode.hasUSE(def))
								ddg.addEdge(new Edge<>(defNode, new DDEdge(DDEdge.Type.FLOW, def), useNode));
					}
				}
			}
		}
	}
	
	/**
	 * Visitor class which performs iterative DEF-USE analysis for all program statements.
	 */
	private static class DefUseVisitor extends JavaBaseVisitor<String> {
		
		private static final int PARAM = 1;
		private static final int FIELD = 101;
		private static final int LOCAL = 202;
		private static final int OUTER = 303;
		
		private int iteration;
		private boolean changed;
		private boolean analysisVisit;
		private JavaClass[] classInfos;
		private DataDependenceGraph ddg;
		private Set<String> defList, useList, selfFlowList;
		private Map<ParserRuleContext, Object> pdNodes;
		private Deque<JavaClass> activeClasses;
		private MethodDefInfo methodDefInfo;
		private JavaField[] methodParams;
		private List<JavaField> localVars;
		
		public DefUseVisitor(int iter, JavaClass[] classInfos, 
				DataDependenceGraph ddg, Map<ParserRuleContext, Object> pdNodes) {
			Logger.debug("FILE IS: " + currentFile);
			this.ddg = ddg;
			changed = false;
			iteration = iter;
			analysisVisit = false;
			this.pdNodes = pdNodes;
			this.classInfos = classInfos;
			defList = new LinkedHashSet<>();
			useList = new LinkedHashSet<>();
			selfFlowList = new LinkedHashSet<>();
			activeClasses = new ArrayDeque<>();
			methodDefInfo = null;
			methodParams = new JavaField[0];
			localVars = new ArrayList<>();
		}
		
		private void analyseDefUse(PDNode node, ParseTree expression) {
			Logger.debug("--- ANALYSIS ---");
			Logger.debug(node.toString());
			analysisVisit = true;
			String expr = visit(expression);
			Logger.debug(expr);
			//
			StringBuilder locVarsStr = new StringBuilder(256);
			locVarsStr.append("LOCAL VARS = [");
			for (JavaField lv: localVars)
				locVarsStr.append(lv.TYPE).append(' ').append(lv.NAME).append(", ");
			locVarsStr.append("]");
			Logger.debug(locVarsStr.toString());
			//
			if (isUsableExpression(expr)) {
				useList.add(expr);
				Logger.debug("USABLE");
			}
			analysisVisit = false;
			Logger.debug("Changed = " + changed);
			Logger.debug("DEFs = " + Arrays.toString(node.getAllDEFs()));
			Logger.debug("USEs = " + Arrays.toString(node.getAllUSEs()));
			for (String def: defList) {
				int status = isDefined(def);
				if (status > -1) {
					if (status < 100) {
						methodDefInfo.setArgDEF(status, true);
						Logger.debug("Method defines argument #" + status);
					} else if (status == FIELD) {
						methodDefInfo.setStateDEF(true);
						if (def.startsWith("this."))
							def = def.substring(5);
						def = "$THIS." + def;
						Logger.debug("Method defines object state.");
					}
					changed |= node.addDEF(def);
				}
				else
					Logger.debug(def + " is not defined!");
			}
			Logger.debug("Changed = " + changed);
			Logger.debug("DEFs = " + Arrays.toString(node.getAllDEFs()));
			//
			for (String use: useList) {
				int status = isDefined(use);
				if (status > -1) {
					if (status == FIELD) {
						if (use.startsWith("this."))
							use = use.substring(5);
						use = "$THIS." + use;
					}
					changed |= node.addUSE(use);
				} else
					Logger.debug(use + " is not defined!");
			}
			Logger.debug("Changed = " + changed);
			Logger.debug("USEs = " + Arrays.toString(node.getAllUSEs()));
			//
			for (String flow: selfFlowList) {
				int status = isDefined(flow);
				if (status > -1) {
					if (status == FIELD) {
						if (flow.startsWith("this."))
							flow = flow.substring(5);
						flow = "$THIS." + flow;
					}
					changed |= node.addSelfFlow(flow);
				} else
					Logger.debug(flow + " is not defined!");
			}
			Logger.debug("Changed = " + changed);
			Logger.debug("SELF_FLOWS = " + Arrays.toString(node.getAllSelfFlows()));
			defList.clear();
			useList.clear();
			selfFlowList.clear();
			Logger.debug("----------------");
		}
		
		/**
		 * Check if a given symbol is a defined variable.
		 * This returns -1 if the symbol is not defined; otherwise, 
		 * it returns 101 if the symbol is a class field,
		 * or returns 202 if the symbol is a local variable,
		 * or returns 303 if the symbol is an outer class field,
		 * or if the symbol is a method parameter, returns the index of the parameter.
		 */
		private int isDefined(String id) {
			for (int i = 0; i < methodParams.length; ++i)
				if (methodParams[i].NAME.equals(id))
					return i;
			for (JavaField local: localVars)
				if (local.NAME.equals(id))
					return LOCAL;
			if (id.startsWith("this."))
				id = id.substring(5);
			for (JavaField field: activeClasses.peek().getAllFields())
				if (field.NAME.equals(id))
					return FIELD;
			for (JavaClass cls: activeClasses)
				for (JavaField field: cls.getAllFields())
					if (field.NAME.equals(id))
						return OUTER;
			return -1;
		}
		
		/**
		 * Return type of a given symbol.
		 * Returns null if symbol is not found.
		 */
		private String getType(String id) {
			if (isUsableExpression(id)) {
				for (JavaField param: methodParams)
					if (param.NAME.equals(id))
						return param.TYPE;
				for (JavaField local: localVars)
					if (local.NAME.equals(id))
						return local.TYPE;
				if (id.startsWith("this."))
					id = id.substring(4);
				for (JavaField field: activeClasses.peek().getAllFields())
					if (field.NAME.equals(id))
						return field.TYPE;
				for (JavaClass cls: activeClasses)
					for (JavaField field: cls.getAllFields())
						if (field.NAME.equals(id))
							return field.TYPE;
				Logger.debug("getType(" + id + ") : is USABLE but NOT DEFINED");
				return null;
			} else {
                Logger.debug("getType(" + id + ") : is NOT USABLE");
				// might be:
				// 'this'
				// 'super'
				// literal ($INT, $DBL, $CHR, $STR, $BOL)
				// class-name  [ ID ]
				// constructor-call [ $NEW creator ]
				// method-call [ expr(exprList) ]
				// casting [ $CAST(type) expr ]
				// array-indexing  [ expr[expr] ]
				// unary-op [ ++, --, !, ~ ]
				// paren-expr [ (...) ]
				// array-init [ {...} ]
				return null;
			}
		}
		
		private JavaClass findClass(String type) {
			return null;
		}
		
		/**
		 * Find and return matching method-definition-info.
		 * Returns null if not found.
		 */
		private MethodDefInfo findDefInfo(String callee, String name, JavaParser.ExpressionListContext ctx) {
			List<MethodDefInfo> list = methodDEFs.get(name);
			Logger.debug("METHOD NAME: " + name);
			Logger.debug("# found = " + (list == null ? 0 : list.size()));
			//
			if (list == null)
				return null;
			//
			if (list.size() == 1) { // only one candidate
				Logger.debug("SINGLE CANDIDATE");
				MethodDefInfo mtd = list.get(0);
				// just check params-count to make sure
				if (ctx != null && mtd.PARAM_TYPES != null &&
						mtd.PARAM_TYPES.length != ctx.expression().size())
					return null;
				Logger.debug("WITH MATCHING PARAMS COUNT");
				return mtd;
			}
			//
			if (callee == null) { // no callee; so search for self methods
				Logger.debug("NO CALLEE");
				forEachDefInfo:
				for (MethodDefInfo mtd : list) {
					// check package-name
					if (!mtd.PACKAGE.equals(activeClasses.peek().PACKAGE))
						continue;
					// check class-name
					boolean classNameMatch = false;
					for (JavaClass cls: activeClasses) {
						if (mtd.CLASS_NAME.equals(cls.NAME)) {
							classNameMatch = true;
							break;
						}
					}
					if (!classNameMatch)
						continue;
					// check params-count
					if (ctx != null && mtd.PARAM_TYPES != null &&
							mtd.PARAM_TYPES.length != ctx.expression().size())
						continue;
					// check params-types
					if (ctx != null) {
						String[] argTypes = new String[ctx.expression().size()];
						for (int i = 0; i < argTypes.length; ++i) {
							String arg = visit(ctx.expression(i));
							argTypes[i] = getType(arg);
						}
						if (mtd.PARAM_TYPES != null) {
							for (int i = 0; i < argTypes.length; ++i) {
								if (argTypes[i] == null)
									continue;
								if (!argTypes[i].equals(mtd.PARAM_TYPES[i]))
									continue forEachDefInfo;
							}
						}
					}
					return mtd;
				}
			} else if (isDefined(callee) > -1) { // has a defined callee
				Logger.debug("DEFINED CALLEE");
				String type = getType(callee);
				JavaClass cls = allClassInfos.get(type);
				if (cls != null && cls.hasMethod(name)) {
					forEachDefInfo:
					for (MethodDefInfo mtd : list) {
						// check package-name
						if (!mtd.PACKAGE.equals(cls.PACKAGE))
							continue;
						// check class-name
						if (!mtd.CLASS_NAME.equals(cls.NAME)) 
							continue;
						// check params-count
						if (ctx != null && mtd.PARAM_TYPES != null &&
								mtd.PARAM_TYPES.length != ctx.expression().size())
							continue;
						// check params-types
						if (ctx != null) {
							String[] argTypes = new String[ctx.expression().size()];
							for (int i = 0; i < argTypes.length; ++i) {
								String arg = visit(ctx.expression(i));
								argTypes[i] = getType(arg);
							}
							if (mtd.PARAM_TYPES != null) {
								for (int i = 0; i < argTypes.length; ++i) {
									if (argTypes[i] == null)
										continue;
									if (!argTypes[i].equals(mtd.PARAM_TYPES[i]))
										continue forEachDefInfo;
								}
							}
						}
						return mtd;
					}
					Logger.debug("METHOD DEF INFO NOT FOUND!");
				} else {
					Logger.debug((cls == null ? 
							"CLASS OF TYPE " + type + " NOT FOUND!" : 
							"CLASS HAS NO SUCH METHOD!"));
				}
			} else { // has an undefined callee
				Logger.debug("UNDEFINED CALLEE.");
				//
				// TODO: use a global retType for visiting expressions
				//
			}
			return null;
		}
		
		/**
		 * Find and return matching method-definition-info.
		 * Returns null if not found.
		 */
		private MethodDefInfo findDefInfo(String name, String type, JavaField[] params) {
			List<MethodDefInfo> infoList = methodDEFs.get(name);
			if (infoList.size() > 1) {
				forEachInfo:
				for (MethodDefInfo info: infoList) {
					if (!info.PACKAGE.equals(activeClasses.peek().PACKAGE)) 
						continue;
					if (!info.CLASS_NAME.equals(activeClasses.peek().NAME)) 
						continue;
					if ((info.RET_TYPE == null && type != null) || 
							(info.RET_TYPE != null && type == null)) 
						continue;
					if (type != null && !type.startsWith(info.RET_TYPE)) 
						continue;
					if (info.PARAM_TYPES != null) {
						if (info.PARAM_TYPES.length != params.length) 
							continue;
						for (int i = 0; i < params.length; ++i)
							if (!params[i].TYPE.startsWith(info.PARAM_TYPES[i])) 
								continue forEachInfo;
					} else if (params.length > 0) 
						continue;
					return info;
				}
			} else 
				if (infoList.size() == 1) 
					return infoList.get(0);
			return null;
		}
		
		/**************************************
		 **************************************
		 ***          DECLARATIONS          ***
		 **************************************
		 **************************************/
		
		@Override
		public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
			// classDeclaration 
			//   :  'class' Identifier typeParameters? 
			//      ('extends' typeType)? ('implements' typeList)? classBody
			for (JavaClass cls: classInfos) {
				if (cls.NAME.equals(ctx.Identifier().getText())) {
					activeClasses.push(cls);
					visit(ctx.classBody());
					activeClasses.pop();
					break;
				}
			}
			return null;
		}

		@Override
		public String visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
			// Just ignore enums for now ...
			return null;
		}
		
		@Override
		public String visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
			// Just ignore interfaces for now ...
			return null;
		}
		
		@Override
		public String visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			// classBodyDeclaration :  ';'  |  'static'? block  |  modifier* memberDeclaration
			if (ctx.block() != null) {
				localVars.clear();
				methodParams = new JavaField[0];
				methodDefInfo = new MethodDefInfo(null, "static-block", "", activeClasses.peek().NAME, null);
				return null;
			} else
				return visitChildren(ctx);
		}

		@Override
		public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
			// Identifier formalParameters ('throws' qualifiedNameList)?  constructorBody
			PDNode entry;
			if (iteration == 1) {
				entry = new PDNode();
				entry.setLineOfCode(ctx.getStart().getLine());
				entry.setCode(ctx.Identifier().getText() + ' ' + getOriginalCodeText(ctx.formalParameters()));
				entry.setProperty("name", ctx.Identifier().getText());
				ddg.addVertex(entry);
				pdNodes.put(ctx, entry);
				//
				// Extract all parameter types and IDs
				List<String> paramIDs = new ArrayList<>();
				List<String> paramTypes = new ArrayList<>();
				if (ctx.formalParameters().formalParameterList() != null) {
					for (JavaParser.FormalParameterContext prm : 
							ctx.formalParameters().formalParameterList().formalParameter()) {
						paramTypes.add(visitType(prm.typeType()));
						paramIDs.add(prm.variableDeclaratorId().Identifier().getText());
					}
					JavaParser.LastFormalParameterContext lastParam = 
							ctx.formalParameters().formalParameterList().lastFormalParameter();
					if (lastParam != null) {
						paramTypes.add(visitType(lastParam.typeType()));
						paramIDs.add(lastParam.variableDeclaratorId().Identifier().getText());
					}
				}
				methodParams = new JavaField[paramIDs.size()];
				for (int i = 0; i < methodParams.length; ++i) 
					methodParams[i] = new JavaField(null, false, paramTypes.get(i), paramIDs.get(i));
				entry.setProperty("params", methodParams);
				//
				// Add initial DEF info: method entry nodes define the input-parameters
				for (String var: paramIDs)
					changed |= entry.addDEF(var);
			} else {
				entry = (PDNode) pdNodes.get(ctx);
				methodParams = (JavaField[]) entry.getProperty("params");
			}

			methodDefInfo = findDefInfo((String) entry.getProperty("name"), null, methodParams);

			if (methodDefInfo == null) {
				Logger.error("Constructor NOT FOUND!");
				Logger.error("NAME = " + (String) entry.getProperty("name"));
				Logger.error("TYPE = null");
				Logger.error("PARAMS = " + Arrays.toString(methodParams));
				Logger.error("CLASS = " + activeClasses.peek().NAME);
				Logger.error("PACKAGE = " + activeClasses.peek().PACKAGE);
				List list = methodDEFs.get((String) entry.getProperty("name"));
				for (int i = 0; i < list.size(); ++i)
					Logger.error(list.get(i).toString());
			}

			// Now visit method body ...
			localVars.clear();
			visit(ctx.constructorBody());
			//
			localVars.clear();
			methodParams = new JavaField[0];
			return null;
		}
		
		@Override
		public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			// methodDeclaration :
			//   (typeType|'void') Identifier formalParameters ('[' ']')*
			//     ('throws' qualifiedNameList)?  ( methodBody | ';' )
			//
			// formalParameters :  '(' formalParameterList? ')'
			//
			// formalParameterList :  formalParameter (',' formalParameter)* (',' lastFormalParameter)?
			//                     |  lastFormalParameter
			//
			// formalParameter :  variableModifier* typeType variableDeclaratorId
			//
			// lastFormalParameter :  variableModifier* typeType '...' variableDeclaratorId
			//
			PDNode entry;
			if (iteration == 1) {
				entry = new PDNode();
				entry.setLineOfCode(ctx.getStart().getLine());
				String retType = "void";
				if (ctx.typeType() != null)
					retType = ctx.typeType().getText();
				String args = getOriginalCodeText(ctx.formalParameters());
				entry.setCode(retType + " " + ctx.Identifier().getText() + args);
				entry.setProperty("name", ctx.Identifier().getText());
				entry.setProperty("type", retType);
				ddg.addVertex(entry);
				pdNodes.put(ctx, entry);
				//
				// Extract all parameter types and IDs
				List<String> paramIDs = new ArrayList<>();
				List<String> paramTypes = new ArrayList<>();
				if (ctx.formalParameters().formalParameterList() != null) {
					for (JavaParser.FormalParameterContext prm : 
							ctx.formalParameters().formalParameterList().formalParameter()) {
						paramTypes.add(visitType(prm.typeType()));
						paramIDs.add(prm.variableDeclaratorId().Identifier().getText());
					}
					JavaParser.LastFormalParameterContext lastParam = 
							ctx.formalParameters().formalParameterList().lastFormalParameter();
					if (lastParam != null) {
						paramTypes.add(visitType(lastParam.typeType()));
						paramIDs.add(lastParam.variableDeclaratorId().Identifier().getText());
					}
				}
				methodParams = new JavaField[paramIDs.size()];
				for (int i = 0; i < methodParams.length; ++i) 
					methodParams[i] = new JavaField(null, false, paramTypes.get(i), paramIDs.get(i));
				entry.setProperty("params", methodParams);
				//
				// Add initial DEF info: method entry nodes define the input-parameters
				for (String pid: paramIDs)
					changed |= entry.addDEF(pid);
			} else {
				entry = (PDNode) pdNodes.get(ctx);
				methodParams = (JavaField[]) entry.getProperty("params");
			}
			
			methodDefInfo = findDefInfo((String) entry.getProperty("name"), 
										(String) entry.getProperty("type"),	methodParams);
			if (methodDefInfo == null) {
				Logger.error("Method NOT FOUND!");
				Logger.error("NAME = " + (String) entry.getProperty("name"));
				Logger.error("TYPE = " + (String) entry.getProperty("type"));
				Logger.error("PARAMS = " + Arrays.toString(methodParams));
				Logger.error("CLASS = " + activeClasses.peek().NAME);
				Logger.error("PACKAGE = " + activeClasses.peek().PACKAGE);
				List list = methodDEFs.get((String) entry.getProperty("name"));
				for (int i = 0; i < list.size(); ++i)
					Logger.error(list.get(i).toString());
			}
			
			// Now visit method body ...
			localVars.clear();
			if (ctx.methodBody() != null)
				visit(ctx.methodBody());
			//
			localVars.clear();
			methodParams = new JavaField[0];
			return null;
		}
		
		private String visitType(JavaParser.TypeTypeContext ctx) {
			// typeType :  classOrInterfaceType ('[' ']')*  |   primitiveType ('[' ']')*
			//
			// classOrInterfaceType :  Identifier typeArguments? ('.' Identifier typeArguments? )*
			//
			return ctx.getText();
		}
		
		@Override
		public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			// localVariableDeclaration :  variableModifier* typeType variableDeclarators
			//
			// variableDeclarators :  variableDeclarator (',' variableDeclarator)*
			//
			// variableDeclarator :  variableDeclaratorId ('=' variableInitializer)?
			//
			// TODO: is this located correctly? Or should it be placed after 'if (analysisVisit) ...'
			for (JavaParser.VariableDeclaratorContext var : ctx.variableDeclarators().variableDeclarator())
				localVars.add(new JavaField(null, false, visitType(ctx.typeType()), 
						var.variableDeclaratorId().Identifier().getText()));
			//
			if (analysisVisit)
				return visit(ctx.variableDeclarators());
			//
			PDNode declr;
			if (iteration == 1) {
				declr = new PDNode();
				declr.setLineOfCode(ctx.getStart().getLine());
				declr.setCode(getOriginalCodeText(ctx));
				ddg.addVertex(declr);
				pdNodes.put(ctx, declr);
			} else 
				declr = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(declr, ctx.variableDeclarators());
			return null;
		}
		
		/************************************
		 ************************************
		 ***          STATEMENTS          ***
		 ************************************
		 ************************************/
		
		@Override
		public String visitBlock(JavaParser.BlockContext ctx) {
			// block :  '{' blockStatement* '}'
			// Local vars defined inside a block, are only valid till the end of that block.
			int entrySize = localVars.size();
			//
			visitChildren(ctx);
			//
			if (localVars.size() > entrySize)
				localVars.subList(entrySize, localVars.size()).clear();
			return null;
		}
		
		@Override
		public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
			// statementExpression ';'
			if (analysisVisit)
				return visit(ctx.expression());
			//
			PDNode expr;
			if (iteration == 1) {
				expr = new PDNode();
				expr.setLineOfCode(ctx.getStart().getLine());
				expr.setCode(getOriginalCodeText(ctx));
				ddg.addVertex(expr);
				pdNodes.put(ctx, expr);
			} else 
				expr = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(expr, ctx.expression());
			return null;
		}
		
		@Override
		public String visitIfStatement(JavaParser.IfStatementContext ctx) {
			// 'if' parExpression statement ('else' statement)?
			PDNode ifNode;
			if (iteration == 1) {
				ifNode = new PDNode();
				ifNode.setLineOfCode(ctx.getStart().getLine());
				ifNode.setCode("if " + getOriginalCodeText(ctx.parExpression()));
				ddg.addVertex(ifNode);
				pdNodes.put(ctx, ifNode);
			} else 
				ifNode = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(ifNode, ctx.parExpression().expression());
			//
			for (JavaParser.StatementContext stmnt: ctx.statement())
				visit(stmnt);
			return null;
		}
		
		@Override
		public String visitForStatement(JavaParser.ForStatementContext ctx) {
			// 'for' '(' forControl ')' statement
			int entrySize = localVars.size();
			//  First, we should check type of for-loop ...
			if (ctx.forControl().enhancedForControl() != null) {
				// This is a for-each loop;
				//   enhancedForControl: 
				//     variableModifier* typeType variableDeclaratorId ':' expression
				PDNode forExpr;
				if (iteration == 1) {
					forExpr = new PDNode();
					forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
					forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl()) + ")");
					ddg.addVertex(forExpr);
					pdNodes.put(ctx.forControl().enhancedForControl(), forExpr);
				} else 
					forExpr = (PDNode) pdNodes.get(ctx.forControl().enhancedForControl());
				//
				// Now analyse DEF-USE by visiting the expression ...
				String type = visitType(ctx.forControl().enhancedForControl().typeType());
				String var = ctx.forControl().enhancedForControl().variableDeclaratorId().Identifier().getText();
				localVars.add(new JavaField(null, false, type, var));
				changed |= forExpr.addDEF(var);
				analyseDefUse(forExpr, ctx.forControl().enhancedForControl().expression());
			} else {
				// It's a traditional for-loop: 
				//   forInit? ';' expression? ';' forUpdate?
				if (ctx.forControl().forInit() != null) { // non-empty init
					PDNode forInit;
					if (iteration == 1) {
						forInit = new PDNode();
						forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
						forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()));
						ddg.addVertex(forInit);
						pdNodes.put(ctx.forControl().forInit(), forInit);
					} else 
						forInit = (PDNode) pdNodes.get(ctx.forControl().forInit());
					//
					// Now analyse DEF-USE by visiting the expression ...
					if (ctx.forControl().forInit().expressionList() != null)
						analyseDefUse(forInit, ctx.forControl().forInit().expressionList());
					else
						analyseDefUse(forInit, ctx.forControl().forInit().localVariableDeclaration());
				}
				// for-expression
				if (ctx.forControl().expression() != null) { // non-empty predicate-expression
					PDNode forExpr;
					if (iteration == 1) {
						forExpr = new PDNode();
						forExpr.setLineOfCode(ctx.forControl().expression().getStart().getLine());
						forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl().expression()) + ")");
						ddg.addVertex(forExpr);
						pdNodes.put(ctx.forControl().expression(), forExpr);
					} else 
						forExpr = (PDNode) pdNodes.get(ctx.forControl().expression());
					//
					// Now analyse DEF-USE by visiting the expression ...
					analyseDefUse(forExpr, ctx.forControl().expression());
				}
				// for-update
				if (ctx.forControl().forUpdate() != null) { // non-empty for-update
					PDNode forUpdate;
					if (iteration == 1) {
						forUpdate = new PDNode();
						forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
						forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
						ddg.addVertex(forUpdate);
						pdNodes.put(ctx.forControl().forUpdate(), forUpdate);
					} else
						forUpdate = (PDNode) pdNodes.get(ctx.forControl().forUpdate());
					//
					// Now analyse DEF-USE by visiting the expression ...
					analyseDefUse(forUpdate, ctx.forControl().forUpdate().expressionList());
				}
			}
			// visit for loop body
			String visit = visit(ctx.statement());
			// clear any local vars defined in the for loop
			if (localVars.size() > entrySize)
				localVars.subList(entrySize, localVars.size()).clear();
			return visit;
		}
		
		@Override
		public String visitWhileStatement(JavaParser.WhileStatementContext ctx) {
			// 'while' parExpression statement
			PDNode whileNode;
			if (iteration == 1) {
				whileNode = new PDNode();
				whileNode.setLineOfCode(ctx.getStart().getLine());
				whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
				ddg.addVertex(whileNode);
				pdNodes.put(ctx, whileNode);
			} else
				whileNode = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(whileNode, ctx.parExpression().expression());
			//
			return visit(ctx.statement());
		}
		
		@Override
		public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
			// 'do' statement 'while' parExpression ';'
			visit(ctx.statement());
			//
			PDNode whileNode;
			if (iteration == 1) {
				whileNode = new PDNode();
				whileNode.setLineOfCode(ctx.parExpression().getStart().getLine());
				whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
				ddg.addVertex(whileNode);
				pdNodes.put(ctx, whileNode);
			} else
				whileNode = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(whileNode, ctx.parExpression().expression());
			return null;
		}
		
		@Override
		public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
			//  'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
			//  switchBlockStatementGroup :  switchLabel+ blockStatement+
			PDNode switchNode;
			if (iteration == 1) {
				switchNode = new PDNode();
				switchNode.setLineOfCode(ctx.getStart().getLine());
				switchNode.setCode("switch " + getOriginalCodeText(ctx.parExpression()));
				ddg.addVertex(switchNode);
				pdNodes.put(ctx, switchNode);
			} else
				switchNode = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(switchNode, ctx.parExpression().expression());
			//
			for (JavaParser.SwitchBlockStatementGroupContext scx : ctx.switchBlockStatementGroup())
				visit(scx);
			for (JavaParser.SwitchLabelContext scx : ctx.switchLabel())
				visit(scx);
			return null;
		}
		
		@Override
		public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
			// 'return' expression? ';'
			PDNode ret;
			if (iteration == 1) {
				ret = new PDNode();
				ret.setLineOfCode(ctx.getStart().getLine());
				ret.setCode(getOriginalCodeText(ctx));
				ddg.addVertex(ret);
				pdNodes.put(ctx, ret);
			} else
				ret = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			if (ctx.expression() != null)
				analyseDefUse(ret, ctx.expression());
			return null;
		}
		
		@Override
		public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
			// 'synchronized' parExpression block
			PDNode syncStmt;
			if (iteration == 1) {
				syncStmt = new PDNode();
				syncStmt.setLineOfCode(ctx.getStart().getLine());
				syncStmt.setCode("synchronized " + getOriginalCodeText(ctx.parExpression()));
				ddg.addVertex(syncStmt);
				pdNodes.put(ctx, syncStmt);
			} else 
				syncStmt = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(syncStmt, ctx.parExpression().expression());
			//
			return visit(ctx.block());
		}
		
		@Override
		public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
			// 'throw' expression ';'
			PDNode throwNode;
			if (iteration == 1) {
				throwNode = new PDNode();
				throwNode.setLineOfCode(ctx.getStart().getLine());
				throwNode.setCode("throw " + getOriginalCodeText(ctx.expression()));
				ddg.addVertex(throwNode);
				pdNodes.put(ctx, throwNode);
			} else
				throwNode = (PDNode) pdNodes.get(ctx);
			//
			// Now analyse DEF-USE by visiting the expression ...
			analyseDefUse(throwNode, ctx.expression());
			return null;
		}
		
		@Override
		public String visitTryStatement(JavaParser.TryStatementContext ctx) {
			// 'try' block (catchClause+ finallyBlock? | finallyBlock)
			//
			// The 'try' block has no DEF-USE effect, so no need for PDNodes;
			// just visit the 'block'
			visit(ctx.block());
			//
			// But the 'catchClause' define a local exception variable;
			// so we need to visit any available catch clauses
			if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				for (JavaParser.CatchClauseContext cx: ctx.catchClause()) {
					PDNode catchNode;
					if (iteration == 1) {
						catchNode = new PDNode();
						catchNode.setLineOfCode(cx.getStart().getLine());
						catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
						ddg.addVertex(catchNode);
						pdNodes.put(cx, catchNode);
					} else 
						catchNode = (PDNode) pdNodes.get(cx);
					//
					// Define the exception var
					String type = cx.catchType().getText();
					String var = cx.Identifier().getText();
					JavaField exceptionVar = new JavaField(null, false, type, var);
					localVars.add(exceptionVar);
					changed |= catchNode.addDEF(var);
					//
					visit(cx.block());
					localVars.remove(exceptionVar);
				}
			}
			if (ctx.finallyBlock() != null)
				// 'finally' block
				visit(ctx.finallyBlock().block());
			
			return null;
		}

		@Override
		public String visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx) {
			// 'try' resourceSpecification block catchClause* finallyBlock?
			// resourceSpecification :  '(' resources ';'? ')'
			// resources :  resource (';' resource)*
			// resource  :  variableModifier* classOrInterfaceType variableDeclaratorId '=' expression
			//
			int entrySize = localVars.size();
			// Analyze all resources
			for (JavaParser.ResourceContext rsrx: ctx.resourceSpecification().resources().resource()) {
				PDNode resource;
				if (iteration == 1) {
					resource = new PDNode();
					resource.setLineOfCode(rsrx.getStart().getLine());
					resource.setCode(getOriginalCodeText(rsrx));
					ddg.addVertex(resource);
					pdNodes.put(rsrx, resource);
				} else {
					resource = (PDNode) pdNodes.get(rsrx);
				}
				// Define the resource variable
				String type = rsrx.classOrInterfaceType().getText();
				String var = rsrx.variableDeclaratorId().getText();
				localVars.add(new JavaField(null, false, type, var));
				//
				// Now analyse DEF-USE by visiting the expression ...
				analyseDefUse(resource, rsrx);
			}
			
			// The 'try' block has no DEF-USE effect, so no need for PDNodes;
			// just visit the 'block'
			visit(ctx.block());
			//
			// But the 'catchClause' define a local exception variable;
			// so we need to visit any available catch clauses
			if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				for (JavaParser.CatchClauseContext cx: ctx.catchClause()) {
					PDNode catchNode;
					if (iteration == 1) {
						catchNode = new PDNode();
						catchNode.setLineOfCode(cx.getStart().getLine());
						catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
						ddg.addVertex(catchNode);
						pdNodes.put(cx, catchNode);
					} else 
						catchNode = (PDNode) pdNodes.get(cx);
					//
					// Define the exception var
					String type = cx.catchType().getText();
					String var = cx.Identifier().getText();
					JavaField exception = new JavaField(null, false, type, var);
					localVars.add(exception);
					changed |= catchNode.addDEF(var);
					//
					visit(cx.block());
					//
					localVars.remove(exception);
				}
			}
			if (ctx.finallyBlock() != null)
				// 'finally' block
				visit(ctx.finallyBlock().block());
			//			
			// Remove resources from local vars ...
			if (localVars.size() > entrySize)
				localVars.subList(entrySize, localVars.size()).clear();
			return null;
		}		
		
		/***********************************************
		 ***********************************************
		 ***       NON-DETERMINANT EXPRESSIONS       ***
		 ***********************************************
		 ***********************************************/

		@Override
		public String visitExprPrimary(JavaParser.ExprPrimaryContext ctx) {
			// primary
			//   :   '(' expression ')'
			//   |   'this'
			//   |   'super'
			//   |   literal
			//   |   Identifier
			//   |   typeType '.' 'class'
			//   |   'void' '.' 'class'
			//   |   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
			//
			// literal
			//   :  IntegerLiteral   |  FloatingPointLiteral
			//   |  CharacterLiteral |  StringLiteral
			//   |  BooleanLiteral   |  'null'
			//
			// nonWildcardTypeArguments :  '<' typeList '>'
			//
			// explicitGenericInvocationSuffix :  'super' superSuffix  |  Identifier arguments
			//
			JavaParser.PrimaryContext primary = ctx.primary();
			if (primary.getText().startsWith("(") && primary.getText().endsWith(")"))
				return '(' + visit(primary.expression()) + ')';
			if (primary.getText().equals("this"))
				return "this";
			if (primary.getText().equals("super"))
				return "super";
			if (primary.literal() != null) {
				if (primary.literal().IntegerLiteral() != null)
					return "$INT";
				if (primary.literal().FloatingPointLiteral() != null)
					return "$DBL";
				if (primary.literal().CharacterLiteral() != null)
					return "$CHR";
				if (primary.literal().StringLiteral() != null)
					return "$STR";
				if (primary.literal().BooleanLiteral() != null)
					return "$BOL";
				return "$NUL";
			}
			if (primary.Identifier() != null)
				return primary.Identifier().getText();
			if (primary.getText().endsWith(".class"))
				return "$CLS";
			//
			return primary.getText();
		}
		
		@Override
		public String visitExprDotID(JavaParser.ExprDotIDContext ctx) {
			// expression '.' Identifier
			return visit(ctx.expression()) + '.' + ctx.Identifier().getText();
		}
		
		@Override
		public String visitExprDotThis(JavaParser.ExprDotThisContext ctx) {
			// expression '.' 'this'
			return visit(ctx.expression()) + ".this";
		}
		
		@Override
		public String visitExprCasting(JavaParser.ExprCastingContext ctx) {
			// '(' typeType ')' expression
			return "$CAST(" + visitType(ctx.typeType()) + ") " + visit(ctx.expression());
		}
		
		@Override
		public String visitExpressionList(JavaParser.ExpressionListContext ctx) {
			// expressionList : expression (',' expression)*
			StringBuilder expList = new StringBuilder(visit(ctx.expression(0)));
			for (int i = 1; i < ctx.expression().size(); ++i) 
				expList.append(", ").append(visit(ctx.expression(i)));
			return expList.toString();
		}

		/*****************************************************
		 *****************************************************
		 ***    DETERMINANT EXPRESSIONS (RETURN OBJECT)    ***
		 *****************************************************
		 *****************************************************/
		
		/**
		 * Check to see if the given expression is USABLE.
		 * An expression is usable if we are required to add it to the USE-list.
		 * Any expression who is DEFINABLE should be added to the USE-list.
		 * An expression is definable, if it holds a value which can be modified in the program.
		 * For example, Class names and Class types are not definable.
		 * Method invocations are not definable.
		 * Literals are also not definable.
		 */
		private boolean isUsableExpression(String expr) {
			// must not be a literal or of type 'class'.
			if (expr.startsWith("$"))
				return false;
			// must not be a method-call or parenthesized expression
			if (expr.endsWith(")"))
				return false;
			// must not be an array-indexing expression
			if (expr.endsWith("]"))
				return false;
			// must not be post unary operation expression
			if (expr.endsWith("++") || expr.endsWith("--"))
				return false;
			// must not be a pre unary operation expression
			if (expr.startsWith("+") || expr.startsWith("-") || expr.startsWith("!") || expr.startsWith("~"))
				return false;
			// must not be an array initialization expression
			if (expr.endsWith("}"))
				return false;
			// must not be an explicit generic invocation expression
			if (expr.startsWith("<"))
				return false;
			//
			return true;
		}
		
		/**
		 * Visit the list of arguments of a method call, and return a proper string.
		 * This method will also add usable expressions to the USE-list.
		 */
		private String visitMethodArgs(JavaParser.ExpressionListContext ctx, MethodDefInfo defInfo) {
			// expressionList :  expression (',' expression)*
			if (ctx != null) {
				StringBuilder args = new StringBuilder();
				List<JavaParser.ExpressionContext> argsList = ctx.expression();
				String arg = visit(argsList.get(0));
				args.append(arg);
				if (isUsableExpression(arg)) {
					useList.add(arg);
					if (defInfo != null && defInfo.argDEFs()[0])
						defList.add(arg);
				}
				for (int i = 1; i < argsList.size(); ++i) {
					arg = visit(argsList.get(i));
					args.append(", ").append(arg);
					if (isUsableExpression(arg)) {
						useList.add(arg);
						if (defInfo != null && defInfo.argDEFs()[i])
							defList.add(arg);
					}
				}
				return args.toString();
			} else
				return "";
		}
		
		@Override
		public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx) {
			// expression '(' expressionList? ')'
			String callee = null;
			String callExpression = visit(ctx.expression());
			String methodName = callExpression;
			Logger.debug("---");
			Logger.debug("CALL EXPR : " + methodName);
			//
			int start = 0, lastDot = callExpression.lastIndexOf('.');
			if (lastDot > 0) {
				//start = callExpression.substring(0, lastDot).lastIndexOf('.');
				//if (start < 0)
				//	start = 0;
				//else
				//	++start;
				callee = callExpression.substring(start, lastDot);
				Logger.debug("HAS CALLEE : " + callee);
				if (isUsableExpression(callee)) {
					useList.add(callee);
					Logger.debug("CALLEE IS USABLE");
				}
				methodName = callExpression.substring(lastDot + 1);
			} else {
				Logger.debug("NO CALLEE");
				methodName = callExpression;
			}
			//
			MethodDefInfo defInfo = findDefInfo(callee, methodName, ctx.expressionList());
			Logger.debug("FIND DEF RESULT: " + defInfo);
			Logger.debug("---");
			if (callee != null && defInfo != null && defInfo.doesStateDEF())
				defList.add(callee);
			return callExpression + '(' + visitMethodArgs(ctx.expressionList(), defInfo) + ')';
		}
		
		@Override
		public String visitExprNewCreator(JavaParser.ExprNewCreatorContext ctx) {
			// 'new' creator
			// 
			// creator
			//   :  nonWildcardTypeArguments createdName classCreatorRest
			//   |  createdName (arrayCreatorRest | classCreatorRest)
			//
			// createdName
			//   :  Identifier typeArgumentsOrDiamond? ('.' Identifier typeArgumentsOrDiamond?)*
			//   |  primitiveType
			//
			// arrayCreatorRest 
			//   :  '[' ( 
			//           ']' ('[' ']')* arrayInitializer  
			//          |  
			//           expression ']' ('[' expression ']')* ('[' ']')* 
			//          )
			//
			// classCreatorRest :  arguments classBody?
			//
			// 1st process 'createdName'
			String creator = null, rest;
			if (ctx.creator().createdName().primitiveType() != null)
				creator = ctx.creator().createdName().primitiveType().getText();
			else {
				for (TerminalNode id: ctx.creator().createdName().Identifier())
					creator = id.getText();
			}
			// 2nd process '(arrayCreatorRest | classCreatorRest)'
			if (ctx.creator().arrayCreatorRest() != null) {
				// array constructor
				if (ctx.creator().arrayCreatorRest().arrayInitializer() != null) {
					// process 'ctx.creator().arrayCreatorRest().arrayInitializer()'
					JavaParser.ArrayInitializerContext arrayInitCtx = 
							ctx.creator().arrayCreatorRest().arrayInitializer();
					StringBuilder arrayInit = new StringBuilder();
					for (JavaParser.VariableInitializerContext initCtx : 
							arrayInitCtx.variableInitializer()) {
						String init = visit(initCtx);
						if (isUsableExpression(init))
							useList.add(init);
						arrayInit.append(init).append(", ");
					}
					rest = "{ " + arrayInit.toString() + " }";
				} else {
					// process '[' expression ']' ('[' expression ']')* ('[' ']')*
					StringBuilder arrayCreate = new StringBuilder();
					for (JavaParser.ExpressionContext exprCtx : 
							ctx.creator().arrayCreatorRest().expression()) {
						String expr = visit(exprCtx);
						if (isUsableExpression(expr))
							useList.add(expr);
						arrayCreate.append('[').append(expr).append(']');
					}
					rest = arrayCreate.toString();
				}
			} else {
				// class constructor ...
				JavaParser.ArgumentsContext argsCtx = ctx.creator().classCreatorRest().arguments();
				MethodDefInfo defInfo = findDefInfo(null, creator, argsCtx.expressionList());
				rest = '(' + visitMethodArgs(argsCtx.expressionList(), defInfo) + ')';
			}
			return "$NEW " + creator + rest;
		}
		
		@Override
		public String visitExprDotNewInnerCreator(JavaParser.ExprDotNewInnerCreatorContext ctx) {
			// expression '.' 'new' nonWildcardTypeArguments? innerCreator
			//
			// innerCreator :  Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
			//
			// classCreatorRest :  arguments classBody?
			//
			// 1st process 'expression'
			String expression = visit(ctx.expression());
			if (isUsableExpression(expression))
				useList.add(expression);
			// 2nd process 'innerCreator'
			String creator = ctx.innerCreator().Identifier().getText();
			// 3rd process constructor arguments ...
			JavaParser.ArgumentsContext argsCtx = ctx.innerCreator().classCreatorRest().arguments();
			MethodDefInfo defInfo = findDefInfo(null, creator, argsCtx.expressionList());
			String rest = '(' + visitMethodArgs(argsCtx.expressionList(), defInfo) + ')';
			return expression + ".$NEW " + creator + rest;
		}
		
		@Override
		public String visitExprDotSuper(JavaParser.ExprDotSuperContext ctx) {
			// expression '.' 'super' superSuffix
			//
			// superSuffix :  arguments  |  '.' Identifier arguments?
			//
			StringBuilder result = new StringBuilder();
			String expr = visit(ctx.expression());
			if (isUsableExpression(expr))
				useList.add(expr);
			result.append(expr).append(".super");
			if (ctx.superSuffix().arguments() != null) {
				// add 'expr.super' to USEs for method-call
				useList.add(result.toString());
				if (ctx.superSuffix().getText().startsWith(".")) {
					// expr.super.method(...) call
					result.append('.').append(ctx.superSuffix().Identifier().getText()).append('(');
				// else  expr.super(...) constructor call
				}
				// visit and add arguments to USEs
				result.append(visitMethodArgs(ctx.superSuffix().arguments().expressionList(), null));
				result.append(')');
			} else {
				// expr.super.filed reference
				result.append('.').append(ctx.superSuffix().Identifier().getText());
			}
			return result.toString();
		}
		
		@Override
		public String visitExprDotGenInvok(JavaParser.ExprDotGenInvokContext ctx) {
			// expression '.' explicitGenericInvocation
			//
			// explicitGenericInvocation :  nonWildcardTypeArguments explicitGenericInvocationSuffix
			//
			// nonWildcardTypeArguments :  '<' typeList '>'
			//
			// explicitGenericInvocationSuffix :  'super' superSuffix  |  Identifier arguments
			//
			String expression = visit(ctx.expression());
			if (isUsableExpression(expression))
				useList.add(expression);
			String invocSuffix;
			JavaParser.ExplicitGenericInvocationSuffixContext suffixContext = 
					ctx.explicitGenericInvocation().explicitGenericInvocationSuffix();
			if (suffixContext.Identifier() != null) {
				invocSuffix = suffixContext.Identifier().getText();
				invocSuffix += '(' + visitMethodArgs(suffixContext.arguments().expressionList(), null) + ')';
			} else {
				invocSuffix = "super";
				if (suffixContext.superSuffix().Identifier() != null) 
					invocSuffix += '.' + suffixContext.superSuffix().Identifier().getText();
				if (suffixContext.superSuffix().arguments() != null) 
					invocSuffix += '(' + visitMethodArgs(suffixContext.superSuffix().arguments().expressionList(), null) + ')';
			}
			return expression + '.' + ctx.explicitGenericInvocation().nonWildcardTypeArguments().getText() + invocSuffix;
		}
		
		@Override
		public String visitExprArrayIndexing(JavaParser.ExprArrayIndexingContext ctx) {
			// expression '[' expression ']'
			String array = visit(ctx.expression(0));
			if (isUsableExpression(array))
				useList.add(array);
			String index = visit(ctx.expression(1));
			if (isUsableExpression(index))
				useList.add(index);
			return array + '[' + index + ']';
		}
		
		@Override
		public String visitExprConditional(JavaParser.ExprConditionalContext ctx) {
			// expression '?' expression ':' expression
			String prdct = visit(ctx.expression(0));
			if (isUsableExpression(prdct))
				useList.add(prdct);
			String retTrue = visit(ctx.expression(1));
			if (isUsableExpression(retTrue))
				useList.add(retTrue);
			String retFalse = visit(ctx.expression(2));
			if (isUsableExpression(retFalse))
				useList.add(retFalse);
			return prdct + " ? " + retTrue + " : " + retFalse;
		}
		
		/*****************************************************
		 *****************************************************
		 ***      DETERMINANT EXPRESSIONS (NO RETURN)      ***
		 *****************************************************
		 *****************************************************/
		
		@Override
		public String visitExprPostUnaryOp(JavaParser.ExprPostUnaryOpContext ctx) {
			// expression ('++' | '--')
			String expr = visit(ctx.expression());
			if (isUsableExpression(expr)) {
				useList.add(expr);
				defList.add(expr);
			}
			if (ctx.getText().endsWith("++"))
				return expr + "++";
			else
				return expr + "--";
		}
		
		@Override
		public String visitExprPreUnaryOp(JavaParser.ExprPreUnaryOpContext ctx) {
			// ('+'|'-'|'++'|'--') expression
			String expr = visit(ctx.expression());
			if (isUsableExpression(expr)) {
				useList.add(expr);
				if (ctx.getText().startsWith("--") || ctx.getText().startsWith("++")) {
					defList.add(expr);
					selfFlowList.add(expr);
				}
			}
			if (ctx.getText().charAt(0) == '+') {
				if (ctx.getText().startsWith("++"))
					return "++" + expr;
				else
					return "+" + expr;
			} else {
				if (ctx.getText().startsWith("--"))
					return "--" + expr;
				else
					return "-" + expr;
			}
		}
		
		@Override
		public String visitExprNegation(JavaParser.ExprNegationContext ctx) {
			// ('~'|'!') expression
			String expr = visit(ctx.expression());
			if (isUsableExpression(expr))
				useList.add(expr);
			if (ctx.getText().startsWith("~"))
				return '~' + expr;
			else
				return '!' + expr;
		}
		
		@Override
		public String visitExprMulDivMod(JavaParser.ExprMulDivModContext ctx) {
			// expression ('*'|'/'|'%') expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			String op;
			switch(ctx.getChild(1).getText().charAt(0)) {
				case '*':
					op = " $MUL ";
					break;
				case '/':
					op = " $DIV ";
					break;
				default:
					op = " $MOD ";
					break;
			}
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + op + expr2 + ')';
		}
		
		@Override
		public String visitExprAddSub(JavaParser.ExprAddSubContext ctx) {
			// expression ('+'|'-') expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			String op;
			switch(ctx.getChild(1).getText().charAt(0)) {
				case '+':
					op = " $ADD ";
					break;
				default:
					op = " $SUB ";
					break;
			}
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + op + expr2 + ')';
		}
		
		@Override
		public String visitExprBitShift(JavaParser.ExprBitShiftContext ctx) {
			// expression ('<' '<' | '>' '>' '>' | '>' '>') expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " $SHIFT " + expr2 + ')';
		}
		
		@Override
		public String visitExprComparison(JavaParser.ExprComparisonContext ctx) {
			// expression ('<=' | '>=' | '>' | '<') expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " $COMP " + expr2 + ')';
		}
		
		@Override
		public String visitExprInstanceOf(JavaParser.ExprInstanceOfContext ctx) {
			// expression 'instanceof' typeType
			String expr = visit(ctx.expression());
			// the parethesis are added to mark this expression as used
			return '(' + expr + " $INSTANCE " + ctx.typeType().getText() + ')';
		}
		
		@Override
		public String visitExprEquality(JavaParser.ExprEqualityContext ctx) {
			// expression ('==' | '!=') expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " $EQL " + expr2 + ')';
		}
		
		@Override
		public String visitExprBitAnd(JavaParser.ExprBitAndContext ctx) {
			// expression '&' expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " & " + expr2 + ')';
		}
		
		@Override
		public String visitExprBitXOR(JavaParser.ExprBitXORContext ctx) {
			// expression '^' expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " ^ " + expr2 + ')';
		}
		
		@Override
		public String visitExprBitOr(JavaParser.ExprBitOrContext ctx) {
			// expression '|' expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " | " + expr2 + ')';
		}
		
		@Override
		public String visitExprLogicAnd(JavaParser.ExprLogicAndContext ctx) {
			// expression '&&' expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " && " + expr2 + ')';
		}
		
		@Override
		public String visitExprLogicOr(JavaParser.ExprLogicOrContext ctx) {
			// expression '||' expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1))
				useList.add(expr1);
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " || " + expr2 + ')';
		}
		
		@Override
		public String visitExprAssignment(JavaParser.ExprAssignmentContext ctx) {
			// expression (  '='   |  '+='  |  '-='  |  '*='  |  '/='  |   '&='  |  '|='
			//            |  '^='  |   '>>='  |  '>>>='  |  '<<='  |  '%=' ) expression
			String expr1 = visit(ctx.expression(0));
			String expr2 = visit(ctx.expression(1));
			if (isUsableExpression(expr1)) {
				if (!ctx.getChild(1).getText().equals("="))
					useList.add(expr1);
				defList.add(expr1);
			}
			if (isUsableExpression(expr2))
				useList.add(expr2);
			// the parethesis are added to mark this expression as used
			return '(' + expr1 + " $ASSIGN " + expr2 + ')';
		}
		
		@Override
		public String visitVariableDeclarators(JavaParser.VariableDeclaratorsContext ctx) {
			// variableDeclarators :  variableDeclarator (',' variableDeclarator)*
			StringBuilder vars = new StringBuilder();
			vars.append(visit(ctx.variableDeclarator(0)));
			for (int i = 1; i < ctx.variableDeclarator().size(); ++i)
				vars.append(", ").append(visit(ctx.variableDeclarator(i)));
			return vars.toString();
		}
		
		@Override
		public String visitVariableDeclarator(JavaParser.VariableDeclaratorContext ctx) {
			// variableDeclarator :  variableDeclaratorId ('=' variableInitializer)?
			//
			// variableDeclaratorId :  Identifier ('[' ']')*
			//
			String init = "";
			String varID = ctx.variableDeclaratorId().Identifier().getText();
			if (ctx.variableInitializer() != null) {
				init = visit(ctx.variableInitializer());
				if (isUsableExpression(init))
					useList.add(init);
				defList.add(varID);
				init = " $INIT " + init;
			}
			return "$VAR " + varID + init;
		}
		
		@Override
		public String visitVariableInitializer(JavaParser.VariableInitializerContext ctx) {
			// variableInitializer :  arrayInitializer  |  expression
			//
			// arrayInitializer :  '{' (variableInitializer (',' variableInitializer)* (',')? )? '}'
			//
			if (ctx.expression() != null)
				return visit(ctx.expression());
			
			StringBuilder arrayInit = new StringBuilder();
			for (JavaParser.VariableInitializerContext initCtx : 
					ctx.arrayInitializer().variableInitializer()) {
				String init = visit(initCtx);
				if (isUsableExpression(init))
					useList.add(init);
				arrayInit.append(init).append(", ");
			}
			return "{ " + arrayInit.toString() + " }";
		}

		/*****************************************************
		 *****************************************************
		 *****************************************************/
		
		/**
		 * Get the original program text for the given parser-rule context.
		 * This is required for preserving whitespaces.
		 */
		private String getOriginalCodeText(ParserRuleContext ctx) {
			int start = ctx.start.getStartIndex();
			int stop = ctx.stop.getStopIndex();
			Interval interval = new Interval(start, stop);
			return ctx.start.getInputStream().getText(interval);
		}
		
	}
	
}


/**
 * A simple structure to store DEF information about a Java method.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
class MethodDefInfo {
	
	// Method ID
	public final String NAME;
	public final String PACKAGE;
	public final String RET_TYPE;
	public final String CLASS_NAME;
	public final String[] PARAM_TYPES;
	
	// DEF Info
	private boolean stateDEF;
	private boolean[] argDEFs;
	private List<String> fieldDEFs;
	
	public MethodDefInfo(String ret, String name, String pkg, String cls, String[] args) {
		NAME = name;
		RET_TYPE = ret;
		CLASS_NAME = cls;
		PACKAGE = pkg == null ? "" : pkg;
		PARAM_TYPES = args == null ? new String[0] : args;
		//
		fieldDEFs = new ArrayList<>(); 
		stateDEF = guessByTypeOrName(); 
		argDEFs = new boolean[PARAM_TYPES.length];  // all initialized to 'false'
	}

	private boolean guessByTypeOrName() {
		// First check if this method is a constructor ...
		if (RET_TYPE == null)
			return true;
		// If not, then try to guess by method-name ...
		String[] prefixes = { "set", "put", "add", "insert", "push", "append" };
		for (String pre: prefixes)
			if (NAME.toLowerCase().startsWith(pre))
				return true;
		return false;
	}

	public boolean doesStateDEF() {
		return stateDEF;
	}

	public void setStateDEF(boolean stateDef) {
		stateDEF = stateDef;
	}

	public boolean[] argDEFs() {
		return argDEFs;
	}
	
	public void setArgDEF(int argIndex, boolean def) {
		argDEFs[argIndex] = def;
	}

	public void setAllArgDEFs(boolean[] argDefs) {
		argDEFs = argDefs;
	}

	public String[] fieldDEFs() {
		return fieldDEFs.toArray(new String[fieldDEFs.size()]);
	}

	public void addFieldDEF(String fieldName) {
		if (!fieldDEFs.contains(fieldName)) {
			fieldDEFs.add(fieldName);
			stateDEF = true;
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MethodDefInfo))
			return false;
		MethodDefInfo info = (MethodDefInfo) obj;
		return this.NAME.equals(info.NAME) && this.CLASS_NAME.equals(info.CLASS_NAME)
				&& this.PACKAGE.equals(info.PACKAGE) && this.RET_TYPE.equals(info.RET_TYPE)
				&& Arrays.equals(this.PARAM_TYPES, info.PARAM_TYPES);
	}

	@Override
	public String toString() {
		String retType = RET_TYPE == null ? "null" : RET_TYPE;
		String args = PARAM_TYPES == null ? "null" : Arrays.toString(PARAM_TYPES);
		StringBuilder str = new StringBuilder();
		str.append("{ TYPE : \"").append(retType).append("\", ");
		str.append("NAME : \"").append(NAME).append("\", ");
		str.append("ARGS : ").append(args).append(", ");
		str.append("CLASS : \"").append(CLASS_NAME).append("\", ");
		str.append("PACKAGE : \"").append(PACKAGE).append("\" }");
		return str.toString();
	}
}
