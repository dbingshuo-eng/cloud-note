package com.clouddisk.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SharePasswordValidator
        implements ConstraintValidator<ValidSharePassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return SharePasswordPolicy.hasValidLength(password);
    }
}
