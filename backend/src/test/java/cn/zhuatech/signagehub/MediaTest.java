// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.signagehub;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** 图片安全处理、密码和导出的独立回归。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class MediaTest {
  @Test
  void imageReencodingStripsAppendedPayload() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB), "png", out);
    byte[] raw = out.toByteArray();
    byte[] bad = Arrays.copyOf(raw, raw.length + 20);
    byte[] cleaned = MediaService.image(bad);
    assertArrayEquals(raw, cleaned);
  }

  @Test
  void htmlIsNotAnImage() {
    assertThrows(Problem.class, () -> MediaService.image("<html>script</html>".getBytes()));
  }

  @Test
  void oversizedImageRejectedBeforeDecode() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", out);
    assertEquals(
        "MEDIA_DIMENSIONS",
        assertThrows(Problem.class, () -> MediaService.image(out.toByteArray())).getMessage());
  }

  @Test
  void passwordsRespectBytesNotCharacters() {
    AdminService.validatePassword("SafeTest9Password");
    assertThrows(Problem.class, () -> AdminService.validatePassword("Aa1" + "中".repeat(25)));
    assertThrows(Problem.class, () -> AdminService.validatePassword("weakpassword"));
  }

  @Test
  void exportedSpreadsheetCellsCannotExecute() {
    assertEquals("\"'  =1+1\"", Rules.csv("  =1+1"));
    assertEquals("\"a\"\"b\"", Rules.csv("a\"b"));
  }
}
