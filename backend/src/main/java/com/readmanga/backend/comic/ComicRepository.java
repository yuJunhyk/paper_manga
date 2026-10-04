package com.readmanga.backend.comic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 만화는 쪽·컷·대사를 함께 저장하므로, 저장소는 만화(Comic) 하나만 둔다. */
public interface ComicRepository extends JpaRepository<Comic, Long> {

    Optional<Comic> findByJob_Id(Long jobId);
}
