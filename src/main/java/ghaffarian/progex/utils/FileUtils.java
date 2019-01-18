/*** In The Name of Allah ***/
package ghaffarian.progex.utils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A utility class for some file operations.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class FileUtils {
	
	/**
	 * Returns a list of file-paths from the given directory-path
	 * where the files exist and the filenames match a given suffix.
	 */
	public static String[] listFilesWithSuffix(String dirPath, String suffix) {
		return listFilesWithSuffix(new File[] {new File(dirPath)}, suffix);
	}
	
	/**
	 * Returns a list of file-paths based on the given input file-paths 
	 * where the files exist and the filenames match a given suffix.
	 */
	public static String[] listFilesWithSuffix(String[] args, String suffix) {
		ArrayList<File> files = new ArrayList<>();
		for (String arg: args) {
			if (arg.contains("*")) {
				File dir = new File(System.getProperty("user.dir"));
				File[] matches = dir.listFiles(new WildcardFilter(arg));
				for (File file : matches) {
					if (file.getName().endsWith(suffix))
						files.add(file);
				}
			} else {
				files.add(new File(arg));
			}
		}
		return listFilesWithSuffix(files.toArray(new File[files.size()]), suffix);
	}
	
	/**
	 * Returns a list of file-paths based on the given file objects 
	 * where the files exist and the filenames match a given suffix.
	 */
	public static String[] listFilesWithSuffix(File[] argFiles, String suffix) {
		ArrayList<String> list = new ArrayList<>();
		for (File file: argFiles) {
			if (file.isDirectory()) {
				list.addAll(Arrays.asList(listFilesWithSuffix(file.listFiles(), suffix)));
			} else {
				if (file.exists() && file.getName().endsWith(suffix))
					list.add(file.getAbsolutePath());
			}
		}
		return list.toArray(new String[list.size()]);
	}
	
	/**
	 * Simple wildcard-matcher for filenames, using REGEX.
	 */
	static class WildcardFilter implements FileFilter {
		private final String regex;
		public WildcardFilter(String wildcard) {
			regex = ".*" + wildcard.replaceAll("\\*", ".*");
		}
		@Override
		public boolean accept(File pathname) {
			return pathname.getPath().matches(regex);
		}
	}	
	
}
