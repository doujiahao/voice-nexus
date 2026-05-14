package org.jeecg.modules.call.service.impl;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.MinioUtil;
import org.jeecg.modules.call.proto.AsrProto;
import org.jeecg.modules.call.service.IAudioPipelineService;
import org.jeecg.modules.call.service.IAsrOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class AudioPipelineServiceImpl implements IAudioPipelineService {

    @Autowired
    private IAsrOrchestrationService asrOrchestrationService;

    @Override
    public void processAudioFrame(String sessionId, byte[] frameData) {
        AsrProto.AudioFrame frame;
        try {
            frame = AsrProto.AudioFrame.parseFrom(frameData);
        } catch (InvalidProtocolBufferException e) {
            log.error("[AudioPipeline] Protobuf 反序列化失败: sessionId={}", sessionId, e);
            return;
        }

        String speakerId = frame.getSpeakerId();
        String speakerRole = frame.getSpeakerRole();
        String speakerName = frame.getSpeakerName();
        boolean isLast = frame.getIsLast();
        byte[] audioData = frame.getData().toByteArray();

        if (audioData.length == 0) {
            return;
        }

        String objectPath = buildObjectPath(sessionId, speakerId);
        try {
            MinioUtil.upload(new ByteArrayInputStream(audioData), objectPath);
        } catch (Exception e) {
            log.error("[AudioPipeline] MinIO 上传失败: sessionId={}, path={}", sessionId, objectPath, e);
            return;
        }

        if (isLast) {
            triggerAsr(sessionId, objectPath, speakerId, speakerName, speakerRole);
        }
    }

    private String buildObjectPath(String sessionId, String speakerId) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = UUID.randomUUID().toString().replace("-", "") + ".wav";
        return "call-audio/" + date + "/" + sessionId + "/" + speakerId + "/" + filename;
    }

    @Async
    protected void triggerAsr(String sessionId, String audioPath, String speakerId, String speakerName, String speakerRole) {
        try {
            asrOrchestrationService.transcribe(sessionId, audioPath, speakerId, speakerName, speakerRole);
        } catch (Exception e) {
            log.error("[AudioPipeline] ASR 编排失败: sessionId={}, path={}", sessionId, audioPath, e);
        }
    }
}
