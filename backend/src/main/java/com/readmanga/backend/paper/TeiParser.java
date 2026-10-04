package com.readmanga.backend.paper;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class TeiParser {

    private static final String TEI = "http://www.tei-c.org/ns/1.0";

    public PaperStructure parse(String teiXml) {
        Element root = readXml(teiXml).getDocumentElement();

        String title = textOf(firstElement(root, "title"));
        List<PaperStructure.Section> sections = new ArrayList<>();

        Element abstractElement = firstElement(root, "abstract");
        if (abstractElement != null) {
            sections.add(new PaperStructure.Section("Abstract", sentencesIn(abstractElement, 1)));
        }

        Element body = firstElement(root, "body");
        if (body != null) {
            for (Element div : childElements(body, "div")) {
                String sectionTitle = textOf(firstElement(div, "head"));
                int nextNumber = sections.stream().mapToInt(section -> section.sentences().size()).sum() + 1;
                sections.add(new PaperStructure.Section(sectionTitle, sentencesIn(div, nextNumber)));
            }
        }

        return new PaperStructure(title, sections);
    }

    /** 문장마다 논문 전체에서 이어지는 번호를 붙인다. 이 번호가 원문 근거를 가리키는 기준이 된다. */
    private List<PaperStructure.Sentence> sentencesIn(Element parent, int firstNumber) {
        List<PaperStructure.Sentence> sentences = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS(TEI, "s");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element s = (Element) nodes.item(i);
            sentences.add(new PaperStructure.Sentence(firstNumber + i, textOf(s), pageOf(s)));
        }
        return sentences;
    }

    private Integer pageOf(Element s) {
        String coords = s.getAttribute("coords");
        if (coords.isEmpty()) {
            return null;
        }
        return Integer.parseInt(coords.split(",")[0]);
    }

    private Element firstElement(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS(TEI, name);
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null;
    }

    private List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private String textOf(Element element) {
        return element == null ? "" : element.getTextContent().replaceAll("\\s+", " ").trim();
    }

    private Document readXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalStateException("GROBID 결과(XML)를 읽지 못했습니다", e);
        }
    }
}
