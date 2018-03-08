package org.visallo.core.model.longRunningProcess;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONObject;
import org.vertexium.ElementType;

public class ReindexLongRunningProcessQueueItem extends LongRunningProcessQueueItemBase {
    public static final int DEFAULT_BATCH_SIZE = 100;
    private final ElementType elementType;
    private final int batchSize;
    private final String startId;
    private final String endId;

    public ReindexLongRunningProcessQueueItem(
            @JsonProperty("elementType") ElementType elementType,
            @JsonProperty("batchSize") Integer batchSize,
            @JsonProperty("startId") String startId,
            @JsonProperty("endId") String endId
    ) {
        this.elementType = elementType;
        this.batchSize = batchSize == null ? DEFAULT_BATCH_SIZE : batchSize;
        this.startId = startId;
        this.endId = endId;
    }

    public static boolean isHandled(JSONObject jsonObject) {
        return isA(jsonObject, ReindexLongRunningProcessQueueItem.class);
    }

    public ElementType getElementType() {
        return elementType;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public String getStartId() {
        return startId;
    }

    public String getEndId() {
        return endId;
    }
}

