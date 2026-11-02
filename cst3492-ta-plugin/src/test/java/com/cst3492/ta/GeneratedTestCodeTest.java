package com.cst3492.ta;

import com.cst3492.ta.service.GeneratedTestCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedTestCodeTest {
    @Test void extractsOneJavaArtifactAndRejectsAmbiguousOrEmptyResponses() {
        assertEquals("class A {}\n", GeneratedTestCode.extract("Explanation\n```java\nclass A {}\n```"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract("```java\nclass A {}\n```\n```java\nclass B {}\n```"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract("```sql\nSELECT 1;\n```"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract(""));
    }
}
