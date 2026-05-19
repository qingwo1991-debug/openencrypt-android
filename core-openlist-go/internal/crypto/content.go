package crypto

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/md5"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"strconv"

	"golang.org/x/crypto/pbkdf2"
)

// ── Public API (alist-encrypt-go compatible format) ──

type ContentEncryptor struct{}

func NewContentEncryptor() *ContentEncryptor { return &ContentEncryptor{} }

func (ce *ContentEncryptor) EncryptStream(r io.Reader, encType, password string, fileSize int64) (io.Reader, error) {
	if password == "" {
		return nil, fmt.Errorf("password required")
	}
	switch normalizeEncType(encType) {
	case "aesctr":
		a, err := newAESCTR(password, fileSize)
		if err != nil {
			return nil, err
		}
		return a.encryptReader(r), nil
	case "rc4md5":
		rc, err := newRC4MD5(password, fileSize)
		if err != nil {
			return nil, err
		}
		return rc.encryptReader(r), nil
	default:
		return nil, fmt.Errorf("unsupported enc_type: %s", encType)
	}
}

func (ce *ContentEncryptor) DecryptStream(r io.Reader, encType, password string, fileSize int64) (io.Reader, error) {
	if password == "" {
		return nil, fmt.Errorf("password required")
	}
	switch normalizeEncType(encType) {
	case "aesctr":
		a, err := newAESCTR(password, fileSize)
		if err != nil {
			return nil, err
		}
		return a.decryptReader(r), nil
	case "rc4md5":
		rc, err := newRC4MD5(password, fileSize)
		if err != nil {
			return nil, err
		}
		return rc.decryptReader(r), nil
	default:
		return nil, fmt.Errorf("unsupported enc_type: %s", encType)
	}
}

func (ce *ContentEncryptor) DecryptStreamWithRange(r io.Reader, encType, password string, fileSize, rangeStart int64) (io.Reader, error) {
	switch normalizeEncType(encType) {
	case "aesctr":
		a, err := newAESCTR(password, fileSize)
		if err != nil {
			return nil, err
		}
		if err := a.setPosition(rangeStart); err != nil {
			return nil, err
		}
		return a.decryptReader(r), nil
	case "rc4md5":
		rc, err := newRC4MD5(password, fileSize)
		if err != nil {
			return nil, err
		}
		if err := rc.setPosition(rangeStart); err != nil {
			return nil, err
		}
		return rc.decryptReader(r), nil
	default:
		return nil, fmt.Errorf("unsupported enc_type: %s", encType)
	}
}

func (ce *ContentEncryptor) EncryptName(name, password, encType string) (string, error) {
	return encodeName(password, encType, name), nil
}

func (ce *ContentEncryptor) DecryptName(encoded, password, encType string) (string, error) {
	result := decodeNameCompat(password, encType, encoded)
	if result == "" {
		return "", fmt.Errorf("filename decryption failed")
	}
	return result, nil
}

// ── Key derivation ──

func getPasswdOutward(password, encType string) string {
	norm := normalizeEncType(encType)
	salt := "AES-CTR"
	switch norm {
	case "rc4md5":
		salt = "RC4"
	}
	key := pbkdf2.Key([]byte(password), []byte(salt), 1000, 16, sha256.New)
	return hex.EncodeToString(key)
}

func normalizeEncType(encType string) string {
	switch encType {
	case "", "aes-ctr", "aesctr":
		return "aesctr"
	case "rc4md5", "rc4":
		return "rc4md5"
	default:
		return encType
	}
}

// ── AES-CTR ──

type aesCTR struct {
	block    cipher.Block
	sourceIv []byte
	iv       []byte
	stream   cipher.Stream
}

