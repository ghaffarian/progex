
public class Test1 {

	public static void main(String[] args) {
		int i = 0;
		i++;
		if (i > 0) {
			System.out.println("Positive");
			i *= 2;
		} else {
			System.out.println("Non-positive");
			i *= -2;
		}
		System.out.println("End");
	}
}

