package org.visallo.vertexium.model.thumbnails;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.visallo.core.model.thumbnails.ThumbnailRepositoryTestBase;

@RunWith(MockitoJUnitRunner.class)
public class VertexiumThumbnailRepositoryTest extends ThumbnailRepositoryTestBase {
    private VertexiumThumbnailRepository artifactThumbnailRepository;

    @Before
    public void before() throws Exception {
        super.before();
        artifactThumbnailRepository = new VertexiumThumbnailRepository(
                getOntologyRepository(),
                getGraphAuthorizationRepository(),
                getAuthorizationRepository(),
                getGraph()
        );
    }

    @Override
    public VertexiumThumbnailRepository getThumbnailRepository() {
        return artifactThumbnailRepository;
    }
}
