
public class Test04 {

	private String str;

	public Test04() {
		str = "init";
	}
	
	public String getString() {
		if (str == null)
			str = "";
		return str;
	}
	
	public void setString(String s) {
		str = s;
	}
	
	public boolean isPrime(int x) {
		int[] primes = {2, 3, 5, 7, 11, 13, 17, 19};
		for (int p: primes) {
			if (x == p)
				return true;
		}
		return false;
	}
	
	public void test1(int n) {
		for (int i = 0; i < n; ++i) {
			if (isPrime(i))
				System.out.println(i);
			else
				continue;
		}		
	}
	
	public void test2(int n) {
		int counter = n;
		do {
			if (isPrime(counter)) {
				System.out.println(counter);
				break;
			}
			--counter;
		} while(counter > 2);
	}
	
	public void test3(int x, int y) {
		int res;
		if (x > y) {
			res = x - y;
			System.out.println("x-y=" + res);
			res = x / y;
			System.out.println("x/y=" + res);
			res = x % y;
			System.out.println("x%y=" + res);
		} else {
			res = x + y;
			System.out.println("x+y=" + res);
			res = x * y;
			System.out.println("x*y=" + res);
		}
	}

	public void test4() {
		int i = 0;
		while (i < 10) {
			i += 2;
			i--;
			if (i == 8)
				break;
			if (i == 5)
				continue;
			System.out.println(i);
		}
	}
	
	public static void main(String[] args) {
		Test04 tst = new Test04();
		tst.setString(tst.getString());
		System.out.println(tst.getString());
		tst.test1(9);
		tst.test2(16);
		tst.test3(20, 5);
		tst.test4();
		System.out.println("End");
	}
}

