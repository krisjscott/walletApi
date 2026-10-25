package main.digitalwalletapi.model;

import jakarta.persistence.*;
import lombok.Getter;


import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "wallet_transaction", uniqueConstraints = @UniqueConstraint(columnNames = {"from_wallet_id", "idempotency_key"}))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_wallet_id")
    private Wallet fromWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id", nullable = false)
    private Wallet toWallet;

    @Column(name = "amount", scale = 2, precision = 14, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {}

    public Transaction(Wallet fromWallet, Wallet toWallet, BigDecimal amount, String idempotencyKey) {
        this.fromWallet = fromWallet;
        this.toWallet = toWallet;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.type = TransactionType.TRANSFER;
        this.status = TransactionStatus.SUCCESS;
    }

    @PrePersist
    public void prePersist()
    {
        this.createdAt = LocalDateTime.now();
    }

}
