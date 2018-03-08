package org.visallo.core.model.thumbnails;

import com.v5analytics.simpleorm.Entity;
import com.v5analytics.simpleorm.Field;
import com.v5analytics.simpleorm.Id;
import org.visallo.core.exception.VisalloException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity(tableName = "artifactThumbnail")
public class SimpleOrmThumbnail {
    @Id
    private String id;

    @Field
    private byte[] data;

    @Field
    private String format;

    // Used by SimpleOrm to create instance
    @SuppressWarnings("UnusedDeclaration")
    protected SimpleOrmThumbnail() {
    }

    public SimpleOrmThumbnail(Thumbnail thumbnail) {
        this.id = thumbnail.getId();
        this.data = thumbnail.getData();
        this.format = thumbnail.getFormat();
    }

    public static Thumbnail toThumbnail(SimpleOrmThumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }
        Matcher m = Pattern.compile("(.*):([^:]*?):[0]*([0-9]*?):[0]*([0-9]*?)").matcher(thumbnail.id);
        if (!m.matches()) {
            throw new VisalloException("Could not parse thumbnail id: " + thumbnail.id);
        }
        String vertexId = m.group(1);
        String type = m.group(2);
        int width = Integer.parseInt(m.group(3));
        int height = Integer.parseInt(m.group(4));
        return new Thumbnail(vertexId, type, thumbnail.data, thumbnail.format, width, height);
    }
}
