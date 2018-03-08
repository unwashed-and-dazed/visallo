package org.visallo.vertexium.model.user;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.json.JSONObject;
import org.visallo.core.config.Configuration;
import org.visallo.core.exception.VisalloException;
import org.visallo.core.model.lock.LockRepository;
import org.visallo.core.model.user.*;
import org.visallo.core.user.SystemUser;
import org.visallo.core.user.User;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Singleton
public class InMemoryUserRepository extends UserRepository {
    private List<User> users = new ArrayList<>();

    @Inject
    public InMemoryUserRepository(
            Configuration configuration,
            UserSessionCounterRepository userSessionCounterRepository,
            LockRepository lockRepository,
            AuthorizationRepository authorizationRepository,
            PrivilegeRepository privilegeRepository
    ) {
        super(
                configuration,
                userSessionCounterRepository,
                lockRepository,
                authorizationRepository,
                privilegeRepository
        );
    }

    @Override
    public User findByUsername(final String username) {
        return Iterables.find(this.users, user -> user.getUsername().equals(username), null);
    }

    @Override
    public Iterable<User> find(int skip, int limit) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public User findById(final String userId) {
        return Iterables.find(this.users, user -> user.getUserId().equals(userId), null);
    }

    @Override
    protected User addUser(
            String username,
            String displayName,
            String emailAddress,
            String password
    ) {
        username = formatUsername(username);
        displayName = displayName.trim();
        InMemoryUser user = new InMemoryUser(
                username,
                displayName,
                emailAddress,
                null
        );
        afterNewUserAdded(user);
        users.add(user);
        return user;
    }

    @Override
    public void setPassword(User user, byte[] salt, byte[] passwordHash) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isPasswordValid(User user, String password) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void updateUser(User user, AuthorizationContext authorizationContext) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public User setCurrentWorkspace(String userId, String workspaceId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getCurrentWorkspaceId(String userId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setUiPreferences(User user, JSONObject preferences) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setDisplayName(User user, String displayName) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setEmailAddress(User user, String emailAddress) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    protected void internalDelete(User user) {

    }

    @Override
    public User findByPasswordResetToken(String token) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setPasswordResetTokenAndExpirationDate(User user, String token, Date expirationDate) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void clearPasswordResetTokenAndExpirationDate(User user) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setPropertyOnUser(User user, String propertyName, Object value) {
        if (user instanceof SystemUser) {
            throw new VisalloException("Cannot set properties on system user");
        }
        ((InMemoryUser) user).setProperty(propertyName, value);
    }
}
