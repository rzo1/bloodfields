package com.github.rzo1.bloodfields.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UpdateCheckerParseTest {

    private static final String GITHUB_FIXTURE = """
            {
              "url": "https://api.github.com/repos/rzo1/bloodfields/releases/123",
              "html_url": "https://github.com/rzo1/bloodfields/releases/tag/v1.2.0",
              "id": 123,
              "author": {
                "login": "rzo1",
                "id": 1,
                "html_url": "https://github.com/rzo1",
                "type": "User"
              },
              "tag_name": "v1.2.0",
              "name": "v1.2.0 — Bloody Hands",
              "draft": false,
              "prerelease": false,
              "body": "Release notes go here.\\nLine two."
            }
            """;

    @Test
    void extractsTopLevelTagName() {
        assertEquals("v1.2.0", UpdateChecker.extractJsonString(GITHUB_FIXTURE, "tag_name"));
    }

    @Test
    void extractsTopLevelHtmlUrlNotNestedAuthorUrl() {
        String got = UpdateChecker.extractJsonString(GITHUB_FIXTURE, "html_url");
        assertEquals("https://github.com/rzo1/bloodfields/releases/tag/v1.2.0", got);
    }

    @Test
    void parseAndCompareReturnsTagAndUrl() {
        UpdateChecker.Result r = UpdateChecker.parseAndCompare(GITHUB_FIXTURE, "1.0.0");
        assertNotNull(r);
        assertEquals("v1.2.0", r.latestTag());
        assertEquals("https://github.com/rzo1/bloodfields/releases/tag/v1.2.0", r.htmlUrl());
    }

    @Test
    void missingKeyReturnsNull() {
        String json = "{\"foo\": \"bar\"}";
        assertNull(UpdateChecker.extractJsonString(json, "tag_name"));
    }

    @Test
    void escapeSequencesInStringsHandled() {
        String json = "{\"name\": \"hello\\nworld\"}";
        assertEquals("hello\nworld", UpdateChecker.extractJsonString(json, "name"));
    }

    @Test
    void quoteEscapeInString() {
        String json = "{\"name\": \"she said \\\"hi\\\"\"}";
        assertEquals("she said \"hi\"", UpdateChecker.extractJsonString(json, "name"));
    }
}
