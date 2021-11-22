package com.ninecookies.wiremock.extensions.api;

/**
 * Represents callback related authentication information.
 *
 * @author M.Scheepers
 * @since 0.0.6
 * @see Callback
 * @see Callbacks
 */
public class Authentication {

    /**
     * Defines the supported authentication methods.
     */
    public enum Type {
        /**
         * The Basic type for user name / password authentication.
         */
        BASIC,
        /**
         * The Bearer type for token authentication.
         */
        BEARER
    }

    private Type type = Type.BASIC;
    private String username;
    private String password;
    private String token;

    /**
     * Gets the type.
     *
     * @return the type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the Basic authentication user name.
     *
     * @return the user name.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the Basic authentication password.
     *
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Gets the Bearer authentication token.
     *
     * @return the token.
     */
    public String getToken() {
        return token;
    }

    /**
     * Initialize a new instance of the {@link Authentication} with the specified arguments.
     *
     * @param username
     *            the Basic authentication user name.
     * @param password
     *            the Basic authentication password.
     */
    public static Authentication of(String username, String password) {
        Authentication result = new Authentication();
        result.username = username;
        result.password = password;
        return result;
    }

    /**
     * Initialize a new instance of the {@link Authentication} with the specified argument.
     *
     * @param bearerToken
     *            the Bearer authentication token.
     */
    public static Authentication of(String bearerToken) {
        Authentication result = new Authentication();
        result.type = Type.BEARER;
        result.token = bearerToken;
        return result;
    }
}
