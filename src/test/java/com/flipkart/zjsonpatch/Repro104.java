package com.flipkart.zjsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Test;

import javax.swing.*;
import java.io.IOException;
import java.util.EnumSet;

public class Repro104 {


    String p1 = "[\n" +
            "        {\n" +
            "          \"id\": \"id1\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id2\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id3\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id4\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id5\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id6\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\",\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id7\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\",\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id8\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id9\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id10\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id11\",\n" +
            "          \"data\": {\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        }\n" +
            "      ]";

    String p2 = "[\n" +

            "        {\n" +
            "          \"id\": \"id5\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id5\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id1\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id2\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id3\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id4\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id7\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\",\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id2\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id3\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id4\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id6\",\n" +
            "          \"data\": {\n" +
            "            \"referrer\": \"ref1\",\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id8\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id10\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id9\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"id\": \"id11\",\n" +
            "          \"data\": {\n" +
            "            \"title\": \"foo\"\n" +
            "          }\n" +
            "        }\n" +
            "      ]";

    @Test
    public void testProcess6() throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        JsonNode source = mapper.readValue(p1, JsonNode.class);
        JsonNode target = mapper.readValue(p2, JsonNode.class);

        JsonNode diff = JsonDiff.asJson(source, target);

        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(source));
        System.out.println("--------------------");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(target));
        System.out.println("--------------------");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(diff));


        // apply the generated diff to source -> we expect to get the target json but it fails with an exception
        JsonNode targetGeneratedViaPatch = JsonPatch.apply(diff, source);


        /*
            Exception:
            [COPY Operation] Missing field "data" at /10
         */

        /*
        When using zjsonpatch v 0.4.6 no error is thrown but if following assertEquals would reveal
        that applying the patch generates unexpected json

       Add this to maven
       <dependency>
            <groupId>org.skyscreamer</groupId>
            <artifactId>jsonassert</artifactId>
            <version>1.5.0</version>
            <scope>test</scope>
        </dependency>

        JSONAssert.assertEquals(p2, mapper.writeValueAsString(targetGeneratedViaPatch), true);

         */
    }
}