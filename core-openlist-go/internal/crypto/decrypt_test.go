package crypto

import (
	"context"
	"encoding/base64"
	"testing"
)

func TestDecryptNamesParallelKeepsOrder(t *testing.T) {
	d := NewDecryptor()
	in := []string{
		"b64_" + base64.RawURLEncoding.EncodeToString([]byte("a/1")),
		"b64_" + base64.RawURLEncoding.EncodeToString([]byte("a/2")),
		"normal.enc",
	}
	out := d.DecryptNames(context.Background(), in, true, 1, 2)
	if len(out) != 3 {
		t.Fatalf("unexpected len: %d", len(out))
	}
	if out[0] != "a/1" || out[1] != "a/2" || out[2] != "normal" {
		t.Fatalf("unexpected out: %#v", out)
	}
}
