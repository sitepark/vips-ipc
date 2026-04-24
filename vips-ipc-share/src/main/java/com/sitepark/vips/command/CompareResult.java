package com.sitepark.vips.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
@SuppressFBWarnings("EI_EXPOSE_REP")
public record CompareResult(
    List<CompareResult.ChannelDifference> channels,
    double totalMae,
    double normalizedTotalMae,
    CompareResult.DimensionMismatch dimensionMismatch)
    implements Result {

  public CompareResult {
    if (channels != null) {
      channels = List.copyOf(channels);
    }
  }

  public record ChannelDifference(String name, double mae, double normalizedMae) {}

  public record DimensionMismatch(int widthA, int heightA, int widthB, int heightB) {}
}
