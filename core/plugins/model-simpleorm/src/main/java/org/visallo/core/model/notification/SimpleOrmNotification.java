package org.visallo.core.model.notification;

import com.v5analytics.simpleorm.Field;
import com.v5analytics.simpleorm.Id;
import org.json.JSONObject;

public class SimpleOrmNotification {
    @Id
    protected String id;

    @Field
    protected String title;

    @Field
    protected String message;

    @Field
    protected String actionEvent;

    @Field
    protected JSONObject actionPayload;
}
