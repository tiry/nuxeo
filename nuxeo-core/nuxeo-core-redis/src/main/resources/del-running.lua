--
-- Deletes completed works with the ids given in ARGV
-- having a completion time before the given time limit.
--

local runningKey = KEYS[1]
local stateKey = KEYS[2]
local dataKey = KEYS[3]
local workId = ARGV[1]
-- the rest of ARGV is the list of work ids to check and delete


redis.call('SREM', runningKey, workId)
redis.call('HDEL', stateKey, workId)
redis.call('HDEL', dataKey, workId)

