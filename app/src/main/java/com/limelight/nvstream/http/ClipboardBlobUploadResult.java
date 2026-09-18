package com.limelight.nvstream.http;

/** Metadata returned by Foundation Sunshine after a clipboard blob upload. */
public final class ClipboardBlobUploadResult {
    public final String id;
    public final String mime;
    public final long size;

    public ClipboardBlobUploadResult(String id, String mime, long size) {
        this.id = id;
        this.mime = mime;
        this.size = size;
    }
}
