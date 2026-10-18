import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the Shulkerporter textures: a dark shell with teal trim and glowing, pulsing runes in
 * beacon blues. Run from the project root:
 *
 *   java tools/PorterTextures.java
 */
public class PorterTextures {

    static final String DIR = "resourcepack/assets/shulkerporter/textures/block/";

    static final int[] BODY = {0xFF0E1220, 0xFF121726, 0xFF151B2B, 0xFF181F31};
    static final int TRIM = 0xFF1F7A84;
    static final int TRIM_LIGHT = 0xFF2FA9B0;
    static final int RING = 0xFF173847;

    static final int GLOW_OUTER = 0xFF3FB8C8;
    static final int GLOW_MAIN = 0xFF6FE8EE;
    static final int GLOW_CORE = 0xFFE4FFFF;

    /** Brightness of each animation frame; the runes pulse through these. */
    static final double[] PULSE = {0.55, 0.65, 0.8, 0.95, 1.0, 0.95, 0.8, 0.65};

    static final String[][] RUNES = {
            {
                    "...oo...",
                    "..#oo#..",
                    ".#.##.#.",
                    "...##...",
                    "...##...",
                    ".######.",
                    "...##...",
                    "..#..#..",
            },
            {
                    "..####..",
                    ".#....#.",
                    "#..oo..#",
                    "#.oooo.#",
                    "#.oooo.#",
                    "#..oo..#",
                    ".#....#.",
                    "..####..",
            },
            {
                    "#......#",
                    ".#....#.",
                    "..#oo#..",
                    "...oo...",
                    "...oo...",
                    "..#oo#..",
                    ".#....#.",
                    "#......#",
            },
            {
                    "...##...",
                    "...##...",
                    "##.oo.##",
                    "..#oo#..",
                    "..#oo#..",
                    "##.oo.##",
                    "...##...",
                    "...##...",
            },
    };

    static final String[] STAR = {
            "....##....",
            "....##....",
            "...+##+...",
            "..+#oo#+..",
            "###oooo###",
            "###oooo###",
            "..+#oo#+..",
            "...+##+...",
            "....##....",
            "....##....",
    };

    public static void main(String[] args) throws IOException {
        new File(DIR).mkdirs();
        Random random = new Random(26_3);

        // Lid top: trim border, a faint ring around the star.
        BufferedImage top = body(random);
        border(top, 0, 15);
        ring(top);
        write(top, "porter_top");

        // Lid side: 16 x 12 (uv rows 0..11).
        BufferedImage lidSide = body(random);
        border(lidSide, 0, 11);
        write(lidSide, "porter_lid_side");

        // Base side: 16 x 8 (uv rows 0..7); only rows 4..7 show while the lid is closed.
        BufferedImage baseSide = body(random);
        border(baseSide, 0, 7);
        write(baseSide, "porter_base_side");

        BufferedImage bottom = body(random);
        border(bottom, 0, 15);
        write(bottom, "porter_bottom");

        // Glowing overlays, animated. Each rune starts at a different point of the pulse.
        for (int i = 0; i < RUNES.length; i++) {
            String[] rune = RUNES[i];
            animated("porter_rune_" + i, i * 2, frame -> paint(frame, rune, 4, 2));
        }
        animated("porter_star", 0, frame -> paint(frame, STAR, 3, 3));
        animated("porter_band", 4, frame -> {
            for (int x = 1; x < 15; x++) {
                if (x % 3 != 0) {
                    frame.setRGB(x, 5, GLOW_MAIN);
                }
            }
        });
        System.out.println("Wrote textures to " + DIR);
    }

    interface Painter {
        void paint(BufferedImage frame);
    }

    static void animated(String name, int phase, Painter painter) throws IOException {
        BufferedImage strip = new BufferedImage(16, 16 * PULSE.length, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < PULSE.length; f++) {
            BufferedImage frame = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            painter.paint(frame);
            double brightness = PULSE[(f + phase) % PULSE.length];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int argb = frame.getRGB(x, y);
                    if ((argb >>> 24) != 0) {
                        strip.setRGB(x, f * 16 + y, scale(argb, brightness));
                    }
                }
            }
        }
        write(strip, name);
        Files.writeString(new File(DIR + name + ".png.mcmeta").toPath(),
                "{\n  \"animation\": {\n    \"frametime\": 5,\n    \"interpolate\": true\n  }\n}\n");
    }

    static BufferedImage body(Random random) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, BODY[random.nextInt(BODY.length)]);
            }
        }
        return image;
    }

    /** 1px trim around rows top..bottom, with lighter corners. */
    static void border(BufferedImage image, int top, int bottom) {
        for (int x = 0; x < 16; x++) {
            image.setRGB(x, top, TRIM);
            image.setRGB(x, bottom, TRIM);
        }
        for (int y = top; y <= bottom; y++) {
            image.setRGB(0, y, TRIM);
            image.setRGB(15, y, TRIM);
        }
        for (int[] corner : new int[][] {{0, top}, {15, top}, {0, bottom}, {15, bottom}}) {
            image.setRGB(corner[0], corner[1], TRIM_LIGHT);
        }
    }

    static void ring(BufferedImage image) {
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d > 5.6 && d < 6.6) {
                    image.setRGB(x, y, RING);
                }
            }
        }
    }

    static void paint(BufferedImage image, String[] pattern, int left, int top) {
        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                int color = switch (pattern[row].charAt(col)) {
                    case '#' -> GLOW_MAIN;
                    case 'o' -> GLOW_CORE;
                    case '+' -> GLOW_OUTER;
                    default -> 0;
                };
                if (color != 0) {
                    image.setRGB(left + col, top + row, color);
                }
            }
        }
    }

    static int scale(int argb, double amount) {
        int r = (int) Math.min(255, ((argb >> 16) & 0xFF) * amount);
        int g = (int) Math.min(255, ((argb >> 8) & 0xFF) * amount);
        int b = (int) Math.min(255, (argb & 0xFF) * amount);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    static void write(BufferedImage image, String name) throws IOException {
        ImageIO.write(image, "png", new File(DIR + name + ".png"));
    }
}
