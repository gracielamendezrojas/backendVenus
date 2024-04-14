package com.sistema.venus.services;

import com.sistema.venus.domain.*;
import com.sistema.venus.util.Utils;
import com.sistema.venus.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.xml.bind.ValidationException;
import java.time.LocalDateTime;

@Service
public class AuthService {
    @Autowired
    private OtpsService otpsService;

    @Autowired
    private JwtUtils jwtUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JavaMailSender javaMailSender;
    @Autowired
    private UserPreferenceService userPreferenceService;

    public LoginResponse getToken(LoginRequest loginRequest) {
        Authentication authentication =
                authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), Utils.passwordEncoder(loginRequest.getPassword())));
        String email = authentication.getName();
        User user = new User(email);
        String token = jwtUtil.createToken(user);
        return new LoginResponse(token);
    }

    public ResponseEntity<String> sendEmail(RecuperaContraReqBody body) {
        try {
            Long userId = userService.getIdByEmail(body.getEmail());
            if(userId == null) return ResponseEntity.ok("Success");
            Otps otps = new Otps();
            otps.setUser_id(userId);
            String codigo =  otpsService.addOtps(otps).getCodigo();
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(body.getEmail());
            message.setSubject("Soporte Venus");
            message.setText(String.format("Hola,\n" +
                    "Visite este enlace para recuperar la contraseña:\n" +
                    "https://venus-health.azurewebsites.net/password_reset/%s", codigo));
            javaMailSender.send(message);
            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public ResponseEntity<String> sendEmailNotice(String body, User pUser) {
        try {
            Long userId = userService.getIdByEmail(pUser.getEmail());
            if(userId == null) return ResponseEntity.ok("Success");
            Otps otps = new Otps();
            otps.setUser_id(userId);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(pUser.getEmail());
//            System.out.println("sendEmailNotice() " + pUser.getEmail());
//            message.setTo("fretanah@ucenfotec.ac.cr");
            message.setSubject("Notificación Venus");
            message.setText(body);
            javaMailSender.send(message);
            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    

    public void passwordChange(PasswordResetChangeRequest body) throws ValidationException {
        Otps otps = otpsService.getOtpsByUserCode(body.getUserCode());
        if (!LocalDateTime.now().isAfter(otps.getTiempoExpiracion().plusMinutes(15))) {
            User user = userService.getUserById(otps.getUser_id());
            user.setPassword(Utils.passwordEncoder(body.getNewPassword()));
            userService.saveUser(user);
        } else {
            throw new ValidationException("Expired password change code");
        }
    }

    public ResponseEntity<Object> registerUser(User user) {
        if (userService.isEmailInUse(user.getEmail())) {
            throw new RuntimeException("El correo ya está en uso.");
        }
        user.setRol(Utils.USER_ROLE);
        user.setActive(true);
        user.setPassword(Utils.passwordEncoder(user.getPassword()));
        User savedUser = userService.saveUser(user);
        userPreferenceService.addPrefNotificacion(new UserPreferences(savedUser.getEmail()));
        return ResponseEntity.ok(savedUser);
    }

}
