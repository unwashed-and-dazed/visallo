package org.visallo.core.model.workQueue;

import org.json.JSONObject;
import org.vertexium.Graph;
import org.visallo.core.config.Configuration;
import org.visallo.core.ingest.WorkerSpout;
import org.visallo.core.model.WorkQueueNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TestWorkQueueRepository extends WorkQueueRepository {
    public List<JSONObject> broadcastJsonValues = new ArrayList<>();
    public Map<String, List<byte[]>> queues = new HashMap<>();

    public TestWorkQueueRepository(
            Graph graph,
            WorkQueueNames workQueueNames,
            Configuration configuration
    ) {
        super(graph, workQueueNames, configuration);
    }

    @Override
    protected void broadcastJson(JSONObject json) {
        broadcastJsonValues.add(json);
    }

    @Override
    public void pushOnQueue(String queueName, byte[] data, Priority priority) {
        List<byte[]> queue = queues.computeIfAbsent(queueName, k -> new ArrayList<>());
        queue.add(data);
    }

    public List<byte[]> getWorkQueue(String queueName) {
        return queues.get(queueName);
    }

    public void clearQueue() {
        broadcastJsonValues.clear();
        queues.clear();
    }

    public List<JSONObject> getBroadcastJsonValues() {
        return broadcastJsonValues;
    }

    public JSONObject getLastBroadcastedJson() {
        int size = broadcastJsonValues.size();
        return size > 0 ? broadcastJsonValues.get(size - 1) : null;
    }

    @Override
    public void flush() {

    }

    @Override
    protected void deleteQueue(String queueName) {

    }

    @Override
    public void subscribeToBroadcastMessages(BroadcastConsumer broadcastConsumer) {

    }

    @Override
    public void unsubscribeFromBroadcastMessages(BroadcastConsumer broadcastConsumer) {

    }

    @Override
    public WorkerSpout createWorkerSpout(String queueName) {
        return null;
    }
}
