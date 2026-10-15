// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

/** 不可信上传规范化、视频校验与持久文件管理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MediaService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final Path root;
  final String probe;

  public MediaService(
      Store db,
      AccessService access,
      Clock clock,
      @Value("${signagehub.media-dir}") String dir,
      @Value("${signagehub.ffprobe}") String probe) {
    this.db = db;
    this.access = access;
    this.clock = clock;
    this.root = Path.of(dir).toAbsolutePath().normalize();
    this.probe = probe;
  }

  /** SHA-256指纹只用于完整性，不当作授权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** 图片解析前限制尺寸，重编码移除元数据及附带载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static byte[] image(byte[] raw) {
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
      var it = ImageIO.getImageReaders(input);
      Rules.check(it.hasNext(), "MEDIA_INVALID");
      var reader = it.next();
      try {
        reader.setInput(input, true, true);
        String f = reader.getFormatName().toLowerCase(Locale.ROOT);
        Rules.check(Set.of("png", "jpeg", "jpg").contains(f), "MEDIA_UNSUPPORTED");
        int w = reader.getWidth(0), h = reader.getHeight(0);
        Rules.check(
            w > 0 && h > 0 && w <= 4096 && h <= 4096 && (long) w * h <= 16777216,
            "MEDIA_DIMENSIONS");
        var img = reader.read(0);
        var out = new ByteArrayOutputStream();
        Rules.check(ImageIO.write(img, "png", out), "MEDIA_INVALID");
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "MEDIA_INVALID");
    }
  }

  /** 文件只保存于配置卷，随机路径且回滚删除未提交文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public MediaAsset upload(Long departmentId, String name, MultipartFile file) {
    access.library(departmentId, "media");
    var dept = db.lock(Department.class, departmentId);
    Rules.check(dept.enabled, "DEPARTMENT_DISABLED");
    Rules.check(!file.isEmpty() && file.getSize() <= 64L * 1024 * 1024, "FILE_TOO_LARGE");
    String label = Rules.text(name, 160, true);
    Path tmp = null;
    Path dest = null;
    try {
      Files.createDirectories(root);
      tmp = Files.createTempFile(root, "upload-", ".tmp");
      file.transferTo(tmp);
      byte[] raw = Files.readAllBytes(tmp);
      var a = new MediaAsset();
      a.departmentId = departmentId;
      a.name = label;
      a.createdAt = clock.instant();
      boolean mp4 =
          raw.length >= 16 && raw[4] == 'f' && raw[5] == 't' && raw[6] == 'y' && raw[7] == 'p';
      if (mp4) {
        var info = probe(tmp);
        a.kind = "VIDEO";
        a.mime = "video/mp4";
        a.width = info.width;
        a.height = info.height;
        a.duration = info.seconds;
      } else {
        raw = image(raw);
        Files.write(tmp, raw);
        var img = ImageIO.read(new ByteArrayInputStream(raw));
        a.kind = "IMAGE";
        a.mime = "image/png";
        a.width = img.getWidth();
        a.height = img.getHeight();
      }
      a.bytes = raw.length;
      Rules.check(a.bytes <= 64L * 1024 * 1024, "FILE_TOO_LARGE");
      a.sha256 = hash(raw);
      a.blobName = UUID.randomUUID() + (mp4 ? ".mp4" : ".png");
      long total =
          db.query(MediaAsset.class, "from MediaAsset where departmentId=?1", departmentId).stream()
              .mapToLong(x -> x.bytes)
              .sum();
      Rules.check(total + a.bytes <= 512L * 1024 * 1024, "MEDIA_QUOTA");
      dest = root.resolve(a.blobName);
      Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
      tmp = null;
      Path completed = dest;
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED)
                try {
                  Files.deleteIfExists(completed);
                } catch (IOException ignored) {
                }
            }
          });
      db.save(a);
      db.flush();
      access.audit("MEDIA_UPLOAD", a.id, departmentId);
      return a;
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "MEDIA_INVALID");
    } finally {
      if (tmp != null)
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
        }
    }
  }

  record VideoInfo(int width, int height, double seconds) {}

  private VideoInfo probe(Path file) throws Exception {
    var process =
        new ProcessBuilder(
                probe,
                "-v",
                "error",
                "-show_entries",
                "format=duration:stream=codec_type,codec_name,width,height",
                "-of",
                "json",
                file.toString())
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    // Output is strictly bounded by the selected metadata; no uploaded filename is used as a
    // command.
    if (!process.waitFor(15, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new Problem(400, "MEDIA_INVALID");
    }
    Rules.check(process.exitValue() == 0, "MEDIA_INVALID");
    byte[] output = process.getInputStream().readNBytes(32769);
    Rules.check(output.length <= 32768, "MEDIA_INVALID");
    var data = new JsonMapper().readTree(output);
    int width = 0, height = 0, videos = 0;
    for (var stream : data.path("streams")) {
      String type = stream.path("codec_type").asString();
      String codec = stream.path("codec_name").asString();
      if (type.equals("video")) {
        videos++;
        Rules.check(codec.equals("h264"), "MEDIA_CODEC");
        width = stream.path("width").asInt();
        height = stream.path("height").asInt();
      } else Rules.check(type.equals("audio") && codec.equals("aac"), "MEDIA_CODEC");
    }
    double seconds = data.path("format").path("duration").asDouble();
    Rules.check(
        videos == 1
            && width > 0
            && height > 0
            && width <= 1920
            && height <= 1920
            && Double.isFinite(seconds)
            && seconds > 0
            && seconds <= 300,
        "MEDIA_DIMENSIONS");
    return new VideoInfo(width, height, seconds);
  }

  /** 读取服务器已登记的不可变文件；禁止客户端指定路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Path path(MediaAsset asset) {
    Rules.check(asset.blobName.matches("[a-f0-9-]{36}\\.(png|mp4)"), "MEDIA_INVALID");
    Path path = root.resolve(asset.blobName).normalize();
    Rules.check(path.startsWith(root) && Files.isRegularFile(path), "MEDIA_MISSING");
    return path;
  }

  /** 获取媒体时执行账号与组织范围检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public MediaAsset readable(Long id) {
    var a = db.get(MediaAsset.class, id);
    access.library(a.departmentId, "media");
    return a;
  }

  /** 停用仅阻止新发布，既有节目快照保留；未引用素材可删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object edit(Long id, Map<String, Object> body) {
    var a = db.get(MediaAsset.class, id);
    access.library(a.departmentId, "media");
    db.lock(Department.class, a.departmentId);
    db.refresh(a);
    Rules.check(a.version == Rules.id(body.get("version")), "VERSION_CONFLICT");
    a.name = Rules.text(body.get("name"), 160, true);
    a.enabled = Rules.flag(body.get("enabled"));
    a.version++;
    access.audit("MEDIA_EDIT", id, a.departmentId);
    return a;
  }

  /** 已在草稿或发布中引用的文件不能删除。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(Long id) {
    var a = db.get(MediaAsset.class, id);
    access.library(a.departmentId, "media");
    db.lock(Department.class, a.departmentId);
    Rules.check(
        db.query(PlaylistItem.class, "from PlaylistItem where assetId=?1", id).isEmpty()
            && db.query(ReleaseAsset.class, "from ReleaseAsset where assetId=?1", id).isEmpty(),
        "MEDIA_IN_USE");
    Path file = path(a);
    db.delete(a);
    access.audit("MEDIA_DELETE", id, a.departmentId);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              Files.deleteIfExists(file);
            } catch (IOException ignored) {
            }
          }
        });
  }
}
