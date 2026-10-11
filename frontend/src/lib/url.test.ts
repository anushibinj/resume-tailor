import test from "node:test";
import assert from "node:assert/strict";
import { truncateUrl, isValidHttpUrl } from "./url.ts";

test("truncateUrl returns empty string for falsy input", () => {
  assert.equal(truncateUrl(""), "");
  assert.equal(truncateUrl(null), "");
  assert.equal(truncateUrl(undefined), "");
});

test("truncateUrl keeps short URLs unchanged", () => {
  const shortUrl = "https://example.com/job/123";
  assert.equal(truncateUrl(shortUrl), shortUrl);
});

test("truncateUrl keeps exactly maxLength URLs unchanged", () => {
  const exactUrl = "https://example.com/some/path/1234567890";
  assert.equal(exactUrl.length, 40);
  assert.equal(truncateUrl(exactUrl, 40), exactUrl);
});

test("truncateUrl middle-truncates long URLs with ellipsis preserving start and end", () => {
  const longUrl = "https://linkedin.com/jobapplications/view/123456";
  const result = truncateUrl(longUrl, 40);
  assert.equal(result, "https://linkedin.com/jobapplic.../123456");
  assert.equal(result.length, 40);
});

test("truncateUrl respects custom maxLength", () => {
  const longUrl = "https://linkedin.com/jobapplications/view/123456";
  const result = truncateUrl(longUrl, 35);
  assert.equal(result.length, 35);
  assert.ok(result.startsWith("https://linkedin.com/"));
  assert.ok(result.includes("..."));
  assert.ok(result.endsWith("/123456"));
});

test("isValidHttpUrl accepts valid http and https URLs", () => {
  assert.equal(isValidHttpUrl("https://linkedin.com/jobs/view/123"), true);
  assert.equal(isValidHttpUrl("http://localhost:3000/runs/1"), true);
  assert.equal(isValidHttpUrl("  https://acme.com/careers  "), true);
});

test("isValidHttpUrl rejects non-http schemes and malformed strings", () => {
  assert.equal(isValidHttpUrl("javascript:alert(1)"), false);
  assert.equal(isValidHttpUrl("data:text/html,test"), false);
  assert.equal(isValidHttpUrl("ftp://example.com"), false);
  assert.equal(isValidHttpUrl("file:///etc/passwd"), false);
  assert.equal(isValidHttpUrl("not-a-url"), false);
  assert.equal(isValidHttpUrl(""), false);
  assert.equal(isValidHttpUrl(null), false);
  assert.equal(isValidHttpUrl(undefined), false);
});
