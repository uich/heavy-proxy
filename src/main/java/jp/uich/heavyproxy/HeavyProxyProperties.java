package jp.uich.heavyproxy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "proxy")
public class HeavyProxyProperties {

  String schema;
  String host;
  int port;
}
