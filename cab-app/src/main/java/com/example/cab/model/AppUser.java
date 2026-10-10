package com.example.cab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Login identity. The rider/driver profile it points to is stored in its own module. */
/**
 * Login identity (phone + BCrypt password hash + role). It points to the rider/driver profile through
 * profileId, so authentication stays separate from the rider/driver data (single responsibility).
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    protected AppUser() {
        // required by JPA
    }

    public AppUser(String phone, String passwordHash, Role role, Long profileId) {
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role;
        this.profileId = profileId;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Long getProfileId() { return profileId; }
}
