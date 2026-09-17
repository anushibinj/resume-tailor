package com.resumetailor.export;

/** A file ready to stream back to the browser. */
public record DownloadPayload(String filename, String contentType, byte[] content) {
}
