package main.digitalwalletapi.repository;

import main.digitalwalletapi.model.Transaction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByFromWalletIdAndIdempotencyKey ( Long walletId, String key );

    @Query("SELECT t FROM Transaction t WHERE t.fromWallet.id = :id OR t.toWallet.id = :id")
    @EntityGraph(attributePaths = {"fromWallet","toWallet"})
    Page<Transaction> findByWalletId ( @Param("id") Long walletId, Pageable pageable );
}
