package org.tuoyu.auth.captcha;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("验证码图片：可解码的 160 × 60 PNG")
class CaptchaImageRendererTest {

    private final CaptchaImageRenderer renderer = new CaptchaImageRenderer();

    private static BufferedImage decode(String base64) throws IOException {
        assertThat(base64).doesNotStartWith("data:");
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    @Test
    @DisplayName("算式与字符都画成 160 × 60 的 PNG，图上有内容")
    void rendersPng() throws IOException {
        for (String text : new String[]{"7×3=?", "ab2Cd", "x"}) {
            BufferedImage image = decode(renderer.render(text));
            assertThat(image).as(text).isNotNull();
            assertThat(image.getWidth()).isEqualTo(160);
            assertThat(image.getHeight()).isEqualTo(60);
            assertThat(hasNonWhitePixel(image)).isTrue();
        }
    }

    @Test
    @DisplayName("同一文本每次画出的图片不同（颜色、旋转与干扰随机）")
    void randomized() {
        assertThat(renderer.render("ab2Cd")).isNotEqualTo(renderer.render("ab2Cd"));
    }

    @Test
    @DisplayName("文本为空时仍生成图片")
    void emptyText() throws IOException {
        assertThat(decode(renderer.render("")).getWidth()).isEqualTo(160);
        assertThat(decode(renderer.render(null)).getHeight()).isEqualTo(60);
    }

    private static boolean hasNonWhitePixel(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }

}
