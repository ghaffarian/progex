/*** In The Name of Allah ***/
package ghaffarian.progex.java;

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
import ghaffarian.progex.graphs.ast.ASNode;
import ghaffarian.progex.graphs.ast.AbstractSyntaxTree;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;
import ghaffarian.nanologger.Logger;
import java.util.LinkedHashMap;

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
        private Map<String, String> vars, fields, methods;
		private int varsCounter, fieldsCounter, methodsCounter;
		private Map<ParserRuleContext, Object> contexutalProperties;
		
		public AbstractSyntaxVisitor(String filePath, String propKey, Map<ParserRuleContext, Object> ctxProps) {
            parentStack = new ArrayDeque<>();
            AST = new AbstractSyntaxTree(filePath);
			this.propKey = propKey;
			contexutalProperties = ctxProps;
            vars = new LinkedHashMap<>();
            fields = new LinkedHashMap<>();
            methods = new LinkedHashMap<>();
            varsCounter = 0; fieldsCounter = 0; methodsCounter = 0;
		}
        
        public AbstractSyntaxTree build(ParseTree tree) {
            JavaParser.CompilationUnitContext rootCntx = (JavaParser.CompilationUnitContext) tree;
            AST.ROOT.setCode(new File(AST.FILE_PATH).getName());
            parentStack.push(AST.ROOT);
            if (rootCntx.packageDeclaration() != null)
                visit(rootCntx.packageDeclaration());
            //
            if (rootCntx.importDeclaration() != null && rootCntx.importDeclaration().size() > 0) {
                ASNode imports = new ASNode(ASNode.Type.IMPORTS);
                imports.setLineOfCode(rootCntx.importDeclaration(0).getStart().getLine());
                Logger.debug("Adding imports");
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
            vars.clear();
            fields.clear();
            methods.clear();
            return AST;
        }

        //=====================================================================//
        //                           DECLARATIONS                              //
        //=====================================================================//        
        
        @Override
        public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
            // packageDeclaration :  annotation* 'package' qualifiedName ';'
            ASNode node = new ASNode(ASNode.Type.PACKAGE);
            node.setCode(ctx.qualifiedName().getText());
            node.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding package");
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
            node.setCode(qualifiedName);
            node.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding import " + qualifiedName);
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
            classNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding class node");
            AST.addVertex(classNode);
            AST.addEdge(parentStack.peek(), classNode);
            //
            ASNode modifierNode = new ASNode(ASNode.Type.MODIFIER);
            modifierNode.setCode(typeModifier);
            modifierNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding class modifier");
            AST.addVertex(modifierNode);
            AST.addEdge(classNode, modifierNode);
            //
            ASNode nameNode = new ASNode(ASNode.Type.NAME);
            String className = ctx.Identifier().getText();
            if (ctx.typeParameters() != null)
                className += ctx.typeParameters().getText();
            nameNode.setCode(className);
            nameNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding class name: " + className);
            AST.addVertex(nameNode);
            AST.addEdge(classNode, nameNode);
            //
            if (ctx.typeType() != null) {
                ASNode extendsNode = new ASNode(ASNode.Type.EXTENDS);
                extendsNode.setCode(ctx.typeType().getText());
                extendsNode.setLineOfCode(ctx.typeType().getStart().getLine());
                Logger.debug("Adding extends " + ctx.typeType().getText());
                AST.addVertex(extendsNode);
                AST.addEdge(classNode, extendsNode);
            }
            //
            if (ctx.typeList() != null) {
                ASNode implementsNode = new ASNode(ASNode.Type.IMPLEMENTS);
                implementsNode.setLineOfCode(ctx.typeList().getStart().getLine());
                Logger.debug("Adding implements node ");
                AST.addVertex(implementsNode);
                AST.addEdge(classNode, implementsNode);
                for (JavaParser.TypeTypeContext type : ctx.typeList().typeType()) {
                    ASNode node = new ASNode(ASNode.Type.INTERFACE);
                    node.setCode(type.getText());
                    node.setLineOfCode(type.getStart().getLine());
                    Logger.debug("Adding interface " + type.getText());
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
                staticBlock.setLineOfCode(ctx.block().getStart().getLine());
                Logger.debug("Adding static block");
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
                    ASNode fieldNode = new ASNode(ASNode.Type.FIELD);
                    fieldNode.setLineOfCode(ctx.memberDeclaration().fieldDeclaration().getStart().getLine());
                    Logger.debug("Adding field node");
                    AST.addVertex(fieldNode);
                    AST.addEdge(parentStack.peek(), fieldNode);
                    parentStack.push(fieldNode);
                    visit(ctx.memberDeclaration().fieldDeclaration());
                    parentStack.pop();
                } else if (ctx.memberDeclaration().constructorDeclaration() != null) {
                    // Constructor member
                    ASNode constructorNode = new ASNode(ASNode.Type.CONSTRUCTOR);
                    constructorNode.setLineOfCode(ctx.memberDeclaration().constructorDeclaration().getStart().getLine());
                    Logger.debug("Adding constructor node");
                    AST.addVertex(constructorNode);
                    AST.addEdge(parentStack.peek(), constructorNode);
                    parentStack.push(constructorNode);
                    visit(ctx.memberDeclaration().constructorDeclaration());
                    parentStack.pop();
                } else if (ctx.memberDeclaration().methodDeclaration() != null) {
                    // Method member
                    ASNode methodNode = new ASNode(ASNode.Type.METHOD);
                    methodNode.setLineOfCode(ctx.memberDeclaration().methodDeclaration().getStart().getLine());
                    Logger.debug("Adding method node");
                    AST.addVertex(methodNode);
                    AST.addEdge(parentStack.peek(), methodNode);
                    parentStack.push(methodNode);
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
            modifierNode.setLineOfCode(ctx.getStart().getLine());
            modifierNode.setCode(memberModifier);
            AST.addVertex(modifierNode);
            AST.addEdge(parentStack.peek(), modifierNode);
            //
            if (ctx.formalParameters().formalParameterList() != null) {
                ASNode paramsNode = new ASNode(ASNode.Type.PARAMS);
                paramsNode.setLineOfCode(ctx.formalParameters().getStart().getLine());
                AST.addVertex(paramsNode);
                AST.addEdge(parentStack.peek(), paramsNode);
                parentStack.push(paramsNode);
                for (JavaParser.FormalParameterContext paramctx: 
                        ctx.formalParameters().formalParameterList().formalParameter()) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    varNode.setLineOfCode(paramctx.getStart().getLine());
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setCode(paramctx.typeType().getText());
                    type.setLineOfCode(paramctx.typeType().getStart().getLine());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setCode(paramctx.variableDeclaratorId().getText());
                    name.setLineOfCode(paramctx.variableDeclaratorId().getStart().getLine());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    varNode.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    type.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getStart().getLine());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    name.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getStart().getLine());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                parentStack.pop();
            }
            //
            ASNode bodyBlock = new ASNode(ASNode.Type.BLOCK);
            bodyBlock.setLineOfCode(ctx.constructorBody().block().getStart().getLine());
            AST.addVertex(bodyBlock);
            AST.addEdge(parentStack.peek(), bodyBlock);
            parentStack.push(bodyBlock);
            visitChildren(ctx.constructorBody().block());
            parentStack.pop();
            resetLocalVars();
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
                modifierNode.setCode(memberModifier);
                modifierNode.setLineOfCode(ctx.getStart().getLine());
                AST.addVertex(modifierNode);
                AST.addEdge(parentStack.peek(), modifierNode);
                //
                ASNode type = new ASNode(ASNode.Type.TYPE);
                type.setCode(ctx.typeType().getText());
                type.setLineOfCode(ctx.typeType().getStart().getLine());
                AST.addVertex(type);
                AST.addEdge(parentStack.peek(), type);
                //
                ++fieldsCounter;
                ASNode name = new ASNode(ASNode.Type.NAME);
                String fieldName = varctx.variableDeclaratorId().getText();
                String normalized = "$FIELD" + fieldsCounter;
                fields.put(fieldName, normalized);
                name.setCode(fieldName);
                name.setNormalizedCode(normalized);
                name.setLineOfCode(varctx.variableDeclaratorId().getStart().getLine());
                AST.addVertex(name);
                AST.addEdge(parentStack.peek(), name);
                //
                if (varctx.variableInitializer() != null) {
                    ASNode initNode = new ASNode(ASNode.Type.INIT_VALUE);
                    initNode.setCode("= " + getOriginalCodeText(varctx.variableInitializer()));
                    initNode.setLineOfCode(varctx.variableInitializer().getStart().getLine());
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
            modifierNode.setCode(memberModifier);
            modifierNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding method modifier");
            AST.addVertex(modifierNode);
            AST.addEdge(parentStack.peek(), modifierNode);
            //
            ASNode retNode = new ASNode(ASNode.Type.RETURN);
            retNode.setCode(ctx.getChild(0).getText());
            retNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding method type");
            AST.addVertex(retNode);
            AST.addEdge(parentStack.peek(), retNode);
            //
            ++methodsCounter;
            ASNode nameNode = new ASNode(ASNode.Type.NAME);
            String methodName = ctx.Identifier().getText();
            String normalized = "$METHOD" + methodsCounter;
            methods.put(methodName, normalized);
            nameNode.setCode(methodName);
            nameNode.setNormalizedCode(normalized);
            nameNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding method name");
            AST.addVertex(nameNode);
            AST.addEdge(parentStack.peek(), nameNode);
            //
            if (ctx.formalParameters().formalParameterList() != null) {
                ASNode paramsNode = new ASNode(ASNode.Type.PARAMS);
                paramsNode.setLineOfCode(ctx.formalParameters().getStart().getLine());
                Logger.debug("Adding method params node");
                AST.addVertex(paramsNode);
                AST.addEdge(parentStack.peek(), paramsNode);
                parentStack.push(paramsNode);
                for (JavaParser.FormalParameterContext paramctx: 
                        ctx.formalParameters().formalParameterList().formalParameter()) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    varNode.setLineOfCode(paramctx.getStart().getLine());
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setCode(paramctx.typeType().getText());
                    type.setLineOfCode(paramctx.typeType().getStart().getLine());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setCode(paramctx.variableDeclaratorId().getText());
                    name.setLineOfCode(paramctx.variableDeclaratorId().getStart().getLine());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
                    ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                    varNode.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().getStart().getLine());
                    AST.addVertex(varNode);
                    AST.addEdge(parentStack.peek(), varNode);
                    //
                    ASNode type = new ASNode(ASNode.Type.TYPE);
                    type.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getText());
                    type.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType().getStart().getLine());
                    AST.addVertex(type);
                    AST.addEdge(varNode, type);
                    //
                    ASNode name = new ASNode(ASNode.Type.NAME);
                    name.setCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getText());
                    name.setLineOfCode(ctx.formalParameters().formalParameterList().lastFormalParameter().variableDeclaratorId().getStart().getLine());
                    AST.addVertex(name);
                    AST.addEdge(varNode, name);
                }
                parentStack.pop();
            }
            //
            if (ctx.methodBody() != null) {
                ASNode methodBody = new ASNode(ASNode.Type.BLOCK);
                methodBody.setLineOfCode(ctx.methodBody().getStart().getLine());
                Logger.debug("Adding method block");
                AST.addVertex(methodBody);
                AST.addEdge(parentStack.peek(), methodBody);
                parentStack.push(methodBody);
                visitChildren(ctx.methodBody());
                parentStack.pop();
                resetLocalVars();
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
                varNode.setLineOfCode(varctx.getStart().getLine());
                AST.addVertex(varNode);
                AST.addEdge(parentStack.peek(), varNode);
                //
                ASNode typeNode = new ASNode(ASNode.Type.TYPE);
                typeNode.setCode(ctx.typeType().getText());
                typeNode.setLineOfCode(ctx.typeType().getStart().getLine());
                AST.addVertex(typeNode);
                AST.addEdge(varNode, typeNode);
                //
                ++varsCounter;
                ASNode nameNode = new ASNode(ASNode.Type.NAME);
                String varName = varctx.variableDeclaratorId().getText();
                String normalized = "$VAR" + varsCounter;
                vars.put(varName, normalized);
                nameNode.setCode(varName);
                nameNode.setNormalizedCode(normalized);
                nameNode.setLineOfCode(varctx.variableDeclaratorId().getStart().getLine());
                AST.addVertex(nameNode);
                AST.addEdge(varNode, nameNode);
                //
                if (varctx.variableInitializer() != null) {
                    ASNode initNode = new ASNode(ASNode.Type.INIT_VALUE);
                    initNode.setCode("= " + getOriginalCodeText(varctx.variableInitializer()));
                    initNode.setLineOfCode(varctx.variableInitializer().getStart().getLine());
                    AST.addVertex(initNode);
                    AST.addEdge(varNode, initNode);
                }
            }
            return null;
        }

        //=====================================================================//
        //                           STATEMENTS                                //
        //=====================================================================//
        
        private void visitStatement(ParserRuleContext ctx, String normalized) {
            Logger.printf(Logger.Level.DEBUG, "Visiting: (%d)  %s", ctx.getStart().getLine(), getOriginalCodeText(ctx));
            ASNode statementNode = new ASNode(ASNode.Type.STATEMENT);
            statementNode.setCode(getOriginalCodeText(ctx));
            statementNode.setNormalizedCode(normalized);
            statementNode.setLineOfCode(ctx.getStart().getLine());
            Logger.debug("Adding statement " + ctx.getStart().getLine());
            AST.addVertex(statementNode);
            AST.addEdge(parentStack.peek(), statementNode);
        }
        
        @Override
        public String visitStatementExpression(JavaParser.StatementExpressionContext ctx) {
            // statementExpression :  expression
            visitStatement(ctx, visit(ctx.expression()));
            return null;
        }
        
        @Override
        public String visitBreakStatement(JavaParser.BreakStatementContext ctx) {
            if (ctx.Identifier() == null)
                visitStatement(ctx, null);
            else
                visitStatement(ctx, "break $LABEL");
            return null;
        }
        
        @Override
        public String visitContinueStatement(JavaParser.ContinueStatementContext ctx) {
            if (ctx.Identifier() == null)
                visitStatement(ctx, null);
            else
                visitStatement(ctx, "continue $LABEL");
            return null;
        }
        
        @Override
        public String visitReturnStatement(JavaParser.ReturnStatementContext ctx) {
            if (ctx.expression() == null)
                visitStatement(ctx, null);
            else
                visitStatement(ctx, "return " + visit(ctx.expression()));
            return null;
        }
        
        @Override
        public String visitThrowStatement(JavaParser.ThrowStatementContext ctx) {
            visitStatement(ctx, "throw " + visit(ctx.expression()));
            return null;
        }
        
        @Override
        public String visitSynchBlockStatement(JavaParser.SynchBlockStatementContext ctx) {
            // synchBlockStatement :  'synchronized' parExpression block
            ASNode synchNode = new ASNode(ASNode.Type.SYNC);
            synchNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(synchNode);
            AST.addEdge(parentStack.peek(), synchNode);
            //
            parentStack.push(synchNode);
            visitStatement(ctx.parExpression().expression(), visit(ctx.parExpression().expression()));
            parentStack.pop();
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            block.setLineOfCode(ctx.block().getStart().getLine());
            AST.addVertex(block);
            AST.addEdge(synchNode, block);
            parentStack.push(block);
            visit(ctx.block());
            parentStack.pop();
            return null;
        }
        
        @Override
        public String visitLabelStatement(JavaParser.LabelStatementContext ctx) {
            // labelStatement :  Identifier ':' statement
            ASNode labelNode = new ASNode(ASNode.Type.LABELED);
            labelNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(labelNode);
            AST.addEdge(parentStack.peek(), labelNode);
            //
            ASNode labelName = new ASNode(ASNode.Type.NAME);
            labelName.setCode(ctx.Identifier().getText());
            labelName.setLineOfCode(ctx.getStart().getLine());
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
            ifNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(ifNode);
            AST.addEdge(parentStack.peek(), ifNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setCode(getOriginalCodeText(ctx.parExpression().expression()));
            cond.setLineOfCode(ctx.parExpression().getStart().getLine());
            AST.addVertex(cond);
            AST.addEdge(ifNode, cond);
            //
            ASNode thenNode = new ASNode(ASNode.Type.THEN);
            thenNode.setLineOfCode(ctx.statement(0).getStart().getLine());
            AST.addVertex(thenNode);
            AST.addEdge(ifNode, thenNode);
            parentStack.push(thenNode);
            visit(ctx.statement(0));
            parentStack.pop();
            //
            if (ctx.statement(1) != null) {
                ASNode elseNode = new ASNode(ASNode.Type.ELSE);
                elseNode.setLineOfCode(ctx.statement(1).getStart().getLine());
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
                forNode.setLineOfCode(ctx.getStart().getLine());
                AST.addVertex(forNode);
                AST.addEdge(parentStack.peek(), forNode);
                //
                ASNode varType = new ASNode(ASNode.Type.TYPE);
                varType.setCode(ctx.forControl().enhancedForControl().typeType().getText());
                varType.setLineOfCode(ctx.forControl().enhancedForControl().typeType().getStart().getLine());
                AST.addVertex(varType);
                AST.addEdge(forNode, varType);
                //
                ASNode varID = new ASNode(ASNode.Type.NAME);
                varID.setCode(ctx.forControl().enhancedForControl().variableDeclaratorId().getText());
                varID.setLineOfCode(ctx.forControl().enhancedForControl().variableDeclaratorId().getStart().getLine());
                AST.addVertex(varID);
                AST.addEdge(forNode, varID);
                //
                ASNode expr = new ASNode(ASNode.Type.IN);
                expr.setCode(getOriginalCodeText(ctx.forControl().enhancedForControl().expression()));
                expr.setLineOfCode(ctx.forControl().enhancedForControl().expression().getStart().getLine());
                AST.addVertex(expr);
                AST.addEdge(forNode, expr);
            } 
            // Classic for(init; expr; update)
            else {
                forNode = new ASNode(ASNode.Type.FOR);
                forNode.setLineOfCode(ctx.getStart().getLine());
                AST.addVertex(forNode);
                AST.addEdge(parentStack.peek(), forNode);
                // for init
                if (ctx.forControl().forInit() != null) {
                    ASNode forInit = new ASNode(ASNode.Type.FOR_INIT);
                    forInit.setCode(getOriginalCodeText(ctx.forControl().forInit()));
                    forInit.setLineOfCode(ctx.forControl().forInit().getStart().getLine());
                    AST.addVertex(forInit);
                    AST.addEdge(forNode, forInit);
                }
                // for expr
                if (ctx.forControl().expression() != null) {
                    ASNode forExpr = new ASNode(ASNode.Type.CONDITION);
                    forExpr.setCode(getOriginalCodeText(ctx.forControl().expression()));
                    forExpr.setLineOfCode(ctx.forControl().expression().getStart().getLine());
                    AST.addVertex(forExpr);
                    AST.addEdge(forNode, forExpr);
                }
                // for update
                if (ctx.forControl().forUpdate() != null) {
                    ASNode forUpdate = new ASNode(ASNode.Type.FOR_UPDATE);
                    forUpdate.setCode(getOriginalCodeText(ctx.forControl().forUpdate()));
                    forUpdate.setLineOfCode(ctx.forControl().forUpdate().getStart().getLine());
                    AST.addVertex(forUpdate);
                    AST.addEdge(forNode, forUpdate);
                }
            }
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            block.setLineOfCode(ctx.statement().getStart().getLine());
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
            whileNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(whileNode);
            AST.addEdge(parentStack.peek(), whileNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setCode(getOriginalCodeText(ctx.parExpression().expression()));
            cond.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            AST.addVertex(cond);
            AST.addEdge(whileNode, cond);
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            block.setLineOfCode(ctx.statement().getStart().getLine());
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
            doWhileNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(doWhileNode);
            AST.addEdge(parentStack.peek(), doWhileNode);
            //
            ASNode cond = new ASNode(ASNode.Type.CONDITION);
            cond.setCode(getOriginalCodeText(ctx.parExpression().expression()));
            cond.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            AST.addVertex(cond);
            AST.addEdge(doWhileNode, cond);
            //
            ASNode block = new ASNode(ASNode.Type.BLOCK);
            block.setLineOfCode(ctx.statement().getStart().getLine());
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
            tryNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(tryNode);
            AST.addEdge(parentStack.peek(), tryNode);
            //
            ASNode tryBlock = new ASNode(ASNode.Type.BLOCK);
            tryBlock.setLineOfCode(ctx.block().getStart().getLine());
            AST.addVertex(tryBlock);
            AST.addEdge(tryNode, tryBlock);
            parentStack.push(tryBlock);
            visit(ctx.block());
            parentStack.pop();
            // catchClause :  'catch' '(' variableModifier* catchType Identifier ')' block
            if (ctx.catchClause() != null && ctx.catchClause().size() > 0) {
                for (JavaParser.CatchClauseContext catchx : ctx.catchClause()) {
                    ASNode catchNode = new ASNode(ASNode.Type.CATCH);
                    catchNode.setLineOfCode(catchx.getStart().getLine());
                    AST.addVertex(catchNode);
                    AST.addEdge(tryNode, catchNode);
                    //
                    ASNode catchType = new ASNode(ASNode.Type.TYPE);
                    catchType.setCode(catchx.catchType().getText());
                    catchType.setLineOfCode(catchx.catchType().getStart().getLine());
                    AST.addVertex(catchType);
                    AST.addEdge(catchNode, catchType);
                    //
                    ASNode catchName = new ASNode(ASNode.Type.NAME);
                    catchName.setCode(catchx.Identifier().getText());
                    catchName.setLineOfCode(catchx.getStart().getLine());
                    AST.addVertex(catchName);
                    AST.addEdge(catchNode, catchName);
                    //
                    ASNode catchBlock = new ASNode(ASNode.Type.BLOCK);
                    catchBlock.setLineOfCode(catchx.block().getStart().getLine());
                    AST.addVertex(catchBlock);
                    AST.addEdge(catchNode, catchBlock);
                    parentStack.push(catchBlock);
                    visit(catchx.block());
                    parentStack.pop();
                }
            }
            // finallyBlock :  'finally' block
            if (ctx.finallyBlock() != null) {
                ASNode finallyNode = new ASNode(ASNode.Type.FINALLY);
                finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
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
            tryNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(tryNode);
            AST.addEdge(parentStack.peek(), tryNode);
            //
            ASNode resNode = new ASNode(ASNode.Type.RESOURCES);
            resNode.setLineOfCode(ctx.resourceSpecification().getStart().getLine());
            AST.addVertex(resNode);
            AST.addEdge(tryNode, resNode);
            for (JavaParser.ResourceContext resctx : ctx.resourceSpecification().resources().resource()) {
                ASNode varNode = new ASNode(ASNode.Type.VARIABLE);
                varNode.setLineOfCode(resctx.getStart().getLine());
                AST.addVertex(varNode);
                AST.addEdge(resNode, varNode);
                //
                ASNode resType = new ASNode(ASNode.Type.TYPE);
                resType.setCode(resctx.classOrInterfaceType().getText());
                resType.setLineOfCode(resctx.classOrInterfaceType().getStart().getLine());
                AST.addVertex(resType);
                AST.addEdge(varNode, resType);
                //
                ASNode resName = new ASNode(ASNode.Type.NAME);
                resName.setCode(resctx.variableDeclaratorId().getText());
                resName.setLineOfCode(resctx.variableDeclaratorId().getStart().getLine());
                AST.addVertex(resName);
                AST.addEdge(varNode, resName);
                //
                ASNode resInit = new ASNode(ASNode.Type.INIT_VALUE);
                resInit.setCode(getOriginalCodeText(resctx.expression()));
                resInit.setLineOfCode(resctx.expression().getStart().getLine());
                AST.addVertex(resInit);
                AST.addEdge(varNode, resInit);
            }
            ASNode tryBlock = new ASNode(ASNode.Type.BLOCK);
            tryBlock.setLineOfCode(ctx.block().getStart().getLine());
            AST.addVertex(tryBlock);
            AST.addEdge(tryNode, tryBlock);
            parentStack.push(tryBlock);
            visit(ctx.block());
            parentStack.pop();
            //
            // catchClause :   'catch' '(' variableModifier* catchType Identifier ')' block
            if (ctx.catchClause().size() > 0 && ctx.catchClause() != null) {
                for (JavaParser.CatchClauseContext catchx : ctx.catchClause()) {
                    ASNode catchNode = new ASNode(ASNode.Type.CATCH);
                    catchNode.setLineOfCode(catchx.getStart().getLine());
                    AST.addVertex(catchNode);
                    AST.addEdge(tryNode, catchNode);
                    //
                    ASNode catchType = new ASNode(ASNode.Type.TYPE);
                    catchType.setCode(catchx.catchType().getText());
                    catchType.setLineOfCode(catchx.catchType().getStart().getLine());
                    AST.addVertex(catchType);
                    AST.addEdge(catchNode, catchType);
                    //
                    ASNode catchName = new ASNode(ASNode.Type.NAME);
                    catchName.setCode(catchx.Identifier().getText());
                    catchName.setLineOfCode(catchx.catchType().getStart().getLine());
                    AST.addVertex(catchName);
                    AST.addEdge(catchNode, catchName);
                    //
                    ASNode catchBlock = new ASNode(ASNode.Type.BLOCK);
                    catchBlock.setLineOfCode(catchx.block().getStart().getLine());
                    AST.addVertex(catchBlock);
                    AST.addEdge(catchNode, catchBlock);
                    parentStack.push(catchBlock);
                    visit(catchx.block());
                    parentStack.pop();
                }
            }
            // finallyBlock :  'finally' block
            if (ctx.finallyBlock() != null) {
                ASNode finallyNode = new ASNode(ASNode.Type.FINALLY);
                finallyNode.setLineOfCode(ctx.finallyBlock().getStart().getLine());
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
            switchNode.setLineOfCode(ctx.getStart().getLine());
            AST.addVertex(switchNode);
            AST.addEdge(parentStack.peek(), switchNode);
            //
            ASNode varName = new ASNode(ASNode.Type.NAME);
            varName.setCode(getOriginalCodeText(ctx.parExpression().expression()));
            varName.setLineOfCode(ctx.parExpression().expression().getStart().getLine());
            AST.addVertex(varName);
            AST.addEdge(switchNode, varName);
            //
            if (ctx.switchBlockStatementGroup() != null) {
                for (JavaParser.SwitchBlockStatementGroupContext grpx : ctx.switchBlockStatementGroup()) {
                    ASNode blockNode = new ASNode(ASNode.Type.BLOCK);
                    blockNode.setLineOfCode(grpx.blockStatement(0).getStart().getLine());
                    AST.addVertex(blockNode);
                    for (JavaParser.SwitchLabelContext lblctx : grpx.switchLabel())
                        visitSwitchLabel(lblctx, switchNode, blockNode);
                    parentStack.push(blockNode);
                    for (JavaParser.BlockStatementContext blk : grpx.blockStatement())
                        visit(blk);
                    parentStack.pop();
                }
            }
            if (ctx.switchLabel() != null && ctx.switchLabel().size() > 0) {
                ASNode blockNode = new ASNode(ASNode.Type.BLOCK);
                blockNode.setLineOfCode(ctx.switchLabel(0).getStart().getLine());
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
                caseNode.setCode(lblctx.constantExpression().getText());
                caseNode.setLineOfCode(lblctx.getStart().getLine());
            } else if (lblctx.enumConstantName() != null) {
                caseNode = new ASNode(ASNode.Type.CASE);
                caseNode.setCode(lblctx.enumConstantName().getText());
                caseNode.setLineOfCode(lblctx.getStart().getLine());
            } else {
                caseNode = new ASNode(ASNode.Type.DEFAULT);
                caseNode.setLineOfCode(lblctx.getStart().getLine());
            }
            AST.addVertex(caseNode);
            AST.addEdge(switchNode, caseNode);
            AST.addEdge(caseNode, blockNode);
        }
        
        //=====================================================================//
        //                          EXPRESSIONS                                //
        //=====================================================================//
        
        @Override
        public String visitExprMethodInvocation(JavaParser.ExprMethodInvocationContext ctx) { 
            // exprMethodInvocation :  expression '(' expressionList? ')'
            return null;
        }
        

        //=====================================================================//
        //                          PRIVATE METHODS                            //
        //=====================================================================//

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
        
        private void resetLocalVars() {
            vars.clear();
            varsCounter = 0;
        }
    }
}
