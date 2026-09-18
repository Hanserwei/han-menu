package com.hanserwei.hanmenu.catalog.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 用明确不可达的本机端点验证降级边界，不靠模拟 SDK 返回值掩盖连接异常. */
class StorageAndCacheFailureTest {
  @Test
  void unavailableCacheIsMissAndWriteDoesNotFail() throws Exception {
    int port = unusedPort();
    var client =
        LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build();
    var connection =
        new LettuceConnectionFactory(new RedisStandaloneConfiguration("127.0.0.1", port), client);
    connection.afterPropertiesSet();
    connection.start();
    try {
      var cache =
          new RedisCatalogCache(new StringRedisTemplate(connection), "han-menu:test:unavailable:");
      assertThat(cache.get("key")).isEmpty();
      cache.put("key", "value");
    } finally {
      connection.destroy();
    }
  }

  @Test
  void failedStorageUploadReturnsUnavailableWithoutExposingEndpointOrCredentials()
      throws Exception {
    URI endpoint = URI.create("http://127.0.0.1:" + unusedPort());
    var credentials =
        StaticCredentialsProvider.create(AwsBasicCredentials.create("test-key", "test-secret"));
    try (var client =
            S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .httpClientBuilder(
                    UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofMillis(100)))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofMillis(300)))
                .build();
        var presigner =
            S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .build()) {
      var storage = new S3ImageStorage(client, presigner, "han-menu-test");
      assertThatThrownBy(() -> storage.put("test.png", new byte[] {1}, "image/png"))
          .isInstanceOf(CatalogException.class)
          .hasMessage("图片存储暂不可用");
    }
  }

  private int unusedPort() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
