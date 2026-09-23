package com.sitepark.vips.worker.metadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parses the whitelisted {@link XmpTag} values out of a libvips {@code xmp-data} packet.
 *
 * <p>The packet comes from arbitrary uploaded images, so the parser is hardened against XXE and
 * entity-expansion attacks and never throws on malformed input — it returns whatever it could read.
 *
 * <p>Both XMP serialisations are handled: a property written as a child element of {@code
 * rdf:Description}, and the shorthand form where a simple property is an attribute on it.
 */
@SuppressWarnings("PMD.UseConcurrentHashMap") // EnumMap: method-local, never shared
final class XmpReader {

  private static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  private static final String DISALLOW_DOCTYPE =
      "http://apache.org/xml/features/disallow-doctype-decl";
  private static final String EXTERNAL_GENERAL_ENTITIES =
      "http://xml.org/sax/features/external-general-entities";
  private static final String EXTERNAL_PARAMETER_ENTITIES =
      "http://xml.org/sax/features/external-parameter-entities";
  private static final String LOAD_EXTERNAL_DTD =
      "http://apache.org/xml/features/nonvalidating/load-external-dtd";

  private XmpReader() {}

  /** Parses {@code xmpData}, keeping only whitelisted properties. Never returns {@code null}. */
  static Map<XmpTag, List<String>> parse(byte[] xmpData) {
    Map<XmpTag, List<String>> fields = new EnumMap<>(XmpTag.class);
    if (xmpData == null || xmpData.length == 0) {
      return fields;
    }
    Document document = parseDocument(xmpData);
    if (document == null) {
      return fields;
    }
    NodeList descriptions = document.getElementsByTagNameNS(RDF_NAMESPACE, "Description");
    for (int i = 0; i < descriptions.getLength(); i++) {
      Element description = (Element) descriptions.item(i);
      readAttributeProperties(description, fields);
      readElementProperties(description, fields);
    }
    return fields;
  }

  private static Document parseDocument(byte[] xmpData) {
    // A packet carries leading and trailing xpacket processing instructions plus padding; the DOM
    // parser tolerates both. Malformed metadata must never fail an image operation, so every parse
    // failure degrades to "no metadata found".
    try {
      return secureBuilder().parse(new ByteArrayInputStream(xmpData));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      return null;
    }
  }

  private static DocumentBuilder secureBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature(DISALLOW_DOCTYPE, true);
    factory.setFeature(EXTERNAL_GENERAL_ENTITIES, false);
    factory.setFeature(EXTERNAL_PARAMETER_ENTITIES, false);
    factory.setFeature(LOAD_EXTERNAL_DTD, false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setNamespaceAware(true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder();
  }

  /** Reads the shorthand form, where a simple property is an attribute on rdf:Description. */
  private static void readAttributeProperties(
      Element description, Map<XmpTag, List<String>> fields) {
    NamedNodeMap attributes = description.getAttributes();
    for (int i = 0; i < attributes.getLength(); i++) {
      Attr attribute = (Attr) attributes.item(i);
      XmpTag tag = XmpTag.of(attribute.getNamespaceURI(), attribute.getLocalName());
      if (tag != null) {
        add(fields, tag, attribute.getValue());
      }
    }
  }

  private static void readElementProperties(Element description, Map<XmpTag, List<String>> fields) {
    NodeList children = description.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) {
        continue;
      }
      XmpTag tag = XmpTag.of(child.getNamespaceURI(), child.getLocalName());
      if (tag == null) {
        continue;
      }
      List<String> items = readContainerItems((Element) child);
      if (items.isEmpty()) {
        add(fields, tag, child.getTextContent());
      } else {
        for (String item : items) {
          add(fields, tag, item);
        }
      }
    }
  }

  /** Returns the {@code rdf:li} values of a contained Alt/Seq/Bag, or an empty list for a literal. */
  private static List<String> readContainerItems(Element property) {
    List<String> items = new ArrayList<>();
    NodeList listItems = property.getElementsByTagNameNS(RDF_NAMESPACE, "li");
    for (int i = 0; i < listItems.getLength(); i++) {
      items.add(listItems.item(i).getTextContent());
    }
    return items;
  }

  /** Records the first value seen for {@code tag}; every whitelisted property is single-valued. */
  private static void add(Map<XmpTag, List<String>> fields, XmpTag tag, String value) {
    if (value != null && !value.isEmpty()) {
      fields.putIfAbsent(tag, List.of(value));
    }
  }
}
