import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the Shulkerpopper textures: a slate, hopper-metal shell with soft glowing details that
 * show the direction items travel. Chevrons on the sides flow towards the output (the base), an
 * intake ring with inward arrows marks the top, and an emblem shows the filter mode (cyan: item
 * type, violet: exact item). Run from the project root:
 *
 *   java tools/PopperTextures.java
 */
public class PopperTextures {

    static final String DIR = "resourcepack/assets/shulkerporter/textures/block/";

    static final int[] BODY = {0xFF282E37, 0xFF2A313B, 0xFF2E3540, 0xFF323A45};
    static final int TRIM = 0xFF4C6E78;
    static final int TRIM_LIGHT = 0xFF6A949E;

    /** Glow colours: outer, main, core. */
    static final int[] CYAN = {0xFF4AA7B2, 0xFF7ADCE2, 0xFFD2FAFA};
    static final int[] VIOLET = {0xFF8A6FD6, 0xFFB89CFF, 0xFFEDE4FF};

    static final double[] PULSE = {0.75, 0.8, 0.88, 0.95, 1.0, 0.95, 0.88, 0.8};

    /** One chevron pointing down, towards the output. Repeats every 6 rows. */
    static final String[] CHEVRON = {
            "##....##",
            ".##..##.",
            "..####..",
            "...##...",
    };
    static final int CHEVRON_PERIOD = 6;

    /** Ring around the top face with arrows pointing in: items enter here. */
    static final String[] INTAKE = {
            "................",
            ".+++++.##.+++++.",
            ".+....####....+.",
            ".+.....##.....+.",
            ".+............+.",
            ".+............+.",
            "..#..........#..",
            ".###........###.",
            ".###........###.",
            "..#..........#..",
            ".+............+.",
            ".+............+.",
            ".+.....##.....+.",
            ".+....####....+.",
            ".+++++.##.+++++.",
            "................",
    };

    static final String[] EMBLEM_TYPE = {
            "...##...",
            "..#..#..",
            ".#....#.",
            "#......#",
            "#......#",
            ".#....#.",
            "..#..#..",
            "...##...",
    };

    static final String[] EMBLEM_EXACT = {
            "...##...",
            "..#oo#..",
            ".#.##.#.",
            "#o#oo#o#",
            "#o#oo#o#",
            ".#.##.#.",
            "..#oo#..",
            "...##...",
    };

    public static void main(String[] args) throws IOException {
        new File(DIR).mkdirs();
        Random random = new Random(26_3_2);

        BufferedImage top = body(random);
        border(top, 0, 15);
        write(top, "popper_top");

        BufferedImage lidSide = body(random);
        border(lidSide, 0, 11);
        write(lidSide, "popper_lid_side");

        BufferedImage baseSide = body(random);
        border(baseSide, 0, 7);
        write(baseSide, "popper_base_side");

        BufferedImage bottom = body(random);
        border(bottom, 0, 15);
        write(bottom, "popper_bottom");

        flow();
        pulsing("popper_band", 0, frame -> {
            // Small arrows pointing down along the visible strip of the base.
            for (int x : new int[] {2, 7, 12}) {
                for (int dx = 0; dx < 3; dx++) {
                    frame.setRGB(x + dx, 5, CYAN[1]);
                }
                frame.setRGB(x + 1, 6, CYAN[2]);
            }
        });
        pulsing("popper_intake_type", 0, frame -> paint(frame, INTAKE, 0, 0, CYAN));
        pulsing("popper_intake_exact", 0, frame -> paint(frame, INTAKE, 0, 0, VIOLET));
        pulsing("popper_emblem_type", 4, frame -> paint(frame, EMBLEM_TYPE, 4, 4, CYAN));
        pulsing("popper_emblem_exact", 4, frame -> paint(frame, EMBLEM_EXACT, 4, 4, VIOLET));
        System.out.println("Wrote textures to " + DIR);
    }

    /** Chevrons scrolling down over the 16 x 12 lid side, fading in from the top. */
    static void flow() throws IOException {
        BufferedImage strip = new BufferedImage(16, 16 * CHEVRON_PERIOD, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < CHEVRON_PERIOD; f++) {
            for (int start = -CHEVRON_PERIOD; start < 12; start += CHEVRON_PERIOD) {
                for (int row = 0; row < CHEVRON.length; row++) {
                    int y = start + f + row;
                    if (y < 1 || y > 10) {
                        continue;
                    }
                    double brightness = 0.45 + 0.55 * (y - 1) / 9.0;
                    for (int col = 0; col < CHEVRON[row].length(); col++) {
                        if (CHEVRON[row].charAt(col) == '#') {
                            strip.setRGB(4 + col, f * 16 + y, scale(CYAN[1], brightness));
                        }
                    }
                }
            }
        }
        write(strip, "popper_flow");
        meta("popper_flow", 3, false);
    }

    interface Painter {
        void paint(BufferedImage frame);
    }

    static void pulsing(String name, int phase, Painter painter) throws IOException {
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
        meta(name, 5, true);
    }

    static void meta(String name, int frametime, boolean interpolate) throws IOException {
        Files.writeString(new File(DIR + name + ".png.mcmeta").toPath(),
                "{\n  \"animation\": {\n    \"frametime\": " + frametime + ",\n    \"interpolate\": " + interpolate + "\n  }\n}\n");
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

    static void paint(BufferedImage image, String[] pattern, int left, int top, int[] palette) {
        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                int color = switch (pattern[row].charAt(col)) {
                    case '+' -> palette[0];
                    case '#' -> palette[1];
                    case 'o' -> palette[2];
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
