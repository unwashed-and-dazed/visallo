package org.visallo.web.routes.search;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.json.JSONObject;
import org.visallo.core.model.search.SearchRepository;
import org.visallo.core.user.User;
import org.visallo.web.clientapi.model.ClientApiSaveSearchResponse;

@Singleton
public class SearchSave implements ParameterizedHandler {
    private final SearchRepository searchRepository;

    @Inject
    public SearchSave(SearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @Handle
    public ClientApiSaveSearchResponse handle(
            @Optional(name = "id") String id,
            @Optional(name = "name") String name,
            @Required(name = "url") String url,
            @Required(name = "parameters") JSONObject searchParameters,
            @Optional(name = "global", defaultValue = "false") boolean global,
            User user
    ) throws Exception {
        if (global) {
            id = this.searchRepository.saveGlobalSearch(id, name, url, searchParameters, user);
        } else {
            id = this.searchRepository.saveSearch(id, name, url, searchParameters, user);
        }
        ClientApiSaveSearchResponse saveSearchResponse = new ClientApiSaveSearchResponse();
        saveSearchResponse.id = id;
        return saveSearchResponse;
    }
}
