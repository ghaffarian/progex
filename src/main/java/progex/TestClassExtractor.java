/*** In The Name of Allah ***/
package progex;

import java.io.File;
import java.io.IOException;
import java.util.List;
import progex.java.JavaClass;
import progex.java.JavaClassExtractor;
import progex.utils.FileUtils;
import progex.utils.Logger;

/**
 * Main class for testing the JavaClassExtractor utility.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class TestClassExtractor {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
		System.out.println("Hello! This is PROGEX!\n");
		try {
			Logger.init("PROGEX.log");
		} catch (IOException ex) {
			System.err.println("Logger init failed : " + ex);
		}
		// 1. Timing
		try {
			Logger.log("\nExtraction time for : \"java.lang.*\"");
			Logger.log("  Start  = " + Logger.time());
			JavaClassExtractor.extractJavaLangInfo();
			Logger.log("  Finish = " + Logger.time());
			//
			File javaFile1, javaFile2, javaFile3;
			javaFile1 = new File("src/progex/java/JavaCFGBuilder.java");
			javaFile2 = new File("src/progex/java/JavaDDGBuilder.java");
			javaFile3 = new File("src/progex/java/JavaClassExtractor.java");
			Logger.log("\nExtraction time for imports of : " 
					+ javaFile1.getName() + " + " + javaFile2.getName() + " + " + javaFile3.getName());
			String[] imports1 = JavaClassExtractor.extractInfo(javaFile1).get(0).IMPORTS;
			String[] imports2 = JavaClassExtractor.extractInfo(javaFile2).get(0).IMPORTS;
			String[] imports3 = JavaClassExtractor.extractInfo(javaFile3).get(0).IMPORTS;
			Logger.log("  Start  = " + Logger.time());
			JavaClassExtractor.extractImportsInfo(imports1);
			JavaClassExtractor.extractImportsInfo(imports2);
			JavaClassExtractor.extractImportsInfo(imports3);
			Logger.log("  Finish = " + Logger.time());
		} catch (IOException ex) {
			System.err.println(ex);
		}
		// 2. Extracting 'java.lang.*'
		try {
			Logger.log("\n================   JAVA.LANG   ================");
			for (JavaClass classInfo : JavaClassExtractor.extractJavaLangInfo())
				Logger.log("\n" + classInfo);
		} catch (IOException ex) {
			System.err.println(ex);
		}
		// 3. Extracting all source file classes + imports
		String[] files = FileUtils.listSourceCodeFiles(args, ".java");
		for (String javaFile: files) {
			try {
				Logger.log("\n========================================\n");
				Logger.log("FILE: " + javaFile);
				// first extract class info
				List<JavaClass> classInfoList = JavaClassExtractor.extractInfo(javaFile);
				for (JavaClass classInfo : classInfoList)
					Logger.log("\n" + classInfo);
				// then extract imports info
				if (classInfoList.size() > 0) {
					Logger.log("\n- - - - - - - - - - - - - - - - - - - - -");
					String[] imports = classInfoList.get(0).IMPORTS;
					for (JavaClass importInfo : JavaClassExtractor.extractImportsInfo(imports)) 
						Logger.log("\n" + importInfo);
				}
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
    }
}
