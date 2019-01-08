/*** In The Name of Allah ***/
package ghaffarian.progex.utils;

import java.io.PrintStream;

/**
 * A utility class providing some system-related capabilities.
 * 
 * @author Seyed Mohammad Ghaffarian
 */
public class SystemUtils {
	
	/**
	 * Print human-readable statistics about the system-memory.
	 */
	public static void printMemoryStats(PrintStream out) {
		long free = Runtime.getRuntime().freeMemory();
		long total = Runtime.getRuntime().totalMemory();
		long max = Runtime.getRuntime().maxMemory();
		StringBuilder memory = new StringBuilder();
		memory.append(String.format(
				"\n  Used Memory   : %6.2f MB  (%%%04.1f)\n"
				+ "  Free Memory   : %6.2f MB  (%%%04.1f)\n"
				+ "  Current Heap  : %6.2f MB  (%%%04.1f)\n"
				+ "  Max Heap Size : %6.2f MB\n",
				(float) (total - free) / (1024 * 1024), (float) 100 * (total - free) / total,
				(float) free / (1024 * 1024), (float) (free * 100) / total,
				(float) total / (1024 * 1024), (float) (total * 100) / max,
				(float) max / (1024 * 1024)));
		out.println(memory.toString());
	}
	
	/**
	 * Get human-readable statistics about the system-memory as a String.
	 */
	public static String getMemoryStats() {
		long free = Runtime.getRuntime().freeMemory();
		long total = Runtime.getRuntime().totalMemory();
		long max = Runtime.getRuntime().maxMemory();
		StringBuilder memory = new StringBuilder();
		memory.append(String.format(
				"\n  Used Memory   : %6.2f MB  (%%%04.1f)\n"
				+ "  Free Memory   : %6.2f MB  (%%%04.1f)\n"
				+ "  Current Heap  : %6.2f MB  (%%%04.1f)\n"
				+ "  Max Heap Size : %6.2f MB\n",
				(float) (total - free) / (1024 * 1024), (float) 100 * (total - free) / total,
				(float) free / (1024 * 1024), (float) (free * 100) / total,
				(float) total / (1024 * 1024), (float) (total * 100) / max,
				(float) max / (1024 * 1024)));
		return memory.toString();
	}	
}
