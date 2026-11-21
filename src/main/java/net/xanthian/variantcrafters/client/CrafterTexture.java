package net.xanthian.variantcrafters.client;

import java.awt.image.BufferedImage;
import java.util.Map;

public final class CrafterTexture {
    // Oak texels in the vanilla crafter sprites, with extra shading for seams and edges.
    private static final Map<Integer, Double> WOOD_SHADING = Map.ofEntries(
            Map.entry(0xFF110E08, 0.17), Map.entry(0xFF19140C, 0.25),
            Map.entry(0xFF271A10, 0.34), Map.entry(0xFF2D200C, 0.40),
            Map.entry(0xFF382116, 0.45), Map.entry(0xFF3B2B12, 0.54),
            Map.entry(0xFF4B2B18, 0.59), Map.entry(0xFF503D1E, 0.76),
            Map.entry(0xFF67502C, 1.0), Map.entry(0xFF7E6237, 1.0),
            Map.entry(0xFF967441, 1.0), Map.entry(0xFF9F844D, 1.0),
            Map.entry(0xFFAF8F55, 1.0), Map.entry(0xFFB8945F, 1.0),
            Map.entry(0xFFC29D62, 1.0), Map.entry(0xFFD6AC6D, 1.10));

    private CrafterTexture() {
    }

    public static BufferedImage compose(BufferedImage template, BufferedImage planks) {
        int width = Math.max(template.getWidth(), planks.getWidth());
        int height = Math.max(template.getHeight(), planks.getHeight());
        // Vanilla's indexed PNG palette cannot represent colours from other plank textures.
        var face = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int original = template.getRGB(x * template.getWidth() / width, y * template.getHeight() / height);
                Double shading = WOOD_SHADING.get(original);
                if (shading != null) {
                    int plank = planks.getRGB(x * planks.getWidth() / width, y * planks.getHeight() / height);
                    if ((plank >>> 24) >= 128) {
                        original = shade(plank, shading);
                    }
                }
                face.setRGB(x, y, original);
            }
        }
        return face;
    }

    private static int shade(int color, double factor) {
        int red = Math.min(255, (int) Math.round((color >> 16 & 255) * factor));
        int green = Math.min(255, (int) Math.round((color >> 8 & 255) * factor));
        int blue = Math.min(255, (int) Math.round((color & 255) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
