package com.codeja.master_acdc.identity.infrastructure.adapters.in.web;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeja.master_acdc.identity.application.port.ConfirmPasswordResetUseCase;
import com.codeja.master_acdc.identity.application.port.RequestPasswordResetUseCase;
import com.codeja.master_acdc.identity.domain.model.PasswordResetConfirmation;
import com.codeja.master_acdc.identity.domain.model.PasswordResetRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private RequestPasswordResetUseCase request;
  @MockitoBean private ConfirmPasswordResetUseCase confirm;

  @Test
  @DisplayName("Solicitud de recuperación notifica al destinatario")
  void should_accept_request_when_email_is_valid() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\"}"))
        .andExpect(status().isAccepted())
        .andExpect(content().string(""));
    verify(request).request(new PasswordResetRequest("user@example.com"));
  }

  @Test
  @DisplayName("Recuperación permite iniciar sesión con la nueva contraseña")
  void should_confirm_when_recovery_data_is_valid() throws Exception {
    mvc.perform(
            post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"user@example.com\",\"token\":\"secret\",\"newPassword\":\"Unique!12345X\"}"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
    verify(confirm)
        .confirm(new PasswordResetConfirmation("user@example.com", "secret", "Unique!12345X"));
  }
}
