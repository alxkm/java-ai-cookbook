package com.example.cookbook;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatBasicTest {

    @Test
    void sendsSystemAndUserMessages() {
        StubChatModel model = new StubChatModel("42");

        String answer = model.chat(
                SystemMessage.from("You are a terse Java assistant."),
                UserMessage.from("How many?")).aiMessage().text();

        assertThat(answer).isEqualTo("42");
        assertThat(model.requests).hasSize(1);
        assertThat(model.requests.get(0).messages()).hasSize(2);
    }
}
