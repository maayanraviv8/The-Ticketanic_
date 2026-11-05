package domain.exceptions;

public class MemberHasRoleException extends RuntimeException {
    public MemberHasRoleException(String message) {
        super("Member " +message+" already has a role in this company");
    }
}
