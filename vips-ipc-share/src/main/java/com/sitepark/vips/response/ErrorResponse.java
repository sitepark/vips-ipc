package com.sitepark.vips.response;

public record ErrorResponse(String message, String stackTrace) implements Response {}
