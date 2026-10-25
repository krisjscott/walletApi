package main.digitalwalletapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;


@Entity
@Table(name = "users")
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected User() {}

    public User(String fullName, String email, String password) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.role = Role.USER;
    }

    @PrePersist
    public void prePersist()
    {this.createdAt = LocalDateTime.now();
    }

}
