package main.digitalwalletapi.controller;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import main.digitalwalletapi.dto.WalletResponse;
import main.digitalwalletapi.service.WalletService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
    private final WalletService wallets;
    @GetMapping
    public WalletResponse balance(Principal principal) { return wallets.balance(principal.getName()); }
}
