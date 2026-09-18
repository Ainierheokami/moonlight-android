package com.limelight.nvstream.input;

import com.limelight.nvstream.http.NvHTTP;

/** Supplies a fresh authenticated host HTTP client for clipboard blob I/O. */
public interface ClipboardHttpProvider {
    NvHTTP get();
}
