package org.visallo.vertexium.model.thumbnails;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.vertexium.*;
import org.vertexium.property.StreamingPropertyValue;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.ontology.OntologyRepository;
import org.visallo.core.model.properties.types.PropertyMetadata;
import org.visallo.core.model.thumbnails.Thumbnail;
import org.visallo.core.model.thumbnails.ThumbnailOntology;
import org.visallo.core.model.thumbnails.ThumbnailRepository;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.core.model.user.GraphAuthorizationRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.VisibilityJson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkNotNull;

@Singleton
public class VertexiumThumbnailRepository extends ThumbnailRepository {
    private static final String VISIBILITY_STRING = "thumbnail";
    private AuthorizationRepository authorizationRepository;
    private Graph graph;

    @Inject
    public VertexiumThumbnailRepository(
            OntologyRepository ontologyRepository,
            GraphAuthorizationRepository graphAuthorizationRepository,
            AuthorizationRepository authorizationRepository,
            Graph graph
    ) {
        super(ontologyRepository);
        this.authorizationRepository = authorizationRepository;
        this.graph = graph;
        graphAuthorizationRepository.addAuthorizationToGraph(VISIBILITY_STRING);
    }

    @Override
    public Thumbnail getThumbnail(String vertexId, String thumbnailType, int width, int height, String workspaceId, User user) {
        try {
            Authorizations authorizations = getAuthorizations(user, workspaceId);
            Vertex vertex = graph.getVertex(vertexId, authorizations);
            checkNotNull(vertex, "Could not find vertex: " + vertexId);
            Property thumbnailProperty = ThumbnailOntology.THUMBNAIL.getProperty(vertex, createId(thumbnailType, width, height));
            if (thumbnailProperty == null) {
                return null;
            }
            String format = ThumbnailOntology.FORMAT.getMetadataValue(thumbnailProperty);
            byte[] data = IOUtils.toByteArray(((StreamingPropertyValue) thumbnailProperty.getValue()).getInputStream());
            return new Thumbnail(vertexId, thumbnailType, data, format, width, height);
        } catch (IOException ex) {
            throw new VisalloException("Could not get thumbnail: " + vertexId + ", " + thumbnailType, ex);
        }
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
        Authorizations authorizations = getAuthorizations(user);
        Thumbnail thumbnail = generateThumbnail(vertex, propertyKey, thumbnailType, in, boundaryDims);
        StreamingPropertyValue spv = new StreamingPropertyValue(new ByteArrayInputStream(thumbnail.getData()), byte[].class);
        String key = createId(thumbnailType, thumbnail);
        Visibility visibility = new Visibility(VISIBILITY_STRING);
        Metadata metadata = new PropertyMetadata(user, new VisibilityJson(), visibility).createMetadata();
        ThumbnailOntology.FORMAT.setMetadata(metadata, thumbnail.getFormat(), visibility);
        ThumbnailOntology.THUMBNAIL.addPropertyValue(vertex, key, spv, metadata, visibility, authorizations);
        return thumbnail;
    }

    private String createId(String thumbnailType, Thumbnail thumbnail) {
        return createId(thumbnailType, thumbnail.getImage().getWidth(), thumbnail.getImage().getHeight());
    }

    public static String createId(String thumbnailType, int width, int height) {
        return thumbnailType
                + ":" + StringUtils.leftPad(Integer.toString(width), 8, '0')
                + ":" + StringUtils.leftPad(Integer.toString(height), 8, '0');
    }

    private Authorizations getAuthorizations(User user, String... additionalAuthorizations) {
        ArrayList<String> auths = Lists.newArrayList(additionalAuthorizations);
        auths.add(VISIBILITY_STRING);
        return authorizationRepository.getGraphAuthorizations(user, auths.toArray(new String[auths.size()]));
    }
}
