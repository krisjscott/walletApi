package main.digitalwalletapi.repository;

import main.digitalwalletapi.model.Wallet;
import org.springframework.data.repository.CrudRepository;

public interface TransactionRepository extends CrudRepository<Wallet, Long> {
}
