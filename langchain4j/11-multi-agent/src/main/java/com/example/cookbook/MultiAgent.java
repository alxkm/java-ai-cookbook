package com.example.cookbook;

import java.util.List;

public class MultiAgent {

    public static void main(String[] args) {
        SupportDesk desk = new SupportDesk(Models.chat());

        List<String> messages = args.length > 0
                ? List.of(String.join(" ", args))
                : List.of("Where is my order A-1002?",
                        "I was charged twice for the same invoice.",
                        "Do you ship to Norway?");

        for (String message : messages) {
            SupportDesk.Handled handled = desk.handle(message);

            System.out.println("> " + message);
            System.out.printf("  routed to %s (%s)%n", handled.desk(), handled.reason());
            System.out.println(handled.answer());
            System.out.println();
        }
    }
}
