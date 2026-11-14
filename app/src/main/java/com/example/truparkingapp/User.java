package com.example.truparkingapp;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/** Profile stored at users/{uid} in Firestore. */
public class User {

    public static final String TYPE_STUDENT = "Student";
    public static final String TYPE_STAFF = "Staff";
    public static final String TYPE_VISITOR = "Visitor";

    private String uid;
    private String username;
    private String email;
    private String userType;
    @ServerTimestamp
    private Date createdAt;

    /** Needed by Firestore to rebuild the object from a document. */
    public User() {
    }

    public User(String uid, String username, String email, String userType) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.userType = userType;
    }

    public String getUid() {
        return uid;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getUserType() {
        return userType;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
