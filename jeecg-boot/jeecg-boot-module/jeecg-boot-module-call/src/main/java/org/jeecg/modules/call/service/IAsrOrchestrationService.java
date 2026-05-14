package org.jeecg.modules.call.service;

public interface IAsrOrchestrationService {

    /**
     * 调用 Gateway ASR 转写，结果落库并推送前端
     */
    void transcribe(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole);
}
