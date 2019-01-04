import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;


public class Test8 {

	public static void run(int x) {
		//
		boolean isPrime = false;
		int[] primes = {2, 3, 5, 7, 11, 13, 17, 19};
		for (int p: primes) {
			if (x == p)
				isPrime = true;
		}
		System.out.println(x + (isPrime ? " is prime" : " is not prime"));
		//
		//
		File file = new File("manifest.mf");
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);	// FileNotFoundException
			byte[] buffer = new byte[x];
			int read = fis.read(buffer);		// IOException
			System.out.println(read + " bytes read.");
			System.out.println(Arrays.toString(Arrays.copyOf(buffer, read)));
		} catch (FileNotFoundException ex) {
			System.err.println(ex);
		} catch (IOException ex) {
			System.err.println(ex);
		} finally {
			try {
				if (fis != null)
					fis.close();				// IOException
			} catch (IOException ex) {
				System.err.println(ex);
			}
		}
		//
		//
		file = new File("build.xml");
		try (FileInputStream fism = new FileInputStream(file)) { 	// FileNotFoundException
			byte[] buffer = new byte[2 * x];
			int read = fism.read(buffer);							// IOException
			System.out.println(read + " bytes read.");
			System.out.println(Arrays.toString(Arrays.copyOf(buffer, read)));
		} catch (FileNotFoundException ex) {
			System.err.println(ex);
		} catch (IOException ex) {
			System.err.println(ex);
		}
		//
	}

	public static void main(String[] args) {
		if (args.length > 0)
			Test8.run(Integer.parseInt(args[0]));
		else
			System.out.println("Nothing to process!");
	}
}
	
