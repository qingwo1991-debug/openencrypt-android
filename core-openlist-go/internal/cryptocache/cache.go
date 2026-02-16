package cryptocache

import "sync"

type shard struct {
	mu sync.RWMutex
	m  map[string]string
}

type Cache struct {
	shards []shard
}

func New(n int) *Cache {
	if n < 1 {
		n = 16
	}
	c := &Cache{shards: make([]shard, n)}
	for i := range c.shards {
		c.shards[i].m = make(map[string]string)
	}
	return c
}

func (c *Cache) Get(key string) (string, bool) {
	s := &c.shards[hash(key)%uint32(len(c.shards))]
	s.mu.RLock()
	v, ok := s.m[key]
	s.mu.RUnlock()
	return v, ok
}

func (c *Cache) Set(key, value string) {
	s := &c.shards[hash(key)%uint32(len(c.shards))]
	s.mu.Lock()
	s.m[key] = value
	s.mu.Unlock()
}

func hash(s string) uint32 {
	var h uint32 = 2166136261
	for i := 0; i < len(s); i++ {
		h ^= uint32(s[i])
		h *= 16777619
	}
	return h
}
