package main.digitalwalletapi.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import main.digitalwalletapi.dto.*;
import main.digitalwalletapi.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {
    private final TransferService transfers;
    private final TransactionService transactions;

    @PostMapping("/transfer")
    public TransactionResponse transfer(Principal principal, @Valid @RequestBody TransferRequest request) {
        return transfers.transfer( principal.getName(), request );
    }

    @GetMapping
    public TransactionHistoryResponse history(Principal principal,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return TransactionHistoryResponse.from( transactions.history( principal.getName(), page, size ) );
    }
}
