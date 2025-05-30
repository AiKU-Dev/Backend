package aiku_main.controller.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.Duration;
import java.time.LocalDateTime;

public class ValidScheduleTimeValidator implements ConstraintValidator<ValidScheduleTime, LocalDateTime> {
    @Override
    public void initialize(ValidScheduleTime constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(LocalDateTime localDateTime, ConstraintValidatorContext constraintValidatorContext) {
        if(localDateTime == null || Duration.between(LocalDateTime.now(), localDateTime).toMinutes() < 40){
            return false;
        }
        return true;
    }
}
