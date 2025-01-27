package org.example.ikproje.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.ikproje.dto.request.LoginRequestDto;
import org.example.ikproje.dto.request.RegisterRequestDto;
import org.example.ikproje.dto.request.ResetPasswordRequestDto;
import org.example.ikproje.dto.response.BaseResponse;
import org.example.ikproje.exception.ErrorType;
import org.example.ikproje.exception.IKProjeException;
import org.example.ikproje.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.example.ikproje.constant.RestApis.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(AUTH)
@CrossOrigin("*")
public class AuthController {

    private final UserService userService;

    @PostMapping(value = REGISTER)
    public ResponseEntity<BaseResponse<Boolean>> register(
            @RequestBody @Valid RegisterRequestDto dto) {

        if (!dto.password().equals(dto.rePassword())){
            throw new IKProjeException(ErrorType.PASSWORDS_NOT_MATCH);
        }

        userService.register(dto);

        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .code(200)
                .data(true)
                .message("Üyelik başarıyla oluşturuldu.")
                .success(true)
                .build());
    }

    @PostMapping(LOGIN)
    public ResponseEntity<BaseResponse<String>> login(@RequestBody @Valid LoginRequestDto dto) {
        return ResponseEntity.ok(BaseResponse.<String>builder()
                .code(200)
                .data(userService.login(dto))
                .success(true)
                .message("Giriş başarılı.")
                .build());
    }

    @GetMapping(VERIFY_ACCOUNT)
    public ResponseEntity<BaseResponse<Boolean>> verifyAccount(@RequestParam("token") String token){
        userService.verifyAccount(token);
        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .code(200)
                .message("Mail başarıyla onaylandı.")
                .data(true)
                .success(true)
                .build());
    }

    @PostMapping(FORGOT_PASSWORD)
    public ResponseEntity<BaseResponse<Boolean>> forgotPassword(@RequestParam String email){
        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .data(userService.forgotPassword(email))
                .code(200)
                .message("Şifre sıfırlama linki kullanıcı mailine gönderildi.")
                .success(true)
                .build());
    }

    @PostMapping(RESET_PASSWORD)
    public ResponseEntity<BaseResponse<Boolean>> resetPassword(@RequestParam("token") String token,@RequestBody ResetPasswordRequestDto dto){
        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .data(userService.resetPassword(token,dto))
                .code(200)
                .message("Şifre başarı ile değiştirildi.")
                .success(true)
                .build());
    }


    @PostMapping(value = UPDATE_USER_AVATAR,consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('COMPANY_MANAGER','EMPLOYEE')")
    public ResponseEntity<BaseResponse<Boolean>> addAvatarToUser(@RequestParam String token,
                                                                 @RequestParam MultipartFile file)
            throws IOException {
        userService.addAvatarToUser(token,file);
        return ResponseEntity.ok(BaseResponse.<Boolean>builder()
                .code(200)
                .data(true)
                .success(true)
                .message("User avatarı eklendi.")
                .build());
    }
}