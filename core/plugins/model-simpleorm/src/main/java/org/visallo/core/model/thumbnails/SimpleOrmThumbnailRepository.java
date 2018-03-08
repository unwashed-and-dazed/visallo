package org.visallo.core.model.thumbnails;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.v5analytics.simpleorm.SimpleOrmSession;
import org.vertexium.Vertex;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.simpleorm.SimpleOrmContextProvider;
import org.visallo.core.user.User;

import java.io.InputStream;

@Singleton
public class SimpleOrmThumbnailRepository extends ThumbnailRepository {
    private static final String VISIBILITY_STRING = "";
    private final SimpleOrmSession simpleOrmSession;
    private SimpleOrmContextProvider simpleOrmContextProvider;

    @Inject
    public SimpleOrmThumbnailRepository(
            SimpleOrmContextProvider simpleOrmContextProvider,
            OntologyRepository ontologyRepository,
            SimpleOrmSession simpleOrmSession
    ) {
        super(ontologyRepository);
        this.simpleOrmContextProvider = simpleOrmContextProvider;
        this.simpleOrmSession = simpleOrmSession;
    }

    @Override
    public Thumbnail getThumbnail(String vertexId, String thumbnailType, int width, int height, String workspaceId, User user) {
        String id = Thumbnail.createId(vertexId, thumbnailType, width, height);
        return SimpleOrmThumbnail.toThumbnail(
                simpleOrmSession.findById(SimpleOrmThumbnail.class, id, simpleOrmContextProvider.getContext(user))
        );
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
        simpleOrmSession.save(new SimpleOrmThumbnail(thumbnail), VISIBILITY_STRING, simpleOrmContextProvider.getContext(user));
        return thumbnail;
    }
}
