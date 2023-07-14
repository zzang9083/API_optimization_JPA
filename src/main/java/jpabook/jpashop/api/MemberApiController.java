package jpabook.jpashop.api;

import jpabook.jpashop.domain.Member;
import jpabook.jpashop.service.MemberService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;

//  @Controller
//+ @ResponseBody : response를 json이나 xml로 바로 보내자
@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;


    /**
     * V1    : 회원을 조회하는 API
     *
     * 문제점 : 조회하고자 하는 정보가 아니더라도 '모든' 정보가 API 응답으로 노출된다.
     *         응답 엔티티 필드를 바꾸면, 응답 스펙이 바뀌어버린다.
     *
     *  SOL 1: 노출 안하려고 하는 필드를 JsonIgnore 에너테이션 처리 - 이 필드를 다른 곳에서 쓴다면? 답이 없다.
     *
     *
     * */
    @GetMapping("/api/v1/members")
    public List<Member> membersV1() {
        return memberService.findMembers();
    }

    /**
     *
     * V2    : 응답으로 나가는 엔티티를 API용으로 별도 생성하여, 내부 객체와 매핑하여 변환.
     *         Result라는 제너릭 타입 필드를 가지고 있는 엔티티로 한번더 감싸서 리턴하도록했다.(확장성)
     *
     * 개선점 :  응답으로 노출하고 싶은 거만 나갈 수 있다.(유지보수성이 좋다.)
     *         제너릭 필드를 가지고 있는 엔티티로 한번 더 감싸면서, 추가적으로 생기는 요구 필드(count 등등)에 대해 유연할 수 있다.
     *
     * */
    @GetMapping("/api/v2/members")
    public Result membersV2() {
        List<Member> findMembers = memberService.findMembers();

        List<MemberDto> collect = findMembers.stream()
                                                .map(m -> new MemberDto(m.getName()))
                                                .collect(Collectors.toList());

        return new Result(collect);
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private T data;
    }

    @Data
    @AllArgsConstructor
    static class MemberDto{
        private String name;
    }


    /**
     * V1    : 회원을 등록하는 API
     *
     * 문제점 : presentation 영역의 엔티티를 직접 사용한다.
     *         엔티티를 변경을 함으로써, API 스펙이 변경된다.
     *
     *   SOL : API 스펙을 위한 별도의 엔티티를 생성해야한다.(엔티티를 외부에 노출하지말자.)
     * */
    @PostMapping("/api/v1/members")
    public CreateMemberResponse saveMemberV1(@RequestBody @Valid Member member) {
        //@RequestBody : 요청을 통해 json 형태로 온 데이터를 객체에 매핑시켜줌
        //@Valid : 객체에 대한 유효성 검증

        Long id = memberService.join(member);

        return new CreateMemberResponse(id);

    }

    /**
     *
     * V2    : 요청으로 들어오는 엔티티를 API용으로 별도 생성하여, 내부 객체와 매핑했다.
     *
     * 개선점 : API 스펙에 종속되지않고, API 스펙이 바뀌면 매핑되는 VO를 변경사항에 맞게 바꾸면된다.
     *         개발자가 굳이 설계문서를 보지 않아도, API 엔티티로 요청으로 들어오는 값을 알 수 있고, 정의(Vaild 체크)할 수 있다.
     *
     * */
    @PostMapping("/api/v2/members")
    public CreateMemberResponse saveMemberV2(@RequestBody @Valid CreateMemberRequest request) {
        Member member = new Member();
        member.setName(request.getName());

        Long id = memberService.join(member);

        return new CreateMemberResponse(id);
    }

    @PutMapping("/api/v2/member/{id}")
    public UpdateMemberResponse updateMemberV2
            (@PathVariable("id")Long id
                    , @RequestBody @Valid UpdateMemeberRequest request) {

        /**
         * 서비스에서 update 했을 때, 바뀐 값을 리턴해줄까?
         * -> NOPE. 명령(갱신)와 쿼리(조회)가 동시에 오면 동작이 애매하다. 명령이면 그냥 CALL하는걸로 끝내자.
         *          아니면 걍 한번 더 호출해서 업데이트한걸 조회한 값을 리턴해주자
         */
        memberService.update(id, request.getName());
        Member findMember = memberService.findOne(id); // 바뀐거 한번 더 조회해서 리턴

        return new UpdateMemberResponse(findMember.getId(), findMember.getName());
    }

    @Data
    static class CreateMemberResponse {
        private Long id;

        public CreateMemberResponse(Long id) {
            this.id = id;
        }
    }

    @Data
    static class CreateMemberRequest {
        private String name;

        public CreateMemberRequest(String name) {
            this.name = name;
        }
    }



    @Data
    static class UpdateMemeberRequest {
        private String name;

        public UpdateMemeberRequest(String name) {
            this.name = name;
        }
    }

    @Data
    static class UpdateMemberResponse {

        private Long id;
        private String name;

        public UpdateMemberResponse(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
