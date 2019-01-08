/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import ghaffarian.graphs.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import ghaffarian.progex.graphs.pdg.CDEdge;
import ghaffarian.progex.graphs.pdg.ControlDependenceGraph;
import ghaffarian.progex.graphs.pdg.PDNode;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;
import ghaffarian.nanologger.Logger;

/**
 * Control Dependence Graph (CDG) builder for Java programs.
 * The CDG is actually a subgraph of the Program Dependence Graph (PDG).
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaCDGBuilder {
	
	public static ControlDependenceGraph build(File javaFile) throws IOException {
		if (!javaFile.getName().endsWith(".java"))
			throw new IOException("Not a Java File!");
		InputStream inFile = new FileInputStream(javaFile);
		ANTLRInputStream input = new ANTLRInputStream(inFile);
		JavaLexer lexer = new JavaLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(tokens);
		ParseTree tree = parser.compilationUnit();
		Logger.debug("CTRL DEP ANALYSIS: " + javaFile.getPath());
		ControlDependenceGraph cdg = new ControlDependenceGraph(javaFile.getName());
		ControlDependencyVisitor visitor = new ControlDependencyVisitor(cdg);
		visitor.visit(tree);
		return cdg;
	}
	

	private static class ControlDependencyVisitor extends JavaBaseVisitor<Void> {
		
		private ControlDependenceGraph cdg;
		private Deque<PDNode> ctrlDeps;
		private Deque<PDNode> negDeps;
		private Deque<Integer> jmpCounts;
		private Deque<PDNode> jumpDeps;
		private boolean buildRegion;
		private boolean follows;
		private int lastFollowDepth;
		private int regionCounter;
		private int jmpCounter;

		public ControlDependencyVisitor(ControlDependenceGraph cdg) {
			this.cdg = cdg;
			ctrlDeps = new ArrayDeque<>();
			negDeps = new ArrayDeque<>();
			jumpDeps = new ArrayDeque<>();
			jmpCounts = new ArrayDeque<>();
			buildRegion = false;
			follows = true;
			lastFollowDepth = 0;
			regionCounter = 1;
			jmpCounter = 0;
		}

		private void init() {
			ctrlDeps.clear();
			negDeps.clear();
			jumpDeps.clear();
			jmpCounts.clear();
			buildRegion = false;
			follows = true;
			lastFollowDepth = 0;
			regionCounter = 1;
			jmpCounter = 0;
		}
		
		@Override
		public Void visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			// classBodyDeclaration :  ';'  |  'static'? block  |  modifier* memberDeclaration
			if (ctx.block() != null) {
				init();
				//
				PDNode block = new PDNode();
				if (ctx.getChildCount() == 2 && ctx.getChild(0).getText().equals("static")) {
					block.setLineOfCode(ctx.getStart().getLine());
					block.setCode("static");
				} else {
					block.setLineOfCode(0);
					block.setCode("block");
				}
				cdg.addVertex(block);
				pushCtrlDep(block);
				visit(ctx.block());
				//
				PDNode exit = new PDNode();
				exit.setLineOfCode(0);
				exit.setCode("exit");
				cdg.addVertex(exit);
				cdg.addEdge(new Edge<>(block, new CDEdge(CDEdge.Type.EPSILON), exit));
				return null;
			} else
				return visitChildren(ctx);
		}

		@Override
		public Void visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
			// Identifier formalParameters ('throws' qualifiedNameList)?  constructorBody
			init();
			//
			PDNode entry = new PDNode();
			entry.setLineOfCode(ctx.getStart().getLine());
			entry.setCode(ctx.Identifier().getText() + ' ' + getOriginalCodeText(ctx.formalParameters()));
			cdg.addVertex(entry);
			//
			pushCtrlDep(entry);
			visit(ctx.constructorBody());
			//
			PDNode exit = new PDNode();
			exit.setLineOfCode(0);
			exit.setCode("exit");
			cdg.addVertex(exit);
			cdg.addEdge(new Edge<>(entry, new CDEdge(CDEdge.Type.EPSILON), exit));
			return null;
		}

		@Override
		public Void visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
			// methodDeclaration :
			//   (typeType|'void') Identifier formalParameters ('[' ']')*
			//     ('throws' qualifiedNameList)?  ( methodBody | ';' )
			init();
			//
			PDNode entry = new PDNode();
			entry.setLineOfCode(ctx.getStart().getLine());
			String retType;
			if (ctx.typeType() == null)
				retType = "void";
			else
				retType = getOriginalCodeText(ctx.typeType());
			String args = getOriginalCodeText(ctx.formalParameters());
			entry.setCode(retType + " " + ctx.Identifier() + args);
			cdg.addVertex(entry);
			//
			pushCtrlDep(entry);
			if (ctx.methodBody() != null)
				visit(ctx.methodBody());
			//
			PDNode exit = new PDNode();
			exit.setLineOfCode(0);
			exit.setCode("exit");
			cdg.addVertex(exit);
			cdg.addEdge(new Edge<>(entry, new CDEdge(CDEdge.Type.EPSILON), exit));
			return null;
		}

		@Override
		public Void visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
			// statementExpression ';'
			PDNode expr = new PDNode();
			expr.setLineOfCode(ctx.getStart().getLine());
			expr.setCode(getOriginalCodeText(ctx));
			Logger.debug(expr.getLineOfCode() + ": " + expr.getCode());
			addNodeEdge(expr);
			return null;
		}

		@Override
		public Void visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
			// localVariableDeclaration :  variableModifier* typeType variableDeclarators
			PDNode varDec = new PDNode();
			varDec.setLineOfCode(ctx.getStart().getLine());
			varDec.setCode(getOriginalCodeText(ctx));
			addNodeEdge(varDec);
			return null;
		}

		@Override
		public Void visitIfStatement(JavaParser.IfStatementContext ctx) {
			// 'if' parExpression statement ('else' statement)?
			PDNode ifNode = new PDNode();
			ifNode.setLineOfCode(ctx.getStart().getLine());
			ifNode.setCode("if " + getOriginalCodeText(ctx.parExpression()));
			addNodeEdge(ifNode);
			//
			PDNode thenRegion = new PDNode();
			thenRegion.setLineOfCode(0);
			thenRegion.setCode("THEN");
			cdg.addVertex(thenRegion);
			cdg.addEdge(new Edge<>(ifNode, new CDEdge(CDEdge.Type.TRUE), thenRegion));
			//
			PDNode elseRegion = new PDNode();
			elseRegion.setLineOfCode(0);
			elseRegion.setCode("ELSE");
			//
			pushCtrlDep(thenRegion);
			negDeps.push(elseRegion);
			visit(ctx.statement(0));
			negDeps.pop();
			popCtrlDep(thenRegion);
			//
			if (ctx.statement().size() > 1) { // if with else
				follows = false;
				cdg.addVertex(elseRegion);
				cdg.addEdge(new Edge<>(ifNode, new CDEdge(CDEdge.Type.FALSE), elseRegion));
				//
				pushCtrlDep(elseRegion);
				negDeps.push(thenRegion);
				visit(ctx.statement(1));
				negDeps.pop();
				popCtrlDep(elseRegion);
			} else if (buildRegion) {
				// there is no else, but we need to add the ELSE region
				cdg.addVertex(elseRegion);
				cdg.addEdge(new Edge<>(ifNode, new CDEdge(CDEdge.Type.FALSE), elseRegion));
			}
			follows = true;
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
				PDNode forExpr = new PDNode();
				forExpr.setLineOfCode(ctx.forControl().getStart().getLine());
				forExpr.setCode("for (" + getOriginalCodeText(ctx.forControl()) + ")");
				addNodeEdge(forExpr);
				//
				PDNode loopRegion = new PDNode();
				loopRegion.setLineOfCode(0);
				loopRegion.setCode("LOOP");
				cdg.addVertex(loopRegion);
				cdg.addEdge(new Edge<>(forExpr, new CDEdge(CDEdge.Type.TRUE), loopRegion));
				//
				pushLoopBlockDep(loopRegion);
				visit(ctx.statement());
				popLoopBlockDep(loopRegion);
			} else {
				// It's a traditional for-loop: 
				//   forInit? ';' expression? ';' forUpdate?
				PDNode forInit;
				PDNode forExpr, forUpdate;
				if (ctx.forControl().forInit() != null) { // non-empty init
					forInit = new PDNode();
					forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
					forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()));
					addNodeEdge(forInit);
				}
				int forExprLine;
				String forExprCode;
				if (ctx.forControl().expression() == null) { // empty for-loop-predicate
					forExprCode = ";";
					forExprLine = ctx.getStart().getLine();
				} else {
					forExprCode = getOriginalCodeText(ctx.forControl().expression());
					forExprLine = ctx.forControl().expression().getStart().getLine();
				}
				forExpr = new PDNode();
				forExpr.setLineOfCode(forExprLine);
				forExpr.setCode("for (" + forExprCode + ")");
				addNodeEdge(forExpr);
				//
				PDNode loopRegion = new PDNode();
				loopRegion.setLineOfCode(0);
				loopRegion.setCode("LOOP");
				cdg.addVertex(loopRegion);
				cdg.addEdge(new Edge<>(forExpr, new CDEdge(CDEdge.Type.TRUE), loopRegion));
				//
				pushLoopBlockDep(loopRegion);
				visit(ctx.statement());
				if (ctx.forControl().forUpdate() != null) { // non-empty for-update
					forUpdate = new PDNode();
					forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
					forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
					// we don't use 'addNodeEdge(forUpdate)' because the behavior of for-update
					// step is different from other statements with regards to break/continue.
					cdg.addVertex(forUpdate);
					cdg.addEdge(new Edge<>(ctrlDeps.peek(), new CDEdge(CDEdge.Type.EPSILON), forUpdate));
				}
				popLoopBlockDep(loopRegion);
			}
			return null;
		}

		@Override
		public Void visitWhileStatement(JavaParser.WhileStatementContext ctx) {
			// 'while' parExpression statement
			PDNode whileNode = new PDNode();
			whileNode.setLineOfCode(ctx.getStart().getLine());
			whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
			addNodeEdge(whileNode);
			//
			PDNode loopRegion = new PDNode();
			loopRegion.setLineOfCode(0);
			loopRegion.setCode("LOOP");
			cdg.addVertex(loopRegion);
			cdg.addEdge(new Edge<>(whileNode, new CDEdge(CDEdge.Type.TRUE), loopRegion));
			//
			pushLoopBlockDep(loopRegion);
			visit(ctx.statement());
			popLoopBlockDep(loopRegion);
			return null;
		}

		@Override
		public Void visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
			// 'do' statement 'while' parExpression ';'
			PDNode doRegion = new PDNode();
			doRegion.setLineOfCode(ctx.getStart().getLine());
			doRegion.setCode("do");
			addNodeEdge(doRegion);
			//
			pushLoopBlockDep(doRegion);
			visit(ctx.statement());
			// the while-node is treated as the last statement of the loop
			PDNode whileNode = new PDNode();
			whileNode.setLineOfCode(ctx.parExpression().getStart().getLine());
			whileNode.setCode("while " + getOriginalCodeText(ctx.parExpression()));
			addNodeEdge(whileNode);
			//
			popLoopBlockDep(doRegion);
			// this TRUE edge was removed, because only the repitition of 
			// the block statements is dependent on the while-predicate
			// cds.addEdge(whileNode, doRegion, new CDEdge(CDEdge.Type.TRUE));
			return null;
		}

		@Override
		public Void visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
			// 'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
			PDNode switchNode = new PDNode();
			switchNode.setLineOfCode(ctx.getStart().getLine());
			switchNode.setCode("switch " + getOriginalCodeText(ctx.parExpression()));
			addNodeEdge(switchNode);
			//
			pushLoopBlockDep(switchNode);
			for (JavaParser.SwitchBlockStatementGroupContext grp: ctx.switchBlockStatementGroup()) {
				// switchBlockStatementGroup :  switchLabel+ blockStatement+
				visitSwitchLabels(grp.switchLabel(), grp.blockStatement());
			}
			visitSwitchLabels(ctx.switchLabel(), null);
			popLoopBlockDep(switchNode);
			return null;
		}

		/**
		 * Visit a chain of consecutive case-statements.
		 */
		private PDNode visitSwitchLabels(List<JavaParser.SwitchLabelContext> cases, 
										   List<JavaParser.BlockStatementContext> block) {
			//  switchLabel :  'case' constantExpression ':'  |  'case' enumConstantName ':'  |  'default' ':'
			if (cases.size() == 1 && cases.get(0).getText().startsWith("default")) {
				PDNode defaultStmnt = new PDNode();
				defaultStmnt.setLineOfCode(cases.get(0).getStart().getLine());
				defaultStmnt.setCode(getOriginalCodeText(cases.get(0)));
				addNodeEdge(defaultStmnt);
				if (block != null) {
					negDeps.push(defaultStmnt);
					for (JavaParser.BlockStatementContext stmnt : block)
						visit(stmnt);
					negDeps.pop();
				}
			} else if (cases.size() > 0) {
				PDNode lastCase =  new PDNode();
				lastCase.setLineOfCode(cases.get(0).getStart().getLine());
				lastCase.setCode(getOriginalCodeText(cases.get(0)));
				addNodeEdge(lastCase);
				//
				PDNode thenRegion = null;
				if (block != null && block.size() > 0) {
					thenRegion = new PDNode();
					thenRegion.setLineOfCode(0);
					thenRegion.setCode("THEN");
					cdg.addVertex(thenRegion);
					cdg.addEdge(new Edge<>(lastCase, new CDEdge(CDEdge.Type.TRUE), thenRegion));
				}
				//
				for (JavaParser.SwitchLabelContext ctx : cases.subList(1, cases.size())) {
					PDNode nextCase = new PDNode();
					nextCase.setLineOfCode(ctx.getStart().getLine());
					nextCase.setCode(getOriginalCodeText(ctx));
					cdg.addVertex(nextCase);
					cdg.addEdge(new Edge<>(lastCase, new CDEdge(CDEdge.Type.FALSE), nextCase));
					cdg.addEdge(new Edge<>(nextCase, new CDEdge(CDEdge.Type.TRUE), thenRegion));
					lastCase = nextCase;
				}
				//
				if (block != null) {
					PDNode elseRegion = new PDNode();
					elseRegion.setLineOfCode(0);
					elseRegion.setCode("ELSE");
					cdg.addVertex(elseRegion); // We have to add the ELSE here, just 
					//                            in case it is needed in the following.
					pushCtrlDep(thenRegion);
					negDeps.push(elseRegion);
					for (JavaParser.BlockStatementContext stmnt : block)
						visit(stmnt);
					negDeps.pop();
					popCtrlDep(thenRegion);
					//
					if (buildRegion) {
						// there was a 'break', so we need to keep the ELSE region
						cdg.addEdge(new Edge<>(lastCase, new CDEdge(CDEdge.Type.FALSE), elseRegion));
					} else if (cdg.getOutDegree(elseRegion) == 0)
						// the ELSE region is not needed, so we remove it
						cdg.removeVertex(elseRegion);
				}
			}
			return null;
		}

		@Override
		public Void visitLabelStatement(JavaParser.LabelStatementContext ctx) {
			// Identifier ':' statement
			PDNode labelRegion = new PDNode();
			labelRegion.setLineOfCode(ctx.getStart().getLine());
			labelRegion.setCode(ctx.Identifier() + ": ");
			addNodeEdge(labelRegion);
			pushCtrlDep(labelRegion);
			visit(ctx.statement());
			popCtrlDep(labelRegion);
			return null;
		}

		@Override
		public Void visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
			// 'synchronized' parExpression block
			PDNode syncRegion = new PDNode();
			syncRegion.setLineOfCode(ctx.getStart().getLine());
			syncRegion.setCode("synchronized " + getOriginalCodeText(ctx.parExpression()));
			addNodeEdge(syncRegion);
			pushCtrlDep(syncRegion);
			visit(ctx.block());
			popCtrlDep(syncRegion);
			return null;
		}	

		@Override
		public Void visitBreakStatement(JavaParser.BreakStatementContext ctx) {
			// 'break' Identifier? ';'
			PDNode brk = new PDNode();
			brk.setLineOfCode(ctx.getStart().getLine());
			brk.setCode(getOriginalCodeText(ctx));
			addNodeEdge(brk);
			//
			// Check for the special case of a 'break' inside a 'default' switch-block:
			if (!negDeps.isEmpty() && negDeps.peek().getCode().startsWith("default"))
				return null; // just ignore it, and do nothing!
			//
			// NOTE: an important assumption here is that 'break' 
			//       is the last statement inside an if-else body,
			//       or it's the last statement inside a case-block
			if (!negDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth) {
				jumpDeps.push(negDeps.peek());
				jumpDeps.peek().setProperty("isExit", Boolean.FALSE);
				jumpDeps.peek().setProperty("isJump", Boolean.TRUE);
				lastFollowDepth = ctrlDeps.size();
				buildRegion = true;
			}
			return null;
		}

		@Override
		public Void visitContinueStatement(JavaParser.ContinueStatementContext ctx) {
			// 'continue' Identifier? ';'
			PDNode cnt = new PDNode();
			cnt.setLineOfCode(ctx.getStart().getLine());
			cnt.setCode(getOriginalCodeText(ctx));
			addNodeEdge(cnt);
			// NOTE: an important assumption here is that 'continue' 
			//       is the last statement inside an if-else body
			if (!negDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth) {
				jumpDeps.push(negDeps.peek());
				jumpDeps.peek().setProperty("isExit", Boolean.FALSE);
				jumpDeps.peek().setProperty("isJump", Boolean.TRUE);
				lastFollowDepth = ctrlDeps.size();
				buildRegion = true;
			}
			return null;
		}

		@Override
		public Void visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
			// 'return' expression? ';'
			PDNode ret = new PDNode();
			ret.setLineOfCode(ctx.getStart().getLine());
			ret.setCode(getOriginalCodeText(ctx));
			addNodeEdge(ret);
			// NOTE: an important assumption here is that 'return' 
			//       is the last statement inside an if-else body
			//       or it's the last statement of the entire method
			if (!negDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth) {
				jumpDeps.push(negDeps.peek());
				jumpDeps.peek().setProperty("isExit", Boolean.TRUE);
				jumpDeps.peek().setProperty("isJump", Boolean.FALSE);
				lastFollowDepth = ctrlDeps.size();
				buildRegion = true;
			}
			return visitChildren(ctx);
		}

		@Override
		public Void visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
			// 'throw' expression ';'
			PDNode thr = new PDNode();
			thr.setLineOfCode(ctx.getStart().getLine());
			thr.setCode(getOriginalCodeText(ctx));
			addNodeEdge(thr);
			// NOTE: an important assumption here is that 'throw' 
			//       is the last statement inside an if-else body,
			//       or it's the last statement of a try-catch block,
			//       or it's the last statement of the entire method
			if (!negDeps.isEmpty() && ctrlDeps.size() >= lastFollowDepth) {
				jumpDeps.push(negDeps.peek());
				jumpDeps.peek().setProperty("isExit", Boolean.TRUE);
				jumpDeps.peek().setProperty("isJump", Boolean.FALSE);
				lastFollowDepth = ctrlDeps.size();
				buildRegion = true;
			}
			return null;
		}

		@Override
		public Void visitTryStatement(JavaParser.TryStatementContext ctx) {
			// 'try' block (catchClause+ finallyBlock? | finallyBlock)
			PDNode tryRegion = new PDNode();
			tryRegion.setLineOfCode(ctx.getStart().getLine());
			tryRegion.setCode("try");
			tryRegion.setProperty("isTry", Boolean.TRUE);
			addNodeEdge(tryRegion);
			pushCtrlDep(tryRegion);
			negDeps.push(tryRegion);
			visit(ctx.block());
			// visit any available catch clauses
			if (ctx.catchClause() != null) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				PDNode catchNode;
				for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
					catchNode = new PDNode();
					catchNode.setLineOfCode(cx.getStart().getLine());
					catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
					cdg.addVertex(catchNode);
					cdg.addEdge(new Edge<>(tryRegion, new CDEdge(CDEdge.Type.THROWS), catchNode));
					pushCtrlDep(catchNode);
					visit(cx.block());
					popCtrlDep(catchNode);
				}
			}
			negDeps.pop(); // pop try-region
			popCtrlDep(tryRegion); // pop try-region
			//
			// If there is a finally-block
			if (ctx.finallyBlock() != null) {
				// 'finally' block
				PDNode finallyRegion = new PDNode();
				finallyRegion.setLineOfCode(ctx.finallyBlock().getStart().getLine());
				finallyRegion.setCode("finally");
				addNodeEdge(finallyRegion);
				pushCtrlDep(finallyRegion);
				visit(ctx.finallyBlock().block());
				popCtrlDep(finallyRegion);
			}
			return null;
		}

		@Override
		public Void visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx) {
			// 'try' resourceSpecification block catchClause* finallyBlock?
			// resourceSpecification :  '(' resources ';'? ')'
			// resources :  resource (';' resource)*
			// resource  :  variableModifier* classOrInterfaceType variableDeclaratorId '=' expression
			PDNode tryRegion = new PDNode();
			tryRegion.setLineOfCode(ctx.getStart().getLine());
			tryRegion.setCode("try");
			tryRegion.setProperty("isTry", Boolean.TRUE);
			addNodeEdge(tryRegion);
			pushCtrlDep(tryRegion);
			negDeps.push(tryRegion);
			//
			// Iterate over all resources ...
			for (JavaParser.ResourceContext rsrc: ctx.resourceSpecification().resources().resource()) {
				PDNode resource = new PDNode();
				resource.setLineOfCode(rsrc.getStart().getLine());
				resource.setCode(getOriginalCodeText(rsrc));
				addNodeEdge(resource);
			}
			//
			visit(ctx.block());
			// visit any available catch clauses
			if (ctx.catchClause() != null) {
				// 'catch' '(' variableModifier* catchType Identifier ')' block
				PDNode catchNode;
				for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
					catchNode = new PDNode();
					catchNode.setLineOfCode(cx.getStart().getLine());
					catchNode.setCode("catch (" + cx.catchType().getText() + " " + cx.Identifier().getText() + ")");
					cdg.addVertex(catchNode);
					cdg.addEdge(new Edge<>(tryRegion, new CDEdge(CDEdge.Type.THROWS), catchNode));
					pushCtrlDep(catchNode);
					visit(cx.block());
					popCtrlDep(catchNode);
				}
			}
			negDeps.pop(); // pop try-region
			popCtrlDep(tryRegion); // pop try-region
			//
			// If there is a finally-block
			if (ctx.finallyBlock() != null) {
				// 'finally' block
				PDNode finallyRegion = new PDNode();
				finallyRegion.setLineOfCode(ctx.finallyBlock().getStart().getLine());
				finallyRegion.setCode("finally");
				addNodeEdge(finallyRegion);
				pushCtrlDep(finallyRegion);
				visit(ctx.finallyBlock().block());
				popCtrlDep(finallyRegion);
			}
			return null;
		}

		/**
		 * Add given node to the CD-subgraph and 
		 * create a new CD-edge based on the last control-dependency.
		 */
		private void addNodeEdge(PDNode node) {
			checkBuildFollowRegion();
			cdg.addVertex(node);
			cdg.addEdge(new Edge<>(ctrlDeps.peek(), new CDEdge(CDEdge.Type.EPSILON), node));
		}

		/**
		 * Check if a follow-region must be created;
		 * if so, create it and push it on the CTRL-dependence stack.
		 */
		private void checkBuildFollowRegion() {
            Logger.debug("FOLLOWS = " + follows);
            Logger.debug("BUILD-REGION = " + buildRegion);
			if (buildRegion && follows) {
				PDNode followRegion = new PDNode();
				followRegion.setLineOfCode(0);
				followRegion.setCode("FOLLOW-" + regionCounter++);
				cdg.addVertex(followRegion);
				// check to see if there are any exit-jumps in the current chain
				followRegion.setProperty("isJump", Boolean.TRUE);
				for (PDNode dep: jumpDeps)
					if ((Boolean) dep.getProperty("isExit")) {
						followRegion.setProperty("isJump", Boolean.FALSE);
						followRegion.setProperty("isExit", Boolean.TRUE);
					}
				if ((Boolean) followRegion.getProperty("isJump"))
					++jmpCounter;
				// connect the follow-region
				if (Boolean.TRUE.equals(jumpDeps.peek().getProperty("isTry"))) {
					PDNode jmpDep = jumpDeps.pop();
					if (!cdg.containsVertex(jmpDep))
						cdg.addVertex(jmpDep);
					cdg.addEdge(new Edge<>(jmpDep, new CDEdge(CDEdge.Type.NOT_THROWS), followRegion));
				} else {
					PDNode jmpDep = jumpDeps.pop();
					if (!cdg.containsVertex(jmpDep))
						cdg.addVertex(jmpDep);
					cdg.addEdge(new Edge<>(jmpDep, new CDEdge(CDEdge.Type.EPSILON), followRegion));
				}
				// if the jump-chain is not empty, remove all non-exit jumps
				if (!jumpDeps.isEmpty()) {
					for (Iterator<PDNode> itr = jumpDeps.iterator(); itr.hasNext(); ) {
						PDNode dep = itr.next();
						if (Boolean.FALSE.equals(dep.getProperty("isExit")))
							itr.remove();
					}
				}
				lastFollowDepth = 0;
				pushCtrlDep(followRegion);
			}
		}

		/**
		 * Push given node to the control-dependency stack.
		 */
		private void pushCtrlDep(PDNode dep) {
			ctrlDeps.push(dep);
			buildRegion = false;
		}

		/**
		 * Push this loop block region to the control-dependency stack
		 * and reset the jumps-counter for this loop-block.
		 */
		private void pushLoopBlockDep(PDNode region) {
			pushCtrlDep(region);
			jmpCounts.push(jmpCounter);
			jmpCounter = 0;
		}

		/**
		 * Pop out the last dependency off the stack and 
		 * set the 'buildRegion' flag if necessary.
		 */
		private void popCtrlDep(PDNode dep) {
			ctrlDeps.remove(dep); //ctrlDeps.pop();
			buildRegion = !jumpDeps.isEmpty();
		}

		/**
		 * Pop out this loop-block region off the control stack 
		 * and also pop off all jump-dependencies of this block.
		 */
		private void popLoopBlockDep(PDNode region) {
			for (Iterator<PDNode> itr = ctrlDeps.iterator(); jmpCounter > 0 && itr.hasNext(); ) {
				// NOTE: This iteration works correctly, even though ctrlDeps is a stack.
				//       This is due to the Deque implementation, which removes in LIFO.
				PDNode dep = itr.next();
				if (Boolean.TRUE.equals(dep.getProperty("isJump"))) {
					itr.remove();
					--jmpCounter;
				}
			}
			jmpCounter = jmpCounts.pop();
			lastFollowDepth = 0;
			popCtrlDep(region);
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

	}
	
}

