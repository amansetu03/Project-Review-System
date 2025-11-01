package com.example.projectreviewsystem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class LoginAdmin extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    private EditText emailEditText, passwordEditText;
    private TextView googleSignIn;
    private Button loginButton;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loginadmin);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        configureGoogleSignIn();

        emailEditText = findViewById(R.id.email_id);
        passwordEditText = findViewById(R.id.pass_word);
        googleSignIn = findViewById(R.id.go_ogle);
        loginButton = findViewById(R.id.login_button);

        googleSignIn.setOnClickListener(v -> startGoogleSignIn());
        loginButton.setOnClickListener(v -> signInWithEmail());
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void startGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignInResult(task);
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult();
            firebaseAuthWithGoogle(account);
        } catch (Exception e) {
            Log.e("GoogleSignIn", "Google Sign-In Failed: " + e.getMessage());
            showSnackbar("Google Sign-In Failed", false);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = firebaseAuth.getCurrentUser ().getUid();
                        String email = account.getEmail();
                        Uri photoUri = account.getPhotoUrl();
                        checkIfAdminExists(userId, email, photoUri); // Check if admin exists in Firestore
                    } else {
                        Log.e("GoogleSignIn", "Google Sign-In Failed: " + (task.getException() != null ? task.getException().getMessage() : ""));
                        showSnackbar("Google Sign-In Failed", false);
                    }
                });
    }

    private void signInWithEmail() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showSnackbar("Please enter a valid email", false);
            return;
        }

        if (password.length() < 6) {
            showSnackbar("Password must be at least 6 characters", false);
            return;
        }

        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        String userId = firebaseAuth.getCurrentUser ().getUid();
                        checkIfAdminExists(userId, email, null); // Check if admin exists in Firestore
                    } else {
                        Log.e("EmailSignIn", "Sign-In Failed: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                        showSnackbar("Sign-In Failed", false);
                    }
                });
    }

    private void checkIfAdminExists(String userId, String email, Uri photoUri) {
        DocumentReference docRef = firestore.collection("admins").document(userId);
        docRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                // Admin already exists, redirect to AdminActivity
                redirectToAdminDashboard();
            } else {
                // Admin does not exist, save admin data to Firestore
                saveAdminDataToFirestore(userId, email, photoUri);
            }
        });
    }

    private void saveAdminDataToFirestore(String userId, String email, Uri photoUri) {
        DocumentReference docRef = firestore.collection("admins").document(userId);

        Map<String, Object> adminData = new HashMap<>();
        adminData.put("email", email);
        adminData.put("name", firebaseAuth.getCurrentUser ().getDisplayName());
        if (photoUri != null) {
            adminData.put("profile_photo", photoUri.toString());
        }

        docRef.set(adminData)
                .addOnSuccessListener(aVoid -> {
                    Log.i("Firestore", "Admin data saved successfully");
                    redirectToAdminDashboard(); // Redirect after saving data
                })
                .addOnFailureListener(e -> Log.e("Firestore", "Failed to save admin data: " + e.getMessage()));
    }

    private void redirectToAdminDashboard() {
        Intent intent = new Intent(LoginAdmin.this, AdminActivity.class);
        startActivity(intent);
        finish();
    }

    private void showSnackbar(String message, boolean isSuccess) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(isSuccess ? getColor(android.R.color.holo_green_light) : getColor(android.R.color.holo_red_light));
        snackbar.show();
    }
}