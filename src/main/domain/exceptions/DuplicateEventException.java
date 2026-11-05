package domain.exceptions;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String name) { super("An event with the name '" + name + "' already exists in this production company."); }
}
