package crypto

import (
	"context"
	"encoding/base64"
	"fmt"
	"strings"
	"sync"

	"github.com/openlist/openencrypt-android/core-openlist-go/internal/cryptocache"
)

type Decryptor struct {
	cache *cryptocache.Cache
}

func NewDecryptor() *Decryptor {
	return &Decryptor{cache: cryptocache.New(32)}
}

func (d *Decryptor) DecryptName(name string) string {
	if v, ok := d.cache.Get(name); ok {
		return v
	}
	v := decodeName(name)
	d.cache.Set(name, v)
	return v
}

func (d *Decryptor) DecryptNames(ctx context.Context, names []string, enableParallel bool, threshold int, concurrency int) []string {
	out := make([]string, len(names))
	if !enableParallel || len(names) < threshold || concurrency <= 1 {
		for i, n := range names {
			select {
			case <-ctx.Done():
				return out
			default:
				out[i] = d.DecryptName(n)
			}
		}
		return out
	}

	jobs := make(chan int)
	var wg sync.WaitGroup
	for w := 0; w < concurrency; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := range jobs {
				select {
				case <-ctx.Done():
					return
				default:
					out[i] = d.DecryptName(names[i])
				}
			}
		}()
	}

	for i := range names {
		select {
		case <-ctx.Done():
			close(jobs)
			wg.Wait()
			return out
		case jobs <- i:
		}
	}
	close(jobs)
	wg.Wait()
	return out
}

func decodeName(name string) string {
	trimmed := strings.TrimSuffix(name, ".enc")
	if strings.HasPrefix(trimmed, "b64_") {
		raw := strings.TrimPrefix(trimmed, "b64_")
		buf, err := base64.RawURLEncoding.DecodeString(raw)
		if err == nil {
			return string(buf)
		}
	}
	return strings.ReplaceAll(trimmed, "__", "/")
}

// decodeNameToRaw returns raw bytes for a filename encoded with b64_ prefix.
// Used by ContentEncryptor.DecryptName for actual cryptographic decryption.
func decodeNameToRaw(name string) ([]byte, error) {
	trimmed := strings.TrimSuffix(name, ".enc")
	if strings.HasPrefix(trimmed, "b64_") {
		raw := strings.TrimPrefix(trimmed, "b64_")
		return base64.RawURLEncoding.DecodeString(raw)
	}
	return nil, fmt.Errorf("name not b64_ encoded: %q", name)
}
