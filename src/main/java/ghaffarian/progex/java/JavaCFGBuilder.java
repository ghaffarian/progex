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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import ghaffarian.progex.graphs.cfg.CFEdge;
import ghaffarian.progex.graphs.cfg.CFNode;
import ghaffarian.progex.graphs.cfg.ControlFlowGraph;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;
import ghaffarian.nanologger.Logger;

/**
 * A Control Flow Graph (CFG) builder for Java programs.
 * A Java parser generated via ANTLRv4 is used for this purpose.
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaCFGBuilder {
	
	/**
	 * ‌Build and return the Control Flow Graph (CFG) for the given Java source file.
	 */
	public static ControlFlowGraph build(String javaFile) throws IOException {
		return build(new File(javaFile));
	}
	
	/**
	 * ‌Build and return the Control Flow Graph (CFG) for the given Java source file.
	 */
	public static ControlFlowGraph build(File javaFile) throws IOException {
		if (!javaFile.getName().endsWith(".java"))
			throw new IOException("Not a Java File!");
		InputStream inFile = new FileInputStream(javaFile);
		ANTLRInputStream input = new ANTLRInputStream(inFile);
		JavaLexer lexer = new JavaLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(tokens);
		ParseTree tree = parser.compilationUnit();
		return build(javaFile.getName(), tree, null, null);
	}
	
	/**
	 * ‌Build and return the Control Flow Graph (CFG) for the given Parse-Tree.
	 * The 'ctxProps' map includes contextual-properties for particular nodes 
	 * in the parse-tree, which can be used for linking this graph with other 
	 * graphs by using the same parse-tree and the same contextual-properties.
	 */
	public static ControlFlowGraph build(String javaFileName, ParseTree tree, 
			String propKey, Map<ParserRuleContext, Object> ctxProps) {
		ControlFlowGraph cfg = new ControlFlowGraph(javaFileName);
		ControlFlowVisitor visitor = new ControlFlowVisitor(cfg, propKey, ctxProps);
		visitor.visit(tree);
		return cfg;
	}
	
	/**
	 * Visitor-class which constructs the CFG by walking the parse-tree.
	 */
	private static class ControlFlowVisitor extends JavaBaseVisitor<Void> {
		
		private ControlFlowGraph cfg;
		private Deque<CFNode> preNodes;
		private Deque<CFEdge.Type> preEdges;
		private Deque<Block> loopBlocks;
		private List<Block> labeledBlocks;
		private Deque<Block> tryBlocks;
		private Queue<CFNode> casesQueue;
		private boolean dontPop;
		private String propKey;
		private Map<ParserRuleContext, Object> contexutalProperties;
		private Deque<String> classNames;

		public ControlFlowVisitor(ControlFlowGraph cfg, String propKey, Map<ParserRuleContext, Object> ctxProps) {
			preNodes = new ArrayDeque<>();
			preEdges = new ArrayDeque<>();
			loopBlocks = new ArrayDeque<>();
			labeledBlocks = new ArrayList<>();
			tryBlocks = new ArrayDeque<>();
			casesQueue = new ArrayDeque<>();
			classNames = new ArrayDeque<>();
			dontPop = false;
			this.cfg = cfg;
			//
			this.propKey = propKey;
			contexutalProperties = ctxProps;
		}

		/**
		 * Reset all data-structures and flags for visiting a new method declaration.
		 */
		private void init() {
			preNodes.clear();
			preEdges.clear();
			loopBlocks.clear();
			labeledBlocks.clear();
			tryBlocks.clear();
			dontPop = false;
		}
		
		/**
		 * Add contextual properties to the given node.
		 * This will first check to see if there is any property for the 
		 * given context, and if so, the property will be added to the node.
		 */
		private void addContextualProperty(CFNode node, ParserRuleContext ctx) {
			if (propKey != null && contexutalProperties != null) {
				Object prop = contexutalProperties.get(ctx);
				if (prop != null)
					node.setProperty(propKey, prop);
			}
		}
		
		@Override
		public Void visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
			// packageDeclaration :  annotation* 'package' qualifiedName ';'
			cfg.setPackage(ctx.qualifiedName().getText());
			return null;
		}

		@Override
		public Void visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
			// classDeclaration 
			//   :  'class' Identifier typeParameters? 
			//      ('extends' typeType)? ('implements' typeList)? classBody
			classNames.push(ctx.Identifier().getText());
			visit(ctx.classBody());
			classNames.pop();
			return null;
		}

		@Override
		public Void visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
			// Just ignore enums for now ...
			return null;
		}
		
		@Override
		public Void visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
			// Just ignore interfaces for now ...
			return null;
		}
		
		@Override
		public Void visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			// classBodyDeclaration :  ';'  |  'static'? block  |  modifier* memberDeclaration
			if (ctx.block() != null) {
				init();
				//
				CFNode block = new CFNode();
				if (ctx.getChildCount() == 2 && ctx.getChild(0).getText().equals("static")) {
					block.setLineOfCode(ctx.getStart().getLine());
					block.setCode("static");
				} else {
					block.setLineOfCode(0);
					block.setCode("block");
				}
				addContextualProperty(block, ctx);
				cfg.addVertex(block);
				//
				block.setProperty("name", "static-block");
				block.setProperty("class", classNames.peek());
				cfg.addMethodEntry(block);
				//
				preNodes.push(block);
				preEdges.push(CFEdge.Type.EPSILON);
			}
			return visitChildren(ctx);
		}

		@Override
		public Void visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
			// Identifier formalParameters ('throws' qualifiedNameList)?  constructorBody
			init();
			//
			CFNode entry = new CFNode();
			entry.setLineOfCode(ctx.getStart().getLine());
			entry.setCode(ctx.Identifier().getText() + ' ' + getOriginalCodeText(ctx.formalParameters()));
			addContextualProperty(entry, ctx);
			cfg.addVertex(entry);
			//
			entry.setProperty("name", ctx.Identifier().getText());
			entry.setProperty("class", classNames.peek());
			cfg.addMethodEntry(entry);
			//
			preNodes.push(entry);
			preEdges.push(CFEdge.Type.EPSILON);
			return visitChildren(ctx);
		}

		@Override
		public Void visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			// methodDeclaration :
			//   (typeType|'void') Identifier formalParameters ('[' ']')*
			//     ('throws' qualifiedNameList)?  ( methodBody | ';' )
			init();
			//
			CFNode entry = new CFNode();
			entry.setLineOfCode(ctx.getStart().getLine());
			String retType = "void";
			if (ctx.typeType() != null)
				retType = ctx.typeType().getText();
			String args = getOriginalCodeText(ctx.formalParameters());
			entry.setCode(retType + " " + ctx.Identifier() + args);
			addContextualProperty(entry, ctx);
			cfg.addVertex(entry);
			//
			entry.setProperty("name", ctx.Identifier().getText());
			entry.setProperty("class", classNames.peek());
			entry.setProperty("type", retType);
			cfg.addMethodEntry(entry);
			//
			preNodes.push(entry);
			preEdges.push(CFEdge.Type.EPSILON);
			return visitChildren(ctx);
		}

		@Override
		public Void visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
			// statementExpression ';'
			CFNode expr = new CFNode();
			expr.setLineOfCode(ctx.getStart().getLine());
			expr.setCode(getOriginalCodeText(ctx));
			//
			Logger.debug(expr.getLineOfCode() + ": " + expr.getCode());
			//
			addContextualProperty(expr, ctx);
			addNodeAndPreEdge(expr);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(expr);
			return null;
		}
		
		@Override
		public Void visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			// localVariableDeclaration :  variableModifier* typeType variableDeclarators
			CFNode declr = new CFNode();
			declr.setLineOfCode(ctx.getStart().getLine());
			declr.setCode(getOriginalCodeText(ctx));
			addContextualProperty(declr, ctx);
			addNodeAndPreEdge(declr);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(declr);
			return null;
		}

		@Override
		public Void visitIfStatement(JavaParser.IfStatementContext ctx) {
			// 'if' parExpression statement ('else' statement)?
			CFNode ifNode = new CFNode();
			ifNode.setLineOfCode(ctx.getStart().getLine());
			ifNode.setCode("if " + getOriginalCodeText(ctx.parExpression()));
			addContextualProperty(ifNode, ctx);
			addNodeAndPreEdge(ifNode);
			//
			preEdges.push(CFEdge.Type.TRUE);
			preNodes.push(ifNode);
			//
			visit(ctx.statement(0));
			//
			CFNode endif = new CFNode();
			endif.setLineOfCode(0);
			endif.setCode("endif");
			addNodeAndPreEdge(endif);
			//
			if (ctx.statement().size() == 1) { // if without else
				cfg.addEdge(new Edge<>(ifNode, new CFEdge(CFEdge.Type.FALSE), endif));
			} else {  //  if with else
				preEdges.push(CFEdge.Type.FALSE);
				preNodes.push(ifNode);
				visit(ctx.statement(1));
				popAddPreEdgeTo(endif);
			}
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(endif);
			return null;
		}

		@Override
		public Void visitForStatement(JavaParser.ForStatementContext ctx) {
			// 'for' '(' forControl ')' statement
			//  First, we should check type of for-loop ...
			if (ctx.forControl().enhancedForControl() != null) {
				// This is a for-each loop;
				//   enhancedForControl: 
				//     variableModifier* typeType variableDeclaratorId ':' expression
				CFNode forExpr = new CFNode();
				forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
				forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl()) + ")");
				addContextualProperty(forExpr, ctx.forControl().enhancedForControl());
				addNodeAndPreEdge(forExpr);
				//
				CFNode forEnd = new CFNode();
				forEnd.setLineOfCode(0);
				forEnd.setCode("endfor");
				cfg.addVertex(forEnd);
				cfg.addEdge(new Edge<>(forExpr, new CFEdge(CFEdge.Type.FALSE), forEnd));
				//
				preEdges.push(CFEdge.Type.TRUE);
				preNodes.push(forExpr);
				//
				loopBlocks.push(new Block(forExpr, forEnd));
				visit(ctx.statement());
				loopBlocks.pop();
				popAddPreEdgeTo(forExpr);
				//
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(forEnd);
			} else {
				// It's a traditional for-loop: 
				//   forInit? ';' expression? ';' forUpdate?
				CFNode forInit = null;
				if (ctx.forControl().forInit() != null) { // non-empty init
					forInit = new CFNode();
					forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
					forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()));
					addContextualProperty(forInit, ctx.forControl().forInit());
					addNodeAndPreEdge(forInit);
				}
				// for-expression
				CFNode forExpr = new CFNode();
				if (ctx.forControl().expression() == null) {
					forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
					forExpr.setCode("for ( ; )");
				} else {
					forExpr.setLineOfCode(ctx.forControl().expression().getStart().getLine());
					forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl().expression()) + ")");
				}
				addContextualProperty(forExpr, ctx.forControl().expression());
				cfg.addVertex(forExpr);
				if (forInit != null)
					cfg.addEdge(new Edge<>(forInit, new CFEdge(CFEdge.Type.EPSILON), forExpr));
				else
					popAddPreEdgeTo(forExpr);
				// for-update
				CFNode forUpdate = new CFNode();
				if (ctx.forControl().forUpdate() == null) { // empty for-update
					forUpdate.setCode(" ; ");
					forUpdate.setLineOfCode(ctx.forControl().getStart().getLine());
				} else {
					forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
					forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
				}
				addContextualProperty(forUpdate, ctx.forControl().forUpdate());
				cfg.addVertex(forUpdate);
				//
				CFNode forEnd = new CFNode();
				forEnd.setLineOfCode(0);
				forEnd.setCode("endfor");
				cfg.addVertex(forEnd);
				cfg.addEdge(new Edge<>(forExpr, new CFEdge(CFEdge.Type.FALSE), forEnd));
				//
				preEdges.push(CFEdge.Type.TRUE);
				preNodes.push(forExpr);
				loopBlocks.push(new Block(forUpdate, forEnd)); // NOTE: start is 'forUpdate'
				visit(ctx.statement());
				loopBlocks.pop();
				popAddPreEdgeTo(forUpdate);
				cfg.addEdge(new Edge<>(forUpdate, new CFEdge(CFEdge.Type.EPSILON), forExpr));
				//
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(forEnd);
			}
			return null;
		}

		@Override
		public Void visitWhileStatement(JavaParser.WhileStatementContext ctx) {
			// 'while' parExpression statement
			CFNode whileNode = new CFNode();
			whileNode.setLineOfCode(ctx.getStart().getLine());
			whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
			addContextualProperty(whileNode, ctx);
			addNodeAndPreEdge(whileNode);
			//
			CFNode endwhile = new CFNode();
			endwhile.setLineOfCode(0);
			endwhile.setCode("endwhile");
			cfg.addVertex(endwhile);
			cfg.addEdge(new Edge<>(whileNode, new CFEdge(CFEdge.Type.FALSE), endwhile));
			//
			preEdges.push(CFEdge.Type.TRUE);
			preNodes.push(whileNode);
			loopBlocks.push(new Block(whileNode, endwhile));
			visit(ctx.statement());
			loopBlocks.pop();
			popAddPreEdgeTo(whileNode);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(endwhile);
			return null;
		}

		@Override
		public Void visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
			// 'do' statement 'while' parExpression ';'
			CFNode doNode = new CFNode();
			doNode.setLineOfCode(ctx.getStart().getLine());
			doNode.setCode("do");
			addNodeAndPreEdge(doNode);
			//
			CFNode whileNode = new CFNode();
			whileNode.setLineOfCode(ctx.parExpression().getStart().getLine());
			whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
			addContextualProperty(whileNode, ctx);
			cfg.addVertex(whileNode);
			//
			CFNode doWhileEnd = new CFNode();
			doWhileEnd.setLineOfCode(0);
			doWhileEnd.setCode("end-do-while");
			cfg.addVertex(doWhileEnd);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(doNode);
			loopBlocks.push(new Block(whileNode, doWhileEnd));
			visit(ctx.statement());
			loopBlocks.pop();
			popAddPreEdgeTo(whileNode);
			cfg.addEdge(new Edge<>(whileNode, new CFEdge(CFEdge.Type.TRUE), doNode));
			cfg.addEdge(new Edge<>(whileNode, new CFEdge(CFEdge.Type.FALSE), doWhileEnd));
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(doWhileEnd);
			return null;
		}

		@Override
		public Void visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
			// 'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
			CFNode switchNode = new CFNode();
			switchNode.setLineOfCode(ctx.getStart().getLine());
			switchNode.setCode("switch " + getOriginalCodeText(ctx.parExpression()));
			addContextualProperty(switchNode, ctx);
			addNodeAndPreEdge(switchNode);
			//
			CFNode endSwitch = new CFNode();
			endSwitch.setLineOfCode(0);
			endSwitch.setCode("end-switch");
			cfg.addVertex(endSwitch);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(switchNode);
			loopBlocks.push(new Block(switchNode, endSwitch));
			//
			CFNode preCase = null;
			for (JavaParser.SwitchBlockStatementGroupContext grp: ctx.switchBlockStatementGroup()) {
				// switchBlockStatementGroup :  switchLabel+ blockStatement+
				preCase = visitSwitchLabels(grp.switchLabel(), preCase);
				for (JavaParser.BlockStatementContext blk: grp.blockStatement())
					visit(blk);
			}
			preCase = visitSwitchLabels(ctx.switchLabel(), preCase);
			loopBlocks.pop();
			popAddPreEdgeTo(endSwitch);
			if (preCase != null)
				cfg.addEdge(new Edge<>(preCase, new CFEdge(CFEdge.Type.FALSE), endSwitch));
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(endSwitch);
			return null;
		}

		private CFNode visitSwitchLabels(List<JavaParser.SwitchLabelContext> list, CFNode preCase) {
			//  switchLabel :  'case' constantExpression ':'  |  'case' enumConstantName ':'  |  'default' ':'
			CFNode caseStmnt = preCase;
			for (JavaParser.SwitchLabelContext ctx: list) {
				caseStmnt = new CFNode();
				caseStmnt.setLineOfCode(ctx.getStart().getLine());
				caseStmnt.setCode(getOriginalCodeText(ctx));
				cfg.addVertex(caseStmnt);
				if (dontPop)
					dontPop = false;
				else
					cfg.addEdge(new Edge<>(preNodes.pop(), new CFEdge(preEdges.pop()), caseStmnt));
				if (preCase != null)
					cfg.addEdge(new Edge<>(preCase, new CFEdge(CFEdge.Type.FALSE), caseStmnt));
				if (ctx.getStart().getText().equals("default")) {
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(caseStmnt);
					caseStmnt = null;
				} else { // any other case ...
					dontPop = true;
					casesQueue.add(caseStmnt);
					preCase = caseStmnt;
				}
			}
			return caseStmnt;
		}

		@Override
		public Void visitLabelStatement(JavaParser.LabelStatementContext ctx) {
			// Identifier ':' statement
			// For each visited label-block, a Block object is created with 
			// the the current node as the start, and a dummy node as the end.
			// The newly created label-block is stored in an ArrayList of Blocks.
			CFNode labelNode = new CFNode();
			labelNode.setLineOfCode(ctx.getStart().getLine());
			labelNode.setCode(ctx.Identifier() + ": ");
			addContextualProperty(labelNode, ctx);
			addNodeAndPreEdge(labelNode);
			//
			CFNode endLabelNode = new CFNode();
			endLabelNode.setLineOfCode(0);
			endLabelNode.setCode("end-label");
			cfg.addVertex(endLabelNode);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(labelNode);
			labeledBlocks.add(new Block(labelNode, endLabelNode, ctx.Identifier().getText()));
			visit(ctx.statement());
			popAddPreEdgeTo(endLabelNode);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(endLabelNode);
			return null;
		}

		@Override
		public Void visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
			// 'return' expression? ';'
			CFNode ret = new CFNode();
			ret.setLineOfCode(ctx.getStart().getLine());
			ret.setCode(getOriginalCodeText(ctx));
			addContextualProperty(ret, ctx);
			addNodeAndPreEdge(ret);
			dontPop = true;
			return null;
		}

		@Override
		public Void visitBreakStatement(JavaParser.BreakStatementContext ctx) {
			// 'break' Identifier? ';'
			// if a label is specified, search for the corresponding block in the labels-list,
			// and create an epsilon edge to the end of the labeled-block; else
			// create an epsilon edge to the end of the loop-block on top of the loopBlocks stack.
			CFNode breakNode = new CFNode();
			breakNode.setLineOfCode(ctx.getStart().getLine());
			breakNode.setCode(getOriginalCodeText(ctx));
			addContextualProperty(breakNode, ctx);
			addNodeAndPreEdge(breakNode);
			if (ctx.Identifier() != null) {
				// a label is specified
				for (Block block: labeledBlocks) {
					if (block.label.equals(ctx.Identifier().getText())) {
						cfg.addEdge(new Edge<>(breakNode, new CFEdge(CFEdge.Type.EPSILON), block.end));
						break;
					}
				}
			} else {
				// no label
				Block block = loopBlocks.peek();
				cfg.addEdge(new Edge<>(breakNode, new CFEdge(CFEdge.Type.EPSILON), block.end));
			}
			dontPop = true;
			return null;
		}

		@Override
		public Void visitContinueStatement(JavaParser.ContinueStatementContext ctx) {
			// 'continue' Identifier? ';'
			// if a label is specified, search for the corresponding block in the labels-list,
			// and create an epsilon edge to the start of the labeled-block; else
			// create an epsilon edge to the start of the loop-block on top of the loopBlocks stack.
			CFNode continueNode = new CFNode();
			continueNode.setLineOfCode(ctx.getStart().getLine());
			continueNode.setCode(getOriginalCodeText(ctx));
			addContextualProperty(continueNode, ctx);
			addNodeAndPreEdge(continueNode);
			if (ctx.Identifier() != null) {  
				// a label is specified
				for (Block block: labeledBlocks) {
					if (block.label.equals(ctx.Identifier().getText())) {
						cfg.addEdge(new Edge<>(continueNode, new CFEdge(CFEdge.Type.EPSILON), block.start));
						break;
					}
				}
			} else {  
				// no label
				Block block = loopBlocks.peek();
				cfg.addEdge(new Edge<>(continueNode, new CFEdge(CFEdge.Type.EPSILON), block.start));
			}
			dontPop = true;
			return null;
		}

		@Override
		public Void visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
			// 'synchronized' parExpression block
			CFNode syncStmt = new CFNode();
			syncStmt.setLineOfCode(ctx.getStart().getLine());
			syncStmt.setCode("synchronized " + getOriginalCodeText(ctx.parExpression()));
			addContextualProperty(syncStmt, ctx);
			addNodeAndPreEdge(syncStmt);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(syncStmt);
			visit(ctx.block());
			//
			CFNode endSyncBlock = new CFNode();
			endSyncBlock.setLineOfCode(0);
			endSyncBlock.setCode("end-synchronized");
			addNodeAndPreEdge(endSyncBlock);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(endSyncBlock);
			return null;
		}

		@Override
		public Void visitTryStatement(JavaParser.TryStatementContext ctx) {
			// 'try' block (catchClause+ finallyBlock? | finallyBlock)
			CFNode tryNode = new CFNode();
			tryNode.setLineOfCode(ctx.getStart().getLine());
			tryNode.setCode("try");
			addContextualProperty(tryNode, ctx);
			addNodeAndPreEdge(tryNode);
			//
			CFNode endTry = new CFNode();
			endTry.setLineOfCode(0);
			endTry.setCode("end-try");
			cfg.addVertex(endTry);
			//
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(tryNode);
			tryBlocks.push(new Block(tryNode, endTry));
			visit(ctx.block());
			popAddPreEdgeTo(endTry);

			// If there is a finally-block, visit it first
			CFNode finallyNode = null;
			CFNode endFinally = null;
			if (ctx.finallyBlock() != null) {
				// 'finally' block
				finallyNode = new CFNode();
				finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
				finallyNode.setCode("finally");
				addContextualProperty(finallyNode, ctx.finallyBlock());
				cfg.addVertex(finallyNode);
				cfg.addEdge(new Edge<>(endTry, new CFEdge(CFEdge.Type.EPSILON), finallyNode));
				//
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(finallyNode);
				visit(ctx.finallyBlock().block());
				//
				endFinally = new CFNode();
				endFinally.setLineOfCode(0);
				endFinally.setCode("end-finally");
				addNodeAndPreEdge(endFinally);
			}
			// Now visit any available catch clauses
			if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				CFNode catchNode;
				CFNode endCatch = new CFNode();
				endCatch.setLineOfCode(0);
				endCatch.setCode("end-catch");
				cfg.addVertex(endCatch);
				for (JavaParser.CatchClauseContext cx: ctx.catchClause()) {
					// connect the try-node to all catch-nodes;
					// create a single end-catch for all catch-blocks;
					catchNode = new CFNode();
					catchNode.setLineOfCode(cx.getStart().getLine());
					catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
					addContextualProperty(catchNode, cx);
					cfg.addVertex(catchNode);
					cfg.addEdge(new Edge<>(endTry, new CFEdge(CFEdge.Type.THROWS), catchNode));
					//
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(catchNode);
					visit(cx.block());
					popAddPreEdgeTo(endCatch);
				}
				if (finallyNode != null) {
					// connect end-catch node to finally-node,
					// and push end-finally to the stack ...
					cfg.addEdge(new Edge<>(endCatch, new CFEdge(CFEdge.Type.EPSILON), finallyNode));
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(endFinally);
				} else {
					// connect end-catch node to end-try,
					// and push end-try to the the stack ...
					cfg.addEdge(new Edge<>(endCatch, new CFEdge(CFEdge.Type.EPSILON), endTry));
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(endTry);
				}
			} else {
				// No catch-clause; it's a try-finally
				// push end-finally to the stack ...
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(endFinally);
			}
			// NOTE that Java does not allow a singular try-block (without catch or finally)
			return null;
		}
		
		@Override
		public Void visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx) {
			// 'try' resourceSpecification block catchClause* finallyBlock?
			// resourceSpecification :  '(' resources ';'? ')'
			// resources :  resource (';' resource)*
			// resource  :  variableModifier* classOrInterfaceType variableDeclaratorId '=' expression
			CFNode tryNode = new CFNode();
			tryNode.setLineOfCode(ctx.getStart().getLine());
			tryNode.setCode("try");
			addContextualProperty(tryNode, ctx);
			addNodeAndPreEdge(tryNode);
			preEdges.push(CFEdge.Type.EPSILON);
			preNodes.push(tryNode);
			//
			// Iterate over all resources ...
			for (JavaParser.ResourceContext rsrc: ctx.resourceSpecification().resources().resource()) {
				CFNode resource = new CFNode();
				resource.setLineOfCode(rsrc.getStart().getLine());
				resource.setCode(getOriginalCodeText(rsrc));
				//
				addContextualProperty(resource, rsrc);
				addNodeAndPreEdge(resource);
				//
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(resource);
			}
			//
			CFNode endTry = new CFNode();
			endTry.setLineOfCode(0);
			endTry.setCode("end-try");
			cfg.addVertex(endTry);
			//
			tryBlocks.push(new Block(tryNode, endTry));
			visit(ctx.block());
			popAddPreEdgeTo(endTry);

			// If there is a finally-block, visit it first
			CFNode finallyNode = null;
			CFNode endFinally = null;
			if (ctx.finallyBlock() != null) {
				// 'finally' block
				finallyNode = new CFNode();
				finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
				finallyNode.setCode("finally");
				addContextualProperty(finallyNode, ctx.finallyBlock());
				cfg.addVertex(finallyNode);
				cfg.addEdge(new Edge<>(endTry, new CFEdge(CFEdge.Type.EPSILON), finallyNode));
				//
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(finallyNode);
				visit(ctx.finallyBlock().block());
				//
				endFinally = new CFNode();
				endFinally.setLineOfCode(0);
				endFinally.setCode("end-finally");
				addNodeAndPreEdge(endFinally);
			}
			// Now visit any available catch clauses
			if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				CFNode catchNode;
				CFNode endCatch = new CFNode();
				endCatch.setLineOfCode(0);
				endCatch.setCode("end-catch");
				cfg.addVertex(endCatch);
				for (JavaParser.CatchClauseContext cx: ctx.catchClause()) {
					// connect the try-node to all catch-nodes;
					// create a single end-catch for all catch-blocks;
					catchNode = new CFNode();
					catchNode.setLineOfCode(cx.getStart().getLine());
					catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
					addContextualProperty(catchNode, cx);
					cfg.addVertex(catchNode);
					cfg.addEdge(new Edge<>(endTry, new CFEdge(CFEdge.Type.THROWS), catchNode));
					//
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(catchNode);
					visit(cx.block());
					popAddPreEdgeTo(endCatch);
				}
				if (finallyNode != null) {
					// connect end-catch node to finally-node,
					// and push end-finally to the stack ...
					cfg.addEdge(new Edge<>(endCatch, new CFEdge(CFEdge.Type.EPSILON), finallyNode));
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(endFinally);
				} else {
					// connect end-catch node to end-try,
					// and push end-try to the the stack ...
					cfg.addEdge(new Edge<>(endCatch, new CFEdge(CFEdge.Type.EPSILON), endTry));
					preEdges.push(CFEdge.Type.EPSILON);
					preNodes.push(endTry);
				}
			} else if (finallyNode != null) {
				// No catch-clause; it's a try-finally
				// push end-finally to the stack ...
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(endFinally);
			} else {
				// No catch-clause and no finally;
				// push end-try to the stack ...
				preEdges.push(CFEdge.Type.EPSILON);
				preNodes.push(endTry);
			}
			return null;
		}

		@Override
		public Void visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
			// 'throw' expression ';'
			CFNode throwNode = new CFNode();
			throwNode.setLineOfCode(ctx.getStart().getLine());
			throwNode.setCode("throw " + getOriginalCodeText(ctx.expression()));
			addContextualProperty(throwNode, ctx);
			addNodeAndPreEdge(throwNode);
			//
			if (!tryBlocks.isEmpty()) {
				Block tryBlock = tryBlocks.peek();
				cfg.addEdge(new Edge<>(throwNode, new CFEdge(CFEdge.Type.THROWS), tryBlock.end));
			} else {
				// do something when it's a throw not in a try-catch block ...
				// in such a situation, the method declaration has a throws clause;
				// so we should create a special node for the method-throws, 
				// and create an edge from this throw-statement to that throws-node.
			}
			dontPop = true;
			return null;
		}

		/**
		 * Get resulting Control-Flow-Graph of this CFG-Builder.
		 */
		public ControlFlowGraph getCFG() {
			return cfg;
		}

		/**
		 * Add this node to the CFG and create edge from pre-node to this node.
		 */
		private void addNodeAndPreEdge(CFNode node) {
			cfg.addVertex(node);
			popAddPreEdgeTo(node);
		}

		/**
		 * Add a new edge to the given node, by poping the edge-type of the stack.
		 */
		private void popAddPreEdgeTo(CFNode node) {
			if (dontPop)
				dontPop = false;
			else {
				Logger.debug("\nPRE-NODES = " + preNodes.size());
				Logger.debug("PRE-EDGES = " + preEdges.size() + '\n');
				cfg.addEdge(new Edge<>(preNodes.pop(), new CFEdge(preEdges.pop()), node));
			}
			//
			for (int i = casesQueue.size(); i > 0; --i)
				cfg.addEdge(new Edge<>(casesQueue.remove(), new CFEdge(CFEdge.Type.TRUE), node));
		}

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

		/**
		 * A simple structure for holding the start, end, and label of code blocks.
		 * These are used to handle 'break' and 'continue' statements.
		 */
		private class Block {

			public final String label;
			public final CFNode start, end;

			Block(CFNode start, CFNode end, String label) {
				this.start = start;
				this.end = end;
				this.label = label;
			}

			Block(CFNode start, CFNode end) {
				this(start, end, "");
			}
		}
	}
}
