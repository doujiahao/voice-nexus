-- call_session: fs_call_id 索引（加速 getByFsCallId 查询）
CREATE INDEX IF NOT EXISTS idx_call_session_fs_call_id ON call_session(fs_call_id);

-- call_session: 乐观锁 version 字段
ALTER TABLE call_session ADD COLUMN IF NOT EXISTS version INT DEFAULT 0;
