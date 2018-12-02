/*** In The Name of Allah ***/
package progex.utils;

/**
 * A utility class providing some String-related capabilities.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class StringUtils {
	
	/**
	 * A simple escaping method based on REGEX.
	 */
	public static String escape(String code) {
		// Match and replace all string literals
		//String escapedCode = code.replaceAll("\".*?\"", "STR");
		String escapedCode = code.replaceAll("\"", "'");
		// Match and replace all numeric literals
		//escapedCode = escapedCode.replaceAll("\\b-?\\d+(.\\d+)*\\b", "$NUM");
		// Match and escape all backslashes
		escapedCode = escapedCode.replaceAll("\\\\", "\\\\\\\\");
		return escapedCode;
	}
	
	/**
	 * Returns a string which replaces all double-quotes (") chars with the escaped version (\").
	 */
	public static String escapeDoubleQuotes(String code) {
		return code.replace("\"", "\\\"");
	}
	
	/**
	 * Returns a JSON array representation of the given String array.
	 * This code is very similar to the implementation of java.util.Arrays.toString(Object[] a)
	 * with some minor modifications for conforming to the JSON format.
	 */
	public static String toJsonArray(String[] strArray) {
        if (strArray == null)
            return "null";
        int max = strArray.length - 1;
        if (max == -1)
            return "[]";
        StringBuilder json = new StringBuilder();
        json.append('[');
        for (int i = 0; ; ++i) {
            json.append('\"').append(strArray[i]).append('\"');
            if (i == max)
                return json.append(']').toString();
            json.append(", ");
        }
	}
}
