/*** In The Name of Allah ***/
package ghaffarian.progex;

import java.io.IOException;
import java.util.List;
import ghaffarian.progex.java.JavaClass;
import ghaffarian.progex.java.JavaClassExtractor;
import ghaffarian.nanologger.Logger;

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
					Logger.info("\n========================================\n");
					Logger.info("FILE: " + srcFilePath);
					// first extract class info
					List<JavaClass> classInfoList = JavaClassExtractor.extractInfo(srcFilePath);
					for (JavaClass classInfo : classInfoList)
						Logger.info("\n" + classInfo);
					// then extract imports info
					if (classInfoList.size() > 0) {
						Logger.info("\n- - - - - - - - - - - - - - - - - - - - -");
						String[] imports = classInfoList.get(0).IMPORTS;
						for (JavaClass importInfo : JavaClassExtractor.extractImportsInfo(imports)) 
							Logger.info("\n" + importInfo);
					}
				} catch (IOException ex) {
					Logger.error(ex);
				}
				return;
			//
			case "Python":
				return;
		}
	}
	
}
