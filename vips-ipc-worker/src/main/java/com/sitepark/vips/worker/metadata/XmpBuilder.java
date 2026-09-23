package com.sitepark.vips.worker.metadata;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a minimal XMP packet from the whitelisted {@link XmpTag} values.
 *
 * <p>The packet is built from scratch rather than edited in place. That is what enforces the
 * whitelist: anything the source packet carried and the whitelist does not name never reaches the
 * output.
 */
final class XmpBuilder {

  private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String PACKET_HEADER =
      "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
          + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">"
          + "<rdf:RDF xmlns:rdf=\"";

  private XmpBuilder() {}

  /**
   * Builds the {@code xmp-data} packet. Returns an empty array when {@code fields} holds no value so
   * callers can skip setting the field rather than embed an empty packet.
   */
  static byte[] buildPacket(Map<XmpTag, List<String>> fields) {
    if (fields.values().stream().allMatch(List::isEmpty)) {
      return new byte[0];
    }

    var xml = new StringBuilder(256);
    xml.append(PACKET_HEADER).append(RDF_NAMESPACE).append("\"><rdf:Description rdf:about=\"\"");
    for (XmpTag.Namespace namespace : usedNamespaces(fields)) {
      xml.append(" xmlns:")
          .append(namespace.prefix())
          .append("=\"")
          .append(namespace.uri())
          .append('"');
    }
    xml.append('>');

    for (XmpTag tag : XmpTag.values()) {
      appendProperty(xml, tag, fields.getOrDefault(tag, List.of()));
    }

    xml.append("</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static Set<XmpTag.Namespace> usedNamespaces(Map<XmpTag, List<String>> fields) {
    Set<XmpTag.Namespace> namespaces = EnumSet.noneOf(XmpTag.Namespace.class);
    for (Map.Entry<XmpTag, List<String>> entry : fields.entrySet()) {
      if (!entry.getValue().isEmpty()) {
        namespaces.add(entry.getKey().namespace());
      }
    }
    return namespaces;
  }

  private static void appendProperty(StringBuilder xml, XmpTag tag, List<String> values) {
    if (values.isEmpty()) {
      return;
    }
    String name = tag.namespace().prefix() + ":" + tag.localName();
    xml.append('<').append(name).append('>');
    escape(xml, values.get(0));
    xml.append("</").append(name).append('>');
  }

  private static void escape(StringBuilder xml, String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> xml.append("&amp;");
        case '<' -> xml.append("&lt;");
        case '>' -> xml.append("&gt;");
        case '"' -> xml.append("&quot;");
        case '\'' -> xml.append("&apos;");
        default -> appendChar(xml, c);
      }
    }
  }

  /**
   * Appends a character, dropping the control characters that XML 1.0 forbids. Metadata copied from
   * a source image can contain them, and a single stray byte would otherwise produce a packet no
   * reader can parse.
   */
  private static void appendChar(StringBuilder xml, char c) {
    boolean allowed =
        c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF);
    if (allowed) {
      xml.append(c);
    }
  }
}
