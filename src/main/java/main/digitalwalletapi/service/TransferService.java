package main.digitalwalletapi.service;
import lombok.RequiredArgsConstructor;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.model.*;
import main.digitalwalletapi.repository.*;
import main.digitalwalletapi.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class TransferService {
    private final WalletRepository wallets;
    private final TransactionRepository transactions;

    @Transactional
    public TransactionResponse transfer(String email, TransferRequest request) {
        Long senderId = wallets.findIdByUserEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        Long recipientId = request.toWalletId();
        if (senderId.equals(recipientId)) throw new IllegalArgumentException("Cannot transfer to the same wallet");
        // Always acquire wallet locks in ID order to avoid opposing-transfer deadlocks.
        Wallet first = lock(Math.min(senderId, recipientId));
        Wallet second = lock(Math.max(senderId, recipientId));
        Wallet sender = first.getId().equals(senderId) ? first : second;
        Wallet recipient = first.getId().equals(recipientId) ? first : second;
        var existing = transactions.findByFromWalletIdAndIdempotencyKey(senderId, request.idempotencyKey());
        if (existing.isPresent()) {
            Transaction previous = existing.get();
            if (!previous.getToWallet().getId().equals(recipientId) || !previous.getAmount().equals(request.amount())) {
                throw new IdempotencyConflictException("Idempotency key was already used for a different transfer");
            }
            return TransactionResponse.from(previous);
        }
        sender.debit(request.amount());
        recipient.credit(request.amount());
        return TransactionResponse.from(transactions.saveAndFlush(new Transaction(sender, recipient, request.amount(), request.idempotencyKey())));
    }
    private Wallet lock(Long id) {
        return wallets.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
    }
}
