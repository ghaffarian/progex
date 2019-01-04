
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

public class Test5 {

	public static void test1() {
		File file = new File("file.log");
		try {
			FileInputStream fis = new FileInputStream(file);	// FileNotFoundException
			byte[] buffer = new byte[256];
			int read = fis.read(buffer);						// IOException
			System.out.println(read + " bytes read.");
			System.out.println(Arrays.toString(Arrays.copyOf(buffer, read)));
		} catch (FileNotFoundException ex) {
			System.err.println(ex);
		} catch (IOException ex) {
			System.err.println(ex);
		} finally {
			System.out.println("finally");
		}
	}

	
	public static void test2() {
		try {
			File file = new File("file.log");
			if (file.exists()) {
				System.out.println("Permissions for 'file.log':");
				System.out.println("  Read = " + file.canRead());
				System.out.println("  Write = " + file.canWrite());
				System.out.println("  Execute = " + file.canExecute());
			} else {
				System.out.println("'file.log' does not exist!");
			}
		} finally {
			System.out.println("finally");
		}
	}
	
	
	public static void test3() {
		int i = 10;
		try {
			while (true) {
				System.out.println(10 / i);  // Division by zero; ArithmeticException
				--i;
			}
		} catch (ArithmeticException ex) {
			System.err.println(ex);
		}
		System.out.println("i = " + i);
	}	

		
	public static void test4(int i) {
		try {
			if (i > 0) {
				System.out.println("Positive");
			} else {
				if (i == 0)
					throw new Exception("ZERO! [ A Pointless Exception! ]");
				System.out.println("Negative");
			}
		} catch (Exception ex) {
			System.err.println(ex);
		}
		System.out.println("Finish");
	}
	

	public static void main(String[] args) {
		System.out.println("test-1:");
		test1();
		System.out.println("\ntest-2:");
		test2();
		System.out.println("\ntest-3:");
		test3();
		System.out.println("\ntest-4:");
		test4(0);
	}
	
}

