package com.medos.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Caches a request body so it can be read more than once.
 *
 * <p>Needed because the body stream is consumed on first read: the idempotency
 * filter fingerprints it, and the controller then binds it. Spring's own
 * {@code CachedBodyHttpServletRequest} does exactly this but is only installed
 * by {@code @RequestBody} argument resolution for the handler itself, not for a
 * filter that runs earlier in the chain — and it is not available outside the
 * MVC dispatcher.
 *
 * <p>The cached bytes are also stored in a request attribute so a filter that
 * has no wrapper of its own (and the response wrapper, which runs later) can
 * reach the same bytes without re-reading the stream.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private static final String BODY_ATTRIBUTE = CachedBodyHttpServletRequest.class.getName() + ".body";

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
        request.setAttribute(BODY_ATTRIBUTE, body);
    }

    @Override
    public ServletInputStream getInputStream() {
        return new ByteArrayServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
    }

    /**
     * Wraps {@code request} with a body-caching wrapper, reading at most
     * {@code maxBytes}. Returns {@code null} if the body cannot be read or
     * exceeds the limit.
     */
    public static CachedBodyHttpServletRequest wrap(HttpServletRequest request, int maxBytes) {
        Object cached = request.getAttribute(BODY_ATTRIBUTE);
        if (cached instanceof byte[] existing) {
            return new CachedBodyHttpServletRequest(request, existing);
        }
        byte[] body = read(request, maxBytes);
        if (body == null) {
            return null;
        }
        return new CachedBodyHttpServletRequest(request, body);
    }

    /**
     * Reads the request body, caching it in a request attribute so a second call
     * does not re-read the (already consumed) stream.
     */
    public static byte[] read(HttpServletRequest request, int maxBytes) {
        Object cached = request.getAttribute(BODY_ATTRIBUTE);
        if (cached instanceof byte[] existing) {
            return existing;
        }
        try {
            byte[] body = request.getInputStream().readAllBytes();
            if (body.length > maxBytes) {
                // Too large to fingerprint; leave the attribute unset so a later
                // read is not fooled into thinking this body was cached.
                return null;
            }
            request.setAttribute(BODY_ATTRIBUTE, body);
            return body;
        } catch (IOException e) {
            return null;
        }
    }

    private static final class ByteArrayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        private ByteArrayServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Synchronous only; no async reads are used on these endpoints.
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }
    }
}
