/*** In The Name of Allah ***/
package ghaffarian.progex.java;

import java.util.Arrays;
import java.util.Objects;

/**
 * A simple structure for storing key information about a Java class method.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class JavaMethod {
	
	public final String NAME;
	public final boolean STATIC;
	public final boolean ABSTRACT;
	public final String MODIFIER;
	public final String RET_TYPE;
	public final int LINE_OF_CODE;
	public final String[] ARG_TYPES;

	public JavaMethod(String modifier, boolean isStatic, boolean isAbstract, String retType, String name, String[] argTypes, int line) {
		NAME = name;
		STATIC = isStatic;
		ABSTRACT = isAbstract;
		MODIFIER = modifier;
		RET_TYPE = retType;
		ARG_TYPES = argTypes;
		LINE_OF_CODE = line;
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
		if (ABSTRACT) { 
			if (modifier.equals("null")) 
				modifier = "abstract"; 
			else 
				modifier += " abstract"; 
		}
		String retType = RET_TYPE == null ? "null" : RET_TYPE;
		String args = ARG_TYPES == null ? "null" : Arrays.toString(ARG_TYPES);
		StringBuilder str = new StringBuilder();
		str.append("{ MODIFIER : \"").append(modifier).append("\", ");
		str.append("TYPE : \"").append(retType).append("\", ");
		str.append("NAME : \"").append(NAME).append("\", ");
		str.append("ARGS : ").append(args).append(", ");
		str.append("LINE : \"").append(LINE_OF_CODE).append("\" }");
		return str.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof JavaMethod))
			return false;
		JavaMethod m = (JavaMethod) obj;
		return (NAME.equals(m.NAME) && RET_TYPE.equals(m.RET_TYPE) &&
				LINE_OF_CODE == m.LINE_OF_CODE && Arrays.equals(ARG_TYPES, m.ARG_TYPES));
	}

	@Override
	public int hashCode() {
		int hash = 5;
		hash = 31 * hash + Objects.hashCode(this.NAME);
		hash = 31 * hash + Objects.hashCode(this.RET_TYPE);
		hash = 31 * hash + this.LINE_OF_CODE;
		hash = 31 * hash + Arrays.deepHashCode(this.ARG_TYPES);
		return hash;
	}
}
