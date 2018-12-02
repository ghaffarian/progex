/*** In The Name of Allah ***/
package progex;

import java.io.IOException;
import java.util.List;
import progex.java.JavaClass;
import progex.java.JavaClassExtractor;
import progex.utils.Logger;

/**
 * Code Information Analyzer.
 * This class invokes the appropriate analysis process based on the given language parameter.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class CodeInfoAnalyzer {
	
	public static void analyzeInfo(String lang, String srcFilePath) {
		switch (lang) {
			case "C":
				return;
			//
			case "Java":
				try {
					Logger.log("\n========================================\n");
					Logger.log("FILE: " + srcFilePath);
					// first extract class info
					List<JavaClass> classInfoList = JavaClassExtractor.extractInfo(srcFilePath);
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
				return;
			//
			case "Python":
				return;
		}
	}
	
}
