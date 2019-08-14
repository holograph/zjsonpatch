/*
 * Copyright 2016 flipkart.com zjsonpatch.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.zjsonpatch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.collections4.ListUtils;

import java.util.*;

/**
 * User: gopi.vishwakarma
 * Date: 30/07/14
 */

public final class JsonDiff {
    private final Map<JsonNode, SortedSet<JsonPointer>> valueMap;
    private final List<Diff> diffs;
    private final EnumSet<DiffFlags> flags;

    private static class JsonPointerLengthComparator implements Comparator<JsonPointer> {
        static JsonPointerLengthComparator INSTANCE = new JsonPointerLengthComparator();

        @Override
        public int compare(JsonPointer o1, JsonPointer o2) {
            return o1.size() - o2.size();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof JsonPointerLengthComparator;
        }
    }

    JsonDiff(EnumSet<DiffFlags> flags, List<Diff> diffs) {
        this.flags = flags.clone();
        this.diffs = new ArrayList<Diff>();
        if (diffs != null) this.diffs.addAll(diffs);
        this.valueMap = flags.contains(DiffFlags.OMIT_COPY_OPERATION)
                ? null
                : new HashMap<JsonNode, SortedSet<JsonPointer>>();
    }

    List<Diff> getDiffs() {
        return diffs;
    }

    public static JsonNode asJson(final JsonNode source, final JsonNode target) {
        return asJson(source, target, DiffFlags.defaults());
    }

    public static JsonNode asJson(final JsonNode source, final JsonNode target, EnumSet<DiffFlags> flags) {
        return create(source, target, flags).getJsonNodes();
    }

    static JsonDiff create(final JsonNode source, final JsonNode target, EnumSet<DiffFlags> flags) {
        JsonDiff diff = new JsonDiff(flags, null);

        if (!flags.contains(DiffFlags.OMIT_COPY_OPERATION))
            diff.populateValueMap(JsonPointer.ROOT, source);

        // generating diffs in the order of their occurrence
        diff.generateDiffs(JsonPointer.ROOT, source, target);

        if (!flags.contains(DiffFlags.OMIT_MOVE_OPERATION))
            // Merging remove & add to move operation
            diff.introduceMoveOperation();

        return diff;
    }

    private JsonPointer validSourceFor(JsonNode value) {
        if (flags.contains(DiffFlags.OMIT_COPY_OPERATION)) return null;
        SortedSet<JsonPointer> pointers = valueMap.get(value);
        return (pointers == null || pointers.isEmpty()) ? null : pointers.first();
    }

