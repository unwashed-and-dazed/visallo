package org.visallo.web.routes.user;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.vertexium.util.ConvertingIterable;
import org.vertexium.util.FilterIterable;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.user.UserSessionCounterRepository;
import org.visallo.core.model.workspace.Workspace;
import org.visallo.core.model.workspace.WorkspaceRepository;
import org.visallo.core.model.workspace.WorkspaceUser;
import org.visallo.core.user.User;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.clientapi.model.ClientApiUsers;
import org.visallo.webster.ParameterizedHandler;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static org.vertexium.util.IterableUtils.toList;

@Singleton
public class UserList implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(UserList.class);
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserSessionCounterRepository userSessionCounterRepository;

    @Inject
    public UserList(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            UserSessionCounterRepository userSessionCounterRepository
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.userSessionCounterRepository = userSessionCounterRepository;
    }

    @Handle
    public ClientApiUsers handle(
            User user,
            @Optional(name = "q") String query,
            @Optional(name = "workspaceId") String workspaceId,
            @Optional(name = "userIds[]") String[] userIds,
            @Optional(name = "online") Boolean online,
            @Optional(name = "skip", defaultValue = "0") int skip,
            @Optional(name = "limit", defaultValue = "100") int limit
    ) throws Exception {
        List<User> users;
        if (userIds != null) {
            checkArgument(query == null, "Cannot use userIds[] and q at the same time");
            checkArgument(workspaceId == null, "Cannot use userIds[] and workspaceId at the same time");
            users = new ArrayList<>();
            for (String userId : userIds) {
                User u = userRepository.findById(userId);
                if (u == null) {
                    LOGGER.error("User " + userId + " not found");
                    continue;
                }
                users.add(u);
            }
        } else if (online != null && online) {
            users = toList(userRepository.find(skip, limit));
            users.removeIf(u -> userSessionCounterRepository.getSessionCount(u.getUserId()) < 1);
        } else {
            users = toList(userRepository.find(query));

            if (workspaceId != null) {
                users = toList(getUsersWithWorkspaceAccess(workspaceId, users, user));
            }
        }

        Iterable<String> workspaceIds = getCurrentWorkspaceIds(users);
        Map<String, String> workspaceNames = getWorkspaceNames(workspaceIds, user);

        return userRepository.toClientApi(users, workspaceNames);
    }

    private Map<String, String> getWorkspaceNames(Iterable<String> workspaceIds, User user) {
        Map<String, String> result = new HashMap<>();
        for (Workspace workspace : workspaceRepository.findByIds(workspaceIds, user)) {
            if (workspace != null) {
                result.put(workspace.getWorkspaceId(), workspace.getDisplayTitle());
            }
        }
        return result;
    }

    private Iterable<String> getCurrentWorkspaceIds(Iterable<User> users) {
        return new ConvertingIterable<User, String>(users) {
            @Override
            protected String convert(User user) {
                return user.getCurrentWorkspaceId();
            }
        };
    }

    private Iterable<User> getUsersWithWorkspaceAccess(String workspaceId, final Iterable<User> users, User user) {
        final List<WorkspaceUser> usersWithAccess = workspaceRepository.findUsersWithAccess(workspaceId, user);
        return new FilterIterable<User>(users) {
            @Override
            protected boolean isIncluded(User u) {
                return contains(usersWithAccess, u);
            }

            private boolean contains(List<WorkspaceUser> usersWithAccess, User u) {
                for (WorkspaceUser userWithAccess : usersWithAccess) {
                    if (userWithAccess.getUserId().equals(u.getUserId())) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
