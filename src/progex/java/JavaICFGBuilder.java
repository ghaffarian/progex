/*** In The Name of Allah ***/
package progex.java;

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
import org.jgrapht.Graphs;
import org.jgrapht.traverse.GraphIterator;
import org.jgrapht.traverse.DepthFirstIterator;
import progex.graphs.cfg.CFEdge;
import progex.graphs.cfg.CFNode;
import progex.graphs.cfg.ControlFlowGraph;
import progex.java.parser.JavaBaseVisitor;
import progex.java.parser.JavaLexer;
import progex.java.parser.JavaParser;

/**
 * A Interprocedural Control Flow Graph (ICFG) builder for Java programs.
 * A Java parser generated via ANTLRv4 is used for this purpose.
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Hossein Homaei, Seyed Mohammad Ghaffarian
 */
public class JavaICFGBuilder {
	
	private static ArrayList<JavaClass> javaClasses = new ArrayList<>();
	
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
		for (File javaFile: javaFiles) {
			try {
				for (JavaClass jc : JavaClassExtractor.extractInfo(javaFile))
					javaClasses.add(jc);
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
		
		// for each Parse-Tree, construct visitor and call visit(tree) 
		//    returns: Map<ParserRuleContext, MethodKey> 'ctxToKey' per each java file
		
		Map<ParserRuleContext, Object>[] ctxToKey = new Map[parseTrees.length];
		for (int i = 0; i < parseTrees.length; i++)
			ctxToKey[i] = new IdentityHashMap<>();
		
		//Map<ParserRuleContext, Object>[] ctxToKey = new Map[parseTrees.length];
		for (int i = 0; i < parseTrees.length; i++) {
			ICFGVisitor icfgvisit = new ICFGVisitor();
			icfgvisit.visit(parseTrees[i]);
			ctxToKey[i] = icfgvisit.getMap();

		}
		//for each parse-tree, build CFG using 'ctxToKey'[i] as the Contextual-Property map
		//    and use the string "calls" as the Contextual-Property key	
		ControlFlowGraph[] cfgs = new ControlFlowGraph[javaFiles.length];
		for(int i = 0; i < javaFiles.length; i++)
			cfgs[i] = JavaCFGBuilder.build(javaFiles[i].getName(), parseTrees[i], "calls", ctxToKey[i]);

		// Build a new Control-Flow-Graph which is the ICFG
		// first, add each cfg to the icfg + the NotImplemented node 
		// then, add all method entries to the ICFG graph

		ControlFlowGraph icfg = new ControlFlowGraph("ICFG.java");
		CFNode notImplemented = new CFNode();
		notImplemented.setLineOfCode(0);
		notImplemented.setCode("Not Implemented");
		notImplemented.setProperty("class", "Not Implemented");
		notImplemented.setProperty("name", "notImplemented");
		icfg.addVertex(notImplemented);
		
		for (ControlFlowGraph cfg: cfgs) {
			Graphs.addGraph(icfg, cfg);
			for(CFNode entry : cfg.getAllMethodEntries()){
				icfg.addMethodEntry(entry);
			}
		}
		// for each CFG, get all method entries, and build the second map 
		// returns: Map<MethodKey, CFNode> 'keyToEntry' per each java files
		Map<MethodKey, CFNode> keyToEntry = new HashMap<>();
		CFNode[] entries = icfg.getAllMethodEntries();
		for(CFNode node: entries){
			MethodKey key = new MethodKey((String) node.getProperty("packageName"), (String) node.getProperty("class"), (String) node.getProperty("name"), node.getLineOfCode());
			keyToEntry.put(key, node);
		}

		MethodKey keyNotImpl = new MethodKey("Not Implemented", "Not Implemented", "notImplemented", 0);
		keyToEntry.put(keyNotImpl, notImplemented);

		GraphIterator<CFNode, CFEdge> iter = new DepthFirstIterator<>(icfg);
		while (iter.hasNext()) {
			CFNode node = iter.next();
			ArrayList<MethodKey> keys = (ArrayList<MethodKey>) node.getProperty("calls");
			if (keys != null){
				for(MethodKey key: keys){
					//if (key != null) {
					CFNode entry = keyToEntry.get(key);
					if (entry != null) {// then this is a call-site
						// add CALLS edge from 'node' to 'entry'
						if(!icfg.containsEdge(node, entry))
							icfg.addEdge(node, entry, new CFEdge(CFEdge.Type.CALLS));
						//System.out.println("Node code:"+node.getCode()+"Entry:"+entry.getCode());
					}
				}
			}
		}
		
		return icfg;
	}

	
	private static class ICFGVisitor extends JavaBaseVisitor<String> {
		//All classes extracted by JavaClassExtractor
		//private ArrayList<JavaClass> javaClasses;
		//All classes of current package and all imported packages
		private ArrayList<JavaClass> availableClasses;
		//Current class and its inner classes
		private Deque<JavaClass> activeClasses;
		private LinkedHashMap<String, String> globalVariables;
		private LinkedHashMap<String, String> localVariables;
		private String currentPackageName;
		//private ArrayList<MethodKey> invocationList;
		private ArrayList<JavaMethod> returnMethod;
		private JavaClass returnType;
		private JavaMethod notImplemented;
		private Map<ParserRuleContext, Object> contextualProperties;
		private ParserRuleContext currentContext;
		
		public ICFGVisitor(){
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
		}
		
		@Override
		public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
			//packageDeclaration
			//annotation* 'package' qualifiedName ';'
			currentPackageName = ctx.qualifiedName().getText();
			//All classes of a package can be used in other files in the same package
			for(JavaClass jc : javaClasses){
				if(jc.PACKAGE.equals(currentPackageName)) 
					availableClasses.add(jc);
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

			if(ctx.getText().contains(".*")){
				for(JavaClass jc : javaClasses){
					if(importedPackage.equals(jc.PACKAGE))
						availableClasses.add(jc);
				}
			}
			else{
				for(JavaClass jc : javaClasses){
					if(importedPackage.equals(jc.PACKAGE+'.'+jc.NAME)){
						availableClasses.add(jc);
					}		
				}
			}
			return null;
		}

		@Override
		public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx){

			for(JavaClass jc: availableClasses){
				if(jc.NAME.equals(ctx.Identifier().getText()) && jc.PACKAGE.equals(currentPackageName)){
					activeClasses.push(jc);
					break;
				}		
			}

			//Add current class fields to he global variables list
			for(JavaClass cls: activeClasses){
				for(JavaField jf: cls.getAllFields()){
					globalVariables.put(jf.NAME, jf.TYPE);
				}
				//localMethods=MergeArrays(localMethods, cls.getAllMethods());
			}

			visit(ctx.classBody());

			//clear the list and regenerate it. (required for inner class)
			globalVariables.clear();
			//localMethods = null;
			activeClasses.pop();
			for(JavaClass cls: activeClasses){
				for(JavaField jf: cls.getAllFields()){
					globalVariables.put(jf.NAME, jf.TYPE);
				}
				//localMethods=MergeArrays(localMethods, cls.getAllMethods());
			}
			return null;
		}

		@Override 
		public String visitBlock(JavaParser.BlockContext ctx) {
			//block:   '{' blockStatement* '}'
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			visitChildren(ctx);
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null; 
		}

		@Override 
		public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
			//'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			for(JavaParser.SwitchBlockStatementGroupContext cx: ctx.switchBlockStatementGroup()){
				visit(cx);
			}
			for(JavaParser.SwitchLabelContext cx: ctx.switchLabel()){
				visit(cx);
			}			
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null;
		}

		@Override 
		public String visitIfStatement(JavaParser.IfStatementContext ctx) {
			//'if' parExpression statement ('else' statement)?
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = ctx;
			visit(ctx.parExpression());
			currentContext = null;
			for(JavaParser.StatementContext cx: ctx.statement()){
				visit(cx);
			}
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
			return null; 
		}

		@Override 
		public String visitWhileStatement(JavaParser.WhileStatementContext ctx) { 
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
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
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
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
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			
			if (ctx.forControl().enhancedForControl() != null){
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
			if (idx > 0)
				type.append(ctx.variableDeclaratorId().getText().substring(idx));
			localVariables.put(name, type.toString());
			visit(ctx.expression());
			return null; 
		}

		@Override
		public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
			// 'return' expression? ';'
			currentContext = ctx;
			visit(ctx.expression());
			return null;
		}

		@Override
		public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
			// 'synchronized' parExpression block
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
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
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
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

				for (JavaParser.CatchClauseContext cx: ctx.catchClause()) {
					tempLocalVariables.clear();
					tempLocalVariables.putAll(localVariables);
					String name = cx.Identifier().getText();
					//Assume that the catch type is a simple class name, for now
					String type = cx.catchType().getText(); 
					localVariables.put(name, type);
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
			for (JavaParser.VariableDeclaratorContext var: ctx.variableDeclarators().variableDeclarator()){
				String name = var.variableDeclaratorId().Identifier().getText();
				StringBuilder type = new StringBuilder(visit(ctx.typeType()));
				int idx = var.variableDeclaratorId().getText().indexOf('[');
				if (idx > 0)
					type.append(var.variableDeclaratorId().getText().substring(idx));
				localVariables.put(name, type.toString());

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
				for (JavaParser.FormalParameterContext param : 
						ctx.formalParameters().formalParameterList().formalParameter()) {
					localVariables.put(param.variableDeclaratorId().getText(), visit(param.typeType()));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType()));
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
				for (JavaParser.FormalParameterContext param : 
						ctx.formalParameters().formalParameterList().formalParameter()) {
					localVariables.put(param.variableDeclaratorId().getText(), visit(param.typeType()));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					localVariables.put(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText(), visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType()));
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

			if(ctx.primary().getText().equals("this")){
				returnType = currentClass;
				returnMethod = findMethodbyName(currentClass, currentClass.NAME);
				return null;
			}

			if(ctx.primary().getText().equals("super")){
				returnType = findClassbyName(currentClass.EXTENDS);
				if(returnType == null)
					return null;
				returnMethod = findMethodbyName(returnType, returnType.NAME);
				return null;
			}

			//literal
			if(ctx.primary().literal() != null)
			{
				if(ctx.primary().literal().BooleanLiteral() != null)
					returnType = new JavaClass("bool", "BuiltinType", "", "", null);

				if(ctx.primary().literal().CharacterLiteral() != null)
					returnType = new JavaClass("char", "BuiltinType", "", "", null);

				if(ctx.primary().literal().FloatingPointLiteral() != null)
					returnType = new JavaClass("float", "BuiltinType", "", "", null);

				if(ctx.primary().literal().IntegerLiteral() != null)
					returnType = new JavaClass("int", "BuiltinType", "", "", null);

				if(ctx.primary().literal().StringLiteral() != null)
					returnType = new JavaClass("String", "BuiltinType", "", "", null);
			}

			if(ctx.primary().Identifier() != null){
				//When the identifier is an object
				returnType = findVariableType(ctx.primary().Identifier().getText());
				//When the identifier is a class name
				if(returnType == null)
					returnType = findClassbyNameInAvailableClasses(ctx.primary().Identifier().getText());
				//When the identifier is a local method call
				if(returnType == null){
					returnMethod = findMethodbyName(currentClass, ctx.primary().Identifier().getText());
					//returnType = currentClass;
	//returnType = findClassbyName(returnMethod.get(0).RET_TYPE);
				}		
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
			JavaClass fieldType;
			fieldType = findFieldType(returnType, ctx.Identifier().getText());
			if(fieldType == null){
				currentMethod = findMethodbyName(returnType, ctx.Identifier().getText());
				if(!currentMethod.isEmpty()){
					returnMethod = currentMethod;
					//returnType = findClassbyName(returnMethod.get(0).RET_TYPE);
				}

			}
			else{
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
			if(ctx.innerCreator().classCreatorRest().arguments().expressionList() != null){
				for(JavaParser.ExpressionContext expr: ctx.innerCreator().classCreatorRest().arguments().expressionList().expression()){
					visit(expr);
					actualArguments.add(returnType);
				}
			}
			//JavaMethod actualMethod = createCLGNode(ctx.innerCreator().Identifier().getText(), ctx.getStart().getLine(), creatorMethods, actualArguments);
			if(currentContext == null)
				currentContext = ctx.expression();
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

			if(returnType != null)
				returnMethod = findMethodbyName(returnType, returnType.NAME);
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
			if(returnType != null)
				parent = findClassbyName(returnType.EXTENDS);
			if(parent != null){
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
		public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx){
			/*
			expression '(' expressionList? ')'
			expressionList:   expression (',' expression)*
			*/

			returnType = null;
			returnMethod.clear();

			visit(ctx.expression());

			ArrayList<JavaMethod> possibleCalls = new ArrayList<>();
			possibleCalls.addAll(returnMethod);
			JavaClass calleeClass;

			//For local method calls
			if(!returnMethod.isEmpty() && returnType == null)
				calleeClass = activeClasses.peek();
			else
				calleeClass = returnType;

			ArrayList<JavaClass> actualTypes = new ArrayList<>();
			returnType = null;
			returnMethod.clear();
			if(ctx.expressionList() != null){
				for(JavaParser.ExpressionContext expr: ctx.expressionList().expression()){
					visit(expr);
					actualTypes.add(returnType);
					returnType = null;
					returnMethod.clear();
				}
			}
			//JavaMethod actualMethod = createCLGNode(ctx.expression().getText(),ctx.start.getLine(), possibleCalls, actualTypes);
			if(currentContext == null)
				currentContext = ctx.expression();
			JavaMethod actualMethod = createCLGNode(calleeClass, possibleCalls, actualTypes);
			//JavaMethod actualMethod = createCLGNode(ctx.expression(), calleeClass, possibleCalls, actualTypes);
			returnType = findClassbyName(actualMethod.RET_TYPE);
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
			if(ctx.creator().createdName().primitiveType() == null){
				last = ctx.creator().createdName().Identifier().size()-1;
				String className = ctx.creator().createdName().Identifier(last).getText();
				lastClass = findClassbyName(className);
			}		

			ArrayList<JavaMethod> creators = new ArrayList<>();
			ArrayList<JavaClass> actualArguments = new ArrayList<>();
			if(lastClass != null){
				creators = findMethodbyName(lastClass, lastClass.NAME);
				if(ctx.creator().classCreatorRest() != null){
					if(ctx.creator().classCreatorRest().arguments() != null){
						if(ctx.creator().classCreatorRest().arguments().expressionList() != null){
							for(JavaParser.ExpressionContext expr: ctx.creator().classCreatorRest().arguments().expressionList().expression()){
								visit(expr);
								actualArguments.add(returnType);
							}
						}
					}
				}
				if(currentContext == null)
					currentContext = ctx.creator().createdName();
				createCLGNode(lastClass, creators, actualArguments);
				//createCLGNode(ctx.creator().createdName(), lastClass, creators, actualArguments);
			}
			else{
				if(currentContext == null)
					currentContext = ctx.creator().createdName();				
				createCLGNode(lastClass, creators, actualArguments);
			}
				//createCLGNode(ctx.creator().createdName(), lastClass, creators, actualArguments);
			if(ctx.creator().classCreatorRest() != null)
				if(ctx.creator().classCreatorRest().classBody() != null)
					visitChildren(ctx.creator().classCreatorRest().classBody());
			if(ctx.creator().arrayCreatorRest() != null)
				visitChildren(ctx.creator().arrayCreatorRest());
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
			if (idx > 0)
				type.append(ctx.getText().substring(idx));
			return type.toString();
		}

		@Override
		public String visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
			// classOrInterfaceType
			//   :  Identifier typeArguments? ('.' Identifier typeArguments? )*
			StringBuilder typeID = new StringBuilder(ctx.Identifier(0).getText());
			for (TerminalNode id: ctx.Identifier().subList(1, ctx.Identifier().size()))
				typeID.append(".").append(id.getText());
			return typeID.toString();
		}

		@Override
		public String visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
			// primitiveType : 
			//     'boolean' | 'char' | 'byte' | 'short'
			//     | 'int' | 'long' | 'float' | 'double'

			return ctx.getText();
		}		
		
		public Map<ParserRuleContext, Object> getMap(){
			return contextualProperties;
		}
/*
		public void printMapinLog(){
			//Map<ParserRuleContext, Object> contextualProperties;
			for(ParserRuleContext ctx: contextualProperties.keySet()){
				MethodKey value = (MethodKey) contextualProperties.get(ctx);
				//System.out.println("Expression: "+ ctx.getText() + ",in line: "+ ctx.getStart().getLine()+ ", called method: "+ value.calleeMethod.NAME+ " in class: "+ value.className+ " of package:"+ value.packageName);
				Logger.log("Expression: "+ ctx.getText() + ",in line: "+ ctx.getStart().getLine()+ ", called method: "+ value.methodName+ " in class: "+ value.className+ " of package:"+ value.packageName);

			}

		}
*/
		private JavaMethod[] MergeArrays(JavaMethod[] jm1, JavaMethod[] jm2){
			if(jm1 == null)
				return jm2;
			if(jm2 == null)
				return jm1;
			int sizeofArray1 = jm1.length;
			int sizeofArray2 = jm2.length;
			JavaMethod[] result = new JavaMethod[sizeofArray1+sizeofArray2];
			System.arraycopy(jm1, 0, result, 0, sizeofArray1);
			System.arraycopy(jm2, 0, result, sizeofArray1, sizeofArray2);
			return result;
		}

		private ArrayList<JavaMethod> findMethodbyName(JavaClass currentClass, String methodName){

			ArrayList<JavaMethod> matchedMethods = new ArrayList<>();
			if(currentClass == null)
				currentClass = activeClasses.peek();

			for(JavaMethod jm: currentClass.getAllMethods()){
				if(jm.NAME.equals(methodName)){
					matchedMethods.add(jm);
				}
			}

			if(!matchedMethods.isEmpty())
				return matchedMethods;

			if(currentClass.EXTENDS != null){
				JavaClass jc = findClassbyName(currentClass.EXTENDS);
				if(jc != null)
					matchedMethods = findMethodbyName(jc, methodName);
			}

			return matchedMethods;
		}

		private JavaClass findFieldType(JavaClass cls, String fieldName){
			if(cls == null)
				return null;
			String fieldType = null;
			for(JavaField jf: cls.getAllFields()){
				if(jf.NAME.equals(fieldName))
					fieldType = jf.TYPE;
			}
			return findClassbyName(fieldType);

		}

		private JavaClass findVariableType(String var){
			if(!localVariables.isEmpty()){
				for(String str: localVariables.keySet()){
					if(str.equals(var))
						return findClassbyName(localVariables.get(str));
				}			
			}

			if(!globalVariables.isEmpty()){
				for(String str: globalVariables.keySet()){
					if(str.equals(var))
						return findClassbyName(globalVariables.get(str));
				}	
			}
			return null;
		}

		private JavaClass findClassbyNameInAvailableClasses(String className){
			for(JavaClass jc: availableClasses){
				if(jc.NAME.equals(className))
					return jc;
			}
			return null;	
		}

		private JavaClass findClassbyName(String className){
			JavaClass cls = findClassbyNameInAvailableClasses(className);
			if(cls != null)
				return cls;
			for(JavaClass jc: javaClasses){
				if(jc.NAME.equals(className))
					return jc;
			}
			return null;
		}

		private void preserveLocalVariablesState(ParserRuleContext context){
			LinkedHashMap<String, String> tempLocalVariables= new LinkedHashMap<>();
			tempLocalVariables.putAll(localVariables);
			currentContext = context;
			visitChildren(context);
			currentContext = null;
			localVariables.clear();
			localVariables.putAll(tempLocalVariables);
		}

		private boolean isActualMethod(JavaMethod possibleMethod, ArrayList<JavaClass> actualTypes){		
			return true;
		}

		private JavaMethod createCLGNode(JavaClass cls, ArrayList<JavaMethod> possibleCalls, ArrayList<JavaClass> actualTypes){
			JavaMethod actualMethod = null;		
			if(possibleCalls.isEmpty())
				actualMethod = notImplemented;
			else{
				if(!actualTypes.isEmpty()){
					actualMethod = possibleCalls.get(0);
				}
				else{
					for(JavaMethod jm: possibleCalls)
						if(isActualMethod(jm, actualTypes)){
							actualMethod = jm;
							break;
						}
				}
			}
			//MethodKey call = new MethodKey(ctx, LoC, actualMethod);
			//MethodKey key = new MethodKey(LoC, actualMethod);
			addKey(cls, actualMethod);
			return actualMethod;
		}

		private void addKey(JavaClass cls, JavaMethod method){
			MethodKey key;
			if(method != null && cls != null)
				key = new MethodKey(cls.PACKAGE, cls.NAME, method.NAME, method.LINE_OF_CODE);
			else
				key = new MethodKey("Not Implemented", "Not Implemented", "notImplemented", 0);
			//contextualProperties.put(currentContext, key);
			ArrayList<MethodKey> keys = new ArrayList<>();
			keys.add(key);
			if(contextualProperties.containsKey(currentContext)){
				keys.addAll((ArrayList<MethodKey>) contextualProperties.get(currentContext)); 
			}
			contextualProperties.put(currentContext, keys);
			//invocationList.add(key);
		}
	}
}

class MethodKey{
	//public ParseTree context;
	String packageName;
	String className;
	//public JavaMethod calleeMethod;
	String methodName;
	int LoC;
	//public MethodKey(String pkgName, String clsName, JavaMethod callee){
	public MethodKey(String pkgName, String clsName, String calleeMethodName, int lineOfCode){ 
		//context = ctx;
		packageName = pkgName;
		className = clsName;
		//calleeMethod = callee; 
		methodName = calleeMethodName;
		LoC = lineOfCode;
	}

	@Override
	public boolean equals(Object obj){
		if(!(obj instanceof MethodKey))
			return false;
		MethodKey key = (MethodKey) obj;
		return this.packageName.equals(key.packageName) &&
				this.className.equals(key.className) &&
				this.methodName.equals(key.methodName) &&
				this.LoC == key.LoC;
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
