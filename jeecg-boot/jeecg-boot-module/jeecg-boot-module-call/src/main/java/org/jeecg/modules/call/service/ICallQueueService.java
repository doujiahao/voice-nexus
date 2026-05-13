package org.jeecg.modules.call.service;

public interface ICallQueueService {

    void enqueue(String skillGroupId, String sessionId);

    String dequeue(String skillGroupId);

    int getQueueSize(String skillGroupId);

    int getPosition(String skillGroupId, String sessionId);

    void remove(String skillGroupId, String sessionId);
}
