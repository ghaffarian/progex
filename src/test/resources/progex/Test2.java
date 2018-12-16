
public class Test {

	private String str;

	public Test() {
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
	
	public void process(int x, int y) {
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

	public static void main(String[] args) {
		int i = 0;
		i++;
		if (i > 0)
			System.out.println("Positive");
		else
			System.out.println("Non-positive");
		Test tst = new Test();
		tst.setString(tst.getString());
		System.out.println(tst.getString());
		tst.process(10, i);
		System.out.println("End");
	}
}

