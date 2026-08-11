package io.visualr.orchestration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compact package record — a packaged pipeline task + result with an
 * integrity checksum (DEVELOPMENT_PLAN §7 packaging/transport contract).
 *
 * @param palFormat    canonical PAL state (v0.2 record format)
 * @param mappingPackId mapping-pack identity
 * @param kernel       lane kernel name used
 * @param resultFormat re-encoded S_(t+1) PAL (v0.2 record format)
 * @param checksum     SHA-256 hex over the task content
 */
public record PackageRecord(String palFormat, String mappingPackId, String kernel,
                            String resultFormat, String checksum) {

    public static final String HEADER = "visualr-package/v1";
}
