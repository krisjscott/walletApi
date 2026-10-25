package main.digitalwalletapi.dto;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

}
