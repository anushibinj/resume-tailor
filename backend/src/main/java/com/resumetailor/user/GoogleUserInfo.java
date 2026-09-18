package com.resumetailor.user;

/** The claims out of a verified Google ID token that identify and describe the signer. */
public record GoogleUserInfo(String googleSub, String email, String displayName, String pictureUrl) {
}
