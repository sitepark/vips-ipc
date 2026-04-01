package com.sitepark.vips.worker;

import java.io.IOException;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

class VipsAvailableCondition implements ExecutionCondition {

  private static final String CACHE_KEY = "vips.available";
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(VipsAvailableCondition.class);

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    boolean available =
        context
            .getRoot()
            .getStore(NAMESPACE)
            .getOrComputeIfAbsent(CACHE_KEY, k -> isVipsAvailable(), Boolean.class);
    if (available) {
      return ConditionEvaluationResult.enabled("libvips is available");
    }
    return ConditionEvaluationResult.disabled(
        "libvips not found on PATH (vips --version failed) – skipping integration test");
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  private static boolean isVipsAvailable() {
    try {
      return new ProcessBuilder("vips", "--version").redirectErrorStream(true).start().waitFor()
          == 0;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (IOException e) {
      return false;
    }
  }
}
