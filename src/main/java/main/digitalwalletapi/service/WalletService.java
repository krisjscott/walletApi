package main.digitalwalletapi.service;
import org.springframework.beans.factory.annotation.Autowired;
import main.digitalwalletapi.dto.WalletResponse;
import main.digitalwalletapi.model.Wallet;
import main.digitalwalletapi.repository.WalletRepository;
import main.digitalwalletapi.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class WalletService {
    @Autowired
    private WalletRepository wallets;
    public Wallet requireWallet(String email) {
        return wallets.findByUserEmail(email).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }
    @Transactional(readOnly = true)
    public WalletResponse balance(String email) { return WalletResponse.from(requireWallet(email)); }
}
