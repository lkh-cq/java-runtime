package io.visualr.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Nested palindrome codec — Java port of the language-independent
 * PAL_NESTED_CONTRACT (visualR inst/PAL_NESTED_CONTRACT.md, 2026-08-07).
 *
 * <p>This is the INTEROP layer (human-readable canonical form), distinct
 * from the v0.2 length-prefixed serialization ({@link PalCodec}).</p>
 *
 * <pre>
 * S_n = {x_0 {x_1 { ... { x_{n-1} { x_n } x_{n-1} } ... } x_1 } x_0 }
 * </pre>
 *
 * <p>Critical rules ported:</p>
 * <ul>
 *   <li>Multi-character symbol compare: closing symbol MUST be compared as a
 *       FULL-LENGTH token, never single-char (a real bug in the Python
 *       reference, fixed 2026-08-07).</li>
 *   <li>UTF-8 safety: Java UTF-16 {@code char} indexing is a hazard — symbols
 *       are sliced with {@code substring} (code-point correct), never
 *       compared char-by-char.</li>
 *   <li>Depth cap MAX_SHELLS = 64.</li>
 *   <li>Complete consumption on parse; odd-length + symmetry check on encode.</li>
 * </ul>
 */
public final class NestedPalCodec {

    /** Contract depth cap. */
    public static final int MAX_SHELLS = 64;

    private NestedPalCodec() {}

    /**
     * Encode a palindrome path into nested text (contract §4).
     * Path must be odd-length and symmetric.
     */
    public static String encode(List<String> path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        int n = path.size();
        if (n % 2 == 0) {
            throw new IllegalArgumentException("path must have odd length (palindrome), got " + n);
        }
        int depth = (n - 1) / 2;
        if (depth > MAX_SHELLS) {
            throw new IllegalArgumentException("nesting depth " + depth + " exceeds MAX_SHELLS=" + MAX_SHELLS);
        }
        // symmetry: path[k] == path[2n-k]
        for (int k = 0; k < depth; k++) {
            if (!path.get(k).equals(path.get(n - 1 - k))) {
                throw new IllegalArgumentException(
                        "path is not symmetric: path[" + k + "]='" + path.get(k)
                                + "' != path[" + (n - 1 - k) + "]='" + path.get(n - 1 - k) + "'");
            }
        }
        for (String s : path) {
            if (s == null || s.isEmpty()) {
                throw new IllegalArgumentException("symbols must be non-empty");
            }
            if (s.indexOf('{') >= 0 || s.indexOf('}') >= 0) {
                throw new IllegalArgumentException("symbols must not contain braces: '" + s + "'");
            }
        }
        // build center-outward
        int core = depth;
        String text = "{" + path.get(core) + "}";
        for (int k = core - 1; k >= 0; k--) {
            text = "{" + path.get(k) + text + path.get(k) + "}";
        }
        return text;
    }

    /** Convenience: encode a PalState by unfolding it to a path. */
    public static String encodePal(PalState pal) {
        return encode(TopologyCell.unfold(pal));
    }

    /**
     * Parse nested text into the palindrome path (contract §3).
     * Full-length symbol compare; complete consumption; depth cap.
     */
    public static List<String> parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("input must not be empty");
        }
        Parser p = new Parser(text);
        List<String> path = new ArrayList<>();
        p.parseNode(path, 0);
        if (!p.atEnd()) {
            throw new IllegalArgumentException(
                    "trailing characters after top-level parse at offset " + p.pos());
        }
        return path;
    }

    /** Convenience: parse nested text into a PAL state (path -> PalState). */
    public static PalState parsePal(String text) {
        List<String> path = parse(text);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("empty path");
        }
        int core = (path.size() - 1) / 2;
        String coreToken = path.get(core);
        List<String> shells = new ArrayList<>();
        for (int k = 0; k < core; k++) {
            shells.add(path.get(k));
        }
        return PalState.of(shells, coreToken, PalState.DEFAULT_MAPPING_PACK_ID,
                new java.util.LinkedHashMap<>());
    }

    /** Recursive-descent parser with length-aware symbol comparison. */
    private static final class Parser {
        private final String text;
        private int pos = 0;

        Parser(String text) {
            this.text = text;
        }

        int pos() { return pos; }

        boolean atEnd() { return pos >= text.length(); }

        /** Parse one node: consume '{', symbol, then inner or '}', symmetric close. */
        void parseNode(List<String> path, int depth) {
            if (depth > MAX_SHELLS) {
                throw new IllegalArgumentException(
                        "nesting depth exceeds MAX_SHELLS=" + MAX_SHELLS);
            }
            expect('{');
            String symbol = readSymbol();
            path.add(symbol);
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of input after symbol '" + symbol + "'");
            }
            char next = text.charAt(pos);
            if (next == '{') {
                parseNode(path, depth + 1);
                // closing symmetric half: full-length symbol compare, then
                // append to path (invariant: parse(encode(path)) == path)
                String close = readSymbol();
                if (!close.equals(symbol)) {
                    throw new IllegalArgumentException(
                            "closing symbol '" + close + "' does not match opening '" + symbol + "'");
                }
                path.add(close);
                expect('}');
            } else if (next == '}') {
                pos++; // leaf (center singularity)
            } else {
                throw new IllegalArgumentException(
                        "unexpected character '" + next + "' at offset " + pos);
            }
        }

        private void expect(char c) {
            if (atEnd() || text.charAt(pos) != c) {
                throw new IllegalArgumentException(
                        "expected '" + c + "' at offset " + pos + " but got "
                                + (atEnd() ? "end of input" : "'" + text.charAt(pos) + "'"));
            }
            pos++;
        }

        /** Read a symbol: non-empty run of chars until '{' or '}' (full-length slice). */
        private String readSymbol() {
            int start = pos;
            while (!atEnd()) {
                char c = text.charAt(pos);
                if (c == '{' || c == '}') {
                    break;
                }
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("empty symbol at offset " + start);
            }
            return text.substring(start, pos); // substring: code-point correct
        }
    }
}
