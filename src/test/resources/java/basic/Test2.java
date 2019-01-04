
public class Test2 {

    private String str;

    public Test2() {
        str = "init";
    }

    public String getString() {
        return str;
    }

    public void setString(String s) {
        str = s;
    }

    public void process(int x, int y) {
        int res;
        int i = 0;
        int test = 0;
        do {
            do {
                for (i = 0; i <= 10; i++) {
                    while (test == 0) {
                        if (test <= 10) {
                            test += 2;
                        } else {
                            if (test == 10) {
                                test++;
                            }
                            test += 3;
                        }
                    }
                }
            } while (test < 10);
            i += 2;
        } while (i < 10);

        if (x > y) {
            res = x - y;
            System.out.println("x-y=" + res);
            res = x / y;
            System.out.println("x/y=" + res);
            res = x % y;
            System.out.println("x%y=" + res);
        } else {
            if (x == y) {
                System.out.println("x==y");
                while (test <= 10) {
                    test++;
                    if (test <= 5) {
                        System.out.println(test);
                    }
                }
            } else {
                res = x + y;
                System.out.println("x+y=" + res);
                res = x * y;
                System.out.println("x*y=" + res);
                for (i = 0; i < 10; i++) {
                    System.out.println(res);
                    int j = 0;
                    while (j < 5) {
                        System.out.println(j);
                        for (int k = 10; k > 0; k--) {
                            System.out.println(k);
                        }
                        j++;
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        int i = 0;
        i++;
        if (i > 0)
            System.out.println("Positive");
        else
            System.out.println("Non-positive");
        Test2 tst = new Test2();
        tst.setString(tst.getString());
        System.out.println(tst.getString());
        tst.process(10, i);
        System.out.println("End");
    }
}
