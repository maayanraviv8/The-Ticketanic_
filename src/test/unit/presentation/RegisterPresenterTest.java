package unit.presentation;

import application.service.MemberService;
import domain.exceptions.EmailAlreadyRegisteredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import presentation.presenter.RegisterPresenter;
import presentation.Result;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterPresenterTest {

    private static final String USERNAME = "user123";
    private static final String EMAIL = "user123@mail.com";
    private static final String PASSWORD = "Password1!";

    private MemberService memberService;
    private RegisterPresenter presenter;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        presenter = new RegisterPresenter(memberService);
    }

    @Test
    void givenValidDetails_whenRegister_thenReturnsOk() {
        when(memberService.register(USERNAME, EMAIL, PASSWORD)).thenReturn(true);

        Result<Void> result = presenter.register(USERNAME, EMAIL, PASSWORD);

        assertInstanceOf(Result.Ok.class, result);
    }

    @Test
    void givenExistingEmail_whenRegister_thenReturnsFail() {
        when(memberService.register(USERNAME, EMAIL, PASSWORD))
                .thenThrow(new EmailAlreadyRegisteredException("An account with that email already exists"));

        Result<Void> result = presenter.register(USERNAME, EMAIL, PASSWORD);

        assertInstanceOf(Result.Fail.class, result);
        assertEquals("An account with that email already exists", ((Result.Fail<Void>) result).message());
    }

    @Test
    void givenUnexpectedError_whenRegister_thenReturnsGenericFail() {
        when(memberService.register(USERNAME, EMAIL, PASSWORD))
                .thenThrow(new RuntimeException("boom"));

        Result<Void> result = presenter.register(USERNAME, EMAIL, PASSWORD);

        assertInstanceOf(Result.Fail.class, result);
        assertEquals("Something went wrong. Please try again.", ((Result.Fail<Void>) result).message());
    }
}
