package io.visualr.orchestration;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Package codec — compact storage/transport unit for a pipeline task and
 * its result (DEVELOPMENT_PLAN §7). Line-counted record format, no eval:
 *
 * <pre>
 * visualr-package/v1
 * checksum:&lt;sha256-hex&gt;
 * pal_lines:&lt;N&gt;
 * &lt;N lines of PAL v0.2 records&gt;
 * kernel:&lt;name&gt;
 * result_lines:&lt;N&gt;
 * &lt;N lines of result PAL v0.2 records&gt;
 * </pre>
 *
 * <p>The checksum covers the task content (pal + kernel + result), so a
 * tampered/stale package FAILS verification (fail-closed).</p>
 */
public final class PackageCodec {

    private PackageCodec() {}

    /** Package a task and its result. */
    public static String pack(PalState pal, String kernel, PalState result) {
        List<String> palLines = List.of(PalCodec.format(pal).split("\n", -1));
        List<String> resultLines = List.of(PalCodec.format(result).split("\n", -1));
        String kernelName = kernel == null ? "identity" : kernel;
        String checksum = checksum(PalCodec.format(pal), kernelName, PalCodec.format(result));

        List<String> out = new ArrayList<>();
        out.add(PackageRecord.HEADER);
        out.add("checksum:" + checksum);
        out.add("pal_lines:" + palLines.size());
        out.addAll(palLines);
        out.add("kernel:" + kernelName);
        out.add("result_lines:" + resultLines.size());
        out.addAll(resultLines);
        return String.join("\n", out);
    }

    /** Unpack a package; FAILS CLOSED when the checksum does not verify. */
    public static PackageRecord unpack(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("empty package");
        }
        String[] lines = text.split("\n", -1);
        int idx = 0;
        if (!lines[idx++].equals(PackageRecord.HEADER)) {
            throw new IllegalArgumentException("missing package header");
        }
        if (!lines[idx].startsWith("checksum:")) {
            throw new IllegalArgumentException("missing checksum record");
        }
        String declared = lines[idx++].substring("checksum:".length());

        if (!lines[idx].startsWith("pal_lines:")) {
            throw new IllegalArgumentException("missing pal_lines record");
        }
        int nPal = Integer.parseInt(lines[idx++].substring("pal_lines:".length()));
        List<String> palLines = new ArrayList<>(nPal);
        for (int i = 0; i < nPal; i++) {
            palLines.add(lines[idx++]);
        }
        String palFormat = String.join("\n", palLines);

        if (!lines[idx].startsWith("kernel:")) {
            throw new IllegalArgumentException("missing kernel record");
        }
        String kernel = lines[idx++].substring("kernel:".length());

        if (!lines[idx].startsWith("result_lines:")) {
            throw new IllegalArgumentException("missing result_lines record");
        }
        int nResult = Integer.parseInt(lines[idx++].substring("result_lines:".length()));
        List<String> resultLines = new ArrayList<>(nResult);
        for (int i = 0; i < nResult; i++) {
            resultLines.add(lines[idx++]);
        }
        String resultFormat = String.join("\n", resultLines);

        // fail-closed integrity check
        String actual = checksum(palFormat, kernel, resultFormat);
        if (!actual.equals(declared)) {
            throw new IllegalArgumentException(
                    "package checksum mismatch (tampered/stale): declared " + declared + " actual " + actual);
        }

        // parse eagerly so a malformed payload cannot silently pass
        PalState pal = PalCodec.parse(palFormat);
        PalState result = PalCodec.parse(resultFormat);
        return new PackageRecord(PalCodec.format(pal), pal.mappingPackId(), kernel,
                PalCodec.format(result), declared);
    }

    /** SHA-256 hex over the task content. */
    public static String checksum(String palFormat, String kernel, String resultFormat) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(palFormat.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(kernel.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(resultFormat.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
