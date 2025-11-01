package com.example.projectreviewsystem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class LoginResearcher extends AppCompatActivity {

    private static final int RC_SIGN_IN = 100;
    private EditText emailEditText, passwordEditText;
    private Button registerButton;
    private TextView googleSignIn, forgotPassword;

    private FirebaseAuth firebaseAuth;
    private GoogleSignInClient googleSignInClient;
    private DatabaseReference databaseReference; // Reference to the Realtime Database

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_research);

        firebaseAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("researchers"); // Reference to the "researchers" node
        configureGoogleSignIn();

        emailEditText = findViewById(R.id.emailid);
        passwordEditText = findViewById(R.id.password);
        registerButton = findViewById(R.id.login_button);
        googleSignIn = findViewById(R.id.google);
//        forgotPassword = findViewById(R.id.forgotpassword);

        registerButton.setOnClickListener(v -> registerUser ());
        googleSignIn.setOnClickListener(v -> startGoogleSignIn());
//        forgotPassword.setOnClickListener(v -> openEmailApp());

        emailEditText.setTextColor(getResources().getColor(android.R.color.white));
        passwordEditText.setTextColor(getResources().getColor(android.R.color.white));
    }

    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

//    private void registerUser () {
//        String email = emailEditText.getText().toString().trim();
//        String password = passwordEditText.getText().toString().trim();
//
//        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
//            Snackbar.make(findViewById(R.id.main), "Please enter a valid email", Snackbar.LENGTH_SHORT).show();
//            return;
//        }
//
//        if (password.length() < 6) {
//            Snackbar.make(findViewById(R.id.main), "Password must be at least 6 characters", Snackbar.LENGTH_SHORT).show();
//            return;
//        }
//
//        firebaseAuth.createUserWithEmailAndPassword(email, password)
//                .addOnCompleteListener(task -> {
//            if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
//                String userId = firebaseAuth.getCurrentUser().getUid();
//                String userName = email.split("@")[0]; // Extract username from email
//                checkIfUserExists(userId, email, userName,""); // Check if user exists in Realtime Database
//            } else {
//                showSnackbar("Registration Failed: " + (task.getException() != null ? task.getException().getMessage() : ""), false);
//            }
//        });
//    }
private void registerUser() {
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

    firebaseAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                    String userId = firebaseAuth.getCurrentUser().getUid();
                    String userName = email.split("@")[0];
                    checkIfUserExists(userId, email, userName, "");
                } else {
                    Exception e = task.getException();
                    if (e != null && e.getMessage() != null && e.getMessage().contains("already in use")) {
                        // 🔁 Account already exists — try signing in instead
                        signInExistingUser(email, password);
                    } else {
                        showSnackbar("Registration Failed: " + e.getMessage(), false);
                    }
                }
            });
}

    private void signInExistingUser(String email, String password) {
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser() != null) {
                        String userId = firebaseAuth.getCurrentUser().getUid();
                        String userName = email.split("@")[0];
                        checkIfUserExists(userId, email, userName, "");
                    } else {
                        showSnackbar("Sign-in Failed: " +
                                (task.getException() != null ? task.getException().getMessage() : ""), false);
                    }
                });
    }


    private void checkIfUserExists(String userId, String email, String userName, String profilePhotoUrl) {
        String[] s=userName.split(" ");
        String userName2= s[0];
        databaseReference.child(userName2).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                // User already exists, redirect to Faculty activity
                redirectToFacultyActivity(userId, userName, email);
            } else {
                // User does not exist , save user data to Realtime Database
                saveUserDataToRealtimeDatabase(userId, email, userName,profilePhotoUrl);
            }
        });
    }


    private void saveUserDataToRealtimeDatabase(String userId, String email, String userName,String profilePhotoUrl) {
        // Create a map for the researcher data
        Map<String, Object> researcherData = new HashMap<>();
        researcherData.put("email", email);
        researcherData.put("profile_photo",profilePhotoUrl);
        researcherData.put("name", userName);
        researcherData.put("department", ""); // Set default or empty department
        researcherData.put("designation", ""); // Set default or empty designation
        researcherData.put("projects", new HashMap<>());

        String[] uniqueId = userName.split(" ");
        // Save the researcher data under the "researchers" node
        databaseReference.child(uniqueId[0]).setValue(researcherData)
                .addOnSuccessListener(aVoid -> {
                    Log.i("RealtimeDatabase", "Researcher data saved successfully");
                    redirectToDesignationActivity(userId, userName, email, researcherData.get("projects"),profilePhotoUrl); // Pass email to Designation activity
                })
                .addOnFailureListener(e -> Log.e("RealtimeDatabase", "Failed to save researcher data", e));
    }
    private void redirectToFacultyActivity(String userId, String userName, String email) {
        Intent intent = new Intent(LoginResearcher.this, Faculty.class); // Replace with your Faculty activity class
        intent.putExtra("USER_ID", userId); // Pass the user ID to Faculty activity
        intent.putExtra("USER_NAME", userName); // Pass the user name
        intent.putExtra("EMAIL", email); // Pass the email
        startActivity(intent);
        finish();
    }

    private void redirectToDesignationActivity(String userId, String userName, String email, Object projects,String profilePhotoUrl) {
        Intent intent = new Intent(LoginResearcher.this, Designation.class);
        intent.putExtra("USER_ID", userId); // Pass the user ID to Designation
        intent.putExtra("USER_NAME", userName); // Pass the user name
        intent.putExtra("EMAIL", email); // Pass the email
        intent.putExtra("PROJECTS", (HashMap<String, Object>) projects); // Pass the projects to Designation
        intent.putExtra("profile_photo",profilePhotoUrl);
        startActivity(intent);
        finish();
    }

    private void startGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
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
            showSnackbar("Google Sign-In Failed: " + e.getMessage(), false);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(account.getIdToken(), null))
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && firebaseAuth.getCurrentUser () != null) {
                        String userId = firebaseAuth.getCurrentUser ().getUid();
                        String userName = account.getDisplayName(); // Get the user's name
                        String email = account.getEmail();
                        String profilePhotoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null; // Get the user's profile photo URL

                        checkIfUserExists(userId, email, userName, profilePhotoUrl); // Check if user exists in Realtime Database
                    } else {
                        showSnackbar("Google Sign-In Failed: " + (task.getException() != null ? task.getException().getMessage() : ""), false);
                    }
                });
    }

    private void openEmailApp() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Reset Password");
        startActivity(Intent.createChooser(intent, "Open Email"));
    }

    private void showSnackbar(String message, boolean isSuccess) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(isSuccess ? getColor(android.R.color.holo_green_light) : getColor(android.R.color.holo_red_light));
        snackbar.show();
    }
}