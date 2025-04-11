package com.walmart.move.nim.receiving.core.config.app;

import com.walmart.atlas.argus.metrics.http.client.restTemplate.RestTemplateRequestInterceptorForMetrics;
import com.walmart.atlas.argus.metrics.utils.ArgusMetricsCaptor;
import com.walmart.platform.txn.springboot.interceptor.SpringBootClientTxnMarkingInterceptor;
import com.walmart.platform.txnmarking.impl.TransactionImpl;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
@Profile("!test")
public class RestTemplateConfiguration {

  private static final int DEFAULT_KEEP_ALIVE_TIME_MILLIS = 3 * 1000;
  private static final int CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS = 15;
  private static final Logger log = LoggerFactory.getLogger(RestTemplateConfiguration.class);

  @Value("${http.client.connection.timeout.miliseconds:-1}")
  private int connectionTimeOut;

  @Value("${http.client.socket.timeout.miliseconds:-1}")
  private int socketTimeOut;

  @Value("${http.client.connectionRequest.timeout.miliseconds:-1}")
  private int connectionRequestTimeOut;

  @Autowired
  private ArgusMetricsCaptor metricsCaptor;

  @Bean
  @ConditionalOnExpression(
      "${enabled.http.basicconnectionmanager:false} || ${http.client.custom.connectionpooling.enabled:false}")
  public SSLConnectionSocketFactory sslConnectionSocketFactory()
      throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
    TrustStrategy acceptingTrustStrategy = (x509Certificates, s) -> true;
    SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
    return new SSLConnectionSocketFactory(sslContext);
  }

  @Bean
  @ConditionalOnExpression(
      "!${enabled.http.basicconnectionmanager:false} && ${http.client.custom.connectionpooling.enabled:false}")
  public PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(
      @Value("${http.client.maxTotalConnections:20}") int maxTotalConnections,
      @Value("${http.client.defaultMaxPerRouteConnections:2}") int defaultMaxPerRouteConnections,
      SSLConnectionSocketFactory sslSF) {
    Registry<ConnectionSocketFactory> registry =
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslSF)
            .build();
    PoolingHttpClientConnectionManager poolingHttpClientConnectionManager =
        new PoolingHttpClientConnectionManager(registry);
    poolingHttpClientConnectionManager.setMaxTotal(maxTotalConnections);
    poolingHttpClientConnectionManager.setDefaultMaxPerRoute(defaultMaxPerRouteConnections);
    return poolingHttpClientConnectionManager;
  }

  @Bean
  @ConditionalOnExpression(
      "${enabled.http.basicconnectionmanager:false} || ${http.client.custom.connectionpooling.enabled:false}")
  public RequestConfig requestConfig() {
    RequestConfig result =
        RequestConfig.custom()
            .setConnectionRequestTimeout(connectionRequestTimeOut)
            .setConnectTimeout(connectionTimeOut)
            .setSocketTimeout(socketTimeOut)
            .build();
    return result;
  }

  @Bean
  @ConditionalOnExpression(
      "!${enabled.http.basicconnectionmanager:false} && ${http.client.custom.connectionpooling.enabled:false}")
  public ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
    return (response, context) -> {
      HeaderElementIterator it =
          new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
      while (it.hasNext()) {
        HeaderElement he = it.nextElement();
        String param = he.getName();
        String value = he.getValue();

        if (value != null && param.equalsIgnoreCase("timeout")) {
          return Long.parseLong(value) * 1000;
        }
      }
      return DEFAULT_KEEP_ALIVE_TIME_MILLIS;
    };
  }

  @Bean
  @ConditionalOnExpression(
      "!${enabled.http.basicconnectionmanager:false} && ${http.client.custom.connectionpooling.enabled:false}")
  public Runnable idleConnectionMonitor(
      final PoolingHttpClientConnectionManager poolingHttpClientConnectionManager) {
    return new Runnable() {
      @Override
      @Scheduled(fixedDelayString = "${http.client.custom.connectionpooling.cleanup.delay:30000}")
      public void run() {
        try {
          if (poolingHttpClientConnectionManager != null) {
            // log.info("IdleConnectionMonitor - Closing expired and idle connections...");
            poolingHttpClientConnectionManager.closeExpiredConnections();
            poolingHttpClientConnectionManager.closeIdleConnections(
                CLOSE_IDLE_CONNECTION_WAIT_TIME_SECS, TimeUnit.SECONDS);
          } else {
            log.error("IdleConnectionMonitor - Http Client Connection manager is not initialised");
          }
        } catch (Exception e) {
          log.error("IdleConnectionMonitor - Exception occurred. msg={}, e={}", e.getMessage(), e);
        }
      }
    };
  }

  @Bean
  @ConditionalOnExpression(
      "!${enabled.http.basicconnectionmanager:false} && ${http.client.custom.connectionpooling.enabled:false}")
  public CloseableHttpClient poolingHttpClient(
      PoolingHttpClientConnectionManager poolingHttpClientConnectionManager,
      RequestConfig requestConfig,
      SSLConnectionSocketFactory sslSF) {
    CloseableHttpClient httpClient =
        HttpClientBuilder.create()
            .setConnectionManager(poolingHttpClientConnectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setKeepAliveStrategy(connectionKeepAliveStrategy())
            .setSSLSocketFactory(sslSF)
            .build();
    return httpClient;
  }

  @Bean
  @ConditionalOnExpression(
      "${enabled.http.basicconnectionmanager:false} && !${http.client.custom.connectionpooling.enabled:false}")
  public CloseableHttpClient basicHttpClient(
      RequestConfig requestConfig, SSLConnectionSocketFactory sslSF) {
    CloseableHttpClient httpClient =
        HttpClientBuilder.create()
            .setDefaultRequestConfig(requestConfig)
            .setSSLSocketFactory(sslSF)
            .build();
    return httpClient;
  }

  @Bean("connectionPoolingRestTemplate")
  @ConditionalOnExpression(
      "${enabled.http.basicconnectionmanager:false} || ${http.client.custom.connectionpooling.enabled:false}")
  public RestTemplate restTemplate(HttpClient httpClient) {
    HttpComponentsClientHttpRequestFactory requestFactory =
        new HttpComponentsClientHttpRequestFactory();
    requestFactory.setHttpClient(httpClient);
    RestTemplate restTemplate = new RestTemplate(requestFactory);
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setSupportedMediaTypes(Collections.singletonList(MediaType.TEXT_HTML));
    restTemplate.getMessageConverters().add(converter);
    List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
    if (CollectionUtils.isEmpty(interceptors)) {
      interceptors = new ArrayList<>();
    }
    interceptors.add(new RequestInterceptorForSvcName());
    interceptors.add(new SpringBootClientTxnMarkingInterceptor(TransactionImpl.NULL_TRANSACTION));
    interceptors.add(new RestTemplateRequestInterceptorForMetrics(metricsCaptor));
    restTemplate.setInterceptors(interceptors);
    return restTemplate;
  }

  @Bean("defaultRestTemplate")
  @ConditionalOnExpression(
      "!${enabled.http.basicconnectionmanager:false} && !${http.client.custom.connectionpooling.enabled:false}")
  public RestTemplate restTemplateWithBuilder() {
    RestTemplate restTemplate =
        new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(connectionTimeOut))
            .setReadTimeout(Duration.ofMillis(socketTimeOut))
            .build();
    List<ClientHttpRequestInterceptor> interceptors = restTemplate.getInterceptors();
    if (CollectionUtils.isEmpty(interceptors)) {
      interceptors = new ArrayList<>();
    }
    interceptors.add(new RequestInterceptorForSvcName());
    interceptors.add(new SpringBootClientTxnMarkingInterceptor(TransactionImpl.NULL_TRANSACTION));
    interceptors.add(new RestTemplateRequestInterceptorForMetrics(metricsCaptor));
    restTemplate.setInterceptors(interceptors);
    return restTemplate;
  }
}
