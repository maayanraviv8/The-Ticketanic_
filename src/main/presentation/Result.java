package presentation;

public sealed interface Result<T> permits Result.Ok, Result.Fail {

    record Ok<T>(T value) implements Result<T> {}

    record Fail<T>(String message) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> fail(String message) {
        return new Fail<>(message);
    }
}
