package org.visallo.web.routes.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Required;
import org.visallo.core.exception.VisalloResourceNotFoundException;
import org.visallo.core.model.search.SearchRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.ClientApiSearch;
import org.visallo.web.parameterProviders.ActiveWorkspaceId;

@Singleton
public class SearchGet implements ParameterizedHandler {
    private final SearchRepository searchRepository;

    @Inject
    public SearchGet(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Handle
    public ClientApiSearch handle(
            @Required(name = "id") String id,
            @ActiveWorkspaceId String workspaceId,
            User user
    ) throws Exception {
        ClientApiSearch search = this.searchRepository.getSavedSearchOnWorkspace(id, user, workspaceId);

        if (search == null) {
            throw new VisalloResourceNotFoundException("Could not find search with id: " + id);
        }

        return search;
    }
}
