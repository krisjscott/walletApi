package main.digitalwalletapi.repository;

import main.digitalwalletapi.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByFromWalletIdAndIdempotencyKey ( Long walletId, String key );

    Page<Transaction> findByFromWalletId ( Long fromId, Pageable pageable );
}
