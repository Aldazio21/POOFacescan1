package com.example.poofacescan1;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;

public class LoginActivity extends AppCompatActivity {
    private EditText usernameInput, passwordInput;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AuthStorage.isAuthenticated()) { openMain(); return; }
        setContentView(R.layout.activity_login);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        findViewById(R.id.loginButton).setOnClickListener(v -> doLogin());
        findViewById(R.id.registerButton).setOnClickListener(v -> doRegister());
    }
    private void doLogin() {
        String name = usernameInput.getText().toString().trim();
        String pass = passwordInput.getText().toString();
        if (AuthStorage.getRemainingLockTime(this, name) > 0) {
            Toast.makeText(this, "Cuenta bloqueada temporalmente. Intenta más tarde.", Toast.LENGTH_LONG).show(); return;
        }
        if (AuthStorage.login(this, name, pass)) openMain();
        else Toast.makeText(this, "Usuario o contraseña incorrectos. Tras 5 intentos habrá un bloqueo de 30 segundos.", Toast.LENGTH_LONG).show();
    }
    private void doRegister() {
        int result = AuthStorage.register(this, usernameInput.getText().toString(), passwordInput.getText().toString());
        if (result == AuthStorage.REGISTER_OK) openMain();
        else if (result == AuthStorage.REGISTER_EXISTS) Toast.makeText(this, "Ese usuario ya existe. Inicia sesión o elige otro nombre.", Toast.LENGTH_LONG).show();
        else if (result == AuthStorage.REGISTER_WEAK_PASSWORD) Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres.", Toast.LENGTH_LONG).show();
        else Toast.makeText(this, "El usuario debe tener entre 3 y 32 caracteres.", Toast.LENGTH_LONG).show();
    }
    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
