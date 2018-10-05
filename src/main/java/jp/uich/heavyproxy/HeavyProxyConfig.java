package jp.uich.heavyproxy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(HeavyProxyProperties.class)
public class HeavyProxyConfig {

  @Bean
  RestTemplate restTemplate() {
    return new RestTemplateBuilder()
        .interceptors(new LoggingRequestResponseInterceptor())
        .build();
  }

}
