/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import ghaffarian.graphs.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import ghaffarian.progex.graphs.cfg.CFEdge;
import ghaffarian.progex.graphs.cfg.CFNode;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;

/**
 * A Interprocedural Control Flow Graph (ICFG) builder for Java programs. 
 * A Java parser generated via ANTLRv4 is used
 * for this purpose. This implementation is based on ANTLRv4's Visitor pattern.
 *
 * @author Hossein Homaei, Seyed Mohammad Ghaffarian
 */
public class JavaICFGBuilder {

	private static ArrayList<JavaClass> javaClasses = new ArrayList<>();
	private static ArrayList<JavaClass> alwaysAvailableClasses = new ArrayList<>();
	private static ArrayList<JavaClass> currentFileClasses = new ArrayList<>();

	public static ControlFlowGraph buildForAll(String[] javaFilePaths) throws IOException {
		File[] javaFiles = new File[javaFilePaths.length];
		for (int i = 0; i < javaFiles.length; ++i) 
			javaFiles[i] = new File(javaFilePaths[i]);
		return buildForAll(javaFiles);
	}

	public static ControlFlowGraph buildForAll(File[] javaFiles) throws IOException {
		//Map<ParserRuleContext, MethodKey>[] ctxToKey = new Map<ParserRuleContext, MethodKey>[files.length];
		// Parse all Java source files
		ParseTree[] parseTrees = new ParseTree[javaFiles.length];
		for (int i = 0; i < javaFiles.length; i++) {
			InputStream is = new FileInputStream(javaFiles[i]);
			ANTLRInputStream input = new ANTLRInputStream(is);
			JavaLexer lexer = new JavaLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			JavaParser parser = new JavaParser(tokens);
			parseTrees[i] = parser.compilationUnit();
		}

		//Extract all class-info
		for (File javaFile : javaFiles) {
			try {
				for (JavaClass jc : JavaClassExtractor.extractInfo(javaFile)) 
					javaClasses.add(jc);
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
		//Extract Java Lang class Info
		alwaysAvailableClasses.addAll(JavaClassExtractor.extractJavaLangInfo());

		// for each Parse-Tree, construct visitor and call visit(tree) 
		//    returns: Map<ParserRuleContext, MethodKey> 'ctxToKey' per each java file		
		Map<ParserRuleContext, Object>[] ctxToKey = new Map[parseTrees.length];
		for (int i = 0; i < parseTrees.length; i++) 
			ctxToKey[i] = new IdentityHashMap<>();

		for (int i = 0; i < parseTrees.length; i++) {
			currentFileClasses.clear();
			currentFileClasses.addAll(JavaClassExtractor.extractInfo(javaFiles[i]));
			ICFGVisitor icfgvisit = new ICFGVisitor();
			icfgvisit.visit(parseTrees[i]);
			ctxToKey[i] = icfgvisit.getMap();
		}
		//for each parse-tree, build CFG using 'ctxToKey'[i] as the Contextual-Property map
		//    and use the string "calls" as the Contextual-Property key	
		ControlFlowGraph[] cfgs = new ControlFlowGraph[javaFiles.length];;
		for (int i = 0; i < javaFiles.length; i++) 
			cfgs[i] = JavaCFGBuilder.build(javaFiles[i].getName(), parseTrees[i], "calls", ctxToKey[i]);

		// Build a new Control-Flow-Graph which is the ICFG
		// first, add each cfg to the icfg + the NotImplemented node 
		// then, add all method entries to the ICFG graph
		ControlFlowGraph icfg = new ControlFlowGraph("ICFG.java");
//		CFNode notImplemented = new CFNode();
//		notImplemented.setLineOfCode(0);
//		notImplemented.setCode("Not Implemented");
//		notImplemented.setProperty("class", "Not Implemented");
//		notImplemented.setProperty("name", "notImplemented");
//		ArrayList<CFNode> temp = new ArrayList<>();
//		temp.add(notImplemented);
//		notImplemented.setProperty("exits", temp);
//		icfg.addVertex(notImplemented);
//		icfg.addMethodEntry(notImplemented);

		for (ControlFlowGraph cfg : cfgs) {
			for (CFNode entry : cfg.getAllMethodEntries()) {
				if (cfg.getPackage() != null) {
					entry.setProperty("packageName", cfg.getPackage());
				} else {
					entry.setProperty("packageName", "");
				}
				ArrayList<CFNode> exitpoints = new ArrayList<>();
				/////////////////////////////////////////
                GraphTraversal<CFNode, CFEdge> iter = new DepthFirstTraversal<>(cfg, entry);
				while (iter.hasNext()) {
					CFNode node = iter.nextVertex();
					if (cfg.getOutDegree(node) == 0) {
						exitpoints.add(node);
					}
				}
				entry.setProperty("exits", exitpoints);
				icfg.addGraph(cfg);
				icfg.addMethodEntry(entry);
			}
		}
		// for each CFG, get all method entries, and build the second map 
		// returns: Map<MethodKey, CFNode> 'keyToEntry' per each java files
		Map<MethodKey, CFNode> keyToEntry = new HashMap<>();
		CFNode[] entries = icfg.getAllMethodEntries();
		for (CFNode node : entries) {
			MethodKey key = new MethodKey((String) node.getProperty("packageName"), (String) node.getProperty("class"), (String) node.getProperty("name"), node.getLineOfCode());
			keyToEntry.put(key, node);
		}

//		MethodKey keyNotImpl = new MethodKey("Not Implemented", "Not Implemented", "notImplemented", 0);
//		keyToEntry.put(keyNotImpl, notImplemented);
		GraphTraversal<CFNode, CFEdge> iter = new DepthFirstTraversal<>(icfg, icfg.getAllMethodEntries()[0]);
		while (iter.hasNext()) {
			CFNode node = iter.nextVertex();
			ArrayList<MethodKey> keys = (ArrayList<MethodKey>) node.getProperty("calls");
			if (keys != null) {
				// ToDO: Fix the counter problem when calling more than one method in a single statement
				// The problem should be fixed in the visitor class 
				// int counter = 1; 
				for (MethodKey key : keys) {
					//if (key != null) {
					CFNode entry = keyToEntry.get(key);
					if (entry != null) {// then this is a call-site
						// add CALLS edge from 'node' to 'entry'
						if (!icfg.containsEdge(node, entry)) {
							icfg.addEdge(new Edge<>(node, new CFEdge(CFEdge.Type.CALLS/*, counter++*/), entry));
							for (CFNode exitNode : (ArrayList<CFNode>) entry.getProperty("exits")) {
								icfg.addEdge(new Edge<>(exitNode, new CFEdge(CFEdge.Type.RETURN), node));
							}
						}
						//System.out.println("Node code:"+node.getCode()+"Entry:"+entry.getCode());
					}
				}
			}
		}

		return icfg;
	}

	private static class ICFGVisitor extends JavaBaseVisitor<String> {

		private ArrayList<JavaClass> availableClasses;
		private Deque<JavaClass> activeClasses;
		private LinkedHashMap<String, String> globalVariables;
		private LinkedHashMap<String, JavaClass> localVariables;
		private String currentPackageName;
		private ArrayList<JavaMethod> returnMethod;
		private JavaClass returnType;
		private JavaMethod notImplemented;
		private Map<ParserRuleContext, Object> contextualProperties;
		private ParserRuleContext currentContext;

		public ICFGVisitor() {
			activeClasses = new ArrayDeque<>();
			globalVariables = new LinkedHashMap<>();
			localVariables = new LinkedHashMap<>();
			availableClasses = new ArrayList<>();
			// = new ArrayList<>();
			currentPackageName = "";
			returnMethod = new ArrayList<>();
			returnType = null;
			notImplemented = new JavaMethod("", false, false, "", "NULL", null, 0);
			contextualProperties = new HashMap<>();
			currentContext = null;
			availableClasses.addAll(alwaysAvailableClasses);
			availableClasses.addAll(currentFileClasses);
		}

		@Override
		public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
			//packageDeclaration
			//annotation* 'package' qualifiedName ';'
			currentPackageName = ctx.qualifiedName().getText();
			//All classes of a package can be used in other files in the same package
			for (JavaClass jc : javaClasses) {
				if (jc.PACKAGE.equals(currentPackageName)) {
					availableClasses.add(jc);
				}
			}
			return null;
		}

		@Override
		public String visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
			//importDeclaration
			//'import' 'static'? qualifiedName ('.' '*')? ';'
			//qualifiedName
			//Identifier ('.' Identifier)*

			String importedPackage = ctx.qualifiedName().getText();

			if (ctx.getText().contains(".*")) {
				for (JavaClass jc : javaClasses) {
					if (importedPackage.equals(jc.PACKAGE)) {
						availableClasses.add(jc);
					}
				}
			} else {
				for (JavaClass jc : javaClasses) {
					if (importedPackage.equals(jc.PACKAGE + '.' + jc.NAME)) {
						availableClasses.add(jc);
					}
				}
			}
			return null;
		}

		@Override
		public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {

			for (JavaClass jc : javaClasses) {
				if (jc.NAME.equals(ctx.Identifier().getText()) && jc.PACKAGE.equals(currentPackageName)) {
					activeClasses.push(jc);
					break;
				}
			}

			//Add current class fields to he global variables list
			for (JavaClass cls : activeClasses) {
				for (JavaField jf : cls.getAllFields()) {
					globalVariables.put(jf.NAME, jf.TYPE);
				}
			}

			//Extract imported class information from Java standard library 
			try {
				for (JavaClass jc : JavaClassExtractor.extractImportsInfo(activeClasses.peek().IMPORTS)) {
					if (!availableClasses.contains(jc)) {
						availableClasses.add(jc);
					}
				}

			} catch (IOException ex) {
				System.err.println(ex);
			}

			visit(ctx.classBody());

			//clear the list and regenerate it. (required for inner class)
			globalVariables.clear();
			activeClasses.pop();
			for (JavaClass cls : activeClasses) {
				for (JavaField jf : cls.getAllFields()) {
					globalVariables.put(jf.NAME, jf.TYPE);
				}
			}
			return null;
		}

		@Override
		public String visitBlock(JavaParser.BlockContext ctx) {
			//block:   '{' blockStatement* '}'
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			visitChildren(ctx);
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
			//'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			for (JavaParser.SwitchBlockStatementGroupContext cx : ctx.switchBlockStatementGroup()) {
				visit(cx);
			}
			for (JavaParser.SwitchLabelContext cx : ctx.switchLabel()) {
				visit(cx);
			}
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitIfStatement(JavaParser.IfStatementContext ctx) {
			//'if' parExpression statement ('else' statement)?
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			for (JavaParser.StatementContext cx : ctx.statement()) {
				visit(cx);
			}
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitWhileStatement(JavaParser.WhileStatementContext ctx) {
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			visit(ctx.statement());
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			visit(ctx.statement());
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitForStatement(JavaParser.ForStatementContext ctx) {
			// 'for' '(' forControl ')' statement
			//forControl:
			//       enhancedForControl |   forInit? ';' expression? ';' forUpdate?
			//enhancedForControl:variableModifier* typeType variableDeclaratorId ':' expression
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);

			if (ctx.forControl().enhancedForControl() != null) {
				currentContext = ctx.forControl().enhancedForControl();
				visit(ctx.forControl().enhancedForControl());
				currentContext = null;
			} else {
				if (ctx.forControl().forInit() != null) {
					currentContext = ctx.forControl().forInit();
					visitChildren(ctx.forControl().forInit());
					currentContext = null;
				}
				if (ctx.forControl().expression() != null) {
					currentContext = ctx.forControl().expression();
					visit(ctx.forControl().expression());
					currentContext = null;
				}
				if (ctx.forControl().forUpdate() != null) {
					currentContext = ctx.forControl().expression();
					visit(ctx.forControl().forUpdate());
					currentContext = null;
				}
			}
			visit(ctx.statement());

			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
			// statementExpression ';'
			currentContext = ctx;
			visitChildren(ctx);
			currentContext = null;
			return null;
		}

		@Override
		public String visitEnhancedForControl(JavaParser.EnhancedForControlContext ctx) {
			//enhancedForControl:   variableModifier* typeType variableDeclaratorId ':' expression
			String name = ctx.variableDeclaratorId().Identifier().getText();
			StringBuilder type = new StringBuilder(visit(ctx.typeType()));
			int idx = ctx.variableDeclaratorId().getText().indexOf('[');
			if (idx > 0) {
				type.append(ctx.variableDeclaratorId().getText().substring(idx));
			}
			localVariables.put(name, findClassbyName(type.toString()));
			visit(ctx.expression());
			return null;
		}

		@Override
		public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
			// 'return' expression? ';'
			currentContext = ctx;
			if(ctx.expression() != null)
				visit(ctx.expression());
			return null;
		}

		@Override
		public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
			// 'synchronized' parExpression block
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			visit(ctx.block());
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override
		public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
			// 'throw' expression ';'
			currentContext = ctx;
			visit(ctx.expression());
			currentContext = null;
			return null;
		}

