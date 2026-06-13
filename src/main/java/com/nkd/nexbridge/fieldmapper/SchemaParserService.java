package com.nkd.nexbridge.fieldmapper;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class SchemaParserService {

    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";
    private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";

    // -----------------------------------------------------------------------
    // XSD parsing
    // -----------------------------------------------------------------------

    public List<SchemaField> parseXsd(String xsdContent) {
        try {
            Document doc = parseXml(xsdContent);
            List<SchemaField> fields = new ArrayList<>();

            // Collect xs:element and xs:attribute nodes (namespace-aware and prefix-based)
            String[] localNames = {"element", "attribute"};
            for (String localName : localNames) {
                NodeList nodes = doc.getElementsByTagNameNS(XS_NS, localName);
                if (nodes.getLength() == 0) {
                    // Try without namespace (prefix-based documents)
                    nodes = doc.getElementsByTagName("xs:" + localName);
                }
                if (nodes.getLength() == 0) {
                    nodes = doc.getElementsByTagName("xsd:" + localName);
                }
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element el = (Element) nodes.item(i);
                    SchemaField field = elementToSchemaField(el, localName.equals("attribute"));
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }

            return fields;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse XSD content: " + e.getMessage(), e);
        }
    }

    private SchemaField elementToSchemaField(Element el, boolean isAttribute) {
        String name = el.getAttribute("name");
        if (name == null || name.isBlank()) {
            return null;
        }

        String rawType = el.getAttribute("type");
        String mappedType = mapXsdType(rawType);

        boolean required;
        if (isAttribute) {
            String use = el.getAttribute("use");
            required = "required".equals(use);
        } else {
            String minOccurs = el.getAttribute("minOccurs");
            required = minOccurs.isBlank() || Integer.parseInt(minOccurs) > 0;
        }

        String description = extractDocumentation(el);

        return new SchemaField(name, mappedType, required, description);
    }

    private String mapXsdType(String xsdType) {
        if (xsdType == null || xsdType.isBlank()) return "string";
        // Strip namespace prefix
        String local = xsdType.contains(":") ? xsdType.substring(xsdType.indexOf(':') + 1) : xsdType;
        return switch (local) {
            case "integer", "int", "long", "short", "byte",
                 "unsignedInt", "unsignedLong", "unsignedShort", "unsignedByte",
                 "nonNegativeInteger", "positiveInteger", "negativeInteger" -> "integer";
            case "decimal", "float", "double" -> "number";
            case "date", "dateTime", "time", "gYear", "gMonth", "gDay" -> "date";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }

    private String extractDocumentation(Element el) {
        // Look for xs:annotation/xs:documentation child
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String childLocal = childEl.getLocalName() != null
                        ? childEl.getLocalName()
                        : childEl.getTagName().replaceFirst("^[^:]+:", "");
                if ("annotation".equals(childLocal)) {
                    NodeList annotChildren = childEl.getChildNodes();
                    for (int j = 0; j < annotChildren.getLength(); j++) {
                        Node annotChild = annotChildren.item(j);
                        if (annotChild.getNodeType() == Node.ELEMENT_NODE) {
                            Element annotEl = (Element) annotChild;
                            String annotLocal = annotEl.getLocalName() != null
                                    ? annotEl.getLocalName()
                                    : annotEl.getTagName().replaceFirst("^[^:]+:", "");
                            if ("documentation".equals(annotLocal)) {
                                return annotEl.getTextContent().trim();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // WSDL parsing
    // -----------------------------------------------------------------------

    public WsdlParseResult parseWsdl(String wsdlContent) {
        try {
            Document doc = parseXml(wsdlContent);

            List<String> operations = new ArrayList<>();
            Map<String, List<SchemaField>> inputFields = new LinkedHashMap<>();
            Map<String, List<SchemaField>> outputFields = new LinkedHashMap<>();

            // --- Collect messages: name -> list of parts ---
            Map<String, List<SchemaField>> messages = new LinkedHashMap<>();
            NodeList messageNodes = getElementsByLocalName(doc, "message");
            for (int i = 0; i < messageNodes.getLength(); i++) {
                Element msgEl = (Element) messageNodes.item(i);
                String msgName = msgEl.getAttribute("name");
                if (msgName == null || msgName.isBlank()) continue;

                List<SchemaField> parts = new ArrayList<>();
                NodeList partNodes = msgEl.getElementsByTagNameNS(WSDL_NS, "part");
                if (partNodes.getLength() == 0) partNodes = getChildrenByLocalName(msgEl, "part");
                for (int j = 0; j < partNodes.getLength(); j++) {
                    Element partEl = (Element) partNodes.item(j);
                    String partName = partEl.getAttribute("name");
                    String partType = partEl.hasAttribute("type") ? partEl.getAttribute("type") : partEl.getAttribute("element");
                    String mappedType = mapXsdType(partType);
                    if (partName != null && !partName.isBlank()) {
                        parts.add(new SchemaField(partName, mappedType, true, null));
                    }
                }
                messages.put(msgName, parts);
            }

            // --- Collect portType operations to find input/output message refs ---
            // We use wsdl:portType/wsdl:operation as canonical source
            NodeList portTypeNodes = getElementsByLocalName(doc, "portType");
            for (int i = 0; i < portTypeNodes.getLength(); i++) {
                Element portType = (Element) portTypeNodes.item(i);
                NodeList opNodes = getChildrenByLocalName(portType, "operation");
                for (int j = 0; j < opNodes.getLength(); j++) {
                    Element opEl = (Element) opNodes.item(j);
                    String opName = opEl.getAttribute("name");
                    if (opName == null || opName.isBlank()) continue;

                    if (!operations.contains(opName)) {
                        operations.add(opName);
                    }

                    // input
                    NodeList inputNodes = getChildrenByLocalName(opEl, "input");
                    if (inputNodes.getLength() > 0) {
                        Element inputEl = (Element) inputNodes.item(0);
                        String msgRef = stripPrefix(inputEl.getAttribute("message"));
                        inputFields.put(opName, messages.getOrDefault(msgRef, Collections.emptyList()));
                    }

                    // output
                    NodeList outputNodes = getChildrenByLocalName(opEl, "output");
                    if (outputNodes.getLength() > 0) {
                        Element outputEl = (Element) outputNodes.item(0);
                        String msgRef = stripPrefix(outputEl.getAttribute("message"));
                        outputFields.put(opName, messages.getOrDefault(msgRef, Collections.emptyList()));
                    }
                }
            }

            // Fallback: if no portType, collect operations from wsdl:binding/wsdl:operation
            if (operations.isEmpty()) {
                NodeList opNodes = getElementsByLocalName(doc, "operation");
                for (int i = 0; i < opNodes.getLength(); i++) {
                    Element opEl = (Element) opNodes.item(i);
                    String opName = opEl.getAttribute("name");
                    if (opName != null && !opName.isBlank() && !operations.contains(opName)) {
                        operations.add(opName);
                    }
                }
            }

            return new WsdlParseResult(operations, inputFields, outputFields);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse WSDL content: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Document parseXml(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity processing for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    /** Search the entire document for elements matching a local name, trying NS-aware first then prefix-based. */
    private NodeList getElementsByLocalName(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS(WSDL_NS, localName);
        if (nodes.getLength() > 0) return nodes;
        nodes = doc.getElementsByTagName("wsdl:" + localName);
        if (nodes.getLength() > 0) return nodes;
        return doc.getElementsByTagName(localName);
    }

    /** Get direct children of an element matching a local name (ignores namespace). */
    private NodeList getChildrenByLocalName(Element parent, String localName) {
        // Return a synthetic NodeList by collecting matching children
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childEl = (Element) child;
                String childLocal = childEl.getLocalName() != null
                        ? childEl.getLocalName()
                        : childEl.getTagName().replaceFirst("^[^:]+:", "");
                if (localName.equals(childLocal)) {
                    result.add(childEl);
                }
            }
        }
        return new SimpleNodeList(result);
    }

    private String stripPrefix(String value) {
        if (value == null) return "";
        return value.contains(":") ? value.substring(value.indexOf(':') + 1) : value;
    }

    /** Minimal NodeList implementation backed by a List<Element>. */
    private static class SimpleNodeList implements NodeList {
        private final List<Element> elements;
        SimpleNodeList(List<Element> elements) { this.elements = elements; }
        @Override public Node item(int index) { return elements.get(index); }
        @Override public int getLength() { return elements.size(); }
    }
}