func newAESCTR(password string, fileSize int64) (*aesCTR, error) {
	passwdOutward := getPasswdOutward(password, "aesctr")
	passwdSalt := passwdOutward + strconv.FormatInt(fileSize, 10)
	keyHash := md5.Sum([]byte(passwdSalt))
	key := keyHash[:]
	ivHash := md5.Sum([]byte(strconv.FormatInt(fileSize, 10)))
	a := &aesCTR{}
	a.iv = make([]byte, 16)
	copy(a.iv, ivHash[:])
	a.sourceIv = make([]byte, 16)
	copy(a.sourceIv, a.iv)
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("aes cipher: %w", err)
	}
	a.block = block
	a.stream = cipher.NewCTR(block, a.iv)
	return a, nil
}

func (a *aesCTR) setPosition(position int64) error {
	if position < 0 {
		return fmt.Errorf("negative position")
	}
	copy(a.iv, a.sourceIv)
	blockCount := position / 16
	incrementIV(a.iv, blockCount)
	a.stream = cipher.NewCTR(a.block, a.iv)
	offset := position % 16
	if offset > 0 {
		discard := make([]byte, offset)
		a.stream.XORKeyStream(discard, discard)
	}
	return nil
}

func (a *aesCTR) encrypt(data []byte) { a.stream.XORKeyStream(data, data) }
func (a *aesCTR) decrypt(data []byte) { a.stream.XORKeyStream(data, data) }
func (a *aesCTR) encryptReader(r io.Reader) io.Reader { return &cipherReader{r: r, xform: a.encrypt} }
func (a *aesCTR) decryptReader(r io.Reader) io.Reader { return &cipherReader{r: r, xform: a.decrypt} }

func incrementIV(iv []byte, increment int64) {
	const maxUint32 = uint64(0xffffffff)
	inc := uint64(increment)
	incBig := int64(inc / maxUint32)
	incLittle := int64(inc%maxUint32) - incBig
	overflow := int64(0)
	for idx := 0; idx < 4; idx++ {
		offset := 12 - idx*4
		num := int64(uint32(iv[offset])<<24 | uint32(iv[offset+1])<<16 |
			uint32(iv[offset+2])<<8 | uint32(iv[offset+3]))
		incPart := overflow
		if idx == 0 {
			incPart += incLittle
		}
		if idx == 1 {
			incPart += incBig
		}
		num += incPart
		numBig := num / int64(maxUint32)
		numLittle := num%int64(maxUint32) - numBig
		overflow = numBig
		v := uint32(numLittle)
		iv[offset] = byte(v >> 24)
		iv[offset+1] = byte(v >> 16)
		iv[offset+2] = byte(v >> 8)
		iv[offset+3] = byte(v)
	}
}

// ── RC4-MD5 ──

const segmentPosition = 1000000

type rc4MD5Cipher struct {
	fileHexKey string
	position   int64
	i, j       int
	sbox       [256]byte
}

func newRC4MD5(password string, fileSize int64) (*rc4MD5Cipher, error) {
	passwdOutward := getPasswdOutward(password, "rc4md5")
	passwdSalt := passwdOutward + strconv.FormatInt(fileSize, 10)
	hash := md5.Sum([]byte(passwdSalt))
	r := &rc4MD5Cipher{fileHexKey: hex.EncodeToString(hash[:])}
	if err := r.resetKSA(); err != nil {
		return nil, err
	}
	return r, nil
}

func (r *rc4MD5Cipher) resetKSA() error {
	offset := (r.position / segmentPosition) * segmentPosition
	offsetBuf := make([]byte, 4)
	binary.BigEndian.PutUint32(offsetBuf, uint32(offset))
	rc4Key, _ := hex.DecodeString(r.fileHexKey)
	j := len(rc4Key) - 4
	for i := 0; i < 4; i++ {
		rc4Key[j+i] ^= offsetBuf[i]
	}
	return r.initKSA(rc4Key)
}