		@Override
		public String visitTryStatement(JavaParser.TryStatementContext ctx) {
			// 'try' block (catchClause+ finallyBlock? | finallyBlock)
			LinkedHashMap<String, JavaClass> tempLocalVariables = new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			visit(ctx.block());
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);

			if (ctx.finallyBlock() != null) {
				// 'finally' block
				tempLocalVariables.clear();
				tempLocalVariables.putAll(localVariables);
				visit(ctx.finallyBlock().block());
				localVariables.clear();
				localVariables.putAll(tempLocalVariables);
			}

			// Now visit any available catch clauses
			if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block

				for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
					tempLocalVariables.clear();
					tempLocalVariables.putAll(localVariables);
					String name = cx.Identifier().getText();
					//Assume that the catch type is a simple class name, for now
					String type = cx.catchType().getText();
					localVariables.put(name, findClassbyName(type));
					visit(cx.block());
					localVariables.clear();
					localVariables.putAll(tempLocalVariables);
				}
			}

			return null;
		}

		@Override
		public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			/*
			localVariableDeclarationStatement:    localVariableDeclaration ';'
			localVariableDeclaration:		variableModifier* typeType variableDeclarators
			variableDeclarators:   variableDeclarator (',' variableDeclarator)*
			variableDeclarator:   variableDeclaratorId ('=' variableInitializer)?
			variableDeclaratorId:   Identifier ('[' ']')*
			variableInitializer:   arrayInitializer	|   expression
			 */
			//Store variable types in the 'loccalVariables' map
			for (JavaParser.VariableDeclaratorContext var : ctx.variableDeclarators().variableDeclarator()) {
				String name = var.variableDeclaratorId().Identifier().getText();
				StringBuilder type = new StringBuilder(visit(ctx.typeType()));
				int idx = var.variableDeclaratorId().getText().indexOf('[');
				if (idx > 0) {
					type.append(var.variableDeclaratorId().getText().substring(idx));
				}
				localVariables.put(name, findClassbyName(type.toString()));
			}

			currentContext = ctx;
			visitChildren(ctx);
			currentContext = null;
			return null;
		}

		@Override
		public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			// methodDeclaration
			//   :  (typeType|'void') Identifier formalParameters ('[' ']')*
			//      ('throws' qualifiedNameList)? ( methodBody | ';' )
			//
			// formalParameters 
			//   :  '(' formalParameterList? ')'
			//
			// formalParameterList
			//   :  formalParameter (',' formalParameter)* (',' lastFormalParameter)?
			//   |  lastFormalParameter
			//
			// formalParameter
			//   :  variableModifier* typeType variableDeclaratorId
			//
			// lastFormalParameter
			//   :  variableModifier* typeType '...' variableDeclaratorId
			//
			// variableDeclaratorId
			//   :  Identifier ('[' ']')*

			if (ctx.formalParameters().formalParameterList() != null) {
				for (JavaParser.FormalParameterContext param
						: ctx.formalParameters().formalParameterList().formalParameter()) {
					localVariables.put(param.variableDeclaratorId().getText(), findClassbyName(visit(param.typeType())));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), findClassbyName(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType())));
				}
			}

			visit(ctx.methodBody());
			localVariables.clear();
			return null;
		}

		@Override
		public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
			// constructorDeclaration
			//   :  Identifier formalParameters ('throws' qualifiedNameList)? constructorBody

			if (ctx.formalParameters().formalParameterList() != null) {
				for (JavaParser.FormalParameterContext param
						: ctx.formalParameters().formalParameterList().formalParameter()) {
					localVariables.put(param.variableDeclaratorId().getText(), findClassbyName(visit(param.typeType())));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), findClassbyName(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType())));
				}
			}

			visit(ctx.constructorBody());
			localVariables.clear();
			return null;
		}

		@Override
		public String visitExprPrimary(JavaParser.ExprPrimaryContext ctx) {
			/*
			primary  :   '(' expression ')'
			|   'this'
			|   'super'
			|   literal
			|   Identifier
			|   typeType '.' 'class'
			|   'void' '.' 'class'
			|   nonWildcardTypeArguments (explicitGenericInvocationSuffix | 'this' arguments)
			;
			 */

			JavaClass currentClass = activeClasses.peek();

			if (ctx.primary().getText().equals("this")) {
				returnType = currentClass;
				returnMethod = findMethodbyName(currentClass, currentClass.NAME);
				return null;
			}

			if (ctx.primary().getText().equals("super")) {
				returnType = findClassbyName(currentClass.EXTENDS);
				if (returnType == null) {
					return null;
				}
				returnMethod = findMethodbyName(returnType, returnType.NAME);
				return null;
			}

			//literal
			if (ctx.primary().literal() != null) {
				if (ctx.primary().literal().BooleanLiteral() != null) {
					returnType = findClassbyName("Boolean");
				}
				//returnType =  new JavaClass("bool", "BuiltinType", "", "");

				if (ctx.primary().literal().CharacterLiteral() != null) {
					returnType = findClassbyName("Character");
				}
				//returnType = new JavaClass("char", "BuiltinType", "", "");

				if (ctx.primary().literal().FloatingPointLiteral() != null) {
					returnType = findClassbyName("Float");
				}
				//returnType = new JavaClass("float", "BuiltinType", "", "");

				if (ctx.primary().literal().IntegerLiteral() != null) {
					returnType = findClassbyName("Integer");
				}
				//returnType = new JavaClass("int", "BuiltinType", "", "");

				if (ctx.primary().literal().StringLiteral() != null) {
					returnType = findClassbyName("String");
				}
				//returnType = new JavaClass("String", "BuiltinType", "", "");
				return null;
			}

			if (ctx.primary().Identifier() != null) {
				//When the identifier is an object
				returnType = findVariableType(ctx.primary().Identifier().getText());
				//When the identifier is a class name
				//if(returnType != null) 
				//	returnType = findClassbyName(ctx.primary().Identifier().getText());
				//returnType = findClassbyNameInAvailableClasses(ctx.primary().Identifier().getText());
				//When the identifier is a local method call
				if (returnType == null) {
					returnMethod = findMethodbyName(currentClass, ctx.primary().Identifier().getText());
				}
				return null;
			}
			return visitChildren(ctx);
		}

		@Override
		public String visitExprDotID(JavaParser.ExprDotIDContext ctx) {
			/*
			expression '.' Identifier
			 */
			visit(ctx.expression());

			ArrayList<JavaMethod> currentMethod;
			//JavaClass fieldType 
			JavaClass fieldType = findFieldType(returnType, ctx.Identifier().getText());
			if (fieldType == null) {
				currentMethod = findMethodbyName(returnType, ctx.Identifier().getText());
				if (!currentMethod.isEmpty()) {
					returnMethod = currentMethod;
					//returnType = findClassbyName(returnMethod.get(0).RET_TYPE);
				}

			} else {
				returnType = fieldType;
			}
			return null;
		}

		@Override
		public String visitExprDotNewInnerCreator(JavaParser.ExprDotNewInnerCreatorContext ctx) {
			/*
			expression '.' 'new' nonWildcardTypeArguments? innerCreator
			innerCreator:   Identifier nonWildcardTypeArgumentsOrDiamond? classCreatorRest
			classCreatorRest:   arguments classBody?
			 */
			visit(ctx.expression());
			String innerClassName = ctx.innerCreator().Identifier().getText();
			JavaClass innerClass = findClassbyName(innerClassName);
			ArrayList<JavaMethod> innerCreatorMethods = findMethodbyName(innerClass, innerClassName);
			ArrayList<JavaClass> actualArguments = new ArrayList<>();
			if (ctx.innerCreator().classCreatorRest().arguments().expressionList() != null) {
				for (JavaParser.ExpressionContext expr
						: ctx.innerCreator().classCreatorRest().arguments().expressionList().expression()) {
					visit(expr);
					actualArguments.add(returnType);
				}
			}
			//JavaMethod actualMethod = createCLGNode(ctx.innerCreator().Identifier().getText(), ctx.getStart().getLine(), creatorMethods, actualArguments);
			if (currentContext == null) {
				currentContext = ctx.expression();
			}
			createCLGNode(innerClass, innerCreatorMethods, actualArguments);
			//createCLGNode(ctx.expression(), innerClass, innerCreatorMethods, actualArguments);
			returnType = innerClass;
			return null;
		}

		@Override
		public String visitExprDotThis(JavaParser.ExprDotThisContext ctx) {
			/*
			expression '.' 'this'
			 */
			visit(ctx.expression());

			if (returnType != null) {
				returnMethod = findMethodbyName(returnType, returnType.NAME);
			}
			return null;
		}

		@Override
		public String visitExprDotSuper(JavaParser.ExprDotSuperContext ctx) {
			/*
			expression '.' 'super' superSuffix
			 */
			visit(ctx.expression());
			JavaClass parent = null;
			//ArrayList<JavaMethod> creators = new ArrayList<>();
			if (returnType != null) {
				parent = findClassbyName(returnType.EXTENDS);
			}
			if (parent != null) {
				returnMethod = findMethodbyName(parent, parent.NAME);
				returnType = parent;
			}
			return null;
		}

		@Override
		public String visitExprDotGenInvok(JavaParser.ExprDotGenInvokContext ctx) {
			/*
			expression '.' explicitGenericInvocation
			 */
			return visitChildren(ctx);
		}

		@Override
		public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx) {
			/*
			expression '(' expressionList? ')'
			expressionList:   expression (',' expression)*
			 */

			returnType = null;
			returnMethod.clear();

			visit(ctx.expression());

			ArrayList<JavaMethod> possibleCalls = new ArrayList<>();
			possibleCalls.addAll(returnMethod);
			JavaClass calleeClass = null;

			//For local method calls
			if (!returnMethod.isEmpty()) {
				if (returnType == null) {
					calleeClass = activeClasses.peek();
				} else {
					calleeClass = returnType;
				}
			}
			ArrayList<JavaClass> actualTypes = new ArrayList<>();
			returnType = null;
			returnMethod.clear();
			if (ctx.expressionList() != null) {
				for (JavaParser.ExpressionContext expr : ctx.expressionList().expression()) {
					visit(expr);
					actualTypes.add(returnType);
					returnType = null;
					returnMethod.clear();
				}
			}
			if (currentContext == null) {
				currentContext = ctx.expression();
			}

			JavaMethod actualMethod = createCLGNode(calleeClass, possibleCalls, actualTypes);

			if (actualMethod != null) {
				returnType = findClassbyName(actualMethod.RET_TYPE);
			} else {
				returnType = null;
			}
			return null;
		}

		@Override
		public String visitExprNewCreator(JavaParser.ExprNewCreatorContext ctx) {
			/*
			'new' creator	
			creator:   nonWildcardTypeArguments createdName classCreatorRest
					|  createdName (arrayCreatorRest | classCreatorRest)
			nonWildcardTypeArguments:   '<' typeList '>'
			typeList:   typeType (',' typeType)*
			createdName:   Identifier typeArgumentsOrDiamond? ('.' Identifier typeArgumentsOrDiamond?)*
						|  primitiveType
			typeArgumentsOrDiamond:   '<' '>'    |   typeArguments
			typeArguments:   '<' typeArgument (',' typeArgument)* '>'
			classCreatorRest:   arguments classBody?
			arrayCreatorRest:   '['
								(   ']' ('[' ']')* arrayInitializer
								|   expression ']' ('[' expression ']')* ('[' ']')*
								)
			 */
			int last;
			JavaClass lastClass = null;
			if (ctx.creator().createdName().primitiveType() == null) {
				last = ctx.creator().createdName().Identifier().size() - 1;
				String className = ctx.creator().createdName().Identifier(last).getText();
				lastClass = findClassbyName(className);
			}

			ArrayList<JavaMethod> creators = new ArrayList<>();
			ArrayList<JavaClass> actualArguments = new ArrayList<>();
			if (lastClass != null) {
				creators = findMethodbyName(lastClass, lastClass.NAME);
				if (ctx.creator().classCreatorRest() != null) {
					if (ctx.creator().classCreatorRest().arguments() != null) {
						if (ctx.creator().classCreatorRest().arguments().expressionList() != null) {
							for (JavaParser.ExpressionContext expr : ctx.creator().classCreatorRest().arguments().expressionList().expression()) {
								visit(expr);
								actualArguments.add(returnType);
							}
						}
					}
				}
				if (currentContext == null) {
					currentContext = ctx.creator().createdName();
				}
				createCLGNode(lastClass, creators, actualArguments);
			} else {
				if (currentContext == null) {
					currentContext = ctx.creator().createdName();
				}
				createCLGNode(lastClass, creators, actualArguments);
			}
			returnType = lastClass;
			if (ctx.creator().classCreatorRest() != null) {
				if (ctx.creator().classCreatorRest().classBody() != null) {
					visitChildren(ctx.creator().classCreatorRest().classBody());
				}
			}
			if (ctx.creator().arrayCreatorRest() != null) {
				visitChildren(ctx.creator().arrayCreatorRest());
			}
			return null;
		}

		@Override
		public String visitExprCasting(JavaParser.ExprCastingContext ctx) {
			/*
			'(' typeType ')' expression
			 */
			visit(ctx.expression());
			returnType = findClassbyName(visitTypeType(ctx.typeType()));
			return null;
		}

		@Override
		public String visitTypeType(JavaParser.TypeTypeContext ctx) {
			// typeType
			//   :  classOrInterfaceType ('[' ']')*  |  primitiveType ('[' ']')*	
			StringBuilder type = new StringBuilder(visit(ctx.getChild(0)));
			int idx = ctx.getText().indexOf('[');
			if (idx > 0) {
				type.append(ctx.getText().substring(idx));
			}
			return type.toString();
		}

		@Override
		public String visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
			// classOrInterfaceType
			//   :  Identifier typeArguments? ('.' Identifier typeArguments? )*
			//typeArguments:   '<' typeArgument (',' typeArgument)* '>'

			StringBuilder typeID = new StringBuilder(ctx.Identifier(0).getText());
			if (ctx.typeArguments(0) != null) {
				typeID.append(ctx.typeArguments(0).getText());
			}
			int i = 1;
			for (TerminalNode id : ctx.Identifier().subList(1, ctx.Identifier().size())) {
				typeID.append(".").append(id.getText());
				if (ctx.typeArguments(i) != null) {
					typeID.append(ctx.typeArguments(i).getText());
				}
				i++;
			}
			return typeID.toString();
		}

		@Override
		public String visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
			// primitiveType : 
			//     'boolean' | 'char' | 'byte' | 'short'
			//     | 'int' | 'long' | 'float' | 'double'

			return ctx.getText();
		}

		public Map<ParserRuleContext, Object> getMap() {
			return contextualProperties;
		}

		private JavaMethod[] MergeArrays(JavaMethod[] jm1, JavaMethod[] jm2) {
			if (jm1 == null) {
				return jm2;
			}
			if (jm2 == null) {
				return jm1;
			}
			int sizeofArray1 = jm1.length;
			int sizeofArray2 = jm2.length;
			JavaMethod[] result = new JavaMethod[sizeofArray1 + sizeofArray2];
			System.arraycopy(jm1, 0, result, 0, sizeofArray1);
			System.arraycopy(jm2, 0, result, sizeofArray1, sizeofArray2);
			return result;
		}

		private ArrayList<JavaMethod> findMethodbyName(JavaClass currentClass, String methodName) {

			ArrayList<JavaMethod> matchedMethods = new ArrayList<>();

			if (currentClass == null) {
				return matchedMethods;
			}
			//currentClass = activeClasses.peek();

			for (JavaMethod jm : currentClass.getAllMethods()) {
				if (jm.NAME.equals(methodName)) {
					matchedMethods.add(jm);
				}
			}

			if (!matchedMethods.isEmpty()) {
				return matchedMethods;
			}

			if (currentClass.EXTENDS != null) {
				JavaClass jc = findClassbyName(currentClass.EXTENDS);
				if (jc != null) {
					matchedMethods = findMethodbyName(jc, methodName);
				}
			}

			return matchedMethods;
		}

		private JavaClass findFieldType(JavaClass cls, String fieldName) {
			if (cls == null) {
				return null;
			}
			String fieldType = null;
			for (JavaField jf : cls.getAllFields()) {
				if (jf.NAME.equals(fieldName)) {
					fieldType = jf.TYPE;
				}
			}
			return findClassbyName(fieldType);

		}

		private JavaClass findVariableType(String var) {
			if (!localVariables.isEmpty()) {
				for (String str : localVariables.keySet()) {
					if (str.equals(var)) {
						return localVariables.get(str);
					}
				}
			}

			if (!globalVariables.isEmpty()) {
				for (String str : globalVariables.keySet()) {
					if (str.equals(var)) {
						return findClassbyName(globalVariables.get(str));
					}
				}
			}
			return null;
		}

		private JavaClass findClassbyName(String className) {

			if (className == null) {
				return null;
			}

			StringBuilder clsName = new StringBuilder(className);
			String genericTypes = null;
			int idx = className.indexOf('<');
			if (idx > 0) {
				genericTypes = className.substring(idx);
				clsName.delete(idx, clsName.length());
			}
			className = clsName.toString();

			for (JavaClass jc : availableClasses) {
				if (jc.NAME.equals(className)) {
					JavaClass cls = classInstantiate(jc, genericTypes);
					return cls;
				}
			}
			return null;
		}

		private boolean isActualMethod(JavaMethod possibleMethod, ArrayList<JavaClass> actualTypes) {
			if (possibleMethod.ARG_TYPES == null) {
				if (actualTypes.isEmpty()) {
					return true;
				} else {
					return false;
				}
			}
			if (actualTypes.size() != possibleMethod.ARG_TYPES.length) {
				return false;
			}

			for (int i = 0; i < actualTypes.size(); i++) {
				String s1 = possibleMethod.ARG_TYPES[i];
				if (actualTypes.get(i) != null) {
					String s2 = actualTypes.get(i).NAME;
					if (!typeIsMatched(s1, s2)) {
						return false;
					}
				}
			}
			return true;
		}

		private boolean typeIsMatched(String s1, String s2) {
			return s1.equals(s2) || s1.equals(alias(s2)) || s2.equals(alias(s1)) || s1.equals("Object") || s2.equals("Object");
		}

		private String alias(String s) {
			if (s.equals("Integer"))
				return "int";
			if (s.equals("Character"))
				return "char";
			if (s.equals("Float") || s.equals("Boolean"))
				return s.toLowerCase();
			return s;
		}

		private JavaMethod createCLGNode(JavaClass cls, ArrayList<JavaMethod> possibleCalls, ArrayList<JavaClass> actualTypes) {
			JavaMethod actualMethod = null;
			if (possibleCalls.isEmpty()) {
				actualMethod = notImplemented;
			} else {
				for (JavaMethod jm : possibleCalls) {
					if (isActualMethod(jm, actualTypes)) {
						actualMethod = jm;
						break;
					}
				}
			}

			addKey(cls, actualMethod);
			return actualMethod;
		}

		private void addKey(JavaClass cls, JavaMethod method) {
			MethodKey key;
			if (method != null && cls != null) {
				key = new MethodKey(cls.PACKAGE, cls.NAME, method.NAME, method.LINE_OF_CODE);
			} else {
				key = new MethodKey("Not Implemented", "Not Implemented", "notImplemented", 0);
			}
			ArrayList<MethodKey> keys = new ArrayList<>();
			keys.add(key);
			if (contextualProperties.containsKey(currentContext)) {
				keys.addAll((ArrayList<MethodKey>) contextualProperties.get(currentContext));
			}
			contextualProperties.put(currentContext, keys);
		}

		private JavaClass classInstantiate(JavaClass cls, String generic){
			if(generic == null)
				return cls;
			else{
				JavaClass instance = new JavaClass(cls.NAME, cls.PACKAGE, cls.EXTENDS, cls.FILE, cls.IMPORTS);
				String genericTypes = generic.substring(1, generic.length()-1).trim();
				instance.setTypeParameters(genericTypes);
				String params = cls.getTypeParameters();
				//String[] genericType = genericTypes.split(",");
				String currentGenericType = "";
				String[] genericParam = params.split(",");
				HashMap<String, String> genericMap = new HashMap<>();
				
				for(int i= 0; i<genericParam.length; i++){
					//genericMap.put(genericParam[i].replace('?', ' ').replaceAll("extends", "").trim(), genericType[i].replace('?', ' ').replaceAll("extends", "").trim());
					if(genericTypes.contains(",")){
						if(genericTypes.contains("<")){
							int index = genericTypes.indexOf("<");
							if(genericTypes.indexOf(",") < index){
								currentGenericType = genericTypes.substring(0, genericTypes.indexOf(","));
								genericTypes = genericTypes.substring(genericTypes.indexOf(",")+1);
							}
							else{
								index += countSteps(genericTypes.substring(genericTypes.indexOf("<")));							
								currentGenericType = genericTypes.substring(0, index);
								if(genericTypes.length() > index)
									genericTypes = genericTypes.substring(index+1);								
							}
						}
						else{
							currentGenericType = genericTypes.substring(0, genericTypes.indexOf(","));
							genericTypes = genericTypes.substring(genericTypes.indexOf(",")+1);
						}
					}
					else
						currentGenericType = genericTypes;
					genericMap.put(genericParam[i].replace('?', ' ').replaceAll("extends", "").trim(), currentGenericType.replace('?', ' ').replaceAll("extends", "").trim());
					//genericMap.put(genericParam[i].trim(), genericType[i].trim());
				}
				for(JavaMethod jm: cls.getAllMethods()){
					//instantiate type of arguments for each method
					String[] args;
					if(jm.ARG_TYPES != null){
						args = new String[jm.ARG_TYPES.length];
						for(int i=0; i< jm.ARG_TYPES.length; i++){
							if (genericMap.keySet().contains(jm.ARG_TYPES[i]))
								args[i] = genericMap.get(jm.ARG_TYPES[i]);
							else
								args[i] = jm.ARG_TYPES[i];
						}
					}
					else
						args = null;
					//instantiate return type for each method
					if(jm.RET_TYPE == null)
						instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, jm.RET_TYPE, jm.NAME, args, jm.LINE_OF_CODE));
					else if (genericMap.keySet().contains(jm.RET_TYPE))
						instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, genericMap.get(jm.RET_TYPE), jm.NAME, args, jm.LINE_OF_CODE));
					else
						instance.addMethod(new JavaMethod(jm.MODIFIER, jm.STATIC, jm.ABSTRACT, jm.RET_TYPE, jm.NAME, args, jm.LINE_OF_CODE));					
				}
				//instantiate class fields
				for(JavaField jf: cls.getAllFields()){
					if(genericMap.keySet().contains(jf.TYPE))
						instance.addField(new JavaField(jf.MODIFIER, jf.STATIC, genericMap.get(jf.TYPE), jf.NAME));
					else
						instance.addField(jf);
				}
				return instance;
			}
		}
		private int countSteps(String str){
			int count = 0;
			for (int i=0; i < str.length(); i++)
			{
				if (str.charAt(i) == '<')
					 count++;
				else if (str.charAt(i) == '>')
					count--;
				if (count == 0)
					return i+1;
			}
			return str.length();
		}		
	}
}

class MethodKey {

	String packageName;
	String className;
	String methodName;
	int LoC;

	public MethodKey(String pkgName, String clsName, String calleeMethodName, int lineOfCode) {
		packageName = pkgName;
		className = clsName;
		methodName = calleeMethodName;
		LoC = lineOfCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof MethodKey)) {
			return false;
		}
		MethodKey key = (MethodKey) obj;
		return this.packageName.equals(key.packageName)
				&& this.className.equals(key.className)
				&& this.methodName.equals(key.methodName)
				&& this.LoC == key.LoC;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 29 * hash + Objects.hashCode(this.packageName);
		hash = 29 * hash + Objects.hashCode(this.className);
		hash = 29 * hash + Objects.hashCode(this.methodName);
		hash = 29 * hash + this.LoC;
		return hash;
	}
}
