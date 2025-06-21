package payment.service;

import com.google.api.services.androidpublisher.model.ProductPurchase;
import common.domain.payment.Payment;
import common.domain.payment.PaymentProduct;
import common.domain.payment.PaymentProductType;
import common.domain.member.Member;
import common.kafka_message.*;
import common.kafka_message.alarm.AlarmMessageType;
import common.kafka_message.alarm.PaymentFailedMessage;
import common.kafka_message.alarm.PaymentSuccessMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import payment.exception.MemberNotFoundException;
import payment.exception.PaymentException;
import payment.kafka.KafkaProducerService;
import payment.repository.MemberRepository;
import payment.repository.PaymentProductRepository;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class PaymentService {

    private final PaymentProductRepository paymentProductRepository;
    private final MemberRepository memberRepository;
    private final GooglePaymentHelper googlePaymentHelper;
    private final KafkaProducerService kafkaProducerService;

    // TODO : 추후 실제값 적용
    public static final String PACKAGE_NAME = "com.example.myapp";

    @Transactional
    public Long verifyProductPurchase(Long memberId, PaymentProductType type, String purchaseToken) {
        // purchaseToken 유일성 체크
        validateDB(purchaseToken);

        PaymentProduct paymentProduct = getPaymentProduct(type);
        // paymentProduct 생성
        makePayment(paymentProduct, memberId, purchaseToken);

        // Google 인앱 결제 영수증 검증
        ProductPurchase purchase = googlePaymentHelper.verifyProductPurchase(PACKAGE_NAME, purchaseToken, paymentProduct.getProductId());

        if (!validateConsumable(purchase)) {
            Payment payment = getPaymentByPurchaseToken(purchaseToken);

            // paymentProduct 무효화
            paymentProduct.invalidatePayment(payment);

            // 알람 생성
            Member member = getMember(memberId);
            makeFailureMessage(purchaseToken, member, paymentProduct);
        }

        // PointChangedEvent 생성, 성공 시에만 consume, 실패 시엔 따로 refund 하지 않음.
        // 구글에서 consume 하지 않은 경우 3일 후 자동 결제 취소 이용
        makePointEvent(memberId, type, purchaseToken);

        return memberId;
    }

    private void makePayment(PaymentProduct paymentProduct, Long memberId, String purchaseToken) {
        int price = paymentProduct.getPaymentProductType().getPrice();

        paymentProduct.makePayment(memberId, price, purchaseToken);
        paymentProductRepository.save(paymentProduct);
    }

    private boolean validateConsumable(ProductPurchase purchase) {
        if(purchase.getPurchaseState() == 0 && purchase.getConsumptionState() == 0) {
            return true;
        }
        return false;
    }

    // 보상 트랜잭션; 사용자 아쿠 증가 성공 시 소비 처리
    @Transactional
    public void consumePurchase(String purchaseToken) {
        Payment payment = getPaymentByPurchaseToken(purchaseToken);
        PaymentProduct paymentProduct = payment.getPaymentProduct();

        // 소비 처리
        googlePaymentHelper.consumeProduct(PACKAGE_NAME, paymentProduct.getProductId(), purchaseToken);
        paymentProduct.acceptPayment(payment);

        // 성공 알림
        Member member = getMember(payment.getMemberValue().getId());
        makeSuccessMessage(purchaseToken, member, paymentProduct);
        log.info("{} purchase completed", purchaseToken);
    }

    // 보상 트랜잭션; 사용자 아쿠 증가 실패 시 실패 알림
    @Transactional
    public void pointChargeFailed(String purchaseToken) {
        log.info("{} point charge failed", purchaseToken);

        Payment payment = getPaymentByPurchaseToken(purchaseToken);
        PaymentProduct paymentProduct = payment.getPaymentProduct();

        Member member = getMember(payment.getMemberValue().getId());

        // STATUS DENIED로 변경
        paymentProduct.denyPayment(payment);

        // 알람 생성
        makeFailureMessage(purchaseToken, member, paymentProduct);
    }

    private void makeFailureMessage(String purchaseToken, Member member, PaymentProduct paymentProduct) {
        kafkaProducerService.sendMessage(KafkaTopic.ALARM,
                new PaymentFailedMessage(List.of(member.getFirebaseToken()),
                        AlarmMessageType.PAYMENT_FAILED,
                        purchaseToken,
                        paymentProduct.getPaymentProductType().getPrice(),
                        paymentProduct.getPaymentProductType().getPoint()
                )
        );
    }

    private void makeSuccessMessage(String purchaseToken, Member member, PaymentProduct paymentProduct) {
        kafkaProducerService.sendMessage(KafkaTopic.ALARM,
                new PaymentSuccessMessage(List.of(member.getFirebaseToken()),
                        AlarmMessageType.PAYMENT_FAILED,
                        purchaseToken,
                        paymentProduct.getPaymentProductType().getPrice(),
                        paymentProduct.getPaymentProductType().getPoint()
                )
        );
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException());
    }

    private void makePointEvent(Long memberId, PaymentProductType type, String purchaseToken) {
        // 아쿠 증가 이벤트
        kafkaProducerService.sendMessage(KafkaTopic.alarm,
                new PaymentPointChangedMessage(memberId,
                        PointChangedType.PLUS,
                        type.getPoint(),
                        purchaseToken
                )
        );
    }

    private Payment getPaymentByPurchaseToken(String purchaseToken) {
        return paymentProductRepository.findPaymentByPaymentPurchaseToken(purchaseToken)
                .orElseThrow(PaymentException::new);
    }

    private PaymentProduct getPaymentProduct(PaymentProductType type) {
        return paymentProductRepository.findByPaymentProductType(type)
                .orElseThrow(PaymentException::new);
    }

    private void validateDB(String purchaseToken) {
        // DB 내 purchaseToken 중복되는지 확인, false -> 유일 보장
        if (paymentProductRepository.existsByPurchaseToken(purchaseToken)) {
            throw new PaymentException();
        }
    }

}
