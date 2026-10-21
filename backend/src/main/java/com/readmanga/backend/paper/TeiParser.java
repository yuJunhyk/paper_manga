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
import java.util.regex.Pattern;

@Component
public class TeiParser {

    private static final String TEI = "http://www.tei-c.org/ns/1.0";
    /**
     * 문장 앞에 붙은 문단 소제목: "Departure to Latent Space Our approach starts …", "Limitations While LDMs …".
     * GROBID가 소제목을 첫 문장에 붙여 주어서, AI가 'Departure to Latent Space'를 '잠재 공간으로 이동한다'는 내용으로 읽었다(204번 2쪽 1컷).
     * 대문자로 시작하는 짧은 낱말 묶음 뒤에 문장 첫 낱말(Our, We, While …)이 오면 소제목으로 보고 뗀다.
     */
    private static final Pattern RUN_IN_HEADING = Pattern.compile(
            "^[A-Z][A-Za-z0-9-]*(?:\\s+(?:[A-Z][A-Za-z0-9-]*|of|to|and|the|in|for|with|on|via|from|&)){0,6}\\s+"
                    + "(?=(?:Our|We|The|This|These|To|For|As|While|Since|Here|Although|Unlike|Given|Finally|Moreover|However|Next|Specifically|Because|Thus|In)\\s)");

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
            sentences.add(new PaperStructure.Sentence(firstNumber + i, withoutRunInHeading(textOf(s)), pageOf(s)));
        }
        return sentences;
    }

    static String withoutRunInHeading(String text) {
        return RUN_IN_HEADING.matcher(text).replaceFirst("");
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
