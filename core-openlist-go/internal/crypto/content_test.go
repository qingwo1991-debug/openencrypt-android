package crypto

import (
	"bytes"
	"io"
	"testing"
)

func TestAESCTRRoundTrip(t *testing.T) {
	ce := NewContentEncryptor()

	cases := []struct {
		name     string
		payload  []byte
		password string
		fileSize int64
	}{
		{"empty", []byte{}, "secret123", 0},
		{"small", []byte("hello world"), "password", 11},
		{"block", make([]byte, 16), "key", 16},
		{"kilo", make([]byte, 1024), "large-key", 1024},
		{"mega", make([]byte, 1024*1024), "big-secret", 1024 * 1024},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			encR, err := ce.EncryptStream(bytes.NewReader(tc.payload), "aes-ctr", tc.password, tc.fileSize)
			if err != nil {
				t.Fatalf("EncryptStream: %v", err)
			}
			encData, _ := io.ReadAll(encR)

			// Old format: no nonce prefix, same size as plaintext
			if len(encData) != len(tc.payload) {
				t.Fatalf("expected same size, got %d vs %d", len(encData), len(tc.payload))
			}

			decR, err := ce.DecryptStream(bytes.NewReader(encData), "aes-ctr", tc.password, tc.fileSize)
			if err != nil {
				t.Fatalf("DecryptStream: %v", err)
			}
			decData, _ := io.ReadAll(decR)

			if !bytes.Equal(decData, tc.payload) {
				t.Fatalf("round-trip mismatch")
			}
		})
	}
}

func TestAESCTRSetPosition(t *testing.T) {
	ce := NewContentEncryptor()
	payload := make([]byte, 1000)
	for i := range payload {
		payload[i] = byte(i % 256)
	}
	fileSize := int64(1000)

	encR, _ := ce.EncryptStream(bytes.NewReader(payload), "aes-ctr", "pw", fileSize)
	encData, _ := io.ReadAll(encR)

	// Decrypt from position 500
	decR, err := ce.DecryptStreamWithRange(bytes.NewReader(encData[500:]), "aes-ctr", "pw", fileSize, 500)
	if err != nil {
		t.Fatalf("DecryptStreamWithRange: %v", err)
	}
	decData, _ := io.ReadAll(decR)

	if !bytes.Equal(decData, payload[500:]) {
		t.Fatalf("range decrypt mismatch: got %d bytes, want %d", len(decData), len(payload[500:]))
	}
}

func TestAESCTRWrongFileSize(t *testing.T) {
	ce := NewContentEncryptor()
	payload := []byte("test data")
	encR, _ := ce.EncryptStream(bytes.NewReader(payload), "aes-ctr", "pw", 9)
	encData, _ := io.ReadAll(encR)

	// Decrypt with wrong fileSize → garbage
	decR, _ := ce.DecryptStream(bytes.NewReader(encData), "aes-ctr", "pw", 10)
	decData, _ := io.ReadAll(decR)

	if bytes.Equal(decData, payload) {
		t.Fatal("wrong fileSize should not produce correct plaintext")
	}
}

func TestRC4MD5RoundTrip(t *testing.T) {
	ce := NewContentEncryptor()
	payload := []byte("rc4 test data stream")
	encR, _ := ce.EncryptStream(bytes.NewReader(payload), "rc4md5", "secret", 20)
	encData, _ := io.ReadAll(encR)
	if len(encData) != len(payload) {
		t.Fatalf("same size expected")
	}
	decR, _ := ce.DecryptStream(bytes.NewReader(encData), "rc4md5", "secret", 20)
	decData, _ := io.ReadAll(decR)
	if !bytes.Equal(decData, payload) {
		t.Fatal("rc4md5 round-trip mismatch")
	}
}

func TestRC4MD5SetPosition(t *testing.T) {
	ce := NewContentEncryptor()
	payload := make([]byte, segmentPosition+500)
	for i := range payload {
		payload[i] = byte(i % 256)
	}
	fileSize := int64(len(payload))

	encR, _ := ce.EncryptStream(bytes.NewReader(payload), "rc4md5", "pw", fileSize)
	encData, _ := io.ReadAll(encR)

	// Decrypt from position segmentPosition+200 (crosses segment boundary)
	start := int64(segmentPosition + 200)
	decR, err := ce.DecryptStreamWithRange(bytes.NewReader(encData[start:]), "rc4md5", "pw", fileSize, start)
	if err != nil {
		t.Fatalf("DecryptStreamWithRange: %v", err)
	}
	decData, _ := io.ReadAll(decR)

	if !bytes.Equal(decData, payload[start:]) {
		t.Fatalf("rc4md5 range decrypt mismatch at offset %d", start)
	}
}

func TestFilenameEncryptDecrypt(t *testing.T) {
	ce := NewContentEncryptor()

	enc, err := ce.EncryptName("oceans.mp4", "secret", "aes-ctr")
	if err != nil {
		t.Fatalf("EncryptName: %v", err)
	}
	if enc == "oceans.mp4" {
		t.Fatal("filename not encrypted")
	}

	dec, err := ce.DecryptName(enc, "secret", "aes-ctr")
	if err != nil {
		t.Fatalf("DecryptName: %v", err)
	}
	if dec != "oceans.mp4" {
		t.Fatalf("expected oceans.mp4, got %q", dec)
	}

	// Wrong password fails
	dec2, err := ce.DecryptName(enc, "wrong", "aes-ctr")
	if err == nil || dec2 != "" {
		t.Fatal("wrong password should fail")
	}
}

func TestNormalizeEncType(t *testing.T) {
	tests := map[string]string{
		"":          "aesctr",
		"aes-ctr":   "aesctr",
		"aesctr":    "aesctr",
		"rc4md5":    "rc4md5",
		"rc4":       "rc4md5",
		"unknown":   "unknown",
	}
	for in, want := range tests {
		if got := normalizeEncType(in); got != want {
			t.Errorf("normalizeEncType(%q) = %q, want %q", in, got, want)
		}
	}
}
