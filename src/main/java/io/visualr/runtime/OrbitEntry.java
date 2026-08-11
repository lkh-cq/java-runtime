package io.visualr.runtime;

/**
 * Orbit table entry — mirror of the R ORBIT_TABLE fourth-dimension fixed
 * mapping (user-frozen 2026-08-05):
 * {@code storage role | head | tail | jiugong coords (1-indexed)}.
 *
 * @param head  palindrome head index (1-based)
 * @param tail  palindrome tail index (1-based)
 * @param addr1 jiugong coordinate of head, [row, col] 1-indexed
 * @param addr2 jiugong coordinate of tail, [row, col] 1-indexed
 */
public record OrbitEntry(int head, int tail, int[] addr1, int[] addr2) {

    public OrbitEntry {
        if (addr1 == null || addr1.length != 2 || addr2 == null || addr2.length != 2) {
            throw new IllegalArgumentException("addr1/addr2 must be [row, col] pairs");
        }
        addr1 = addr1.clone();
        addr2 = addr2.clone();
    }

    @Override
    public int[] addr1() { return addr1.clone(); }

    @Override
    public int[] addr2() { return addr2.clone(); }
}
