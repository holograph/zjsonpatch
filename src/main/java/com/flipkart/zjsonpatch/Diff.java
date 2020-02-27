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

/**
 * User: gopi.vishwakarma
 * Date: 30/07/14
 */
class Diff {
    private final Operation operation;
    private final JsonPointer path;
    private final JsonNode value;
    private JsonPointer toPath; //only to be used in move operation
    private final JsonNode srcValue; // only used in replace operation

    Diff(Operation operation, JsonPointer path, JsonNode value) {
        this.operation = operation;
        this.path = path;
        this.value = value;
        this.srcValue = null;
    }

    Diff(Operation operation, JsonPointer fromPath, JsonPointer toPath) {
        this.operation = operation;
        this.path = fromPath;
        this.toPath = toPath;
        this.value = null;
        this.srcValue = null;
    }
    
    Diff(Operation operation, JsonPointer path, JsonNode srcValue, JsonNode value) {
        this.operation = operation;
        this.path = path;
        this.value = value;
        this.srcValue = srcValue;
    }

    public Operation getOperation() {
        return operation;
    }

    public JsonPointer getPath() {
        return path;
    }

    public JsonNode getValue() {
        return value;
    }

    public static Diff generateDiff(Operation replace, JsonPointer path, JsonNode target) {
        return new Diff(replace, path, target);
    }
    
    public static Diff generateDiff(Operation replace, JsonPointer path, JsonNode source, JsonNode target) {
        return new Diff(replace, path, source, target);
    }

    JsonPointer getToPath() {
        return toPath;
    }
    
    public JsonNode getSrcValue(){
        return srcValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Diff diff = (Diff) o;

        if (operation != diff.operation) return false;
        if (!path.equals(diff.path)) return false;
        if (value != null ? !value.equals(diff.value) : diff.value != null) return false;
        if (toPath != null ? !toPath.equals(diff.toPath) : diff.toPath != null) return false;
        return srcValue != null ? srcValue.equals(diff.srcValue) : diff.srcValue == null;
    }

    @Override
    public int hashCode() {
        int result = operation.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (toPath != null ? toPath.hashCode() : 0);
        result = 31 * result + (srcValue != null ? srcValue.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Diff{" +
                "operation=" + operation +
                ", path=" + path +
                ", value=" + value +
                ", toPath=" + toPath +
                ", srcValue=" + srcValue +
                '}';
    }
}
