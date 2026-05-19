package cryptocache

import (
	"container/list"
	"sync"
	"time"
)

const defaultMaxEntries = 10000

type entry struct {
	key       string
	value     string
	expiresAt time.Time
}

type shard struct {
	mu       sync.RWMutex
	m        map[string]*list.Element
	lru      *list.List
	maxItems int
}

type Cache struct {
	shards []*shard
	ttl    time.Duration
}

// New creates a sharded cache with n shards. Entries older than ttl are
// lazily evicted on access. If ttl is 0, entries never expire.
func New(n int) *Cache {
	if n < 1 {
		n = 16
	}
	c := &Cache{
		shards: make([]*shard, n),
		ttl:    0,
	}
	for i := range c.shards {
		c.shards[i] = &shard{
			m:        make(map[string]*list.Element),
			lru:      list.New(),
			maxItems: defaultMaxEntries,
		}
	}
	return c
}

// NewWithTTL creates a cache with per-entry time-to-live.
func NewWithTTL(n int, ttl time.Duration) *Cache {
	c := New(n)
	c.ttl = ttl
	return c
}

// SetMaxItems limits the total entries per shard (default 10000).
func (c *Cache) SetMaxItems(n int) {
	for _, s := range c.shards {
		s.mu.Lock()
		s.maxItems = n
		s.mu.Unlock()
	}
}

func (c *Cache) Get(key string) (string, bool) {
	s := c.shards[hash(key)%uint32(len(c.shards))]
	s.mu.Lock()
	defer s.mu.Unlock()

	el, ok := s.m[key]
	if !ok {
		return "", false
	}
	e := el.Value.(*entry)

	// Lazy TTL eviction
	if c.ttl > 0 && time.Now().After(e.expiresAt) {
		s.lru.Remove(el)
		delete(s.m, e.key)
		return "", false
	}

	s.lru.MoveToFront(el)
	return e.value, true
}

func (c *Cache) Set(key, value string) {
	s := c.shards[hash(key)%uint32(len(c.shards))]
	s.mu.Lock()
	defer s.mu.Unlock()

	if el, ok := s.m[key]; ok {
		s.lru.MoveToFront(el)
		e := el.Value.(*entry)
		e.value = value
		if c.ttl > 0 {
			e.expiresAt = time.Now().Add(c.ttl)
		}
		return
	}

	// Evict LRU if at capacity
	for s.lru.Len() >= s.maxItems {
		oldest := s.lru.Back()
		if oldest == nil {
			break
		}
		oe := oldest.Value.(*entry)
		delete(s.m, oe.key)
		s.lru.Remove(oldest)
	}

	expiresAt := time.Time{}
	if c.ttl > 0 {
		expiresAt = time.Now().Add(c.ttl)
	}
	el := s.lru.PushFront(&entry{key: key, value: value, expiresAt: expiresAt})
	s.m[key] = el
}

func (c *Cache) Len() int {
	total := 0
	for _, s := range c.shards {
		s.mu.RLock()
		total += len(s.m)
		s.mu.RUnlock()
	}
	return total
}

func hash(s string) uint32 {
	var h uint32 = 2166136261
	for i := 0; i < len(s); i++ {
		h ^= uint32(s[i])
		h *= 16777619
	}
	return h
}
