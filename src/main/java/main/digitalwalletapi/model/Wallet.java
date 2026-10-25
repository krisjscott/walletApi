package main.digitalwalletapi.model;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance", precision = 14, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Version
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected Wallet() {}

    public Wallet(User user) { this.user = user; }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new main.digitalwalletapi.exception.InsufficientBalanceException("Insufficient balance");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        if (balance.add(amount).compareTo(new BigDecimal("999999999999.99")) > 0) {
            throw new IllegalArgumentException("Recipient balance limit exceeded");
        }
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must be positive with at most two decimal places");
        }
    }

    @PrePersist
    public void prePersist()
    {
        this.createdAt = LocalDateTime.now();
    }

}
