package com.resumetailor.keyword;

/** A skill or technology the job description asks for. */
public record JdKeyword(String keyword, KeywordImportance importance) {
}
