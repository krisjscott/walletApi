package main.digitalwalletapi.repository;

import main.digitalwalletapi.model.Wallet;
import org.springframework.data.repository.CrudRepository;

public interface WalletRepository extends CrudRepository<Wallet, Long> {

}
