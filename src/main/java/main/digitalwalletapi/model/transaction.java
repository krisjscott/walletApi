package main.digitalwalletapi.model;

import jakarta.persistence.*;
import org.hibernate.resource.transaction.spi.TransactionStatus;


import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private wallet toWallet;

    @Column(name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;



}
