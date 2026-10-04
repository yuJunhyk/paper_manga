package com.readmanga.backend.comic.plan;

import java.util.List;

/** 기승전결 중 한 쪽의 역할. */
public record PageRole(String name, String meaning) {

    private static final PageRole INTRO = new PageRole("기", "문제 제기: 누리가 겪는 사건으로 시작해, 이 연구가 왜 필요했는지 보여 준다");
    private static final PageRole DEVELOPMENT = new PageRole("승", "해결 방법: 연구자들이 생각해 낸 방법을 차근차근 이해한다");
    private static final PageRole TURN = new PageRole("전", "반전과 절정: 실험해 보니 어떤 놀라운 결과가 나왔는지 긴장감 있게 보여 준다");
    private static final PageRole DEVELOPMENT_AND_TURN = new PageRole("승·전", "해결 방법과 놀라운 실험 결과를 차근차근, 긴장감 있게 보여 준다");
    private static final PageRole CONCLUSION = new PageRole("결", "마무리: 결과의 의미와 한계를 정리하고, 처음 사건이 해결되며 누리가 깨닫는다");

    /** 4쪽이면 기·승·전·결, 3쪽이면 기·승전·결. */
    public static List<PageRole> forPages(int pages) {
        return pages >= 4
                ? List.of(INTRO, DEVELOPMENT, TURN, CONCLUSION)
                : List.of(INTRO, DEVELOPMENT_AND_TURN, CONCLUSION);
    }
}
