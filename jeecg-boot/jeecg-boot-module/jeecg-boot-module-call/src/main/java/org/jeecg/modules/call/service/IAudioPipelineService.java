package org.jeecg.modules.call.service;

public interface IAudioPipelineService {

    /**
     * 处理来自 freeswichService 的音频帧（Protobuf 编码）
     */
    void processAudioFrame(String sessionId, byte[] frameData);
}