func (r *rc4MD5Cipher) initKSA(key []byte) error {
	K := make([]byte, 256)
	for i := 0; i < 256; i++ {
		r.sbox[i] = byte(i)
		K[i] = key[i%len(key)]
	}
	j := 0
	for i := 0; i < 256; i++ {
		j = (j + int(r.sbox[i]) + int(K[i])) % 256
		r.sbox[i], r.sbox[j] = r.sbox[j], r.sbox[i]
	}
	r.i, r.j = 0, 0
	return nil
}

func (r *rc4MD5Cipher) setPosition(position int64) error {
	if position < 0 {
		return fmt.Errorf("negative position")
	}
	r.position = position
	if err := r.resetKSA(); err != nil {
		return err
	}
	posInSegment := position % segmentPosition
	if posInSegment > 0 {
		r.prgaAdvance(int(posInSegment))
	}
	return nil
}

func (r *rc4MD5Cipher) prgaAdvance(count int) {
	for k := 0; k < count; k++ {
		r.i = (r.i + 1) % 256
		r.j = (r.j + int(r.sbox[r.i])) % 256
		r.sbox[r.i], r.sbox[r.j] = r.sbox[r.j], r.sbox[r.i]
	}
}

func (r *rc4MD5Cipher) encrypt(data []byte) {
	for k := 0; k < len(data); k++ {
		r.i = (r.i + 1) % 256
		r.j = (r.j + int(r.sbox[r.i])) % 256
		r.sbox[r.i], r.sbox[r.j] = r.sbox[r.j], r.sbox[r.i]
		data[k] ^= r.sbox[(int(r.sbox[r.i])+int(r.sbox[r.j]))%256]
		r.position++
		if r.position%segmentPosition == 0 {
			r.resetKSA()
		}
	}
}
func (r *rc4MD5Cipher) decrypt(data []byte)                { r.encrypt(data) }
func (r *rc4MD5Cipher) encryptReader(rd io.Reader) io.Reader { return &cipherReader{r: rd, xform: r.encrypt} }
func (r *rc4MD5Cipher) decryptReader(rd io.Reader) io.Reader { return &cipherReader{r: rd, xform: r.decrypt} }

// ── generic cipher reader ──

type cipherReader struct {
	r     io.Reader
	xform func([]byte)
}

func (cr *cipherReader) Read(p []byte) (int, error) {
	n, err := cr.r.Read(p)
	if n > 0 {
		cr.xform(p[:n])
	}
	return n, err
}

// ── Filename: MixBase64 + CRC6 ──

const sourceChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-~+"

type mixBase64 struct {
	chars     [65]byte
	decodeMap map[byte]int
}

func newMixBase64(passwd string) *mixBase64 {
	m := &mixBase64{decodeMap: make(map[byte]int)}
	var secret string
	if len(passwd) == 64 {
		secret = passwd
	} else {
		secret = initMixKSA(passwd + "mix64")
	}
	for i := 0; i < 64; i++ {
		m.chars[i] = secret[i]
	}
	if len(secret) > 64 {
		m.chars[64] = secret[64]
	} else {
		m.chars[64] = '+'
	}
	for i := 0; i < 65; i++ {
		m.decodeMap[m.chars[i]] = i
	}
	return m
}

func initMixKSA(passwd string) string {
	key := sha256.Sum256([]byte(passwd))
	n := len(sourceChars)
	sbox := make([]int, n)
	for i := range sbox {
		sbox[i] = i
	}
	K := make([]byte, n)
	for i := 0; i < n; i++ {
		K[i] = key[i%len(key)]
	}
	j := 0
	for i := 0; i < n; i++ {
		j = (j + sbox[i] + int(K[i])) % n
		sbox[i], sbox[j] = sbox[j], sbox[i]
	}
	secret := make([]byte, n)
	for i, idx := range sbox {
		secret[i] = sourceChars[idx]
	}
	return string(secret)
}

