package com.resumetailor.user;

import com.resumetailor.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditedEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** Google's stable per-account id (the ID token's "sub" claim). Null for pre-v2 rows. */
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(name = "picture_url")
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    public User(String email, String displayName, String googleSub, String pictureUrl, Role role) {
        this.email = email;
        this.displayName = displayName;
        this.googleSub = googleSub;
        this.pictureUrl = pictureUrl;
        this.role = role;
    }
}
