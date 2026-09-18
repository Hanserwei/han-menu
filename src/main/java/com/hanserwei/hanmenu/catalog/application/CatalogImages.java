package com.hanserwei.hanmenu.catalog.application;

import com.hanserwei.hanmenu.catalog.domain.CatalogException;
import com.hanserwei.hanmenu.catalog.domain.CatalogImage;
import com.hanserwei.hanmenu.catalog.domain.CatalogRepository;
import com.hanserwei.hanmenu.catalog.domain.ImageStorage;
import com.hanserwei.hanmenu.identity.api.StaffAuthorization;
import com.hanserwei.hanmenu.identity.api.StaffIdentity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 图片用例在数据库事务外调用对象存储，元数据提交失败时补偿删除本次独立对象. */
@Service
public class CatalogImages {
  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogImages.class);
  private final CatalogRepository repository;
  private final ImageStorage storage;
  private final StaffAuthorization authorization;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final String prefix;

  /** 组合文件端口与事务模板，避免把外部上传放进长数据库事务. */
  public CatalogImages(
      CatalogRepository repository,
      ImageStorage storage,
      StaffAuthorization authorization,
      TransactionTemplate transactions,
      Clock clock,
      @Value("${han-menu.catalog.storage.key-prefix}") String prefix) {
    if (!prefix.startsWith("catalog/") && !prefix.startsWith("han-menu-test/")) {
      throw new IllegalArgumentException("图片前缀不合法");
    }
    this.repository = repository;
    this.storage = storage;
    this.authorization = authorization;
    this.transactions = transactions;
    this.clock = clock;
    this.prefix = prefix;
  }

  /** 上传 PNG/JPEG；不信任文件名或 Content-Type，验证真实格式和解码像素规模. */
  public ImageView upload(StaffIdentity actor, byte[] bytes) {
    authorization.requireAdministrator(actor);
    String type = inspect(bytes);
    UUID id = UUID.randomUUID();
    String key = prefix + id + (type.equals("image/png") ? ".png" : ".jpg");
    var image = new CatalogImage(id, key, type, bytes.length, clock.instant());
    try {
      storage.put(key, bytes, type);
      transactions.executeWithoutResult(status -> repository.addImage(image));
    } catch (RuntimeException failure) {
      try {
        storage.delete(key);
      } catch (RuntimeException ignored) {
        LOGGER.warn("image_cleanup_required imageId={}", id);
      }
      throw failure;
    }
    return new ImageView(id, type, bytes.length, image.createdAt());
  }

  /** 公开图片下载只允许已被公开可售商品引用的图片，其他图片仅管理员可取链接. */
  public ImageLink link(UUID id, StaffIdentity actor, boolean publicAccess) {
    CatalogImage image =
        transactions.execute(
            status -> {
              if (!publicAccess) {
                authorization.requireAdministrator(actor);
              } else if (!repository.imagePublished(id)) {
                throw new CatalogException(CatalogException.Reason.NOT_FOUND, "图片尚未公开");
              }
              return repository
                  .image(id)
                  .orElseThrow(
                      () -> new CatalogException(CatalogException.Reason.NOT_FOUND, "图片不存在"));
            });
    return new ImageLink(storage.downloadUrl(image.objectKey()), clock.instant().plusSeconds(300));
  }

  private String inspect(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > 5 * 1024 * 1024) {
      invalid();
    }
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        invalid();
      }
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("png") && !format.equals("jpeg")) {
          invalid();
        }
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 4096
            || height > 4096
            || (long) width * height > 16_000_000) {
          invalid();
        }
        if (reader.read(0) == null) {
          invalid();
        }
        return format.equals("png") ? "image/png" : "image/jpeg";
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw new CatalogException(CatalogException.Reason.INVALID_INPUT, "图片无法解码");
    }
  }

  private void invalid() {
    throw new CatalogException(
        CatalogException.Reason.INVALID_INPUT, "仅支持 5 MiB 内且不超过 4096 边长的有效 PNG/JPEG 图片");
  }

  /** 上传响应不带存储密钥或内部对象路径. */
  public record ImageView(UUID id, String mediaType, long size, Instant createdAt) {}

  /** 临时签名链接禁止记录到日志，过期后需重新申请. */
  public record ImageLink(URI url, Instant expiresAt) {
    @Override
    public String toString() {
      return "ImageLink[签名已隐藏]";
    }
  }
}
