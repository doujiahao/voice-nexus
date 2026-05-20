-- call_session: B-leg FreeSWITCH channel UUID（坐席侧通道），用于 uuid_answer
ALTER TABLE call_session ADD COLUMN IF NOT EXISTS b_leg_fs_call_id VARCHAR(64);
