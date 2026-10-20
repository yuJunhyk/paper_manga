-- [2026-10-20] Flyway 도입 후로는 쓰지 않는다. 이 변경은 db/migration/V1__init.sql에 들어 있다. 기록으로만 남긴다.
-- 작업 상태에 CANCELLED(취소됨)를 추가한다 (2026-10-05, 작업 취소 기능).
-- Hibernate는 테이블을 처음 만들 때 enum 칸에 허용 값 검사(CHECK)를 붙이는데, ddl-auto=update는 새 칸은 추가해도
-- 이미 있는 검사는 고치지 않는다. 그래서 이미 만들어진 DB에는 이 문장을 한 번 직접 실행해야 한다.
-- (새로 만드는 DB는 Hibernate가 CANCELLED를 포함해 만든다. 나중에 Flyway 같은 마이그레이션 도구로 옮길 대상)
ALTER TABLE paper_job DROP CONSTRAINT IF EXISTS paper_job_status_check;
ALTER TABLE paper_job ADD CONSTRAINT paper_job_status_check
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));
