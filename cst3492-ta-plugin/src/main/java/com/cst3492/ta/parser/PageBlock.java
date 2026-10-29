package com.cst3492.ta.parser;

/** The text of one physical page (or slide) of a course material file. */
public record PageBlock(String docName, int pageNo, String title, String text) {
}
