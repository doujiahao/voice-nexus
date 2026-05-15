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

        if (isLast) {
            log.info("[AudioPipeline] 收到最终音频帧: sessionId={}, speakerId={}, speakerName={}, speakerRole={}, sequence={}, bytes={}",
                    sessionId, speakerId, speakerName, speakerRole, frame.getSequenceNumber(), audioData.length);
        }

        if (isEmptyAudio(audioData)) {
            log.debug("[AudioPipeline] 空音频帧已跳过: sessionId={}, speakerId={}, sequence={}",
                    sessionId, speakerId, frame.getSequenceNumber());
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

    private boolean isEmptyAudio(byte[] audioData) {
        if (audioData.length == 0) {
            return true;
        }
        int dataOffset = findWavDataOffset(audioData);
        if (dataOffset < 0) {
            return false;
        }
        for (int i = dataOffset; i < audioData.length; i++) {
            if (audioData[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private int findWavDataOffset(byte[] audioData) {
        for (int i = 12; i <= audioData.length - 8; i++) {
            if (audioData[i] == 'd' && audioData[i + 1] == 'a' && audioData[i + 2] == 't' && audioData[i + 3] == 'a') {
                int dataSize = (audioData[i + 4] & 0xff)
                        | ((audioData[i + 5] & 0xff) << 8)
                        | ((audioData[i + 6] & 0xff) << 16)
                        | ((audioData[i + 7] & 0xff) << 24);
                if (dataSize <= 0) {
                    return audioData.length;
                }
                return i + 8;
            }
        }
        return -1;
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
