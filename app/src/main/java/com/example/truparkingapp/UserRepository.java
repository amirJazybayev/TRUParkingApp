package com.example.truparkingapp;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Every Firebase call for accounts goes through here, so activities never talk
 * to Firebase directly.
 *
 * Firestore layout:
 *   users/{uid}            -> User (username, email, userType, createdAt)
 *   usernames/{lowercase}  -> { uid }   one document per taken username
 */
public class UserRepository {

    public interface Callback<T> {
        void onSuccess(T result);

        void onError(Exception e);
    }

    /** Thrown when the chosen username already belongs to someone. */
    public static class UsernameTakenException extends Exception {
        public UsernameTakenException() {
            super("Username taken");
        }
    }

    private static final String USERS = "users";
    private static final String USERNAMES = "usernames";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void isUsernameAvailable(String username, Callback<Boolean> callback) {
        db.collection(USERNAMES)
                .document(UsernameValidator.toKey(username))
                .get()
                .addOnSuccessListener(doc -> callback.onSuccess(!doc.exists()))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Sign-up flow: check the username, create the login, then claim the
     * username and save the profile in one transaction. If the claim fails the
     * new login is deleted again so the email can be reused.
     */
    public void signUp(String email, String password, String username, String userType,
                       Callback<FirebaseUser> callback) {
        // A guest session must not carry over into the new account.
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.isAnonymous()) {
            auth.signOut();
        }

        isUsernameAvailable(username, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean available) {
                if (!available) {
                    callback.onError(new UsernameTakenException());
                    return;
                }
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(result -> claimUsernameAndSaveProfile(
                                result.getUser(), username.trim(), email, userType, callback))
                        .addOnFailureListener(callback::onError);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    private void claimUsernameAndSaveProfile(FirebaseUser user, String username, String email,
                                             String userType, Callback<FirebaseUser> callback) {
        DocumentReference nameRef = db.collection(USERNAMES).document(UsernameValidator.toKey(username));
        DocumentReference userRef = db.collection(USERS).document(user.getUid());

        db.runTransaction((Transaction.Function<Void>) transaction -> {
                    DocumentSnapshot existing = transaction.get(nameRef);
                    if (existing.exists()) {
                        throw new FirebaseFirestoreException("Username taken",
                                FirebaseFirestoreException.Code.ALREADY_EXISTS);
                    }
                    Map<String, Object> claim = new HashMap<>();
                    claim.put("uid", user.getUid());
                    transaction.set(nameRef, claim);
                    transaction.set(userRef, new User(user.getUid(), username, email, userType));
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    user.sendEmailVerification();
                    callback.onSuccess(user);
                })
                .addOnFailureListener(e -> {
                    Exception reported = isAlreadyExists(e) ? new UsernameTakenException() : e;
                    user.delete().addOnCompleteListener(task -> {
                        auth.signOut();
                        callback.onError(reported);
                    });
                });
    }

    private static boolean isAlreadyExists(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.ALREADY_EXISTS;
    }

    public void logIn(String email, String password, Callback<FirebaseUser> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
                .addOnFailureListener(callback::onError);
    }

    /** Guests get an anonymous Firebase login so the lot data rules can still require auth. */
    public void continueAsGuest(Callback<FirebaseUser> callback) {
        auth.signInAnonymously()
                .addOnSuccessListener(result -> callback.onSuccess(result.getUser()))
                .addOnFailureListener(callback::onError);
    }

    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    public void signOut() {
        auth.signOut();
    }
}
