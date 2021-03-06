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
         * The Basic authentication type.
         */
        BASIC
    }

    private Type type = Type.BASIC;
    private String username;
    private String password;

    /**
     * Gets the type.
     *
     * @return the type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the user name.
     *
     * @return the user name.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password.
     *
     * @return the password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Initialize a new instance of the {@link Authentication} with the specified arguments.
     *
     * @param username
     *            the authentication user name.
     * @param password
     *            the authentication password.
     */
    public static Authentication of(String username, String password) {
        Authentication result = new Authentication();
        result.username = username;
        result.password = password;
        return result;
    }
}
