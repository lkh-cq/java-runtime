package io.visualr.runtime;

import java.util.List;

/**
 * PAL projections — mirror of R {@code pal_to_jiugong} /
 * {@code pal_to_square_view} / {@code mirror_addr} (jiugong.R,
 * complement.R).
 *
 * <p>Jiugong is STRICTLY the S_4 -&gt; 3x3 mapping per the frozen spec
 * (元九宫). Other perfect-square unfolded lengths (S_0 -&gt; 1x1,
 * S_12 -&gt; 5x5) are general square views, not jiugong — they go
 * through {@link #palToSquareView}.</p>
 */
public final class PalProjection {

    private PalProjection() {}

    /**
     * S_4 -&gt; 3x3 jiugong view (mirror of R {@code pal_to_jiugong}).
     * Unfolded length MUST be 9; otherwise error with guidance.
     *
     * @return 3x3 grid, row-major: unfolded[1:3] = row 1, etc.
     */
    public static String[][] palToJiugong(PalState pal) {
        List<String> unfolded = TopologyCell.unfold(pal);
        int n = unfolded.size();
        if (n != 9) {
            throw new IllegalArgumentException(
                    "palToJiugong is the S_4 -> 3x3 mapping only (unfolded length " + n
                            + "). Use palToSquareView() for general square views.");
        }
        String[][] grid = new String[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                grid[r][c] = unfolded.get(r * 3 + c);
            }
        }
        return grid;
    }

    /**
     * General square view: S_n -&gt; k x k grid when the unfolded
     * palindrome length is a perfect square (1, 9, 25, ...).
     * NOT the frozen S_4 -&gt; 3x3 jiugong.
     */
    public static String[][] palToSquareView(PalState pal) {
        List<String> unfolded = TopologyCell.unfold(pal);
        int n = unfolded.size();
        int k = (int) Math.sqrt(n);
        if (k * k != n) {
            throw new IllegalArgumentException(
                    "palToSquareView requires unfolded length to be a perfect square, got " + n);
        }
        String[][] grid = new String[k][k];
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < k; c++) {
                grid[r][c] = unfolded.get(r * k + c);
            }
        }
        return grid;
    }

    /**
     * Central inversion — mirror of R {@code mirror_addr}: (r,c) -&gt;
     * (4-r, 4-c) in 1-indexed 3x3 jiugong. Invariant: Sigma^2 = I.
     *
     * @param row 1-indexed row (1..3)
     * @param col 1-indexed column (1..3)
     * @return [newRow, newCol] (1-indexed)
     */
    public static int[] mirrorAddr(int row, int col) {
        if (row < 1 || row > 3 || col < 1 || col > 3) {
            throw new IllegalArgumentException("Coordinates must be in 1..3 (3x3 jiugong)");
        }
        return new int[] {4 - row, 4 - col};
    }
}
