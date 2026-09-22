package com.example.cookbook;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.function.Predicate;

/**
 * Deciding what is worth trying again.
 *
 * The distinction that matters is not "did it fail" but "would the same request succeed later".
 * A 429 or a 503 says yes. A 400, a 401 or a content-policy refusal says no, and retrying it only
 * spends the budget before returning the same error.
 */
final class Transient {

    /** The codes a provider uses to mean "later". 408 and 409 are included because OpenAI uses both. */
    private static final int[] RETRYABLE_STATUS = { 408, 409, 429, 500, 502, 503, 504 };

    private Transient() {
    }

    static Predicate<Throwable> classifier() {
        return Transient::isTransient;
    }

    static boolean isTransient(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            // Spring AI has already made this call for its own retry layer; reuse it rather than
            // second-guess it.
            if (e instanceof NonTransientAiException) {
                return false;
            }
            if (e instanceof TransientAiException) {
                return true;
            }
            if (e instanceof HttpStatusCodeException http && hasRetryableStatus(http.getStatusCode().value())) {
                return true;
            }
            // The two non-HTTP failures worth repeating. A timeout may never have reached the
            // provider at all, and a refused connection is what a rolling restart behind a load
            // balancer looks like from the client side.
            if (e instanceof SocketTimeoutException || e instanceof ConnectException) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRetryableStatus(int status) {
        for (int candidate : RETRYABLE_STATUS) {
            if (candidate == status) {
                return true;
            }
        }
        return false;
    }
}
