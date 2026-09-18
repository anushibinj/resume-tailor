package com.resumetailor.user;

/**
 * RBAC roles. Every user created via Google Sign-In starts as {@link #NORMAL_USER};
 * {@link #ORG_ADMIN} and {@link #ADMIN} are granted later by a data change, not through
 * any signup flow.
 */
public enum Role {
    NORMAL_USER,
    ORG_ADMIN,
    ADMIN
}
