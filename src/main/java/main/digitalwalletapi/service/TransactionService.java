package main.digitalwalletapi.service;
import lombok.RequiredArgsConstructor;
import main.digitalwalletapi.dto.TransactionResponse;
import main.digitalwalletapi.repository.TransactionRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final WalletService walletService;
    private final TransactionRepository transactions;
    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(String email, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100");
        Long walletId = walletService.requireWallet(email).getId();
        return transactions.findByFromWalletId(walletId,
            PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.ASC, "createdAt", "id"))).map(TransactionResponse::from);
    }
}
