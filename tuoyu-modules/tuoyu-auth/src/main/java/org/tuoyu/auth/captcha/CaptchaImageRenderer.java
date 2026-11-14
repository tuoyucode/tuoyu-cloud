package org.tuoyu.auth.captcha;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 把验证码文本画成 PNG 图片（Java2D，无图形界面的服务器上也可用）。
 * <p>
 * 每个字符随机颜色并轻微旋转，背景上加干扰线与干扰圆，增加机器识别的难度。
 * 字体用逻辑字体 {@link Font#SANS_SERIF}，任何 JDK 都能映射到可用的字体。
 *
 * @author tuoyu
 */
@Component
public class CaptchaImageRenderer {

    /**
     * 图片宽度（像素）
     */
    public static final int WIDTH = 160;

    /**
     * 图片高度（像素）
     */
    public static final int HEIGHT = 60;

    private static final int NOISE_LINES = 6;

    private static final int NOISE_CIRCLES = 12;

    /**
     * 字符最大旋转角度（弧度，约 20 度）
     */
    private static final double MAX_ROTATION = 0.35;

    private final SecureRandom random = new SecureRandom();

    /**
     * @param text 要画的文本
     * @return PNG 图片的 Base64（不带 {@code data:} 前缀）
     */
    public String render(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, WIDTH, HEIGHT);
            drawNoise(g);
            drawText(g, text == null ? "" : text);
        } finally {
            g.dispose();
        }
        return Base64.getEncoder().encodeToString(toPng(image));
    }

    private void drawNoise(Graphics2D g) {
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < NOISE_LINES; i++) {
            g.setColor(randomColor(120, 200));
            g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT), random.nextInt(WIDTH), random.nextInt(HEIGHT));
        }
        for (int i = 0; i < NOISE_CIRCLES; i++) {
            g.setColor(randomColor(150, 220));
            int size = 4 + random.nextInt(HEIGHT / 3);
            g.drawOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), size, size);
        }
    }

    private void drawText(Graphics2D g, String text) {
        if (text.isEmpty()) {
            return;
        }
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) (HEIGHT * 0.6));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int cell = WIDTH / text.length();
        int baseline = (HEIGHT - metrics.getHeight()) / 2 + metrics.getAscent();
        AffineTransform original = g.getTransform();
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int x = i * cell + Math.max(0, (cell - metrics.stringWidth(ch)) / 2);
            double angle = (random.nextDouble() * 2 - 1) * MAX_ROTATION;
            g.rotate(angle, x + metrics.stringWidth(ch) / 2.0, baseline - metrics.getAscent() / 2.0);
            g.setColor(randomColor(20, 130));
            g.drawString(ch, x, baseline);
            g.setTransform(original);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range), min + random.nextInt(range));
    }

    private static byte[] toPng(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("当前 JDK 不支持写入 PNG 图片");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("生成验证码图片失败", e);
        }
        return out.toByteArray();
    }

}
