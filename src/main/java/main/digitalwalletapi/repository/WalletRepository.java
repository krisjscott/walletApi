package main.digitalwalletapi.repository;
import main.digitalwalletapi.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserEmail(String email);
    @Query("select w.id from Wallet w where w.user.email = :email")
    Optional<Long> findIdByUserEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findLockedById(@Param("id") Long id);
}
