package com.sitepark.vips.command;

public record Thumbnail(String source, String target, int width) implements Command {}
