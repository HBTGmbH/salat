package de.hbt.salat.common.graphql;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a GraphQL request whose body is larger than {@code maxBytes} (concept 6.2), before it is read into memory
 * and parsed. The body carries the query and the variables; a variable is JSON parsed by Jackson, and nothing else
 * bounds its size, so a multi-megabyte body is otherwise buffered and parsed in full. Over the limit the answer is
 * HTTP 413, like the rate limiter answers 429 — a transport limit, not a GraphQL error.
 *
 * <p>The {@code Content-Length} is checked first, which rejects the usual case at once. A request without a reliable
 * length (chunked transfer) is guarded while it is read: the wrapped input stream counts bytes and aborts past the
 * limit, and the filter turns that into a 413 as long as nothing has been written to the response yet.
 */
@Slf4j
class GraphqlRequestSizeLimitFilter extends OncePerRequestFilter {

  private final long maxBytes;

  GraphqlRequestSizeLimitFilter(long maxBytes) {
    this.maxBytes = maxBytes;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      reject(response);
      return;
    }
    try {
      chain.doFilter(new LimitedRequest(request, maxBytes), response);
    } catch (RequestTooLargeException e) {
      if (response.isCommitted()) {
        throw e;
      }
      response.reset();
      reject(response);
    }
  }

  private void reject(HttpServletResponse response) throws IOException {
    log.info("GraphQL request refused: body larger than {} bytes", maxBytes);
    response.sendError(413, "Request body larger than " + maxBytes + " bytes");
  }

  /** Thrown while reading a body that exceeds the limit without a (reliable) {@code Content-Length}. */
  private static final class RequestTooLargeException extends RuntimeException {
  }

  /** Hands out an input stream that aborts once more than {@code maxBytes} have been read. */
  private static final class LimitedRequest extends HttpServletRequestWrapper {

    private final long maxBytes;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      ServletInputStream delegate = super.getInputStream();
      return new ServletInputStream() {

        private long count;

        private void add(long n) {
          count += n;
          if (count > maxBytes) {
            throw new RequestTooLargeException();
          }
        }

        @Override
        public int read() throws IOException {
          int b = delegate.read();
          if (b >= 0) {
            add(1);
          }
          return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
          int n = delegate.read(b, off, len);
          if (n > 0) {
            add(n);
          }
          return n;
        }

        @Override
        public boolean isFinished() {
          return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
          return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          delegate.setReadListener(readListener);
        }
      };
    }
  }
}
