package com.example.GiftHub.controller;

import com.example.GiftHub.domain.user.AuthDTO;
import com.example.GiftHub.domain.user.LoginResponseDTO;
import com.example.GiftHub.domain.user.User;
import com.example.GiftHub.domain.user.UserDTO;
import com.example.GiftHub.infra.email.EmailService;
import com.example.GiftHub.infra.security.TokenService;
import com.example.GiftHub.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthDTO data, HttpServletRequest request) {
        logger.info("Iniciando processo de login para o usuário: {}", data.getLogin());
        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("token") != null) {
            logger.info("Usuário já está logado.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Usuário já está logado.");
        }

        try {
            var usernamePassword = new UsernamePasswordAuthenticationToken(data.getLogin(), data.getPassword());
            var auth = this.authenticationManager.authenticate(usernamePassword);

            // Gerar token com informações do usuário
            String token = tokenService.generateToken((User) auth.getPrincipal());

            // Definir atributos de sessão para controle
            session = request.getSession(true);
            session.setAttribute("token", token);

            // Obter userId como String para armazenar na sessão
            User user = (User) auth.getPrincipal();
            String userIdAsString = String.valueOf(user.getUserId()); // Converte userId para String

            session.setAttribute("userId", userIdAsString); // Armazena userId como String

            logger.info("Usuário {} logado com sucesso.", data.getLogin());
            // Retornar todas as informações do usuário no response
            return ResponseEntity.ok(new LoginResponseDTO(token, userIdAsString, user.getUsername()));
        } catch (Exception e) {
            logger.error("Erro durante o login: {}", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid UserDTO userDTO) {
        if (this.userRepository.findByLogin(userDTO.login()) != null) {
            return ResponseEntity.badRequest().body("Usuário já existe com este login.");
        }

        String encryptedPassword = new BCryptPasswordEncoder().encode(userDTO.password());
        User newUser = new User(userDTO);
        newUser.setPassword(encryptedPassword);
        newUser.setVerificationToken(UUID.randomUUID());
        this.userRepository.save(newUser);

        emailService.enviarEmailVerificacao(newUser);

        return new ResponseEntity<>(newUser, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // Invalida a sessão
        }
        return ResponseEntity.ok("Logout realizado com sucesso");
    }
}

