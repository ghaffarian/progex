
public class Test9 {

	public static String test() {
		StringBuilder str = new StringBuilder();
		str.append("hello").append("World");
		int x = 0;
		if (x < 0)
			return "";
		return str.toString();
	}

	public static void main(String [] args) {
		System.out.println(test() + test());
	}
}
