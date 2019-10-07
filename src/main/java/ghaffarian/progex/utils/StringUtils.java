/*** In The Name of Allah ***/
package ghaffarian.progex.utils;

/**
 * A utility class providing some String-related capabilities.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class StringUtils {
    
    /**
     * Returns true if given String is null or empty after trim.
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
	
	/**
	 * A simple escaping method based on REGEX.
	 */
	public static String escape(String code) {
        if (code == null)
            return null;
		// Match and replace all string literals
		//String escapedCode = code.replaceAll("\".*?\"", "STR");
		String escapedCode = code.replaceAll("\"", "'");
        escapedCode = escapedCode.replaceAll("\n", " ");
        escapedCode = escapedCode.replaceAll("\r", " ");
		// Match and replace all numeric literals
		//escapedCode = escapedCode.replaceAll("\\b-?\\d+(.\\d+)*\\b", "$NUM");
		// Match and escape all backslashes
		escapedCode = escapedCode.replaceAll("\\\\", "\\\\\\\\");
        //
		return removeConsecutiveSpaces(escapedCode);
	}
    
    /**
     * Replaces all occurrences of 3 spaces with a single space.
     * The resulting string will only have runs of at most two spaces.
     */
    public static String removeConsecutiveSpaces(String str) {
        String before;
        do {
            before = str;
            str = str.replace("   ", " ");
        } while (!before.equals(str));
        return str;
    }
	
	/**
	 * Returns a string which replaces all double-quotes (") chars with the escaped version (\").
	 */
	public static String escapeDoubleQuotes(String code) {
		return (code == null) ? null : code.replace("\"", "\\\"");
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
	
	/**
	 * Returns a GML array representation of the given String array.
     * 
     * @param name given name for elements of this array
	 */
	public static String toGmlArray(String[] strArray, String name) {
        if (strArray == null)
            return "null";
        int max = strArray.length - 1;
        if (max == -1)
            return "[]";
        StringBuilder gml = new StringBuilder();
        gml.append('[');
        for (int i = 0; ; ++i) {
            gml.append(name).append(" \"").append(strArray[i]).append('\"');
            if (i == max)
                return gml.append(']').toString();
            gml.append(' ');
        }
	}
}
