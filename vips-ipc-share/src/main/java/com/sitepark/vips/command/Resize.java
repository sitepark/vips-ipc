package com.sitepark.vips.command;

public record Resize(String source, String target, double scale) implements Command {}
