package main.digitalwalletapi.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Date;

@Entity
public class wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private user user;

    @Column(name = "balance")
    private BigDecimal balance;

    @Version
    private Long version;

    @Column(name = "created_at")
    private Date createdAt;


}
