package main.digitalwalletapi.repository;

import main.digitalwalletapi.model.Wallet;
import org.springframework.data.repository.CrudRepository;

import java.math.BigDecimal;

public interface WalletRepository extends CrudRepository<Wallet, Long> {

}
