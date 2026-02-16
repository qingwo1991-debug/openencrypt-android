package rules

import (
	"encoding/json"
	"fmt"
	"strings"
)

type EncryptRule struct {
	Path     string `json:"path"`
	Password string `json:"password"`
	EncType  string `json:"enc_type"`
	EncName  bool   `json:"enc_name"`
	Enable   bool   `json:"enable"`
}

type CompiledRule struct {
	Path     string
	BasePath string
	Password string
	EncType  string
	EncName  bool
}

type Matcher struct {
	rules []CompiledRule
}

func FromJSON(raw string) (*Matcher, error) {
	if strings.TrimSpace(raw) == "" {
		return &Matcher{}, nil
	}
	var source []EncryptRule
	if err := json.Unmarshal([]byte(raw), &source); err != nil {
		return nil, fmt.Errorf("parse encrypt rules json: %w", err)
	}

	compiled := make([]CompiledRule, 0, len(source))
	for _, item := range source {
		if !item.Enable {
			continue
		}
		norm, base, err := normalizeRulePath(item.Path)
		if err != nil {
			return nil, err
		}
		if item.EncType == "" {
			item.EncType = "aes-ctr"
		}
		compiled = append(compiled, CompiledRule{
			Path:     norm,
			BasePath: base,
			Password: item.Password,
			EncType:  item.EncType,
			EncName:  item.EncName,
		})
	}

	// Prefer more specific matches first.
	for i := 0; i < len(compiled); i++ {
		for j := i + 1; j < len(compiled); j++ {
			if len(compiled[j].BasePath) > len(compiled[i].BasePath) {
				compiled[i], compiled[j] = compiled[j], compiled[i]
			}
		}
	}
	return &Matcher{rules: compiled}, nil
}

func (m *Matcher) Count() int {
	if m == nil {
		return 0
	}
	return len(m.rules)
}

func (m *Matcher) Match(requestPath string) (CompiledRule, bool) {
	if m == nil {
		return CompiledRule{}, false
	}
	p := normalizeRequestPath(requestPath)
	for _, r := range m.rules {
		if pathMatches(r.BasePath, p) {
			return r, true
		}
	}
	return CompiledRule{}, false
}

func normalizeRequestPath(path string) string {
	p := strings.TrimSpace(path)
	if p == "" {
		return "/"
	}
	if !strings.HasPrefix(p, "/") {
		p = "/" + p
	}
	p = collapseSlashes(p)
	if len(p) > 1 {
		p = strings.TrimRight(p, "/")
	}
	if p == "" {
		return "/"
	}
	return p
}

func normalizeRulePath(path string) (normalized string, base string, err error) {
	p := strings.TrimSpace(path)
	if p == "" {
		return "", "", fmt.Errorf("encrypt rule path invalid")
	}
	wildcard := strings.HasSuffix(p, "/*")
	if strings.Contains(p, "*") && !wildcard {
		return "", "", fmt.Errorf("encrypt rule wildcard only allowed as trailing /*")
	}
	base = p
	if wildcard {
		base = strings.TrimSuffix(base, "/*")
	}
	base = normalizeRequestPath(base)
	if base == "/" {
		return "", "", fmt.Errorf("encrypt rule path invalid")
	}
	if wildcard {
		return base + "/*", base, nil
	}
	return base, base, nil
}

func collapseSlashes(path string) string {
	for strings.Contains(path, "//") {
		path = strings.ReplaceAll(path, "//", "/")
	}
	return path
}

func pathMatches(base, actual string) bool {
	if actual == base {
		return true
	}
	return strings.HasPrefix(actual, base+"/")
}
