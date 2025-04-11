package com.walmart.move.nim.receiving.rx.service.v2.posting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.http.HttpHeaders;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.testng.Assert.*;

public class DefaultReceivedPostingServiceTest {

    @Spy
    DefaultReceivedPostingService defaultReceivedPostingService;

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testPublishSerializedData() {
        Throwable exception = Assertions.assertThrows(ReceivingException.class,
                () -> defaultReceivedPostingService.publishSerializedData(new Container(), new Instruction(), MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(   "Unknown instruction receiving method", exception.getMessage());
    }
}