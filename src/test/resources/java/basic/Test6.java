
public class Test6 {

	public void test() {
		int x = 0;
		boolean p1 = true;
		while (p1) {
			x *= 2;
			x -= 10;
			if (x > 10) {
				x += 8;
				x /= 3;
				if (x > 0)
					x -= 2;
				else
					break;
			} else 
				continue;
			x--;
			System.out.println(x);
			if (x > 20)
				break;
			--x;
			p1 = x > 7 ? true : false;
		}
	}
	
	public static void main(String[] args) {
		int x = 120;
		while (true) {
			if (x > 100) {
				x -= 10;
				continue;
			} else {
				if (x < 10)
					break;
				else
					x -= 5;
			}
			System.out.println(x);
		}
		
		Test6 tst = new Test6();
		tst.test();
	}
}

