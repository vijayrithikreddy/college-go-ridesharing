package com.rithik.collegego;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    EditText etName, etRollNo, etEmail, etBranch, etYear, etPhone, etAddress, etPasswordReg, etConfirmPassword;
    Button btnRegister;
    TextView tvAlreadyUser;
    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etName = findViewById(R.id.etName);
        etRollNo = findViewById(R.id.etRollNo);
        etEmail = findViewById(R.id.etEmail);
        etBranch = findViewById(R.id.etBranch);
        etYear = findViewById(R.id.etYear);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etPasswordReg = findViewById(R.id.etPasswordReg);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvAlreadyUser = findViewById(R.id.tvAlreadyUser);

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String name = etName.getText().toString().trim();
                String rollNo = etRollNo.getText().toString().trim();
                String email = etEmail.getText().toString().trim();
                String branch = etBranch.getText().toString().trim();
                String year = etYear.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String address = etAddress.getText().toString().trim();
                String password = etPasswordReg.getText().toString().trim();
                String confirmPassword = etConfirmPassword.getText().toString().trim();

                if (!password.equals(confirmPassword)) {
                    Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (password.length() < 6) {
                    Toast.makeText(RegisterActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (TextUtils.isEmpty(email)) {
                    Toast.makeText(RegisterActivity.this, "Enter the Email", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(password)) {
                    Toast.makeText(RegisterActivity.this, "Enter the password", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Firebase Auth: create user
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    // Account created in Auth
                                    FirebaseUser user = mAuth.getCurrentUser();
                                    Toast.makeText(RegisterActivity.this, "Account Created", Toast.LENGTH_SHORT).show();

                                    if (user != null) {
                                        String uid = user.getUid();

                                        // Prepare user profile map (do NOT store password)
                                        Map<String, Object> userProfile = new HashMap<>();
                                        userProfile.put("uid", uid);
                                        userProfile.put("rollNo", rollNo);
                                        userProfile.put("name", name);
                                        userProfile.put("email", email);
                                        userProfile.put("phone", phone);
                                        userProfile.put("branch", branch);
                                        userProfile.put("year", year);
                                        userProfile.put("address", address);
                                        userProfile.put("role", "NONE"); // set later when user selects Rider/Pillion
                                        userProfile.put("createdAt", FieldValue.serverTimestamp());

                                        // Save profile to Firestore users/{uid}
                                        db.collection("users").document(uid)
                                                .set(userProfile)
                                                .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                    @Override
                                                    public void onComplete(@NonNull Task<Void> profileTask) {
                                                        if (profileTask.isSuccessful()) {
                                                            // Profile saved — proceed to MainActivity
                                                            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                                            startActivity(intent);
                                                            finish();
                                                        } else {
                                                            // Failed to write profile — still proceed but notify
                                                            Toast.makeText(RegisterActivity.this, "Registered but failed to save profile: " +
                                                                    (profileTask.getException() != null ? profileTask.getException().getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                                                            Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    }
                                                });
                                    } else {
                                        // Shouldn't normally happen, but fallback: continue to main
                                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                        startActivity(intent);
                                        finish();
                                    }

                                } else {
                                    // Auth failed
                                    Toast.makeText(RegisterActivity.this, "Authentication failed: " +
                                            (task.getException() != null ? task.getException().getMessage() : "unknown"), Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
            }
        });

        tvAlreadyUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
