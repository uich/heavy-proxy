package jp.uich.heavyproxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor
public class LoggingRequestResponseInterceptor implements ClientHttpRequestInterceptor {

  private Config config = Config.builder().build();

  @Override
  public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
      throws IOException {
    if (log.isDebugEnabled()) {
      ClientHttpResponse response = Execution.from(request, body, config)
          .logRequest()
          .execute(execution)
          .logResponse()
          .getResponse();
      return response;
    }
    return execution.execute(request, body);
  }

  @Builder
  public static class Config {

    @Builder.Default
    private boolean printRequestBody = true;
    @Builder.Default
    private boolean printResponseBody = true;
  }

  private static class Execution {

    private final String uuid = UUID.randomUUID().toString();

    private HttpRequest request;
    private byte[] body;

    private final Config config;

    @Getter
    private ClientHttpResponse response;

    Execution(HttpRequest request, byte[] body, Config config) {
      this.request = request;
      this.body = body;
      this.config = config;
    }

    static Execution from(HttpRequest request, byte[] body, Config config) {
      return new Execution(request, body, config);
    }

    Execution execute(ClientHttpRequestExecution execution) throws IOException {
      long start = System.currentTimeMillis();
      this.response = execution.execute(this.request, this.body);
      log.debug("Time:[uuid:[{}]: time:[{}msec], uri:[{}]]", this.uuid,
          System.currentTimeMillis() - start, request.getURI());
      return this;
    }

    Execution logRequest() {
      final String requestBody = this.body != null && this.body.length > 0
          ? new String(this.body, extractCharset(this.request.getHeaders()))
          : null;

      if (config.printRequestBody) {
        log.debug("Request:[uuid:[{}], method:[{}], uri:[{}], headers:[{}]], body:[\n{}\n]", this.uuid,
            this.request.getMethod(),
            this.request.getURI(), this.request.getHeaders(), requestBody);
      } else {
        log.debug("Request:[uuid:[{}], method:[{}], uri:[{}], headers:[{}]]", this.uuid,
            this.request.getMethod(),
            this.request.getURI(), this.request.getHeaders());
      }

      return this;
    }

    Execution logResponse() {
      SafeResponse safe = new SafeResponse(this.response);

      if (config.printResponseBody) {
        log.debug("Response:[uuid:[{}], status:[{}], text:[{}], headers:[{}]], body:[\n{}\n]", this.uuid, safe.getStatusCode(),
            safe.getStatusText(), safe.getHttpHeaders(), safe.getResponseBodyAsText());
      } else {
        log.debug("Response:[uuid:[{}], status:[{}], text:[{}], headers:[{}]]", this.uuid, safe.getStatusCode(),
            safe.getStatusText(), safe.getHttpHeaders());
      }

      this.response = safe.createRecycledClientHttpResponse();

      return this;
    }
  }

  private static Charset extractCharset(HttpHeaders httpHeaders) {
    return Optional.ofNullable(httpHeaders.getContentType())
        .map(MediaType::getCharset)
        .orElse(StandardCharsets.UTF_8);
  }

  @Getter
  private static class SafeResponse {

    private final ClientHttpResponse original;

    private final Charset responseCharset;

    private final byte[] responseBodyBinary;
    private final String statusText;
    private final HttpStatus statusCode;
    private final HttpHeaders httpHeaders;

    SafeResponse(ClientHttpResponse response) {
      this.original = response;
      this.responseCharset = Optional.ofNullable(response.getHeaders().getContentType())
          .map(MediaType::getCharset)
          .orElse(StandardCharsets.UTF_8);

      this.responseBodyBinary = ignoreError(() -> {
        try (InputStream body = response.getBody()) {
          return StreamUtils.copyToByteArray(body);
        }
      });
      this.statusText = ignoreError(response::getStatusText);
      this.statusCode = ignoreError(response::getStatusCode);
      this.httpHeaders = ignoreError(response::getHeaders);
    }

    ClientHttpResponse createRecycledClientHttpResponse() {
      return new RecycledClientHttpResponse(this.original, this.responseBodyBinary);
    }

    public String getResponseBodyAsText() {
      return new String(responseBodyBinary, responseCharset);
    }

    private static <T> T ignoreError(Callable<T> callable) {
      try {
        return callable.call();
      } catch (Throwable t) {
        return null;
      }
    }
  }

  @RequiredArgsConstructor
  private static class RecycledClientHttpResponse implements ClientHttpResponse {

    private final ClientHttpResponse original;
    private final byte[] responseBodyBinary;

    @Override
    public HttpHeaders getHeaders() {
      return this.original.getHeaders();
    }

    @Override
    public InputStream getBody() throws IOException {
      if (this.responseBodyBinary == null) {
        return new ByteArrayInputStream(new byte[0]);
      }
      return new ByteArrayInputStream(this.responseBodyBinary);
    }

    @Override
    public HttpStatus getStatusCode() throws IOException {
      return this.original.getStatusCode();
    }

    @Override
    public int getRawStatusCode() throws IOException {
      return this.original.getRawStatusCode();
    }

    @Override
    public String getStatusText() throws IOException {
      return this.original.getStatusText();
    }

    @Override
    public void close() {
      this.original.close();
    }
  }
}
