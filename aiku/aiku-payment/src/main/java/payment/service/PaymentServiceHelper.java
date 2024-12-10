package payment.service;

import common.domain.PaymentProduct;
import common.domain.member.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import payment.exception.MemberNotFoundException;
import payment.repository.MemberRepository;
import payment.repository.PaymentProductRepository;

@RequiredArgsConstructor
@Component
public class PaymentServiceHelper {

    private final PaymentProductRepository paymentProductRepository;
    private final MemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void makePayment(PaymentProduct paymentProduct, Long memberId, String purchaseToken) {
        int price = paymentProduct.getPaymentProductType().getPrice();
        Member member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);

        paymentProduct.makePayment(member, price, purchaseToken);

        paymentProductRepository.save(paymentProduct);
    }

}
