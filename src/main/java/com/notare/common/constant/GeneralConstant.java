package com.notare.common.constant;

public class GeneralConstant {

    public static final char[] CONSONANTS = {'B','D','F','G','H','J','K','L','M','N','P','R','S','T','V','Z'};
    public static final char[] VOWELS = {'A','E','I','O','U'};
    public static final int LENGTH = 7;
    public static final int MAX_ID_GENERATION_ATTEMPTS = 10;

    /**
     * Ceiling on ids per GET /user/lookup request.
     *
     * <p>Not a formality. Every id becomes a term in an IN clause, and an
     * unbounded batch endpoint is an open invitation: a request carrying fifty
     * thousand ids builds a query the database plans badly and materialises a
     * response nobody asked for. The same reasoning as the 200-operation cap on
     * sync batches.
     *
     * <p>100 comfortably covers the real caller — a page of 20 notifications or
     * shares references at most a few dozen distinct people — while leaving a
     * client that needs more to page through it.
     */
    public static final int MAX_USER_LOOKUP_IDS = 100;
    public static final String TOKEN_TYPE = "Bearer";
    public static final String SESSION_ID_CLAIM = "sid";
    public static final String RESET_PURPOSE_CLAIM = "purpose";
    public static final String RESET_PURPOSE_VALUE = "PASSWORD_RESET";

    public static final String SUBJECT = "Your Notare verification code";
}