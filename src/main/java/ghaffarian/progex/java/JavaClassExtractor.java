/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import ghaffarian.progex.java.parser.JavaBaseVisitor;
import ghaffarian.progex.java.parser.JavaLexer;
import ghaffarian.progex.java.parser.JavaParser;

/**
 * A utility class for building JavaClass structures from a given Java source file.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaClassExtractor {
	
	public static List<JavaClass> extractInfo(String javaFile) throws IOException {
		return extractInfo(new File(javaFile));
	}
	
	public static List<JavaClass> extractInfo(File javaFile) throws IOException {
		return extractInfo(javaFile.getAbsolutePath(), new FileInputStream(javaFile));
	}
	
	public static List<JavaClass> extractInfo(String javaFilePath, InputStream inStream) throws IOException {
		ANTLRInputStream input = new ANTLRInputStream(inStream);
		JavaLexer lexer = new JavaLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		JavaParser parser = new JavaParser(tokens);
		ParseTree tree = parser.compilationUnit();
		return extractInfo(javaFilePath, tree);
	}
	
	public static List<JavaClass> extractInfo(String javaFilePath, ParseTree tree) {
		JavaClassVisitor visitor = new JavaClassVisitor(javaFilePath);
		return visitor.build(tree);
	}
	
	public static List<JavaClass> extractJavaLangInfo() throws IOException {
		ZipFile zip = new ZipFile("lib/src.zip");
		ArrayList<JavaClass> javaLangClasses = new ArrayList<>();
		String qualifiedName = "java.lang.*";
		for (ZipEntry ent: getPackageEntries(zip, qualifiedName))
			javaLangClasses.addAll(JavaClassExtractor.extractInfo("src.zip/" + ent.getName(), zip.getInputStream(ent)));
		return javaLangClasses;
	}
	
	public static List<JavaClass> extractImportsInfo(String[] imports) throws IOException {
		ZipFile zip = new ZipFile("lib/src.zip");
		ArrayList<JavaClass> classes = new ArrayList<>();
		for (String qualifiedName: imports) {
			if (qualifiedName.endsWith(".*")) {
				for (ZipEntry ent: getPackageEntries(zip, qualifiedName))
					classes.addAll(JavaClassExtractor.extractInfo("src.zip/" + ent.getName(), zip.getInputStream(ent)));
			} else {
				ZipEntry entry = getZipEntry(zip, qualifiedName);
				if (entry == null)
					continue;
				classes.addAll(JavaClassExtractor.extractInfo("src.zip/" + entry.getName(), zip.getInputStream(entry)));
			}
		}
		return classes;
	}
	
	private static ZipEntry getZipEntry(ZipFile zip, String qualifiedName) {
		// qualifiedName does not end with ".*"
		return zip.getEntry(qualifiedName.replace('.', '/') + ".java");
	}
	
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
	
	private static int countSlashes(String str) {
		int slashCount = 0;
		for (char chr: str.toCharArray())
			if (chr == '/')
				++slashCount;
		return slashCount;
	}
	
	private static class JavaClassVisitor extends JavaBaseVisitor<String> {
		
		private String filePath;
		private boolean isStatic;
		private boolean isAbstract;
		private String packageName;
		private String lastModifier;
		private List<String> importsList;
		private List<JavaClass> javaClasses;
		private Deque<JavaClass> activeClasses;
		
		public JavaClassVisitor(String path) {
			filePath = path;
		}
		
		public List<JavaClass> build(ParseTree tree) {
			packageName = "";
			isStatic = false;
			isAbstract = false;
			javaClasses = new ArrayList<>();
			importsList = new ArrayList<>();
			activeClasses = new ArrayDeque<>();
			visit(tree);
			return javaClasses;
		}
		
		@Override
		public String visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
			// packageDeclaration :  annotation* 'package' qualifiedName ';'
			packageName = ctx.qualifiedName().getText();
			return null;
		}
		
		@Override
		public String visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
			// importDeclaration :  'import' 'static'? qualifiedName ('.' '*')? ';'
			String qualifiedName = ctx.qualifiedName().getText();
			int last = ctx.getChildCount() - 1;
			if (ctx.getChild(last - 1).getText().equals("*")
					&& ctx.getChild(last - 2).getText().equals("."))
				qualifiedName += ".*";
			importsList.add(qualifiedName);
			return null;
		}
		
		@Override
		public String visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
			// classDeclaration 
			//   :  'class' Identifier typeParameters? 
			//      ('extends' typeType)? ('implements' typeList)? classBody
			String extend = null;
			if (ctx.typeType() != null)
				extend = visit(ctx.typeType());
			String[] implementations = null;
			ArrayList<String> impList = new ArrayList<>();
			if (ctx.typeList() != null) {
				for (JavaParser.TypeTypeContext type: ctx.typeList().typeType())
					impList.add(visit(type));
			}
			if (impList.size() > 0)
				implementations = impList.toArray(new String[impList.size()]);
			String[] imports = importsList.toArray(new String[importsList.size()]);
			
			JavaClass cls = new JavaClass(ctx.Identifier().getText(), packageName, extend, filePath, imports);
			if(ctx.typeParameters() != null)
				cls.setTypeParameters(ctx.typeParameters().getText().substring(1, ctx.typeParameters().getText().length()-1).trim());
			activeClasses.push(cls);
						
			activeClasses.peek().setInterfaces(implementations);
			visit(ctx.classBody());
			javaClasses.add(activeClasses.pop());
			return null;
		}
		
		@Override
		public String visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
			// We need this only for the modifier!
			//
			// classBodyDeclaration
			//   :  ';'
			//   |  'static'? block
			//   |   modifier* memberDeclaration
			//
			// modifier
			//   :  classOrInterfaceModifier
			//   |  ( 'native' | 'synchronized' | 'transient' | 'volatile' )
			//
			// classOrInterfaceModifier
			//   :  annotation 
			//   |  ( 'public' | 'protected' | 'private' | 'static' | 'abstract' | 'final' | 'strictfp' )
			//
			if (ctx.memberDeclaration() != null) {
				isStatic = false;
				isAbstract = false;
				lastModifier = null;
				if (ctx.modifier() != null && ctx.modifier().size() > 0) {
					for (JavaParser.ModifierContext cx : ctx.modifier()) {
						if (cx.classOrInterfaceModifier() != null) {
							if (cx.classOrInterfaceModifier().getText().startsWith("public"))
								lastModifier = "public";
							else if (cx.classOrInterfaceModifier().getText().startsWith("private"))
								lastModifier = "private";
							else if (cx.classOrInterfaceModifier().getText().startsWith("protected"))
								lastModifier = "protected";
							else if (cx.classOrInterfaceModifier().getText().startsWith("static"))
								isStatic = true;
							else if (cx.classOrInterfaceModifier().getText().startsWith("abstract"))
								isAbstract = true;
						}
					}
				}
				return visit(ctx.memberDeclaration());
			}
			// memberDeclaration
			//   :   methodDeclaration
			//   |   fieldDeclaration
			//   |   constructorDeclaration
			//   |   classDeclaration
			//   |   enumDeclaration
			//   |   ...			
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
			//
			String type = "void";
			if (ctx.typeType() != null)
				type = visit(ctx.typeType());
			String name = ctx.Identifier().getText();
			String[] args = null;
			List<String> argsList = new ArrayList<>();
			if (ctx.formalParameters().formalParameterList() != null) {
				for (JavaParser.FormalParameterContext param : 
						ctx.formalParameters().formalParameterList().formalParameter()) {
					argsList.add(visit(param.typeType()));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					argsList.add(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType()));
				}
			}
			if (argsList.size() > 0)
				args = argsList.toArray(new String[argsList.size()]);
			int line = ctx.getStart().getLine();
			activeClasses.peek().addMethod(new JavaMethod(lastModifier, isStatic, isAbstract, type, name, args, line));
			return null;
		}
		
		@Override
		public String visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
			// constructorDeclaration
			//   :  Identifier formalParameters ('throws' qualifiedNameList)? constructorBody
			String type = null;
			String name = ctx.Identifier().getText();
			String[] args = null;
			List<String> argsList = new ArrayList<>();
			if (ctx.formalParameters().formalParameterList() != null) {
				for (JavaParser.FormalParameterContext param : 
						ctx.formalParameters().formalParameterList().formalParameter()) {
					argsList.add(visit(param.typeType()));
				}
				if (ctx.formalParameters().formalParameterList().lastFormalParameter() != null) {
					argsList.add(visit(ctx.formalParameters().formalParameterList().lastFormalParameter().typeType()));
				}
			}
			if (argsList.size() > 0)
				args = argsList.toArray(new String[argsList.size()]);
			int line = ctx.getStart().getLine();
			activeClasses.peek().addMethod(new JavaMethod(lastModifier, isStatic, isAbstract, type, name, args, line));
			return null;
		}
		
		@Override
		public String visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
			// fieldDeclaration     :  typeType variableDeclarators ';'
			// variableDeclarators  :  variableDeclarator (',' variableDeclarator)*
			// variableDeclarator   :  variableDeclaratorId ('=' variableInitializer)?
			// variableDeclaratorId :  Identifier ('[' ']')*
			for (JavaParser.VariableDeclaratorContext var: ctx.variableDeclarators().variableDeclarator()) {
				String name = var.variableDeclaratorId().Identifier().getText();
				StringBuilder type = new StringBuilder(visit(ctx.typeType()));
				int idx = var.variableDeclaratorId().getText().indexOf('[');
				if (idx > 0)
					type.append(var.variableDeclaratorId().getText().substring(idx));
				activeClasses.peek().addField(new JavaField(lastModifier, isStatic, type.toString(), name));
			}
			return null;
		}
		
		@Override
		public String visitTypeType(JavaParser.TypeTypeContext ctx) {
			// typeType
			//   :  classOrInterfaceType ('[' ']')*  |  primitiveType ('[' ']')*
//			StringBuilder type = new StringBuilder(visit(ctx.getChild(0)));
//			int idx = ctx.getText().indexOf('[');
//			if (idx > 0)
//				type.append(ctx.getText().substring(idx));
//			return type.toString();
			return ctx.getText();
		}
		
//		@Override
//		public String visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
//			// classOrInterfaceType
//			//   :  Identifier typeArguments? ('.' Identifier typeArguments? )*
//			StringBuilder typeID = new StringBuilder(ctx.Identifier(0).getText());
//			for (TerminalNode id: ctx.Identifier().subList(1, ctx.Identifier().size()))
//				typeID.append(".").append(id.getText());
//			return typeID.toString();
//		}
		
//		@Override
//		public String visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
//			// primitiveType : 
//			//     'boolean' | 'char' | 'byte' | 'short'
//			//     | 'int' | 'long' | 'float' | 'double'
//			return ctx.getText();
//		}
		
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
	}
}
