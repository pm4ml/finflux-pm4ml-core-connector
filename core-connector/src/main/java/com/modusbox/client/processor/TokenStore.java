package com.modusbox.client.processor;

import java.time.Instant;

public class TokenStore {
    //////////////////// Refresh Token methods ////////////////////
    public static String refreshToken = "";
    public static Instant refreshTokenExpirationTimestamp = Instant.now();

    public String getRefreshToken() {
        if (hasRefreshTokenExpired()){
            return "";
        }
        return refreshToken;
    }

    //  Format of t is UUID: "083464d7-a606-4423-a2fb-fc3ce0de8abe"
    //  Format of e is UTC: "2021-10-12T07:53:08.998Z"
    public void setRefreshToken(String t, String e) {
        refreshToken = t;
        refreshTokenExpirationTimestamp = Instant.now().plusSeconds(Long.parseLong(e));
    }

    public boolean hasRefreshTokenExpired() {
        if (refreshToken.equals("")) {
            return true;
        } else if (refreshTokenExpirationTimestamp.isBefore(Instant.now())) {
            return true;
        }
        return false;
    }

    //////////////////// Access Token methods ////////////////////
    public static String accessToken = "";
    public static Instant accessTokenExpirationTimestamp = Instant.now();

    public String getAccessToken() {
        if (hasAccessTokenExpired()){
            return "";
        }
        return accessToken;
    }

    //  Format of t is UUID: "083464d7-a606-4423-a2fb-fc3ce0de8abe"
    //  Format of e is in seconds: "3600"
    public void setAccessToken(String t, String e) {
        accessToken = t;
        accessTokenExpirationTimestamp = Instant.now().plusSeconds(Long.parseLong(e));
    }

    public boolean hasAccessTokenExpired() {
        if (accessToken.equals("")) {
            return true;
        } else if (accessTokenExpirationTimestamp.isBefore(Instant.now())) {
            return true;
        }
        return false;
    }


}
