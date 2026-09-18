package com.hanserwei.hanmenu.catalog.infrastructure;

import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import com.hanserwei.hanmenu.catalog.domain.ImageStorage;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** RustFS 的 S3 SDK 适配器，使用私有桶和短期签名读取，外部错误不泄露凭证. */
final class S3ImageStorage implements ImageStorage {
  private final S3Client client;
  private final S3Presigner presigner;
  private final String bucket;

  S3ImageStorage(S3Client client, S3Presigner presigner, String bucket) {
    this.client = client;
    this.presigner = presigner;
    this.bucket = bucket;
  }

  @Override
  public void put(String key, byte[] bytes, String mediaType) {
    try {
      client.putObject(
          request -> request.bucket(bucket).key(key).contentType(mediaType),
          RequestBody.fromBytes(bytes));
    } catch (RuntimeException exception) {
      throw unavailable();
    }
  }

  @Override
  public void delete(String key) {
    try {
      client.deleteObject(request -> request.bucket(bucket).key(key));
    } catch (RuntimeException exception) {
      throw unavailable();
    }
  }

  @Override
  public URI downloadUrl(String key) {
    try {
      return presigner
          .presignGetObject(
              request ->
                  request
                      .signatureDuration(Duration.ofMinutes(5))
                      .getObjectRequest(object -> object.bucket(bucket).key(key)))
          .url()
          .toURI();
    } catch (Exception exception) {
      throw unavailable();
    }
  }

  private CatalogException unavailable() {
    return new CatalogException(CatalogException.Reason.UNAVAILABLE, "图片存储暂不可用");
  }

  /** 只在适配层创建 SDK 对象，客户端和签名器由 Spring 在关闭时释放. */
  @Configuration(proxyBeanMethods = false)
  static class ConfigurationBeans {
    @Bean
    S3Client catalogS3Client(
        @Value("${han-menu.catalog.storage.endpoint}") URI endpoint,
        @Value("${han-menu.catalog.storage.region}") String region,
        @Value("${han-menu.catalog.storage.access-key}") String accessKey,
        @Value("${han-menu.catalog.storage.secret-key}") String secretKey) {
      return S3Client.builder()
          .endpointOverride(endpoint)
          .region(Region.of(region))
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
          .httpClientBuilder(
              UrlConnectionHttpClient.builder()
                  .connectionTimeout(Duration.ofSeconds(2))
                  .socketTimeout(Duration.ofSeconds(5)))
          .overrideConfiguration(
              ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(10)).build())
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .build();
    }

    @Bean
    S3Presigner catalogS3Presigner(
        @Value("${han-menu.catalog.storage.public-endpoint}") URI endpoint,
        @Value("${han-menu.catalog.storage.region}") String region,
        @Value("${han-menu.catalog.storage.access-key}") String accessKey,
        @Value("${han-menu.catalog.storage.secret-key}") String secretKey) {
      return S3Presigner.builder()
          .endpointOverride(endpoint)
          .region(Region.of(region))
          .credentialsProvider(
              StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
          .build();
    }

    @Bean
    ImageStorage catalogImageStorage(
        S3Client client,
        S3Presigner presigner,
        @Value("${han-menu.catalog.storage.bucket}") String bucket) {
      return new S3ImageStorage(client, presigner, bucket);
    }
  }
}
