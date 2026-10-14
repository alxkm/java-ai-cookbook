package com.example.cookbook;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicRetryableException;
import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;
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
 *
 * In Spring AI 2.0 the answer comes in three dialects, one per kind of client. The OpenAI and
 * Anthropic models are built on the providers' own SDKs and throw those SDKs' exceptions untouched;
 * Ollama goes through Spring's RestClient and Spring AI's TransientAiException. A classifier that
 * knows only the Spring types sees a real OpenAI 429 as permanent and gives up on exactly the error
 * this recipe is about.
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
            // The SDKs: a status code on every service error, and separate types for a transport
            // failure and for "retries exhausted on something retryable".
            if (e instanceof OpenAIServiceException service) {
                return hasRetryableStatus(service.statusCode());
            }
            if (e instanceof AnthropicServiceException service) {
                return hasRetryableStatus(service.statusCode());
            }
            if (e instanceof OpenAIIoException || e instanceof OpenAIRetryableException
                    || e instanceof AnthropicIoException || e instanceof AnthropicRetryableException) {
                return true;
            }

            // Spring AI's own types, for the models that still go through its RetryTemplate.
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
