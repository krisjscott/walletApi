package main.digitalwalletapi.model;

import jakarta.persistence.*;

import java.util.Date;

@Entity
@Table(name = "users")
public class user {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "role")
    private Role role;

    @Column(name = "Password")
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_at")
    private Date createdAt;




}
