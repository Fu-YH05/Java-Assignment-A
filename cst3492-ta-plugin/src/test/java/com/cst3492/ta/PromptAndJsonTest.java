package com.cst3492.ta;

import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.prompt.PromptBuilder;
import com.cst3492.ta.util.TextUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptAndJsonTest {

    @Test
    void groundedSystemPromptContainsCitationProtocol() {
        String system = PromptBuilder.systemGrounded();
        assertTrue(system.contains("{{cite:docId#page}}"));
        assertTrue(system.contains(PromptBuilder.GENERAL_DISCLAIMER));
    }

    @Test
    void userPromptNumbersTheExcerpts() {
        com.cst3492.ta.model.KnowledgeChunk c = new com.cst3492.ta.model.KnowledgeChunk();
        c.docId = "lec04";
        c.pageFrom = 12;
        c.title = "IoC";
        c.text = "The container wires beans.";
        String user = PromptBuilder.userPrompt("What is IoC?", "class A {}",
                List.of(c, c));
        assertTrue(user.contains("[1] docId=lec04, page=12, title=IoC"));
        assertTrue(user.contains("[2] docId=lec04"));
        assertTrue(user.contains("```java"));
        assertTrue(user.contains("Question: What is IoC?"));
        assertTrue(user.contains("class A {}"));
    }

    @Test
    void generalSystemPromptRequiresDisclaimer() {
        assertTrue(PromptBuilder.systemGeneral().contains(PromptBuilder.GENERAL_DISCLAIMER));
    }

    @Test
    void firstJsonObjectFindsBalancedBraces() {
        String text = "Sure! {\"a\":{\"b\":\"}\"},\"c\":1} trailing";
        assertEquals("{\"a\":{\"b\":\"}\"},\"c\":1}", TextUtil.firstJsonObject(text));
        assertEquals(null, TextUtil.firstJsonObject("no json here"));
    }

    @Test
    void completeJsonExtractsFromNoisyReply() {
        LlmClient noisy = (messages, temperature, maxTokens) ->
                "Here is the JSON you requested:\n{\"type\":\"feat\",\"subject\":\"add x\"}\nDone.";
        String json = noisy.completeJson(List.of(new Message("user", "hi")), 0.1, 100);
        assertEquals("{\"type\":\"feat\",\"subject\":\"add x\"}", json);
    }

    @Test
    void tokenEstimatorCountsWords() {
        assertEquals(3, TextUtil.estimateTokens("one two  three\n"));
        assertEquals(0, TextUtil.estimateTokens("   "));
    }
}
