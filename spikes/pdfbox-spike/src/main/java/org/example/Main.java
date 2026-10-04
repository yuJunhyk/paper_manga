package org.example;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class Main {
    public static void main(String[] args) throws IOException{
        File pdf = new File("/Users/yujunhyeog/orca/workspaces/new_project/read_manga/samples/data-comics-education-2607.02361.pdf");
        System.out.println("파일 경로 : " + pdf.getAbsolutePath());
        System.out.println("파일 존재 : " + pdf.exists());

        try(PDDocument document = Loader.loadPDF(pdf)){
            System.out.println("전체 페이지 수 : " + document.getNumberOfPages());

            PDFTextStripper stripper = new PDFTextStripper();

            String text = stripper.getText(document);

            Path output = Path.of(pdf.getName().replace(".pdf", ".txt"));
            Files.writeString(output, text);
            System.out.println("저장 완료 : " + output.toAbsolutePath());
        }

    }


}