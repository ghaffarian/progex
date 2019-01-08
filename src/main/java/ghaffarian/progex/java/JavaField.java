/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import java.util.Objects;

/**
 * A simple structure for storing key information about a Java class field.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaField {
	
	public final String NAME;
	public final String TYPE;
	public final boolean STATIC;
	public final String MODIFIER;
	
	public JavaField(String modifier, boolean isStatic, String type, String name) {
		NAME = name;
		TYPE = type;
		STATIC = isStatic;
		MODIFIER = modifier;
	}
	
	@Override
	public String toString() {
		String modifier = MODIFIER == null ? "null" : MODIFIER;
		if (STATIC) { 
			if (modifier.equals("null")) 
				modifier = "static"; 
			else 
				modifier += " static"; 
		}
		StringBuilder str = new StringBuilder();
		str.append("{ MODIFIER : \"").append(modifier).append("\", ");
		str.append("TYPE : \"").append(TYPE).append("\", ");
		str.append("NAME : \"").append(NAME).append("\" }");
		return str.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JavaField))
			return false;
		JavaField f = (JavaField) obj;
		return (NAME.equals(f.NAME) && TYPE.equals(f.TYPE));
	}
}
