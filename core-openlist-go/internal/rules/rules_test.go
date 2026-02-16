package rules

import "testing"

func TestMatcherTrailingWildcardMatchesBaseAndChildren(t *testing.T) {
	m, err := FromJSON(`[{"path":"/123/encrypt/*","password":"p","enc_type":"aes-ctr","enable":true}]`)
	if err != nil {
		t.Fatalf("from json: %v", err)
	}
	if _, ok := m.Match("/123/encrypt"); !ok {
		t.Fatalf("expected base path match")
	}
	if _, ok := m.Match("/123/encrypt/a.txt"); !ok {
		t.Fatalf("expected child path match")
	}
	if _, ok := m.Match("/123/other/a.txt"); ok {
		t.Fatalf("unexpected match")
	}
}

func TestMatcherRejectsInvalidWildcard(t *testing.T) {
	_, err := FromJSON(`[{"path":"/123/*/x","password":"p","enable":true}]`)
	if err == nil {
		t.Fatalf("expected wildcard error")
	}
}

func TestMatcherPicksMostSpecificRule(t *testing.T) {
	m, err := FromJSON(`[
		{"path":"/a/*","password":"one","enable":true},
		{"path":"/a/b/*","password":"two","enable":true}
	]`)
	if err != nil {
		t.Fatalf("from json: %v", err)
	}
	got, ok := m.Match("/a/b/c.txt")
	if !ok {
		t.Fatalf("expected match")
	}
	if got.Password != "two" {
		t.Fatalf("expected most specific rule, got=%q", got.Password)
	}
}
