package io.onedev.server.util.validation; // (rank 413) copied from https://github.com/theonedev/onedev/blob/f34af86d0d952ec210e84e98fbd02102e6fe230a/server-core/src/main/java/io/onedev/server/util/validation/SecretNameValidator.java

import java.util.function.Function;
import java.util.regex.Pattern;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import io.onedev.commons.utils.StringUtils;
import io.onedev.server.util.interpolative.Interpolative;
import io.onedev.server.util.validation.annotation.SecretName;

public class SecretNameValidator implements ConstraintValidator<SecretName, String> {

	public static final Pattern PATTERN = Pattern.compile("\\w([\\w-\\.]*\\w)?");
	
	private boolean interpolative;
	
	private String message;
	
	@Override
	public void initialize(SecretName constaintAnnotation) {
		interpolative = constaintAnnotation.interpolative();
		message = constaintAnnotation.message();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext constraintContext) {
		if (value == null)
			return true;
		
		if (interpolative && !Interpolated.get()) try {
			value = StringUtils.unescape(Interpolative.parse(value).interpolateWith(new Function<String, String>() {

				@Override
				public String apply(String t) {
					return "a";
				}
				
			}));
		} catch (Exception e) {
			return true; // will be handled by interpolative validator
		}
		
		if (!PATTERN.matcher(value).matches()) {
			String message = this.message;
			if (message.length() == 0) {
				message = "Should start and end with alphanumeric or underscore. "
						+ "Only alphanumeric, underscore, dash, and dot are allowed in the middle.";
			}
			constraintContext.buildConstraintViolationWithTemplate(message).addConstraintViolation();
			return false;
		} else {
			return true;
		}
	}
	
}
