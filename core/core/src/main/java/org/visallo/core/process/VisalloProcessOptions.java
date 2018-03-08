package org.visallo.core.process;

import org.visallo.core.user.User;

public class VisalloProcessOptions {
    private final User user;

    public VisalloProcessOptions(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}
