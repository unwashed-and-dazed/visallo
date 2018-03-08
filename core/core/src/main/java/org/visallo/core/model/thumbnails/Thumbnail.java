package org.visallo.core.model.thumbnails;

import org.apache.commons.lang.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class Thumbnail {
    private String id;
    private byte[] data;
    private String format;

    @SuppressWarnings("UnusedDeclaration")
    protected Thumbnail() {
    }

    public Thumbnail(
            String vertexId,
            String type,
            byte[] data,
            String format,
            int width, int height
    ) {
        this.id = createId(vertexId, type, width, height);
        this.data = data;
        this.format = format;
    }

    public static String createId(String vertexId, String type, int width, int height) {
        return vertexId
                + ":" + type
                + ":" + StringUtils.leftPad(Integer.toString(width), 8, '0')
                + ":" + StringUtils.leftPad(Integer.toString(height), 8, '0');
    }

    public String getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }

    public String getFormat() {
        return format;
    }

    public BufferedImage getImage() {
        try {
            byte[] data = getData();
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new RuntimeException("Could not load image", e);
        }
    }
}
