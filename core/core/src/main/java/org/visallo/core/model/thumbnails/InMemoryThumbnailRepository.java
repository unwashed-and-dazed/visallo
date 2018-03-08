package org.visallo.core.model.thumbnails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang.StringUtils;
import org.vertexium.Vertex;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.user.User;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class InMemoryThumbnailRepository extends ThumbnailRepository {
    private Map<String, Thumbnail> thumbnails = new HashMap<>();

    @Inject
    public InMemoryThumbnailRepository(OntologyRepository ontologyRepository) {
        super(ontologyRepository);
    }

    @Override
    public Thumbnail getThumbnail(String vertexId, String thumbnailType, int width, int height, String workspaceId, User user) {
        return thumbnails.get(createId(vertexId, thumbnailType, width, height));
    }

    @Override
    public Thumbnail createThumbnail(
            Vertex vertex,
            String propertyKey,
            String thumbnailType,
            InputStream in,
            int[] boundaryDims,
            User user
    ) {
        Thumbnail thumbnail = generateThumbnail(vertex, propertyKey, thumbnailType, in, boundaryDims);
        String id = createId(vertex.getId(), thumbnailType, thumbnail);
        thumbnails.put(id, thumbnail);
        return thumbnails.get(id);
    }

    private String createId(String vertexId, String thumbnailType, Thumbnail thumbnail) {
        return createId(vertexId, thumbnailType, thumbnail.getImage().getWidth(), thumbnail.getImage().getHeight());
    }

    private String createId(String vertexId, String thumbnailType, int width, int height) {
        return vertexId
                + ":" + thumbnailType
                + ":" + StringUtils.leftPad(Integer.toString(width), 8, '0')
                + ":" + StringUtils.leftPad(Integer.toString(height), 8, '0');
    }
}
