/*** In The Name of Allah ***/
package progex.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import progex.graphs.ast.ASNode;
import progex.graphs.ast.AbstractSyntaxTree;
import progex.java.parser.JavaBaseVisitor;
import progex.java.parser.JavaLexer;
import progex.java.parser.JavaParser;
import progex.utils.Logger;

/**
 * Abstract Syntax Tree (AST) builder for Java programs.
 * A Java parser generated via ANTLRv4 is used for this purpose.
 * This implementation is based on ANTLRv4's Visitor pattern.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaASTBuilder {
	
	/**
	 * ‌Build and return the Abstract Syntax Tree (AST) for the given Java source file.
	 */
	public static AbstractSyntaxTree build(String javaFile) throws IOException {
		return build(new File(javaFile));
	}
	
	/**
	 * ‌Build and return the Abstract Syntax Tree (AST) for the given Java source file.
	 */
	public static AbstractSyntaxTree build(File javaFile) throws IOException {
		if (!javaFile.getName().endsWith(".java"))
			throw new IOException("Not a Java File!");
		InputStream inFile = new FileInputStream(javaFile);
		ANTLRInputStream input = new ANTLRInputStream(inFile);
		JavaLexer lexer = new JavaLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(tokens);
		ParseTree tree = parser.compilationUnit();
		return build(javaFile.getPath(), tree, null, null);
	}
	
	/**
	 * ‌Build and return the Abstract Syntax Tree (AST) for the given Parse-Tree.
	 * The 'ctxProps' map includes contextual-properties for particular nodes 
	 * in the parse-tree, which can be used for linking this graph with other 
	 * graphs by using the same parse-tree and the same contextual-properties.
	 */
	public static AbstractSyntaxTree build(String filePath, ParseTree tree, 
            String propKey, Map<ParserRuleContext, Object> ctxProps) {
		AbstractSyntaxVisitor visitor = new AbstractSyntaxVisitor(filePath, propKey, ctxProps);
        Logger.debug("Visitor building AST of: " + filePath);
        return visitor.build(tree);
	}
	
	/**
	 * Visitor class which constructs the AST for a given ParseTree.
	 */
	private static class AbstractSyntaxVisitor extends JavaBaseVisitor<String> {
        
		private String propKey;
        private String typeModifier;
        private String memberModifier;
        private Deque<ASNode> parentStack;
        private final AbstractSyntaxTree AST;
		private Map<ParserRuleContext, Object> contexutalProperties;
		
		public AbstractSyntaxVisitor(String filePath, String propKey, Map<ParserRuleContext, Object> ctxProps) {
            parentStack = new ArrayDeque<>();
            AST = new AbstractSyntaxTree(filePath);
			this.propKey = propKey;
			contexutalProperties = ctxProps;
		}
        
        public AbstractSyntaxTree build(ParseTree tree) {
            JavaParser.CompilationUnitContext rootCntx = (JavaParser.CompilationUnitContext) tree;
            AST.ROOT.setValue(new File(AST.FILE_PATH).getName());
            parentStack.push(AST.ROOT);
            if (rootCntx.packageDeclaration() != null)
                visit(rootCntx.packageDeclaration());
            //
            if (rootCntx.importDeclaration() != null && rootCntx.importDeclaration().size() > 0) {
                ASNode imports = new ASNode(ASNode.Type.IMPORTS);
                AST.addVertex(imports);
                AST.addEdge(AST.ROOT, imports);
                parentStack.push(imports);
                for (JavaParser.ImportDeclarationContext importCtx : rootCntx.importDeclaration())
                    visit(importCtx);
                parentStack.pop();
            }
            //
            if (rootCntx.typeDeclaration() != null)
                for (JavaParser.TypeDeclarationContext typeDecCtx : rootCntx.typeDeclaration())
                    visit(typeDecCtx);
            parentStack.pop();
            return AST;
        }
		
        @Override
        public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
            // packageDeclaration :  annotation* 'package' qualifiedName ';'
            String packageName = ctx.qualifiedName().getText();
            ASNode node = new ASNode(ASNode.Type.PACKAGE);
            node.setValue(packageName);
            AST.addVertex(node);
            AST.addEdge(parentStack.peek(), node);
            return null;
        }

        @Override
        public String visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
            // importDeclaration :  'import' 'static'? qualifiedName ('.' '*')? ';'
            String qualifiedName = ctx.qualifiedName().getText();
            int last = ctx.getChildCount() - 1;
            if (ctx.getChild(last - 1).getText().equals("*")
                    && ctx.getChild(last - 2).getText().equals(".")) {
                qualifiedName += ".*";
            }
            ASNode node = new ASNode(ASNode.Type.IMPORT);
            node.setValue(qualifiedName);
            AST.addVertex(node);
            AST.addEdge(parentStack.peek(), node);
            return null;
        }

        @Override
        public String visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
            // typeDeclaration
            //    :   classOrInterfaceModifier* classDeclaration
            //    |   classOrInterfaceModifier* enumDeclaration
            //    |   classOrInterfaceModifier* interfaceDeclaration
            //    |   classOrInterfaceModifier* annotationTypeDeclaration
            //    |   ';'
            typeModifier = "";
            for (JavaParser.ClassOrInterfaceModifierContext modifierCtx : ctx.classOrInterfaceModifier())
                typeModifier += modifierCtx.getText() + " ";
            typeModifier = typeModifier.trim();
            visitChildren(ctx);
            return null;
        }

        @Override
        public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
            // classDeclaration 
            //   :  'class' Identifier typeParameters? 
            //      ('extends' typeType)? ('implements' typeList)? classBody
            ASNode classNode = new ASNode(ASNode.Type.CLASS);
            AST.addVertex(classNode);
            AST.addEdge(parentStack.peek(), classNode);
            //
            ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
            modifierNode.setValue(typeModifier);
            AST.addVertex(modifierNode);
            AST.addEdge(classNode, modifierNode);
            //
            ASNode nameNode = new ASNode(ASNode.Type.NAME);
            String className = ctx.Identifier().getText();
            if (ctx.typeParameters() != null)
                className += ctx.typeParameters().getText();
            nameNode.setValue(className);
            AST.addVertex(nameNode);
            AST.addEdge(classNode, nameNode);
            //
            if (ctx.typeType() != null) {
                String extend = ctx.typeType().getText();
                ASNode extendsNode = new ASNode(ASNode.Type.EXTENDS);
                extendsNode.setValue(extend);
                AST.addVertex(extendsNode);
                AST.addEdge(classNode, extendsNode);
            }
            //
            if (ctx.typeList() != null) {
                ASNode implementsNode = new ASNode(ASNode.Type.IMPLEMENTS);
                AST.addVertex(implementsNode);
                AST.addEdge(classNode, implementsNode);
                for (JavaParser.TypeTypeContext type : ctx.typeList().typeType()) {
                    ASNode node = new ASNode(ASNode.Type.INTERFACE);
                    node.setValue(type.getText());
                    AST.addVertex(node);
                    AST.addEdge(implementsNode, node);
                }
            }
            parentStack.push(classNode);
            visit(ctx.classBody());
            parentStack.pop();
            return null;
        }

        @Override
        public String visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
            // classBodyDeclaration
            //   :  ';'
            //   |  'static'? block
            //   |   modifier* memberDeclaration
            //
            // memberDeclaration
            //    :   methodDeclaration
            //    |   genericMethodDeclaration
            //    |   fieldDeclaration
            //    |   constructorDeclaration
            //    |   genericConstructorDeclaration
            //    |   interfaceDeclaration
            //    |   annotationTypeDeclaration
            //    |   classDeclaration
            //    |   enumDeclaration
            //
            if (ctx.block() != null) {
                ASNode staticBlock = new ASNode(ASNode.Type.STATIC_BLOCK);
                AST.addVertex(staticBlock);
                AST.addEdge(parentStack.peek(), staticBlock);
                parentStack.push(staticBlock);
                visitChildren(ctx.block());
                parentStack.pop();
            } else if (ctx.memberDeclaration() != null) {
                // Modifier
                memberModifier = "";
                for (JavaParser.ModifierContext modCtx: ctx.modifier())
                    memberModifier += modCtx.getText() + " ";
                memberModifier = memberModifier.trim();
                // Field member
                if (ctx.memberDeclaration().fieldDeclaration() != null) {
                    ASNode fieldsNode = new ASNode(ASNode.Type.FIELD);
                    AST.addVertex(fieldsNode);
                    AST.addEdge(parentStack.peek(), fieldsNode);
                    parentStack.push(fieldsNode);
                    visit(ctx.memberDeclaration().fieldDeclaration());
                    parentStack.pop();
                } else if (ctx.memberDeclaration().constructorDeclaration() != null) {
                    // Constructor member
                    ASNode constructorNode = new ASNode(ASNode.Type.CONSTRUCTOR);
                    AST.addVertex(constructorNode);
                    AST.addEdge(parentStack.peek(), constructorNode);
                    parentStack.push(constructorNode);
                    visit(ctx.memberDeclaration().constructorDeclaration());
                    parentStack.pop();
                } else if (ctx.memberDeclaration().methodDeclaration() != null) {
                    // Method member
                    ASNode methodsNode = new ASNode(ASNode.Type.METHOD);
                    AST.addVertex(methodsNode);
                    AST.addEdge(parentStack.peek(), methodsNode);
                    parentStack.push(methodsNode);
                    visit(ctx.memberDeclaration().methodDeclaration());
                    parentStack.pop();
                } else if (ctx.memberDeclaration().classDeclaration() != null) {
                    // Inner-type member
                    visitChildren(ctx.memberDeclaration());
                }
            }
            return null;
        }
        
        @Override
        public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
            // constructorDeclaration :  Identifier formalParameters ('throws' qualifiedNameList)? constructorBody
            // constructorBody :  block
            //
            ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
            modifierNode.setValue(memberModifier);
            AST.addVertex(modifierNode);
            AST.addEdge(parentStack.peek(), modifierNode);
            //
            if (ctx.formalParameters().formalParameterList() != null) {
                ASNode paramsNode = new ASNode(ASNode.Type.PARAMS);
                AST.addVertex(paramsNode);
                AST.addEdge(parentStack.peek(), paramsNode);
                parentStack.push(paramsNode);
                for (JavaParser.FormalParameterContext paramctx: 
                        ctx.formalParameters().formalParameterList().formalParameter()) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    AST.addVertex(type);
                    type.setValue(paramctx.typeType().getText());
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    AST.addVertex(name);
                    name.setValue(paramctx.variableDeclaratorId().getText());
                    AST.addEdge(varNode, name);
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setValue(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setValue(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                parentStack.pop();
            }
            //
            ASNode bodyBlock = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(bodyBlock);
            AST.addEdge(parentStack.peek(), bodyBlock);
            parentStack.push(bodyBlock);
            visitChildren(ctx.constructorBody().block());
            parentStack.pop();
            return null;
        }

        @Override
        public String visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
            // fieldDeclaration    :  typeType variableDeclarators ';'
            // variableDeclarators :  variableDeclarator (',' variableDeclarator)*
            // variableDeclarator  :  variableDeclaratorId ('=' variableInitializer)?
            //
            for (JavaParser.VariableDeclaratorContext varctx : ctx.variableDeclarators().variableDeclarator()) {
                ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
                modifierNode.setValue(memberModifier);
                AST.addVertex(modifierNode);
                AST.addEdge(parentStack.peek(), modifierNode);
                //
                ASNode type = new ASNode(ASNode.Type.TYPE);
                AST.addVertex(type);
                type.setValue(ctx.typeType().getText());
                AST.addEdge(parentStack.peek(), type);
                //
                ASNode name = new ASNode(ASNode.Type.NAME);
                AST.addVertex(name);
                name.setValue(varctx.variableDeclaratorId().getText());
                AST.addEdge(parentStack.peek(), name);
                //
                if (varctx.variableInitializer() != null) {
                    ASNode initNode = new ASNode(ASNode.Type.INIT_VALUE);
                    initNode.setValue("= " + getOriginalCodeText(varctx.variableInitializer()));
                    AST.addVertex(initNode);
                    AST.addEdge(parentStack.peek(), initNode);
                }
            }
            return null;
        }

        @Override
        public String visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
            //  methodDeclaration
            //  :   (typeType|'void') Identifier formalParameters ('[' ']')* ('throws' qualifiedNameList)?  ( methodBody | ';')
            //
            //  formalParameters :  '(' formalParameterList? ')'
            //
            //  formalParameterList
            //    :   formalParameter (',' formalParameter)* (',' lastFormalParameter)?
            //    |   lastFormalParameter
            //
            //  formalParameter :  variableModifier* typeType variableDeclaratorId
            //
            //  lastFormalParameter :  variableModifier* typeType '...' variableDeclaratorId
            //
            ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
            modifierNode.setValue(memberModifier);
            AST.addVertex(modifierNode);
            AST.addEdge(parentStack.peek(), modifierNode);
            //
            ASNode retNode = new ASNode(ASNode.Type.RETURN);
            retNode.setValue(ctx.getChild(0).getText());
            AST.addVertex(retNode);
            AST.addEdge(parentStack.peek(), retNode);
            //
            String methodName = ctx.Identifier().getText();
            ASNode nameNode = new ASNode(ASNode.Type.NAME);
            nameNode.setValue(methodName);
            AST.addVertex(nameNode);
            AST.addEdge(parentStack.peek(), nameNode);
            //
            if (ctx.formalParameters().formalParameterList() != null) {
                ASNode paramsNode = new ASNode(ASNode.Type.PARAMS);
                AST.addVertex(paramsNode);
                AST.addEdge(parentStack.peek(), paramsNode);
                parentStack.push(paramsNode);
                for (JavaParser.FormalParameterContext paramctx: 
                        ctx.formalParameters().formalParameterList().formalParameter()) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    AST.addVertex(type);
                    type.setValue(paramctx.typeType().getText());
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    AST.addVertex(name);
                    name.setValue(paramctx.variableDeclaratorId().getText());
                    AST.addEdge(varNode, name);
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setValue(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setValue(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                parentStack.pop();
            }
            //
            if (ctx.methodBody() != null) {
                ASNode methodBody = new ASNode(ASNode.Type.BLOCK);
                AST.addVertex(methodBody);
                AST.addEdge(parentStack.peek(), methodBody);
                parentStack.push(methodBody);
                visitChildren(ctx.methodBody());
                parentStack.pop();
            }
            return null;
        }

        @Override
        public String visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
            // localVariableDeclaration :  variableModifier* typeType variableDeclarators
            // variableDeclarators      :  variableDeclarator (',' variableDeclarator)*
            // variableDeclarator       :  variableDeclaratorId ('=' variableInitializer)?
            //
            for (JavaParser.VariableDeclaratorContext varctx: ctx.variableDeclarators().variableDeclarator()) {
                ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                AST.addVertex(varNode);
                AST.addEdge(parentStack.peek(), varNode);
                //
                ASNode typeNode = new ASNode(ASNode.Type.TYPE);
                typeNode.setValue(ctx.typeType().getText());
                AST.addVertex(typeNode);
                AST.addEdge(varNode, typeNode);
                //
                ASNode nameNode = new ASNode(ASNode.Type.NAME);
                nameNode.setValue(varctx.variableDeclaratorId().getText());
                AST.addVertex(nameNode);
                AST.addEdge(varNode, nameNode);
                //
                if (varctx.variableInitializer() != null) {
                    ASNode initNode = new ASNode(ASNode.Type.INIT_VALUE);
                    initNode.setValue("= " + getOriginalCodeText(varctx.variableInitializer()));
                    AST.addVertex(initNode);
                    AST.addEdge(varNode, initNode);
                }
            }
            return null;
        }
        
        private void visitStatement(ParserRuleContext ctx) {
            Logger.printf(Logger.Level.DEBUG, "Visiting: (%d)  %s", ctx.getStart().getLine(), getOriginalCodeText(ctx));
            ASNode statementNode = new ASNode(ASNode.Type.STATEMENT);
            statementNode.setLineOfCode(ctx.getStart().getLine());
            statementNode.setValue(getOriginalCodeText(ctx));
            AST.addVertex(statementNode);
            AST.addEdge(parentStack.peek(), statementNode);
        }
        
        @Override
        public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitBreakStatement(JavaParser.BreakStatementContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitContinueStatement(JavaParser.ContinueStatementContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
            visitStatement(ctx);
            return null;
        }
        
        @Override
        public String visitLabelStatement(JavaParser.LabelStatementContext ctx) {
            // labelStatement :  Identifier ':' statement
            ASNode labelNode = new ASNode(ASNode.Type.LABELED);
            AST.addVertex(labelNode);
            AST.addEdge(parentStack.peek(), labelNode);
            //
            ASNode labelName = new ASNode(ASNode.Type.NAME);
            labelName.setValue(ctx.Identifier().getText());
            AST.addVertex(labelName);
            AST.addEdge(labelNode, labelName);
            //
            parentStack.push(labelNode);
            visit(ctx.statement());
            parentStack.pop();
            return null;
        }
        
        @Override
        public String visitIfStatement(JavaParser.IfStatementContext ctx) {
            // 'if' parExpression statement ('else' statement)?
            ASNode ifNode = new ASNode(ASNode.Type.IF);
            AST.addVertex(ifNode);
            AST.addEdge(parentStack.peek(), ifNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setValue(ctx.parExpression().getText());
            AST.addVertex(cond);
            AST.addEdge(ifNode, cond);
            //
            ASNode thenNode = new ASNode(ASNode.Type.THEN);
            AST.addVertex(thenNode);
            AST.addEdge(ifNode, thenNode);
            parentStack.push(thenNode);
            visit(ctx.statement(0));
            parentStack.pop();
            //
            if (ctx.statement(1) != null) {
                ASNode elseNode = new ASNode(ASNode.Type.ELSE);
                AST.addVertex(elseNode);
                AST.addEdge(ifNode, elseNode);
                parentStack.push(elseNode);
                visit(ctx.statement(1));
                parentStack.pop();
            }
            return null;
        }

        @Override
        public String visitForStatement(JavaParser.ForStatementContext ctx) {
            // 'for' '(' forControl ')' statement
            // forControl :  enhancedForControl  |  forInit? ';' expression? ';' forUpdate?
            // enhancedForControl :  variableModifier* typeType variableDeclaratorId ':' expression
            // forInit   :  localVariableDeclaration  |  expressionList
            // forUpdate :  expressionList
            //
            ASNode forNode;
            if (ctx.forControl().enhancedForControl() != null) {
                // for-each loop
                forNode = new ASNode(ASNode.Type.FOR_EACH);
                AST.addVertex(forNode);
                AST.addEdge(parentStack.peek(), forNode);
                //
                ASNode initType = new ASNode(ASNode.Type.TYPE);
                ASNode initId = new ASNode(ASNode.Type.NAME);
                AST.addVertex(initType);
                AST.addVertex(initId);
                String typetype = ctx.forControl().enhancedForControl().typeType().getText();
                String id = ctx.forControl().enhancedForControl().variableDeclaratorId().getText();
                initType.setValue(typetype);
                initId.setValue(id);
                AST.addEdge(forNode, initType);
                AST.addEdge(forNode, initId);
                //
                ASNode expr = new ASNode(ASNode.Type.STATEMENT);
                AST.addVertex(expr);
                expr.setValue(ctx.forControl().enhancedForControl().expression().getText());
                AST.addEdge(forNode, expr);
            } 
            // Classic for(init; expr; update)
            else {
                forNode = new ASNode(ASNode.Type.FOR);
                AST.addVertex(forNode);
                AST.addEdge(parentStack.peek(), forNode);
                //
                // for init
                if (ctx.forControl().forInit() != null) {
                    ASNode forInit = new ASNode(ASNode.Type.FOR_INIT);
                    forInit.setValue(ctx.forControl().forInit().getText());
                    AST.addVertex(forInit);
                    AST.addEdge(forNode, forInit);
                }
                // for expr
                ASNode forExpr = new ASNode(ASNode.Type.CONDITION);
                forExpr.setValue(ctx.forControl().expression().getText());
                AST.addVertex(forExpr);
                AST.addEdge(forNode, forExpr);
                // for update
                ASNode forUpdate = new ASNode(ASNode.Type.FOR_UPDATE);
                forUpdate.setValue(ctx.forControl().forUpdate().getText());
                AST.addVertex(forUpdate);
                AST.addEdge(forNode, forUpdate);
            }
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(block);
            AST.addEdge(forNode, block);
            parentStack.push(block);
            visit(ctx.statement());
            parentStack.pop();
            return null;
        }
        
        @Override
        public String visitWhileStatement(JavaParser.WhileStatementContext ctx) {
            // 'while' parExpression statement
            ASNode whileNode = new ASNode(ASNode.Type.WHILE);
            AST.addVertex(whileNode);
            AST.addEdge(parentStack.peek(), whileNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setValue(getOriginalCodeText(ctx.parExpression()));
            AST.addVertex(cond);
            AST.addEdge(whileNode, cond);
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(block);
            AST.addEdge(whileNode, block);
            parentStack.push(block);
            visit(ctx.statement());
            parentStack.pop();
            return null;
        }

        @Override
        public String visitDoWhileStatement(JavaParser.DoWhileStatementContext ctx) {
            // 'do' statement 'while' parExpression ';'
            ASNode doWhileNode = new ASNode(ASNode.Type.DO_WHILE);
            AST.addVertex(doWhileNode);
            AST.addEdge(parentStack.peek(), doWhileNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setValue(getOriginalCodeText(ctx.parExpression()));
            AST.addVertex(cond);
            AST.addEdge(doWhileNode, cond);
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(block);
            AST.addEdge(doWhileNode, block);
            parentStack.push(block);
            visit(ctx.statement());
            parentStack.pop();
            return null;
        }

        @Override
        public String visitTryStatement(JavaParser.TryStatementContext ctx) {
            // 'try' block (catchClause+ finallyBlock? | finallyBlock)
            ASNode tryNode = new ASNode(ASNode.Type.TRY);
            AST.addVertex(tryNode);
            AST.addEdge(parentStack.peek(), tryNode);
            //
            ASNode tryBlock = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(tryBlock);
            AST.addEdge(tryNode, tryBlock);
            parentStack.push(tryBlock);
            visit(ctx.block());
            parentStack.pop();
            // catchClause :  'catch' '(' variableModifier* catchType Identifier ')' block
            if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
                for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
                    ASNode catchNode = new ASNode(ASNode.Type.CATCH);
                    AST.addVertex(catchNode);
                    AST.addEdge(tryNode, catchNode);
                    //
                    ASNode catchType = new ASNode(ASNode.Type.TYPE);
                    catchType.setValue(cx.catchType().getText());
                    AST.addVertex(catchType);
                    AST.addEdge(catchNode, catchType);
                    //
                    ASNode catchName = new ASNode(ASNode.Type.NAME);
                    catchName.setValue(cx.Identifier().getText());
                    AST.addVertex(catchName);
                    AST.addEdge(catchNode, catchName);
                    //
                    ASNode catchBlock = new ASNode(ASNode.Type.BLOCK);
                    AST.addVertex(catchBlock);
                    AST.addEdge(catchNode, catchBlock);
                    parentStack.push(catchBlock);
                    visit(cx.block());
                    parentStack.pop();
                }
            }
            // finallyBlock :  'finally' block
            if (ctx.finallyBlock() != null) {
                ASNode finallyNode = new ASNode(ASNode.Type.FINALLY);
                AST.addVertex(finallyNode);
                AST.addEdge(tryNode, finallyNode);
                parentStack.push(finallyNode);
                visit(ctx.finallyBlock().block());
                parentStack.pop();
            }
            return null;
        }

        @Override
        public String visitTryWithResourceStatement(JavaParser.TryWithResourceStatementContext ctx) {
            // 'try' resourceSpecification block catchClause* finallyBlock?
            // resourceSpecification :  '(' resources ';'? ')'
            // resources :  resource (';' resource)*
            // resource  :  variableModifier* classOrInterfaceType variableDeclaratorId '=' expression
            //
            ASNode tryNode = new ASNode(ASNode.Type.TRY);
            AST.addVertex(tryNode);
            AST.addEdge(parentStack.peek(), tryNode);
            //
            ASNode resNode = new ASNode(ASNode.Type.RESOURCES);
            AST.addVertex(resNode);
            AST.addEdge(tryNode, resNode);
            for (JavaParser.ResourceContext resctx : ctx.resourceSpecification().resources().resource()) {
                ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                AST.addVertex(varNode);
                AST.addEdge(resNode, varNode);
                //
                ASNode resType = new ASNode(ASNode.Type.TYPE);
                resType.setValue(resctx.classOrInterfaceType().getText());
                AST.addVertex(resType);
                AST.addEdge(varNode, resType);
                //
                ASNode resName = new ASNode(ASNode.Type.NAME);
                resName.setValue(resctx.variableDeclaratorId().getText());
                AST.addVertex(resName);
                AST.addEdge(varNode, resName);
                //
                ASNode resInit = new ASNode(ASNode.Type.INIT_VALUE);
                resInit.setValue(resctx.expression().getText());
                AST.addVertex(resInit);
                AST.addEdge(varNode, resInit);
            }
            ASNode tryBlock = new ASNode(ASNode.Type.BLOCK);
            AST.addVertex(tryBlock);
            AST.addEdge(tryNode, tryBlock);
            parentStack.push(tryBlock);
            visit(ctx.block());
            parentStack.pop();
            //
            // catchClause :   'catch' '(' variableModifier* catchType Identifier ')' block
            if (ctx.catchClause().size() > 0 && ctx.catchClause() != null) {
                for (JavaParser.CatchClauseContext cx : ctx.catchClause()) {
                    ASNode catchNode = new ASNode(ASNode.Type.CATCH);
                    AST.addVertex(catchNode);
                    AST.addEdge(tryNode, catchNode);
                    //
                    ASNode catchType = new ASNode(ASNode.Type.TYPE);
                    catchType.setValue(cx.catchType().getText());
                    AST.addVertex(catchType);
                    AST.addEdge(catchNode, catchType);
                    //
                    ASNode catchName = new ASNode(ASNode.Type.NAME);
                    catchName.setValue(cx.Identifier().getText());
                    AST.addVertex(catchName);
                    AST.addEdge(catchNode, catchName);
                    //
                    ASNode catchBlock = new ASNode(ASNode.Type.BLOCK);
                    AST.addVertex(catchBlock);
                    AST.addEdge(catchNode, catchBlock);
                    parentStack.push(catchBlock);
                    visit(cx.block());
                    parentStack.pop();
                }
            }
            // finallyBlock :  'finally' block
            if (ctx.finallyBlock() != null) {
                ASNode finallyNode = new ASNode(ASNode.Type.FINALLY);
                AST.addVertex(finallyNode);
                AST.addEdge(tryNode, finallyNode);
                parentStack.push(finallyNode);
                visit(ctx.finallyBlock().block());
                parentStack.pop();
            }
            return null;
        }

        @Override
        public String visitSwitchStatement(JavaParser.SwitchStatementContext ctx) {
            // 'switch' parExpression '{' switchBlockStatementGroup* switchLabel* '}'
            // switchBlockStatementGroup  :   switchLabel+ blockStatement+
            // switchLabel :   'case' constantExpression ':'
            //             |   'case' enumConstantName ':'
            //             |   'default' ':'
            //
            ASNode switchNode = new ASNode(ASNode.Type.SWITCH);
            AST.addVertex(switchNode);
            AST.addEdge(parentStack.peek(), switchNode);
            //
            ASNode varName = new ASNode(ASNode.Type.NAME);
            varName.setValue(ctx.parExpression().getText());
            AST.addVertex(varName);
            AST.addEdge(switchNode, varName);
            //
            if (ctx.switchBlockStatementGroup() != null) {
                for (JavaParser.SwitchBlockStatementGroupContext grp : ctx.switchBlockStatementGroup()) {
                    ASNode blockNode = new ASNode(ASNode.Type.BLOCK);
                    AST.addVertex(blockNode);
                    for (JavaParser.SwitchLabelContext lblctx : grp.switchLabel())
                        visitSwitchLabel(lblctx, switchNode, blockNode);
                    parentStack.push(blockNode);
                    for (JavaParser.BlockStatementContext blk : grp.blockStatement())
                        visit(blk);
                    parentStack.pop();
                }
            }
            if (ctx.switchLabel() != null) {
                ASNode blockNode = new ASNode(ASNode.Type.BLOCK);
                AST.addVertex(blockNode);
                for (JavaParser.SwitchLabelContext lblctx : ctx.switchLabel())
                    visitSwitchLabel(lblctx, switchNode, blockNode);
            }
            return null;
        }
        
        private void visitSwitchLabel(JavaParser.SwitchLabelContext lblctx, ASNode switchNode, ASNode blockNode) {
            ASNode caseNode;
            if (lblctx.constantExpression() != null) {
                caseNode = new ASNode(ASNode.Type.CASE);
                caseNode.setValue(lblctx.constantExpression().getText());
            } else if (lblctx.enumConstantName() != null) {
                caseNode = new ASNode(ASNode.Type.CASE);
                caseNode.setValue(lblctx.enumConstantName().getText());
            } else
                caseNode = new ASNode(ASNode.Type.DEFAULT);
            AST.addVertex(caseNode);
            AST.addEdge(switchNode, caseNode);
            AST.addEdge(caseNode, blockNode);
        }

        /**
         * Get the original program text for the given parser-rule context. 
         * This is required for preserving white-spaces.
         */
        private String getOriginalCodeText(ParserRuleContext ctx) {
            int start = ctx.start.getStartIndex();
            int stop = ctx.stop.getStopIndex();
            Interval interval = new Interval(start, stop);
            return ctx.start.getInputStream().getText(interval);
        }
    }
}
