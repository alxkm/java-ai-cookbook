package com.example.cookbook;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import java.util.function.Predicate;

/**
 * Deciding what is worth trying again.
 *
 * LangChain4j already answers this in its type hierarchy: RateLimitException, TimeoutException and
 * InternalServerException extend RetriableException, while AuthenticationException,
 * InvalidRequestException and ContentFilteredException extend NonRetriableException. Two instanceof
 * checks cover every provider, which is more than the status-code table the Spring AI side needs.
 *
 * The HttpException fallback is for a provider integration that raises a raw HTTP failure without
 * mapping it into that hierarchy.
 */
final class Transient {

    private static final int[] RETRYABLE_STATUS = { 408, 409, 429, 500, 502, 503, 504 };

    private Transient() {
    }

    static Predicate<Throwable> classifier() {
        return Transient::isTransient;
    }

    static boolean isTransient(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (e instanceof NonRetriableException) {
                return false;
            }
            if (e instanceof RetriableException) {
                return true;
            }
            if (e instanceof HttpException http && hasRetryableStatus(http.statusCode())) {
                return true;
            }
            // A refused connection or a socket timeout never reaches that hierarchy - the JDK
            // client raises them before any provider mapping happens. Both are worth repeating:
            // a rolling restart behind a load balancer looks exactly like this from here.
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