func (m *mixBase64) encodeString(s string) string {
	data := []byte(s)
	if len(data) == 0 {
		return ""
	}
	var result []byte
	i := 0
	for ; i+3 <= len(data); i += 3 {
		b0, b1, b2 := data[i], data[i+1], data[i+2]
		result = append(result,
			m.chars[b0>>2],
			m.chars[((b0&3)<<4)|(b1>>4)],
			m.chars[((b1&15)<<2)|(b2>>6)],
			m.chars[b2&63])
	}
	remaining := len(data) - i
	if remaining == 1 {
		b0 := data[i]
		result = append(result, m.chars[b0>>2], m.chars[(b0&3)<<4], m.chars[64], m.chars[64])
	} else if remaining == 2 {
		b0, b1 := data[i], data[i+1]
		result = append(result, m.chars[b0>>2], m.chars[((b0&3)<<4)|(b1>>4)], m.chars[(b1&15)<<2], m.chars[64])
	}
	return string(result)
}

func (m *mixBase64) decodeString(s string) (string, error) {
	if len(s) == 0 {
		return "", nil
	}
	if len(s)%4 != 0 {
		return "", fmt.Errorf("invalid base64 length")
	}
	size := (len(s) / 4) * 3
	pad := m.chars[64]
	if len(s) >= 2 && s[len(s)-2] == pad && s[len(s)-1] == pad {
		size -= 2
	} else if len(s) >= 1 && s[len(s)-1] == pad {
		size -= 1
	}
	buf := make([]byte, size)
	j := 0
	for i := 0; i < len(s); i += 4 {
		e1, ok1 := m.decodeMap[s[i]]
		e2, ok2 := m.decodeMap[s[i+1]]
		e3, ok3 := m.decodeMap[s[i+2]]
		e4, ok4 := m.decodeMap[s[i+3]]
		if !ok1 || !ok2 || !ok3 || !ok4 {
			return "", fmt.Errorf("invalid mix64 char")
		}
		buf[j] = byte((e1 << 2) | (e2 >> 4))
		j++
		if e3 != 64 && j < size {
			buf[j] = byte(((e2 & 15) << 4) | (e3 >> 2))
			j++
		}
		if e4 != 64 && j < size {
			buf[j] = byte(((e3 & 3) << 6) | e4)
			j++
		}
	}
	return string(buf[:j]), nil
}

// ── CRC6 ──

type crc6Table struct{ table [256]byte }

var crc6Inst = func() *crc6Table {
	c := &crc6Table{}
	for i := 0; i < 256; i++ {
		curr := byte(i)
		for j := 0; j < 8; j++ {
			if (curr & 0x01) != 0 {
				curr = ((curr >> 1) ^ 0x30)
			} else {
				curr = curr >> 1
			}
		}
		c.table[i] = curr
	}
	return c
}()

func (c *crc6Table) checksum(data []byte) int {
	crc := byte(0)
	for _, b := range data {
		crc = c.table[crc^b]
	}
	return int(crc)
}

func encodeName(password, encType, plainName string) string {
	passwdOutward := getPasswdOutward(password, encType)
	mix64 := newMixBase64(passwdOutward)
	encoded := mix64.encodeString(plainName)
	check := encoded + passwdOutward
	bit := crc6Inst.checksum([]byte(check))
	return encoded + string(sourceChars[bit%64])
}

func decodeNameCompat(password, encType, encodedName string) string {
	if len(encodedName) < 2 {
		return ""
	}
	crcChar := encodedName[len(encodedName)-1]
	passwdOutward := getPasswdOutward(password, encType)
	mix64 := newMixBase64(passwdOutward)
	sub := encodedName[:len(encodedName)-1]
	check := sub + passwdOutward
	bit := crc6Inst.checksum([]byte(check))
	if sourceChars[bit%64] != crcChar {
		return ""
	}
	decoded, err := mix64.decodeString(sub)
	if err != nil {
		return ""
	}
	return decoded
}
