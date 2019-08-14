package com.flipkart.zjsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.Test;

public class TestNodesEmissionTest {

    private static ObjectMapper mapper = new ObjectMapper();

    private static EnumSet<DiffFlags> flags = DiffFlags.defaultsWith(DiffFlags.EMIT_TEST_OPERATIONS);

    @Test
    public void testNodeEmittedBeforeReplaceOperation() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":\"original\"}");
        JsonNode target = mapper.readTree("{\"key\":\"replaced\"}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key\",\"value\":\"original\"}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeCopyOperation() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":\"original\"}");
        JsonNode target = mapper.readTree("{\"key\":\"original\", \"copied\":\"original\"}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key\",\"value\":\"original\"}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeCopyOperationToArrayPrefix() throws IOException {
        JsonNode source =
                mapper.readTree("{\"array\":[{\"value\":1},{\"value\":2},{\"value\":3}],\"all\":[{\"value\":1},{\"value\":2},{\"value\":3},{\"value\":4}]}");
        JsonNode target =
                mapper.readTree("{\"array\":[{\"value\":4},{\"value\":1},{\"value\":2},{\"value\":3}],\"all\":[{\"value\":1},{\"value\":2},{\"value\":3},{\"value\":4}]}");
        JsonNode diff = JsonDiff.asJson(source, target, DiffFlags.defaultsWith(DiffFlags.EMIT_TEST_OPERATIONS));

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/all/3\",\"value\":{\"value\":4}}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeMoveOperation() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":\"original\"}");
        JsonNode target = mapper.readTree("{\"moved\":\"original\"}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key\",\"value\":\"original\"}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeRemoveOperation() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":\"original\"}");
        JsonNode target = mapper.readTree("{}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key\",\"value\":\"original\"}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeRemoveFromMiddleOfArray() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":[1,2,3]}");
        JsonNode target = mapper.readTree("{\"key\":[1,3]}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key/1\",\"value\":2}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }

    @Test
    public void testNodeEmittedBeforeRemoveFromTailOfArray() throws IOException {
        JsonNode source = mapper.readTree("{\"key\":[1,2,3]}");
        JsonNode target = mapper.readTree("{\"key\":[1,2]}");

        JsonNode diff = JsonDiff.asJson(source, target, flags);

        JsonNode testNode = mapper.readTree("{\"op\":\"test\",\"path\":\"/key/2\",\"value\":3}");
        assertEquals(2, diff.size());
        assertEquals(testNode, diff.iterator().next());
    }
}
