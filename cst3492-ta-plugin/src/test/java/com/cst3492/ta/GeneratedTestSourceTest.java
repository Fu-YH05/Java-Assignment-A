package com.cst3492.ta;

import com.cst3492.ta.util.GeneratedTestSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedTestSourceTest {
    @Test void parsesFencesAndPackageIgnoringCommentsAndStrings() {
        var source = GeneratedTestSource.parse("```java\npackage demo.tests; // class Wrong {}\n"
                + "public class StudentTest { String s = \"class Wrong\"; }\n```");
        assertEquals("demo/tests/StudentTest.java", source.relativePath());
        assertFalse(source.code().contains("```"));
    }
    @Test void supportsPackagePrivateJUnit5Class() {
        assertEquals("StudentTest.java", GeneratedTestSource.parse("class StudentTest {}").relativePath());
    }
    @Test void rejectsIncompleteOrAmbiguousSource() {
        for (String source : new String[]{"class X {", "public class A {} public class B {}", "interface X {}", ""}) {
            assertThrows(IllegalArgumentException.class, () -> GeneratedTestSource.parse(source));
        }
    }
}
