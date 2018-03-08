package org.visallo.core.model.thumbnails;

import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InMemoryThumbnailRepositoryTest extends ThumbnailRepositoryTestBase {
    private InMemoryThumbnailRepository artifactThumbnailRepository;

    @Override
    public void before() throws Exception {
        super.before();
        artifactThumbnailRepository = new InMemoryThumbnailRepository(
                getOntologyRepository()
        );
    }

    @Override
    public InMemoryThumbnailRepository getThumbnailRepository() {
        return artifactThumbnailRepository;
    }
}
