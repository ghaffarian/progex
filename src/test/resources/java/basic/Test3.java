
public class Test3 {

	public static void test1(int i) {
		switch (i) {
			case 1:
				System.out.println("One");
				break;
			case 2:
			case 3:
			case 5:
			case 7:
				System.out.println(i + " is Prime");
				break;
			case 4:
			case 6:
			case 8:
			case 9:
				System.out.println(i + " is Composite");
				break;
			default:
				System.out.println(i);
		}
	}
	
	
	public static void test2(int i) {
		System.out.println("Start");
		switch (i % 2) {
			case 0:
				System.out.println(i + " is Even");
				//break;
			case 1:
				System.out.println(i + " is Odd");
				//break;
			default:
				System.out.println("WTF!");
		}
		System.out.println("Finish");
	}
	
	
	public static void test3(int i) {
		switch (i % 2) {
			case 0:
				System.out.println(i + " is Even");
				break;
			case 1:
				System.out.println(i + " is Odd");
				break;
		}
	}
	
	
	public static void test4() {
		int i = 0;
		switch (i) {
			default:
		}
		System.out.println("Finish");
	}
	
	
	public static void test5() {
		int i = 0;
		switch (i) {
			case 0:
		}
	}
	
	
	public static void main(String[] args) {
		System.out.println("test-1:");
		test1(5);
		System.out.println("\ntest-2:");
		test2(7);
		System.out.println("\ntest-3:");
		test3(4);
		System.out.println("\ntest-4:");
		test4();
		System.out.println("\ntest-5:");
		test5();
	}
	
}

