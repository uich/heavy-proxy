package jp.uich.heavyproxy;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@Slf4j
public class HeavyProxyController {

  @Autowired
  HeavyProxyProperties proxyProperties;

  @Autowired
  RestOperations restOps;

  AtomicInteger counter = new AtomicInteger(2);

  @Autowired
  Environment environment;

  @PostConstruct
  void afterPropertiesSet() {
    log.debug("{}", Arrays.asList(environment.getActiveProfiles()));
  }

  @RequestMapping("/**")
  @SneakyThrows
  ResponseEntity<byte[]> proxy(RequestEntity<byte[]> requestEntity) {
    if (counter.decrementAndGet() > 0) {
      Thread.sleep(5000);
    } else {
      counter = new AtomicInteger(2);
    }

    URI uri = UriComponentsBuilder.fromUri(requestEntity.getUrl())
        .scheme(proxyProperties.getSchema())
        .host(proxyProperties.getHost())
        .port(proxyProperties.getPort())
        .build()
        .toUri();

    return restOps.exchange(ProxyRequestEntity.proxy(uri, requestEntity), byte[].class);
  }

  private static class ProxyRequestEntity<T> extends RequestEntity<T> {

    private ProxyRequestEntity(URI proxyUri, RequestEntity<T> original) {
      super(original.getBody(), original.getHeaders(), original.getMethod(), proxyUri, original.getType());
    }

    static <T> ProxyRequestEntity<T> proxy(URI proxyUri, RequestEntity<T> original) {
      return new ProxyRequestEntity<>(proxyUri, original);
    }
  }

}
