package com.readmanga.backend.paper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class GrobidClient {

    private final RestClient restClient;

    public GrobidClient(@Value("${grobid.url}") String grobidUrl) {
        this.restClient = RestClient.create(grobidUrl);
    }

    public String processFulltext(byte[] pdf, String fileName) {
        ByteArrayResource pdfResource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("input", pdfResource);
        form.add("segmentSentences", "1");
        form.add("teiCoordinates", "s");
        form.add("teiCoordinates", "head");

        return restClient.post()
                .uri("/api/processFulltextDocument")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(String.class);
    }
}