    private void populateValueMap(JsonPointer path, JsonNode value) {
        SortedSet<JsonPointer> pointers = valueMap.get(value);
        if (pointers == null) {
            pointers = new TreeSet<JsonPointer>(JsonPointerLengthComparator.INSTANCE);
            valueMap.put(value, pointers);
        }
        pointers.add(path);
        if (value.isArray()) {
            for (int i = 0; i < value.size(); ++i)
                populateValueMap(path.append(i), value.get(i));
        } else if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                populateValueMap(path.append(field.getKey()), field.getValue());
            }
        }
    }

    private void removeFromValueMap(JsonPointer path, JsonNode value) {
        SortedSet<JsonPointer> pointers = valueMap.get(value);
        if (pointers == null)
            throw new IllegalStateException("No valid pointers found for value " + value);
        if (!pointers.remove(path))
            return;
        if (value.isArray()) {
            for (int i = 0; i < value.size(); ++i)
                removeFromValueMap(path.append(i), value.get(i));
        } else if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                removeFromValueMap(path.append(field.getKey()), field.getValue());
            }
        }
    }

    /**
     * This method merge 2 diffs (remove then add, or vice versa) with same value into one Move operation,
     * all the core logic resides here only
     */
    void introduceMoveOperation() {
        for (int i = 0; i < diffs.size(); i++) {
            Diff diff1 = diffs.get(i);

            // if not remove OR add, move to next diff
            if (!(Operation.REMOVE == diff1.getOperation() ||
                    Operation.ADD == diff1.getOperation())) {
                continue;
            }

            for (int j = i + 1; j < diffs.size(); j++) {
                Diff diff2 = diffs.get(j);
                if (!diff1.getValue().equals(diff2.getValue())) {
                    continue;
                }

                Diff moveDiff = null;
                if (Operation.REMOVE == diff1.getOperation() &&
                        Operation.ADD == diff2.getOperation()) {

                    JsonPointer relativePath = computeRelativePath(diff2.getPath(), i + 1, j - 1, diffs);
                    moveDiff = new Diff(Operation.MOVE, diff1.getPath(), diff1.getValue(), relativePath);
                } else if (Operation.ADD == diff1.getOperation() &&
                        Operation.REMOVE == diff2.getOperation()) {

                    JsonPointer relativePath = computeRelativePath(diff2.getPath(), i, j - 1, diffs); // diff1's add should also be considered
                    moveDiff = new Diff(Operation.MOVE, relativePath, diff2.getValue(), diff1.getPath());
                }

                if (moveDiff != null) {
                    diffs.remove(j);
                    diffs.set(i, moveDiff);
                }
            }
        }
    }

    //Note : only to be used for arrays
    //Finds the longest common Ancestor ending at Array
    private static JsonPointer computeRelativePath(JsonPointer path, int startIdx, int endIdx, List<Diff> diffs) {
        List<Integer> counters = new ArrayList<Integer>(path.size());
        for (int i = 0; i < path.size(); i++) {
            counters.add(0);
        }

        for (int i = startIdx; i <= endIdx; i++) {
            Diff diff = diffs.get(i);
            //Adjust relative path according to #ADD and #Remove
            if (Operation.ADD == diff.getOperation() || Operation.REMOVE == diff.getOperation()) {
                updatePath(path, diff, counters);
            }
        }
        return updatePathWithCounters(counters, path);
    }

    private static JsonPointer updatePathWithCounters(List<Integer> counters, JsonPointer path) {
        List<JsonPointer.RefToken> tokens = path.decompose();
        for (int i = 0; i < counters.size(); i++) {
            int value = counters.get(i);
            if (value != 0) {
                int currValue = tokens.get(i).getIndex();
                tokens.set(i, new JsonPointer.RefToken(Integer.toString(currValue + value)));
            }
        }
        return new JsonPointer(tokens);
    }

    private static void updatePath(JsonPointer path, Diff pseudo, List<Integer> counters) {
        //find longest common prefix of both the paths

        if (pseudo.getPath().size() <= path.size()) {
            int idx = -1;
            for (int i = 0; i < pseudo.getPath().size() - 1; i++) {
                if (pseudo.getPath().get(i).equals(path.get(i))) {
                    idx = i;
                } else {
                    break;
                }
            }
            if (idx == pseudo.getPath().size() - 2) {
                if (pseudo.getPath().get(pseudo.getPath().size() - 1).isArrayIndex()) {
                    updateCounters(pseudo, pseudo.getPath().size() - 1, counters);
                }
            }
        }
    }

    private static void updateCounters(Diff pseudo, int idx, List<Integer> counters) {
        if (Operation.ADD == pseudo.getOperation()) {
            counters.set(idx, counters.get(idx) - 1);
        } else {
            if (Operation.REMOVE == pseudo.getOperation()) {
                counters.set(idx, counters.get(idx) + 1);
            }
        }
    }

    ArrayNode getJsonNodes() {
        final ArrayNode patch = JsonNodeFactory.instance.arrayNode();
        for (Diff diff : diffs) {
            if (flags.contains(DiffFlags.EMIT_TEST_OPERATIONS)) {
                ObjectNode testNode = emitTestNode(diff, flags);
                if (testNode != null)
                    patch.add(testNode);
            }
            ObjectNode jsonNode = renderJsonNode(diff, flags);
            patch.add(jsonNode);
        }
        return patch;
    }

    private static ObjectNode emitTestNode(Diff diff, EnumSet<DiffFlags> flags) {
        ObjectNode testNode;

        switch (diff.getOperation()) {
            case REPLACE:
            case MOVE:
            case COPY:
                testNode = JsonNodeFactory.instance.objectNode();
                testNode.put(Constants.OP, Operation.TEST.rfcName());
                testNode.put(Constants.PATH, diff.getPath().toString());
                testNode.set(Constants.VALUE, diff.getSrcValue());
                return testNode;

            case REMOVE:
                testNode = JsonNodeFactory.instance.objectNode();
                testNode.put(Constants.OP, Operation.TEST.rfcName());
                testNode.put(Constants.PATH, diff.getPath().toString());
                testNode.set(Constants.VALUE, diff.getValue());
                return testNode;

            case ADD:
            case TEST:
                return null;

            default:
                // Safety net
                throw new IllegalArgumentException("Unknown operation specified:" + diff.getOperation());
        }
    }

    private static ObjectNode renderJsonNode(Diff diff, EnumSet<DiffFlags> flags) {
        ObjectNode jsonNode = JsonNodeFactory.instance.objectNode();
        jsonNode.put(Constants.OP, diff.getOperation().rfcName());

        switch (diff.getOperation()) {
            case MOVE:
            case COPY:
                jsonNode.put(Constants.FROM, diff.getPath().toString());    // required {from} only in case of Move Operation
                jsonNode.put(Constants.PATH, diff.getToPath().toString());  // destination Path
                break;

            case REMOVE:
                jsonNode.put(Constants.PATH, diff.getPath().toString());
                if (!flags.contains(DiffFlags.OMIT_VALUE_ON_REMOVE))
                    jsonNode.set(Constants.VALUE, diff.getValue());
                break;

            case REPLACE:
                if (flags.contains(DiffFlags.ADD_ORIGINAL_VALUE_ON_REPLACE)) {
                    jsonNode.set(Constants.FROM_VALUE, diff.getSrcValue());
                }
            case ADD:
            case TEST:
                jsonNode.put(Constants.PATH, diff.getPath().toString());
                jsonNode.set(Constants.VALUE, diff.getValue());
                break;

            default:
                // Safety net
                throw new IllegalArgumentException("Unknown operation specified:" + diff.getOperation());
        }

        return jsonNode;
    }

    void generateDiffs(JsonPointer path, JsonNode source, JsonNode target) {
        if (!source.equals(target)) {
            final NodeType sourceType = NodeType.getNodeType(source);
            final NodeType targetType = NodeType.getNodeType(target);

            if (sourceType == NodeType.ARRAY && targetType == NodeType.ARRAY) {
                //both are arrays
                compareArray(path, source, target);
            } else if (sourceType == NodeType.OBJECT && targetType == NodeType.OBJECT) {
                //both are json
                compareObjects(path, source, target);
            } else {
                //can be replaced
                // TODO potential optimization with COPY
                diffs.add(new Diff(Operation.REPLACE, path, source, target));
            }
        }
    }

    private void compareArray(JsonPointer path, JsonNode source, JsonNode target) {
        if (!flags.contains(DiffFlags.OMIT_COPY_OPERATION))
            removeFromValueMap(path, source);

        List<JsonNode> lcs = getLCS(source, target);

        int srcIdx = 0;
        int targetIdx = 0;
        int lcsIdx = 0;
        int srcSize = source.size();
        int targetSize = target.size();
        int lcsSize = lcs.size();

        int pos = 0;
        while (lcsIdx < lcsSize) {
            JsonNode lcsNode = lcs.get(lcsIdx);
            JsonNode srcNode = source.get(srcIdx);
            JsonNode targetNode = target.get(targetIdx);

            if (lcsNode.equals(srcNode) && lcsNode.equals(targetNode)) { // Both are same as lcs node, nothing to do here
                srcIdx++;
                targetIdx++;
                lcsIdx++;
                pos++;
            } else {
                if (lcsNode.equals(srcNode)) { // src node is same as lcs, but not targetNode
                    //addition
                    JsonPointer currPath = path.append(pos);
                    JsonPointer sourcePath = validSourceFor(targetNode);
                    if (sourcePath != null) {
                        diffs.add(new Diff(Operation.COPY, sourcePath, targetNode, currPath));
                    } else
                        diffs.add(new Diff(Operation.ADD, currPath, targetNode));
                    pos++;
                    targetIdx++;
                } else if (lcsNode.equals(targetNode)) { //targetNode node is same as lcs, but not src
                    //removal,
                    JsonPointer currPath = path.append(pos);
                    diffs.add(new Diff(Operation.REMOVE, currPath, srcNode));
                    srcIdx++;
                } else {
                    JsonPointer currPath = path.append(pos);
                    //both are unequal to lcs node
                    generateDiffs(currPath, srcNode, targetNode);
                    srcIdx++;
                    targetIdx++;
                    pos++;
                }
            }
        }

        while ((srcIdx < srcSize) && (targetIdx < targetSize)) {
            JsonNode srcNode = source.get(srcIdx);
            JsonNode targetNode = target.get(targetIdx);
            JsonPointer currPath = path.append(pos);
            generateDiffs(currPath, srcNode, targetNode);
            srcIdx++;
            targetIdx++;
            pos++;
        }
        pos = addRemaining(path, target, pos, targetIdx, targetSize);
        removeRemaining(path, pos, srcIdx, srcSize, source);

        if (!flags.contains(DiffFlags.OMIT_COPY_OPERATION))
            populateValueMap(path, target);
    }

    private void removeRemaining(JsonPointer path, int pos, int srcIdx, int srcSize, JsonNode source) {
        while (srcIdx < srcSize) {
            JsonPointer currPath = path.append(pos);
            diffs.add(new Diff(Operation.REMOVE, currPath, source.get(srcIdx)));
            srcIdx++;
        }
    }

    private int addRemaining(JsonPointer path, JsonNode target, int pos, int targetIdx, int targetSize) {
        while (targetIdx < targetSize) {
            JsonNode jsonNode = target.get(targetIdx);
            JsonPointer currPath = path.append(pos);
            diffs.add(new Diff(Operation.ADD, currPath, jsonNode.deepCopy()));
            pos++;
            targetIdx++;
        }
        return pos;
    }

    private void compareObjects(JsonPointer path, JsonNode source, JsonNode target) {
        Iterator<String> keysFromSrc = source.fieldNames();
        while (keysFromSrc.hasNext()) {
            String key = keysFromSrc.next();
            JsonPointer currPath = path.append(key);
            JsonNode sourceValue = source.get(key);
            if (!target.has(key)) {
                //remove case
                if (!flags.contains(DiffFlags.OMIT_COPY_OPERATION))
                    removeFromValueMap(currPath, sourceValue);
                diffs.add(new Diff(Operation.REMOVE, currPath, sourceValue));
                continue;
            }
            generateDiffs(currPath, sourceValue, target.get(key));
        }
        Iterator<String> keysFromTarget = target.fieldNames();
        while (keysFromTarget.hasNext()) {
            String key = keysFromTarget.next();
            if (!source.has(key)) {
                // add case
                JsonNode targetValue = target.get(key);
                JsonPointer currPath = path.append(key);
                JsonPointer fromPath = validSourceFor(targetValue);
                if (fromPath != null) {
                    // copy case
                    diffs.add(new Diff(Operation.COPY, fromPath, targetValue, currPath));
                    populateValueMap(currPath, targetValue);
                } else
                    diffs.add(new Diff(Operation.ADD, currPath, targetValue));
            }
        }
    }

    private static List<JsonNode> getLCS(final JsonNode first, final JsonNode second) {
        return ListUtils.longestCommonSubsequence(InternalUtils.toList((ArrayNode) first), InternalUtils.toList((ArrayNode) second));
    }
}
