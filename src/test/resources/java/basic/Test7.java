
public class Test7 {

	private String str;
	private Integer integer;

	public Test7() {
		str = "";
		integer = 0;
	}
	
	public Test7(String str, int sub) {
		this.str = str.substring(sub);
		integer = 0;
	}

	public String getSTR() {
		return str;
	}
	
	public void setInteger(int i) {
		integer = i;
	}
	
	public int getInteger() {
		return integer;
	}

	public void setSTR(String str) {
		this.str = str;
	}

	public void method1() {
		int len = str.length();
		System.out.println("STR length is " + len);
	}

	public String addSTR(String str) {
		return this.str + str;
	}
	
	public static void simple() {
		int i = 1, j;
		i++;
		System.out.println(i);
		j = i + 3;
		System.out.println(j);
	}
	
	public static void main(String[] args) {
		int idx = 4;
		Test7 t = new Test7();
		t.method1();
		String str = "some-string";
		t.setSTR(t.addSTR(str).concat(str));
		System.out.println(t.getSTR());
		System.out.println(t.getSTR().charAt(idx += 2));
		t = new Test7(str.concat("-plus-more"), idx);
		new Test7("This is a string", 10).getSTR();
		simple();
		t.sideEffect();
		System.out.println("t.integer = " + t.getInteger());
		StringBuilder val = new StringBuilder("10");
		System.out.println("val = " + val);
		t.doubleSideEffect(val);
		System.out.println("val = " + val);
		System.out.println("t.integer = " + t.getInteger());
	}
	
	public void sideEffect() {
		integer++;
	}
	
	public void doubleSideEffect(StringBuilder intStr) {
		intStr.append(0);
		integer = Integer.parseInt(intStr.toString());
	}
}

