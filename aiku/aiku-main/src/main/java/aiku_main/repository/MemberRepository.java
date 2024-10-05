package aiku_main.repository;

import common.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByNickname(String recommenderNickname);

    Optional<Member> findByKakaoId(Long aLong);

    boolean existsByNickname(String nickname);
}
