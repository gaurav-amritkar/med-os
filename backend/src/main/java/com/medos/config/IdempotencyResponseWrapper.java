package com.medos.config;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Response wrapper that captures the response body for idempotency caching.
 */
public class IdempotencyResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private int status = HttpServletResponse.SC_OK;

    public IdempotencyResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public void setStatus(int sc) {
        super.setStatus(sc);
        this.status = sc;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() already called");
        }
        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    bodyBuffer.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // Not needed for synchronous processing
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() already called");
        }
        if (writer == null) {
            writer = new PrintWriter(bodyBuffer);
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        if (outputStream != null) {
            outputStream.flush();
        }
        super.flushBuffer();
    }

    public String getBodyAsString() {
        return bodyBuffer.toString();
    }

    public int getStatus() {
        return status;
    }

    public void copyTo(HttpServletResponse response) throws IOException {
        response.setStatus(status);
        // Copy headers
        for (String headerName : getHeaderNames()) {
            for (String headerValue : getHeaders(headerName)) {
                response.addHeader(headerName, headerValue);
            }
        }
        // Write body
        byte[] body = bodyBuffer.toByteArray();
        if (body.length > 0) {
            response.setContentLength(body.length);
            try (ServletOutputStream out = response.getOutputStream()) {
                out.write(body);
            }
        }
    }
}