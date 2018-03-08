package org.visallo.web.routes.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.core.model.search.SearchRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.ClientApiSearchListResponse;

@Singleton
public class SearchList implements ParameterizedHandler {
    private final SearchRepository searchRepository;

    @Inject
    public SearchList(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Handle
    public ClientApiSearchListResponse handle(User user) throws Exception {
        return this.searchRepository.getSavedSearches(user);
    }
}
